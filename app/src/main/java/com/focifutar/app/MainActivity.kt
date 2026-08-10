package com.focifutar.app

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ==========================================
// TÉMA SZÍNEK (SÖTÉT ÉS VILÁGOS MÓD)
// ==========================================
data class AppColors(
    val background: Color,
    val cardBackground: Color,
    val accentPrimary: Color,
    val accentRed: Color,
    val accentYellow: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val border: Color
)

val DarkColors = AppColors(
    background = Color(0xFF101214),
    cardBackground = Color(0xFF1A1D21),
    accentPrimary = Color(0xFF00FF66),
    accentRed = Color(0xFFFF3366),
    accentYellow = Color(0xFFFFCC00),
    textPrimary = Color(0xFFFFFFFF),
    textMuted = Color(0xFF8C96A0),
    border = Color(0xFF2A2E33)
)

val LightColors = AppColors(
    background = Color(0xFFF1F5F9),
    cardBackground = Color(0xFFFFFFFF),
    accentPrimary = Color(0xFF0284C7),
    accentRed = Color(0xFFE11D48),
    accentYellow = Color(0xFFD97706),
    textPrimary = Color(0xFF0F172A),
    textMuted = Color(0xFF64748B),
    border = Color(0xFFE2E8F0)
)

data class BetSlipItem(
    val matchId: String,
    val matchTitle: String,
    val choiceName: String,
    val odds: Double
)

data class SavedBet(
    val id: String,
    val items: List<BetSlipItem>,
    val stake: Double,
    val totalOdds: Double,
    val potentialWin: Double,
    val dateStr: String,
    var status: String = "FÜGGŐBEN",
    var isPaidOut: Boolean = false
)

fun isDarkModeSaved(context: Context): Boolean {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("is_dark_mode", true)
}

fun saveDarkMode(context: Context, isDark: Boolean) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("is_dark_mode", isDark).apply()
}

fun getFavoriteLeagueIds(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getStringSet("favorite_leagues", emptySet()) ?: emptySet()
}

fun toggleFavoriteLeague(context: Context, leagueKey: String) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val current = prefs.getStringSet("favorite_leagues", emptySet())?.toMutableSet() ?: mutableSetOf()
    if (current.contains(leagueKey)) {
        current.remove(leagueKey)
    } else {
        current.add(leagueKey)
    }
    prefs.edit().putStringSet("favorite_leagues", current).apply()
}

// ==========================================
// ÉRTESÍTÉSI PREFERENCIÁK KEZELÉSE
// ==========================================
fun getNotificationPref(context: Context, key: String, defaultVal: Boolean): Boolean {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean(key, defaultVal)
}

fun setNotificationPref(context: Context, key: String, value: Boolean) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean(key, value).apply()
}

// ==========================================
// TARTÓS ESEMÉNY- ÉS ÉRTESÍTÉS KEZELÉS (DUPLIKÁCIÓ SZŰRÉS)
// ==========================================
fun isEventAlreadyProcessed(context: Context, eventKey: String): Boolean {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val processed = prefs.getStringSet("processed_events_set", emptySet()) ?: emptySet()
    return processed.contains(eventKey)
}

fun markEventAsProcessed(context: Context, eventKey: String) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val current = prefs.getStringSet("processed_events_set", emptySet())?.toMutableSet() ?: mutableSetOf()
    if (current.add(eventKey)) {
        if (current.size > 1500) {
            val trimmed = current.drop(current.size - 1000).toSet()
            prefs.edit().putStringSet("processed_events_set", trimmed).apply()
        } else {
            prefs.edit().putStringSet("processed_events_set", current).apply()
        }
    }
}

// ==========================================
// BANKROLL & VIRTUÁLIS EGYENLEG KEZELÉS
// ==========================================
fun getVirtualBalance(context: Context): Double {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getFloat("virtual_balance", 50000f).toDouble()
}

fun updateVirtualBalance(context: Context, newBalance: Double) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    prefs.edit().putFloat("virtual_balance", newBalance.toFloat()).apply()
}

fun getSavedBets(context: Context): List<SavedBet> {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val json = prefs.getString("saved_bets_json", null) ?: return emptyList()
    val type = object : TypeToken<List<SavedBet>>() {}.type
    return try {
        Gson().fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

fun saveNewBet(context: Context, bet: SavedBet) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val currentBets = getSavedBets(context).toMutableList()
    currentBets.add(0, bet)
    val json = Gson().toJson(currentBets)
    prefs.edit().putString("saved_bets_json", json).apply()
}

fun updateSavedBetsStorage(context: Context, bets: List<SavedBet>) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val json = Gson().toJson(bets)
    prefs.edit().putString("saved_bets_json", json).apply()
}

// ==========================================
// API TÁROLÓ (CACHE MANAGER)
// ==========================================
object ApiCacheManager {
    private val cacheMap = mutableMapOf<String, Pair<Long, Any>>()
    private const val DEFAULT_TTL_MS = 5 * 60 * 1000L

    fun <T> get(key: String, ttlMs: Long = DEFAULT_TTL_MS): T? {
        val entry = cacheMap[key] ?: return null
        if (System.currentTimeMillis() - entry.first > ttlMs) {
            cacheMap.remove(key)
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return entry.second as? T
    }

    fun put(key: String, value: Any) {
        cacheMap[key] = Pair(System.currentTimeMillis(), value)
    }
}

fun saveApiKey(context: Context, key: String) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val cleanKey = key.trim().replace("\"", "").replace("'", "")
    prefs.edit().putString("statpal_api_key", cleanKey).apply()
}

fun getApiKey(context: Context): String {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getString("statpal_api_key", "")?.trim()?.replace("\"", "")?.replace("'", "") ?: ""
}

fun saveGeminiApiKey(context: Context, key: String) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val cleanKey = key.trim().replace("\"", "").replace("'", "")
    prefs.edit().putString("gemini_api_key", cleanKey).apply()
}

fun getGeminiApiKey(context: Context): String {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getString("gemini_api_key", "")?.trim()?.replace("\"", "")?.replace("'", "") ?: ""
}

// ==========================================
// GEMINI WEB ODDS LEKÉRDEZÉS (BŐVÍTETT PIACOK)
// ==========================================
data class GeminiMarketOdds(
    val home: String,
    val draw: String,
    val away: String,
    val bttsYes: String,
    val bttsNo: String,
    val over25: String,
    val under25: String,
    val bttsOver25: String,
    val ht1: String,
    val htX: String,
    val ht2: String,
    val redCardYes: String
)

suspend fun fetchOddsFromGemini(apiKey: String, home: String, away: String): GeminiMarketOdds? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val prompt = "Keresd meg a neten a $home vs $away futball mérkőzés fogadási oddsait: 1X2 (hazai, döntetlen, vendég), Gól-gól igen és nem, Over 2.5 és Under 2.5 gól, GG + Over 2.5, Félidő 1X2 (félidő hazai, döntetlen, vendég), valamint Piros lap igen. Add vissza KIZÁRÓLAG egy valid JSON formátumban, semmilyen egyéb szöveg vagy markdown blokk nélkül: {\"home\": \"1.xx\", \"draw\": \"3.xx\", \"away\": \"3.xx\", \"bttsYes\": \"1.xx\", \"bttsNo\": \"1.xx\", \"over25\": \"1.xx\", \"under25\": \"1.xx\", \"bttsOver25\": \"2.xx\", \"ht1\": \"2.xx\", \"htX\": \"2.xx\", \"ht2\": \"2.xx\", \"redCardYes\": \"3.xx\"}"
            
            val body = """
                {
                  "contents": [{
                    "parts": [{
                      "text": "$prompt"
                    }]
                  }],
                  "tools": [{"googleSearch": {}}]
                }
            """.trimIndent()

            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

            if (conn.responseCode == 200) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JsonParser().parse(responseStr).asJsonObject
                val candidates = jsonObj.getAsJsonArray("candidates")
                if (candidates != null && candidates.size() > 0) {
                    val content = candidates.get(0).asJsonObject.getAsJsonObject("content")
                    val parts = content.getAsJsonArray("parts")
                    val text = parts.get(0).asJsonObject.get("text").asString
                    
                    val cleanJson = text.replace("```json", "").replace("```", "").trim()
                    val oddsObj = JsonParser().parse(cleanJson).asJsonObject
                    
                    return@withContext GeminiMarketOdds(
                        home = oddsObj.get("home")?.asString ?: "-",
                        draw = oddsObj.get("draw")?.asString ?: "-",
                        away = oddsObj.get("away")?.asString ?: "-",
                        bttsYes = oddsObj.get("bttsYes")?.asString ?: "-",
                        bttsNo = oddsObj.get("bttsNo")?.asString ?: "-",
                        over25 = oddsObj.get("over25")?.asString ?: "-",
                        under25 = oddsObj.get("under25")?.asString ?: "-",
                        bttsOver25 = oddsObj.get("bttsOver25")?.asString ?: "-",
                        ht1 = oddsObj.get("ht1")?.asString ?: "-",
                        htX = oddsObj.get("htX")?.asString ?: "-",
                        ht2 = oddsObj.get("ht2")?.asString ?: "-",
                        redCardYes = oddsObj.get("redCardYes")?.asString ?: "-"
                    )
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}

fun getFavoriteMatchIds(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getStringSet("favorite_matches", emptySet()) ?: emptySet()
}

fun toggleFavoriteMatch(context: Context, matchId: String) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val current = prefs.getStringSet("favorite_matches", emptySet())?.toMutableSet() ?: mutableSetOf()
    if (current.contains(matchId)) {
        current.remove(matchId)
    } else {
        current.add(matchId)
    }
    prefs.edit().putStringSet("favorite_matches", current).apply()
}

fun getTeamLogoUrl(team: StatPalTeam?, apiKey: String): String? {
    if (team == null) return null
    val teamId = team.id?.trim()

    if (!teamId.isNullOrBlank() && apiKey.isNotBlank()) {
        return "https://statpal.io/api/v2/soccer/images?type=team&id=$teamId&access_key=$apiKey"
    }

    val raw = team.logo ?: team.image ?: return null
    val url = raw.trim()
    if (url.isBlank()) return null

    return when {
        url.startsWith("https://") -> url
        url.startsWith("http://") -> url.replace("http://", "https://")
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "https://statpal.io$url"
        else -> "https://statpal.io/$url"
    }
}

@Composable
fun TeamLogo(
    team: StatPalTeam?,
    colors: AppColors,
    modifier: Modifier = Modifier.size(22.dp),
    fontSize: TextUnit = 11.sp
) {
    val context = LocalContext.current
    val apiKey = getApiKey(context)
    val logoUrl = remember(team, apiKey) { getTeamLogoUrl(team, apiKey) }
    val teamName = team?.name ?: ""

    if (!logoUrl.isNullOrBlank()) {
        AsyncImage(
            model = logoUrl,
            contentDescription = teamName,
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(colors.cardBackground)
                .border(1.dp, colors.accentPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = teamName.take(1).uppercase(),
                color = colors.accentPrimary,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FormIndicator(formStr: String, colors: AppColors) {
    if (formStr.isBlank()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val chars = formStr.take(5).uppercase().toCharArray()
        for (c in chars) {
            val color = when (c) {
                'W', 'G' -> Color(0xFF22C55E)
                'D' -> Color(0xFFEAB308)
                'L', 'V' -> Color(0xFFEF4444)
                else -> colors.textMuted
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (c) {
                        'W', 'G' -> "GY"
                        'D' -> "D"
                        'L', 'V' -> "V"
                        else -> "-"
                    },
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun calculateDayOffset(targetCal: Calendar): Int {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val target = (targetCal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diffMillis = target.timeInMillis - today.timeInMillis
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
    return diffDays
}

fun formatDateForDisplay(cal: Calendar): String {
    val sdf = SimpleDateFormat("yyyy.MM.dd. (EEE)", Locale("hu", "HU"))
    return sdf.format(cal.time).uppercase()
}

fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun isLiveMatch(match: StatPalMatch): Boolean {
    val rawStatus = (match.status ?: match.time ?: "").trim().uppercase()
    if (rawStatus.isBlank()) return false

    if (rawStatus.contains("POSTP") || rawStatus.contains("PPD") || 
        rawStatus.contains("CANC") || rawStatus.contains("ABAND") || 
        rawStatus.contains("SUSP") || rawStatus.contains("INTERR") ||
        rawStatus.contains("ELHAL") || rawStatus.contains("ELMARADT")) {
        return false
    }

    if (rawStatus == "FT" || rawStatus == "FINISHED" || rawStatus == "VÉGE" || 
        rawStatus == "AET" || rawStatus == "AP" || rawStatus == "ENDED") {
        return false
    }

    if (rawStatus.contains(":") && !rawStatus.contains("'")) {
        return false
    }

    return true
}

fun isAllowedLeague(leagueName: String?): Boolean {
    val name = leagueName ?: return false
    if (name.isBlank()) return false
    val nameLower = name.lowercase()

    val forbiddenKeywords = listOf(
        "women", "női", "wom", "femenina", "feminina", "frauen", "ladies",
        "u17", "u18", "u19", "u20", "u21", "u23", "youth", "junior", "u-19", "u-21",
        "reserve", "reserves", "(am)", "b-team", "b team", "sub-20", "sub-17",
        "promocional amateur", "npl", "state league", "queensland premier",
        "3. cfl", "3. msfl", "4. liga", "kakkonen", "oberliga", "landesliga",
        "regionalliga", "torneo federal", "gaucho 2", "amazonense 2", "vtora liga",
        "calcutta", "durand cup", "cecafa", "1.liga classic", "esiliiga b",
        "cymru south", "cymru north", "iii liga", "fnl 2", "npl premier",
        "southern league", "isthmian", "serie d", "division 2", "division 3",
        "capixaba", "cearense", "goiano", "mineiro", "paranaense", "copa paulista",
        "copa governo", "nakotnes"
    )

    return forbiddenKeywords.none { nameLower.contains(it) }
}

fun isTopLeague(leagueName: String?): Boolean {
    val name = leagueName ?: return false
    if (name.isBlank()) return false
    val l = name.uppercase()
    val topKeywords = listOf(
        "NB I", "PREMIER LEAGUE", "LA LIGA", "SERIE A", "BUNDESLIGA", "LIGUE 1",
        "CHAMPIONS LEAGUE", "EUROPA LEAGUE", "CONFERENCE LEAGUE", "EREDIVISIE",
        "PRIMEIRA LIGA", "SUPER LIG", "EKSTRAKLASA", "COPPA ITALIA"
    )
    return topKeywords.any { l.contains(it) }
}

fun translateLeagueName(leagueName: String?): String {
    val name = leagueName ?: return "ISMERETLEN BAJNOKSÁG"
    if (name.isBlank()) return "ISMERETLEN BAJNOKSÁG"

    val parts = name.split(":")
    var countryPart = parts.getOrNull(0)?.trim() ?: ""
    var leaguePart = if (parts.size > 1) parts.getOrNull(1)?.trim() ?: "" else ""

    val countryMap = mapOf(
        "AFRICA" to "🌍 AFRIKA", "ALBANIA" to "🇦🇱 ALBÁNIA", "ALGERIA" to "🇩🇿 ALGÉRIA", "ANDORRA" to "🇦🇩 ANDORRA",
        "ANGOLA" to "🇦🇴 ANGOLA", "ARGENTINA" to "🇦🇷 ARGENTÍNA", "ARMENIA" to "🇦🇲 ÖRMÉNYORSZÁG", "ASIA" to "🌏 ÁZSIA",
        "AUSTRALIA" to "🇦🇺 AUSZTRÁLIA", "AUSTRIA" to "🇦🇹 AUSZTRIA", "AZERBAIJAN" to "🇦🇿 AZERBAJDZSÁN",
        "BELARUS" to "🇧🇾 FEHÉROROSZORSZÁG", "BELGIUM" to "🇧🇪 BELGIUM", "BHUTAN" to "🇧🇹 BHUTÁN", "BOLIVIA" to "🇧🇴 BOLÍVIA",
        "BOSNIA AND HERZEGOVINA" to "🇦🇽 BOSZNIA-HERCEGOVINA", "BRAZIL" to "🇧🇷 BRAZÍLIA", "BULGARIA" to "🇧🇬 BULGÁRIA",
        "BURUNDI" to "🇧🇮 BURUNDI", "CANADA" to "🇨🇦 KANADA", "CHILE" to "🇨🇱 CHILE", "CHINA" to "🇨🇳 KÍNA",
        "COLOMBIA" to "🇨🇴 KOLUMBIA", "COSTA RICA" to "🇨🇷 COSTA RICA", "CROATIA" to "🇭🇷 HORVÁTORSZÁG",
        "CYPRUS" to "🇨🇾 CIPRUS", "CZECH REPUBLIC" to "🇨🇿 CSEHORSZÁG", "DENMARK" to "🇩🇰 DÁNIA",
        "ECUADOR" to "🇪🇨 ECUADOR", "EGYPT" to "🇪🇬 EGYIPTOM", "EL SALVADOR" to "🇸🇻 EL SALVADOR",
        "ENGLAND" to "🏴󠁧󠁢󠁥󠁮󠁧󠁿 ANGLIA", "ESTONIA" to "🇪🇪 ÉSZTORSZÁG", "EUROPE" to "🇪🇺 EURÓPA",
        "FAROE ISLANDS" to "🇫🇴 FERÖER", "FIJI" to "🇫🇯 FIJI", "FINLAND" to "🇫🇮 FINNORSZÁG",
        "FRANCE" to "🇫🇷 FRANCIAORSZÁG", "GEORGIA" to "🇬🇪 GRÚZIA", "GERMANY" to "🇩🇪 NÉMETORSZÁG",
        "GREECE" to "🇬🇷 GÖRÖGORSZÁG", "GUATEMALA" to "🇬🇹 GUATEMALA", "HONDURAS" to "🇭🇳 HONDURAS",
        "HUNGARY" to "🇭🇺 MAGYARORSZÁG", "ICELAND" to "🇮🇸 IZLAND", "INDIA" to "🇮🇳 INDIA", "INDONESIA" to "🇮🇩 INDONÉZIA",
        "IRAN" to "🇮🇷 IRÁN", "IRELAND" to "🇮🇪 ÍRORSZÁG", "ISRAEL" to "🇮🇱 IZRAEL",
        "ITALY" to "🇮🇹 OLASZORSZÁG", "JAPAN" to "🇯🇵 JAPÁN", "KAZAKHSTAN" to "🇰🇿 KAZAHSZTÁN",
        "KUWAIT" to "🇰🇼 KUVAT", "KYRGYZSTAN" to "🇰🇬 KIRGIZISZTÁN", "LATVIA" to "🇱🇻 LETTORSZÁG",
        "LITHUANIA" to "🇱🇹 LITVÁNIA", "LUXEMBOURG" to "🇱🇺 LUXEMBURG", "MEXICO" to "🇲🇽 MEXIKÓ",
        "MOLDOVA" to "🇲🇩 MOLDOVA", "MONTENEGRO" to "🇲🇪 MONTENEGRÓ", "MOROCCO" to "🇲🇦 MAROKKÓ",
        "NETHERLANDS" to "🇳🇱 HOLLANDIA", "NICARAGUA" to "🇳🇮 NICARAGUA", "NORTH MACEDONIA" to "🇲🇰 ÉSZAK-MAKEDÓNIA",
        "NORWAY" to "🇳🇴 NORVÉGIA", "PANAMA" to "🇵🇦 PANAMA", "PARAGUAY" to "🇵🇾 PARAGUAY",
        "PERU" to "🇵🇪 PERU", "POLAND" to "🇵🇱 LENGYELORSZÁG", "PORTUGAL" to "🇵🇹 PORTUGÁLIA",
        "QATAR" to "🇶🇦 KATAR", "ROMANIA" to "🇷🇴 ROMÁNIA", "RUSSIA" to "🇷🇺 OROSZORSZÁG",
        "SAUDI ARABIA" to "🇸🇦 SZAÚD-ARÁBIA", "SCOTLAND" to "🏴󠁧󠁢󠁳󠁣󠁴󠁿 SKÓCIA", "SERBIA" to "🇷🇸 SZERBIA",
        "SLOVAKIA" to "🇸🇰 SZLOVÁKIA", "SLOVENIA" to "🇸🇮 SZLOVÉNIA", "SOUTH AFRICA" to "🇿🇦 DÉL-AFRIKA",
        "SOUTH AMERICA" to "🌎 DÉL-AMERIKA", "SOUTH KOREA" to "🇰🇷 DÉL-KOREA", "SPAIN" to "🇪🇸 SPANYOLORSZÁG",
        "SRI LANKA" to "🇱🇰 SRI LANKA", "SWEDEN" to "🇸🇪 SVÉDORSZÁG", "SWITZERLAND" to "🇨🇭 SVÁJC",
        "TURKEY" to "🇹🇷 TÖRÖKORSZÁG", "UKRAINE" to "🇺🇦 UKRAJNA", "UNITED ARAB EMIRATES" to "🇦🇪 EGYESÜLT ARAB EMÍRSÉGEK",
        "URUGUAY" to "🇺🇾 URUGUAY", "UZBEKISTAN" to "🇺🇿 ÜZBEGISZTÁN", "USA" to "🇺🇸 USA",
        "VENEZUELA" to "🇻🇪 VENEZUELA", "WORLD" to "🌐 VILÁG"
    )

    for ((en, hu) in countryMap) {
        if (countryPart.equals(en, ignoreCase = true)) {
            countryPart = hu
            break
        }
    }

    return if (leaguePart.isNotBlank()) "$countryPart: $leaguePart" else countryPart
}

// JAVÍTVA: Biztonságos null-kezelés a 250. és 261. sor környékén lévő hiba elkerülésére
fun translateStatus(status: String?): String {
    val s = status?.uppercase()?.trim() ?: return ""
    return when {
        s == "FT" || s == "FINISHED" -> "VÉGE"
        s == "AET" -> "H.U. VÉGE"
        s == "AP" -> "BÜNT. VÉGE"
        s == "HT" -> "FÉLIDŐ"
        s == "PEN" || s == "PEN." -> "BÜNTETŐK"
        s.startsWith("POSTP") || s == "PPD" -> "ELHAL."
        s.startsWith("CANC") || s.startsWith("ABAND") -> "ELMARADT"
        s.startsWith("SUSP") || s.startsWith("INTERR") -> "FÉLBESZ."
        else -> s
    }
}

fun translatePosition(pos: String?): String {
    val p = pos?.lowercase()?.trim() ?: return ""
    return when (p) {
        "goalkeeper" -> "Kapus"
        "defender" -> "Védő"
        "midfielder" -> "Középpályás"
        "attacker", "forward" -> "Támadó"
        else -> p
    }
}

fun translateInjuryStatus(status: String?): String {
    var result = status ?: return ""
    val translations = mapOf(
        "hamstring injury" to "Combhajlító-sérülés",
        "knee injury" to "Térdsérülés",
        "ankle injury" to "Bokasérülés",
        "shoulder injury" to "Vállsérülés",
        "injury" to "Sérülés",
        "doubtful" to "Kérdéses",
        "out" to "Kimarad"
    )
    for ((en, hu) in translations) {
        if (result.lowercase().contains(en)) {
            result = result.replace(Regex("(?i)" + Regex.escape(en)), hu)
        }
    }
    return result
}

fun translateChoice(choice: String?): String {
    val c = choice?.trim() ?: return "-"
    return when {
        c.equals("Home win", ignoreCase = true) || c.equals("Home to win", ignoreCase = true) -> "Hazai győzelem"
        c.equals("Away win", ignoreCase = true) || c.equals("Away to win", ignoreCase = true) -> "Vendég győzelem"
        c.equals("Draw", ignoreCase = true) -> "Döntetlen"
        else -> c
    }
}

fun translateReasoning(text: String?): String {
    return text?.takeIf { it.isNotBlank() } ?: "-"
}

// ==========================================
// FUTÁR AI ALGORITMUS (OKOS HELYETTESÍTŐ MOTOR)
// ==========================================
fun generateFuturAiAnalysis(match: StatPalMatch): Pair<String, String> {
    val status = (match.status ?: match.time ?: "").uppercase()
    val homeName = match.home?.name ?: "Hazai"
    val awayName = match.away?.name ?: "Vendég"
    val homeGoals = match.home?.goals?.toIntOrNull() ?: 0
    val awayGoals = match.away?.goals?.toIntOrNull() ?: 0
    val isLive = isLiveMatch(match)

    if (isLive) {
        val cleanStatus = status.replace("'", "").trim()
        val minuteNum = cleanStatus.toIntOrNull() ?: 45
        val diff = kotlin.math.abs(homeGoals - awayGoals)

        return when {
            minuteNum >= 75 && diff <= 1 -> {
                Pair(
                    "🔥 Hajrá-dráma és Gól esély ($homeGoals-$awayGoals)",
                    "A mérkőzés utolsó szakaszában járunk, az állás nagyon szoros. A statisztikák és a percek alapján a hátrányban lévő gárda mindent egy lapra tehet fel, így nagy valószínűséggel eshet még találat a hajrában!"
                )
            }
            homeGoals > awayGoals -> {
                Pair(
                    "🛡️ $homeName előny és kontroll",
                    "A hazai csapat magabiztosan őrzi előnyét ($homeGoals-$awayGoals). A fegyelmezett védekezés és a kontratámadási lehetőségek elegendőek lehetnek a győzelem megtartásához."
                )
            }
            awayGoals > homeGoals -> {
                Pair(
                    "⚡ Vendég vezetés ($homeGoals-$awayGoals)",
                    "A vendég együttes átvette az irányítást. A hazaiak kénytelenek kockáztatni, ami területeket nyithat a kontrák előtt."
                )
            }
            else -> {
                Pair(
                    "⚔️ Kiélezett döntetlen állás",
                    "A csapatok játék képe kiegyenlített. A következő gól mindent eldönthet, a taktikai fegyelem most kulcsfontosságú."
                )
            }
        }
    } else {
        return Pair(
            "🎯 $homeName vs $awayName - Futár Pre-Match Elemzés",
            "Mérkőzés előtti elemzés: A két csapat bajnoki formája és stílusa alapján taktikai csatára számítunk. Az első félidőben óvatosabb tapogatózés várható, de a hazai pálya és a pontrúgások döntő faktorok lehetnek a találkozó sorsában."
        )
    }
}

fun formatToLocalTime(dateStr: String?, timeStr: String?): String {
    if (timeStr.isNullOrBlank()) return ""
    val translatedTime = translateStatus(timeStr)
    if (!translatedTime.contains(":")) return translatedTime

    return try {
        val datePart = if (!dateStr.isNullOrBlank()) dateStr else "01.01.2026"
        val utcFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsedDate = utcFormat.parse("$datePart $timeStr")
        val localFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        if (parsedDate != null) localFormat.format(parsedDate) else translatedTime
    } catch (e: Exception) {
        translatedTime
    }
}

fun sendSystemNotification(context: Context, title: String, message: String) {
    val channelId = "football_futar_goals"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Élő Gól & Meccs Értesítések",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
}

var selectedMatchGlobal: StatPalMatch? = null
var selectedLeagueIdGlobal: String? = null

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        val serviceIntent = Intent(this, MatchLiveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        val workRequest = PeriodicWorkRequestBuilder<GoalCheckWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "GoalCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        
        setContent {
            val context = LocalContext.current
            var isDarkMode by remember { mutableStateOf(isDarkModeSaved(context)) }
            val colors = if (isDarkMode) DarkColors else LightColors

            var betSlipItems by remember { mutableStateOf<List<BetSlipItem>>(emptyList()) }

            MaterialTheme {
                val navController = rememberNavController()

                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController = navController, startDestination = "matches_list") {
                        composable("matches_list") {
                            MatchesListScreen(navController, isDarkMode, colors) { newMode ->
                                isDarkMode = newMode
                                saveDarkMode(context, newMode)
                            }
                        }
                        composable("api_settings") {
                            ApiSettingsScreen(navController, colors)
                        }
                        composable("saved_bets") {
                            SavedBetsScreen(navController, colors)
                        }
                        composable("match_detail") {
                            selectedMatchGlobal?.let { match ->
                                MatchDetailScreen(match, selectedLeagueIdGlobal, navController, colors, betSlipItems) { item ->
                                    val exists = betSlipItems.find { it.matchId == item.matchId && it.choiceName == item.choiceName }
                                    betSlipItems = if (exists != null) {
                                        betSlipItems.filter { !(it.matchId == item.matchId && it.choiceName == item.choiceName) }
                                    } else {
                                        betSlipItems.filter { it.matchId != item.matchId } + item
                                    }
                                }
                            }
                        }
                    }

                    if (betSlipItems.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            BetSlipBar(
                                items = betSlipItems,
                                colors = colors,
                                onClear = { betSlipItems = emptyList() },
                                onSaveBet = { stake ->
                                    val currentBal = getVirtualBalance(context)
                                    if (currentBal >= stake) {
                                        val newBal = currentBal - stake
                                        updateVirtualBalance(context, newBal)

                                        val totalOdds = betSlipItems.fold(1.0) { acc, i -> acc * i.odds }
                                        val newBet = SavedBet(
                                            id = UUID.randomUUID().toString(),
                                            items = betSlipItems,
                                            stake = stake,
                                            totalOdds = totalOdds,
                                            potentialWin = totalOdds * stake,
                                            dateStr = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date()),
                                            status = "FÜGGŐBEN",
                                            isPaidOut = false
                                        )
                                        saveNewBet(context, newBet)

                                        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                                        val currentFavs = prefs.getStringSet("favorite_matches", emptySet())?.toMutableSet() ?: mutableSetOf()
                                        var addedCount = 0
                                        for (item in betSlipItems) {
                                            if (currentFavs.add(item.matchId)) {
                                                addedCount++
                                            }
                                        }
                                        prefs.edit().putStringSet("favorite_matches", currentFavs).apply()

                                        Toast.makeText(context, "Fogadás elmentve! $addedCount meccs a kedvencekhez adva ⚽🔔", Toast.LENGTH_LONG).show()
                                        betSlipItems = emptyList()
                                    } else {
                                        Toast.makeText(context, "Nincs elég virtuális egyenleged!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchesListScreen(
    navController: NavController,
    isDarkMode: Boolean,
    colors: AppColors,
    onToggleDarkMode: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var isOnlyLiveFilter by remember { mutableStateOf(false) }
    var isTopLeaguesFilter by remember { mutableStateOf(false) }
    var isFavoriteLeaguesFilter by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var favoriteIds by remember { mutableStateOf(getFavoriteMatchIds(context)) }
    var favoriteLeagueKeys by remember { mutableStateOf(getFavoriteLeagueIds(context)) }
    var virtualBalance by remember { mutableStateOf(getVirtualBalance(context)) }

    var leagues by remember { mutableStateOf<List<StatPalLeague>>(emptyList()) }
    var collapsedLeagueIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var previousScoresMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var finishedMatchesMap by remember { mutableStateOf<Set<String>>(emptySet()) }
    var halftimeMatchesMap by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    var flashingMatchesState by remember { mutableStateOf<Map<String, Color>>(emptyMap()) }

    fun fetchMatches(forceRefresh: Boolean = false) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            errorMessage = "Kérlek add meg a StatPal API kulcsodat a Beállításokban (⚙️)!"
            leagues = emptyList()
            return
        }

        val offset = calculateDayOffset(selectedCalendar)
        val cacheKey = "matches_offset_$offset"

        if (!forceRefresh) {
            val cached: List<StatPalLeague>? = ApiCacheManager.get(cacheKey)
            if (cached != null) {
                leagues = cached
                errorMessage = null
                isLoading = false
                return
            }
        }

        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val response = StatPalClient.service.getDailyMatches(apiKey, offset)
                val rawLeagues = response.liveMatches?.league.orEmpty()
                val validLeagues = rawLeagues.filter { isAllowedLeague(it.name) }

                val newScoresMap = mutableMapOf<String, String>()
                val newlyFinished = finishedMatchesMap.toMutableSet()
                val newlyHt = halftimeMatchesMap.toMutableSet()

                val goalsEnabled = getNotificationPref(context, "notif_goals", true)
                val cardsEnabled = getNotificationPref(context, "notif_cards", true)
                val statusEnabled = getNotificationPref(context, "notif_status", true)

                validLeagues.flatMap { it.match }.forEach { m ->
                    val id = m.mainId ?: "${m.home?.name}-${m.away?.name}"
                    val scoreStr = "${m.home?.goals ?: "0"}-${m.away?.goals ?: "0"}"
                    newScoresMap[id] = scoreStr

                    val isFav = favoriteIds.contains(id)
                    val rawStatus = (m.status ?: m.time ?: "").uppercase()
                    val isFinishedNow = rawStatus == "FT" || rawStatus == "FINISHED" || rawStatus == "VÉGE" || rawStatus == "ENDED"
                    val isHtNow = rawStatus.contains("HT") || rawStatus.contains("FÉLIDŐ")

                    if (isFav) {
                        if (previousScoresMap.containsKey(id) && isLiveMatch(m) && goalsEnabled) {
                            val oldScore = previousScoresMap[id]
                            if (oldScore != null && oldScore != scoreStr) {
                                val goalEventKey = "goal_${id}_${scoreStr}"
                                if (!isEventAlreadyProcessed(context, goalEventKey)) {
                                    markEventAsProcessed(context, goalEventKey)
                                    Toast.makeText(context, "⚽ KEDVENC GÓL! ${m.home?.name} $scoreStr ${m.away?.name}", Toast.LENGTH_LONG).show()
                                    sendSystemNotification(context, "⚽ Élő Gól Értesítés", "${m.home?.name} $scoreStr ${m.away?.name}")
                                    
                                    coroutineScope.launch {
                                        flashingMatchesState = flashingMatchesState + (id to colors.accentPrimary)
                                        delay(15000L)
                                        flashingMatchesState = flashingMatchesState - id
                                    }
                                }
                            }
                        }

                        val htEventKey = "ht_${id}"
                        if (isHtNow && !newlyHt.contains(id) && !isEventAlreadyProcessed(context, htEventKey) && statusEnabled) {
                            markEventAsProcessed(context, htEventKey)
                            newlyHt.add(id)
                            sendSystemNotification(context, "⏱️ Félidő", "Félidő a mérkőzésen: ${m.home?.name} $scoreStr ${m.away?.name}")
                        }

                        val ftEventKey = "ft_${id}"
                        if (isFinishedNow && !newlyFinished.contains(id) && !isEventAlreadyProcessed(context, ftEventKey) && statusEnabled) {
                            markEventAsProcessed(context, ftEventKey)
                            newlyFinished.add(id)
                            sendSystemNotification(context, "🏁 Mérkőzés Vége", "Vége a meccsnek: ${m.home?.name} $scoreStr ${m.away?.name}")
                        }

                        val events = m.events?.event.orEmpty()
                        events.forEach { event ->
                            val type = event.type?.lowercase() ?: ""
                            if ((type.contains("yellow") || type.contains("red")) && cardsEnabled) {
                                val eventKey = "card_${id}_${event.minute}_${event.player}_${event.type}"
                                if (!isEventAlreadyProcessed(context, eventKey)) {
                                    markEventAsProcessed(context, eventKey)
                                    val isRed = type.contains("red")
                                    val cardIcon = if (isRed) "🟥" else "🟨"
                                    val cardName = if (isRed) "Piros lap" else "Sárga lap"
                                    val cardText = "$cardIcon $cardName (${event.minute}'): ${event.player} (${m.home?.name} - ${m.away?.name})"
                                    sendSystemNotification(context, "⚠️ Lap esemény", cardText)

                                    coroutineScope.launch {
                                        val flashColor = if (isRed) Color(0xFFEF4444) else Color(0xFFEAB308)
                                        flashingMatchesState = flashingMatchesState + (id to flashColor)
                                        delay(15000L)
                                        flashingMatchesState = flashingMatchesState - id
                                    }
                                }
                            }
                        }
                    }
                }
                previousScoresMap = newScoresMap
                finishedMatchesMap = newlyFinished
                halftimeMatchesMap = newlyHt

                leagues = validLeagues
                if (validLeagues.isNotEmpty()) {
                    ApiCacheManager.put(cacheKey, validLeagues)
                } else {
                    errorMessage = "Ezen a napon (${formatDateForDisplay(selectedCalendar)}) nincsenek mérkőzések."
                }
            } catch (e: Exception) {
                val errDetails = if (e is HttpException) "HTTP ${e.code()} (${e.message()})" else e.localizedMessage ?: "Ismeretlen hiba"
                errorMessage = "API Hiba: $errDetails\nOffset: $offset"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedCalendar) {
        fetchMatches()
        while (true) {
            delay(15000L)
            if (calculateDayOffset(selectedCalendar) == 0) {
                fetchMatches(forceRefresh = true)
            }
        }
    }

    val totalLiveCount = remember(leagues) {
        leagues.sumOf { league -> league.match.count { isLiveMatch(it) } }
    }

    val filteredLeagues = remember(leagues, isOnlyLiveFilter, isTopLeaguesFilter, isFavoriteLeaguesFilter, searchQuery, favoriteLeagueKeys) {
        val baseList = leagues.mapNotNull { league ->
            val leagueKey = league.id ?: league.name ?: ""
            if (isFavoriteLeaguesFilter && !favoriteLeagueKeys.contains(leagueKey)) {
                return@mapNotNull null
            }

            val matches = league.match
            val matchesAfterLive = if (isOnlyLiveFilter) matches.filter { isLiveMatch(it) } else matches
            val matchesAfterSearch = if (searchQuery.isNotBlank()) {
                val q = searchQuery.lowercase().trim()
                matchesAfterLive.filter { match ->
                    (match.home?.name?.lowercase()?.contains(q) == true) ||
                    (match.away?.name?.lowercase()?.contains(q) == true) ||
                    (league.name?.lowercase()?.contains(q) == true)
                }
            } else matchesAfterLive

            if (matchesAfterSearch.isNotEmpty()) league else null
        }

        val topFiltered = if (isTopLeaguesFilter) {
            baseList.filter { isTopLeague(it.name) }
        } else baseList

        topFiltered.sortedByDescending { league ->
            val key = league.id ?: league.name ?: ""
            favoriteLeagueKeys.contains(key)
        }
    }

    val featuredMatchPair = remember(leagues) {
        for (league in leagues) {
            val m = league.match.firstOrNull { isTopLeague(it.home?.name) || isLiveMatch(it) }
            if (m != null) return@remember Pair(m, league.id)
        }
        val fallbackLeague = leagues.firstOrNull()
        val fallbackMatch = fallbackLeague?.match?.firstOrNull()
        if (fallbackMatch != null) Pair(fallbackMatch, fallbackLeague.id) else null
    }

    val favoriteMatchesList = remember(leagues, favoriteIds) {
        leagues.flatMap { league ->
            league.match.filter { match ->
                val id = match.mainId ?: "${match.home?.name}-${match.away?.name}"
                favoriteIds.contains(id)
            }.map { Pair(it, league.id) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        virtualBalance = getVirtualBalance(context)
                        navController.navigate("saved_bets")
                    }
                ) {
                    Text(
                        text = "FOOTBALL FUTÁR",
                        color = colors.accentPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.accentYellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "💰 ${virtualBalance.toInt()} Ft 🎟️",
                            color = colors.accentYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSearchOpen) colors.accentPrimary else colors.cardBackground)
                            .clickable { isSearchOpen = !isSearchOpen }
                            .padding(8.dp)
                    ) {
                        Text(text = "🔍", fontSize = 13.sp)
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isFavoriteLeaguesFilter) colors.accentYellow else colors.cardBackground)
                            .clickable { isFavoriteLeaguesFilter = !isFavoriteLeaguesFilter }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(text = "⭐ Kedvencek", color = if (isFavoriteLeaguesFilter) Color.White else colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isOnlyLiveFilter) colors.accentRed else colors.cardBackground)
                            .clickable { isOnlyLiveFilter = !isOnlyLiveFilter }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🔴", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (totalLiveCount > 0) "ÉLŐ ($totalLiveCount)" else "ÉLŐ",
                                color = if (isOnlyLiveFilter) Color.White else colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isTopLeaguesFilter) colors.accentPrimary else colors.cardBackground)
                            .clickable { isTopLeaguesFilter = !isTopLeaguesFilter }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🏆 TOP",
                            color = if (isTopLeaguesFilter) Color.White else colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.cardBackground)
                            .clickable {
                                collapsedLeagueIds = if (collapsedLeagueIds.isNotEmpty() && collapsedLeagueIds.size == leagues.size) {
                                    emptySet()
                                } else {
                                    leagues.mapNotNull { it.id ?: it.name }.toSet()
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Text(text = if (collapsedLeagueIds.isNotEmpty() && collapsedLeagueIds.size == leagues.size) "📂" else "📁", fontSize = 13.sp)
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.cardBackground)
                            .clickable { onToggleDarkMode(!isDarkMode) }
                            .padding(8.dp)
                    ) {
                        Text(text = if (isDarkMode) "☀️" else "🌙", fontSize = 13.sp)
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.cardBackground)
                            .clickable { fetchMatches(forceRefresh = true) }
                            .padding(8.dp)
                    ) {
                        Text(text = "🔄", fontSize = 13.sp)
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.cardBackground)
                            .clickable { navController.navigate("api_settings") }
                            .padding(8.dp)
                    ) {
                        Text(text = "⚙️", fontSize = 13.sp)
                    }
                }
            }
        }

        if (isSearchOpen) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Keresés csapat vagy bajnokság alapján...", color = colors.textMuted, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentPrimary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedContainerColor = colors.cardBackground,
                    unfocusedContainerColor = colors.cardBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        DateStrip(
            selectedCalendar = selectedCalendar,
            onDateSelected = { selectedCalendar = it },
            colors = colors
        )

        if (isLoading && leagues.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accentPrimary)
            }
        } else if (errorMessage != null && leagues.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorMessage!!,
                    color = colors.accentRed,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (featuredMatchPair != null && searchQuery.isBlank() && !isOnlyLiveFilter) {
                    item {
                        FeaturedMatchBanner(featuredMatchPair.first, colors) {
                            selectedMatchGlobal = featuredMatchPair.first
                            selectedLeagueIdGlobal = featuredMatchPair.second
                            navController.navigate("match_detail")
                        }
                    }
                }

                if (favoriteMatchesList.isNotEmpty() && searchQuery.isBlank() && !isOnlyLiveFilter) {
                    item {
                        LeagueHeader(
                            title = "⭐ KEDVENC MECCSEK",
                            isCollapsed = false,
                            isPinned = false,
                            colors = colors,
                            onToggle = {},
                            onTogglePin = {}
                        )
                    }
                    items(favoriteMatchesList) { pairItem ->
                        val match = pairItem.first
                        val leagueId = pairItem.second
                        val matchId = match.mainId ?: "${match.home?.name}-${match.away?.name}"
                        val flashColor = flashingMatchesState[matchId]
                        MatchRow(
                            match,
                            true,
                            flashColor,
                            colors,
                            {
                                toggleFavoriteMatch(context, matchId)
                                favoriteIds = getFavoriteMatchIds(context)
                            },
                            {
                                selectedMatchGlobal = match
                                selectedLeagueIdGlobal = leagueId
                                navController.navigate("match_detail")
                            }
                        )
                    }
                }

                filteredLeagues.forEach { league ->
                    val leagueKey = league.id ?: league.name ?: ""
                    val isCollapsed = collapsedLeagueIds.contains(leagueKey)
                    val isPinned = favoriteLeagueKeys.contains(leagueKey)

                    item {
                        LeagueHeader(
                            title = translateLeagueName(league.name),
                            isCollapsed = isCollapsed,
                            isPinned = isPinned,
                            colors = colors,
                            onToggle = {
                                collapsedLeagueIds = if (isCollapsed) {
                                    collapsedLeagueIds - leagueKey
                                } else {
                                    collapsedLeagueIds + leagueKey
                                }
                            },
                            onTogglePin = {
                                toggleFavoriteLeague(context, leagueKey)
                                favoriteLeagueKeys = getFavoriteLeagueIds(context)
                            }
                        )
                    }

                    if (!isCollapsed) {
                        items(league.match) { matchItem ->
                            val matchId = matchItem.mainId ?: "${matchItem.home?.name}-${matchItem.away?.name}"
                            val isFav = favoriteIds.contains(matchId)
                            val flashColor = flashingMatchesState[matchId]

                            MatchRow(
                                matchItem,
                                isFav,
                                flashColor,
                                colors,
                                {
                                    toggleFavoriteMatch(context, matchId)
                                    favoriteIds = getFavoriteMatchIds(context)
                                },
                                {
                                    selectedMatchGlobal = matchItem
                                    selectedLeagueIdGlobal = league.id
                                    navController.navigate("match_detail")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedBetsScreen(navController: NavController, colors: AppColors) {
    val context = LocalContext.current
    var savedBets by remember { mutableStateOf(getSavedBets(context)) }
    var balance by remember { mutableStateOf(getVirtualBalance(context)) }

    LaunchedEffect(Unit) {
        val updatedBets = savedBets.map { bet ->
            if (bet.status == "FÜGGŐBEN") {
                var allFinished = true
                var allWon = true

                for (item in bet.items) {
                    val cachedLeagues: List<StatPalLeague>? = ApiCacheManager.get("matches_offset_0")
                    val allMatches = cachedLeagues?.flatMap { it.match }.orEmpty()
                    val match = allMatches.find { (it.mainId ?: "${it.home?.name}-${it.away?.name}") == item.matchId }

                    if (match == null) {
                        allFinished = false
                        break
                    }

                    val rawStatus = (match.status ?: match.time ?: "").uppercase()
                    val isFinished = rawStatus == "FT" || rawStatus == "FINISHED" || rawStatus == "VÉGE" || rawStatus == "ENDED"

                    if (!isFinished) {
                        allFinished = false
                        break
                    }

                    val hG = match.home?.goals?.toIntOrNull() ?: 0
                    val aG = match.away?.goals?.toIntOrNull() ?: 0

                    val choice = item.choiceName
                    val isChoiceWon = when {
                        choice.contains("1") && hG > aG -> true
                        choice.contains("X") && hG == aG -> true
                        choice.contains("2") && aG > hG -> true
                        choice.contains("Gól-gól (Igen)") && hG > 0 && aG > 0 -> true
                        choice.contains("Gól-gól (Nem)") && !(hG > 0 && aG > 0) -> true
                        choice.contains("Over 2.5") && (hG + aG) > 2 -> true
                        choice.contains("Under 2.5") && (hG + aG) < 3 -> true
                        choice.contains("GG + Over 2.5") && hG > 0 && aG > 0 && (hG + aG) > 2 -> true
                        else -> false
                    }

                    if (!isChoiceWon) {
                        allWon = false
                    }
                }

                if (allFinished) {
                    bet.status = if (allWon) "NYERT" else "VESZÍTETT"
                    if (allWon && !bet.isPaidOut) {
                        bet.isPaidOut = true
                        val currentBal = getVirtualBalance(context)
                        val newBal = currentBal + bet.potentialWin
                        updateVirtualBalance(context, newBal)
                        balance = newBal
                    }
                }
            }
            bet
        }
        updateSavedBetsStorage(context, updatedBets)
        savedBets = updatedBets
    }

    val totalStaked = savedBets.sumOf { it.stake }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Vissza",
                color = colors.accentPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { navController.popBackStack() }
            )
            Button(
                onClick = {
                    updateVirtualBalance(context, 50000.0)
                    balance = 50000.0
                    Toast.makeText(context, "Egyenleg visszaállítva 50 000 Ft-ra!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.cardBackground),
                border = BorderStroke(1.dp, colors.accentYellow)
            ) {
                Text("Egyenleg Reset", color = colors.accentYellow, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.accentYellow, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("VIRTUÁLIS BANKROLL ÉS STATISZTIKA", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${balance.toInt()} Ft", color = colors.accentYellow, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = colors.border)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Összes fogadás: ${savedBets.size} db", color = colors.textPrimary, fontSize = 12.sp)
                    Text("Megjátszott tét: ${totalStaked.toInt()} Ft", color = colors.textMuted, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("ELMENTETT FOGADÁSOK", color = colors.accentPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (savedBets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Még nincsenek elmentett fogadásaid.", color = colors.textMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(savedBets) { bet ->
                    val cardBg = when (bet.status) {
                        "NYERT" -> Color(0xFF22C55E).copy(alpha = 0.2f)
                        "VESZÍTETT" -> Color(0xFFEF4444).copy(alpha = 0.2f)
                        else -> colors.cardBackground
                    }
                    val borderColor = when (bet.status) {
                        "NYERT" -> Color(0xFF22C55E)
                        "VESZÍTETT" -> Color(0xFFEF4444)
                        else -> colors.border
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(bet.dateStr, color = colors.textMuted, fontSize = 10.sp)
                                Text(
                                    text = bet.status,
                                    color = when (bet.status) {
                                        "NYERT" -> Color(0xFF22C55E)
                                        "VESZÍTETT" -> Color(0xFFEF4444)
                                        else -> colors.accentYellow
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            bet.items.forEach { item ->
                                Text("• ${item.matchTitle}: ${item.choiceName} (${item.odds})", color = colors.textPrimary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Divider(color = colors.border)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tét: ${bet.stake.toInt()} Ft | Odds: %.2f".format(Locale.US, bet.totalOdds), color = colors.textMuted, fontSize = 11.sp)
                                Text("Várható: ${bet.potentialWin.toInt()} Ft", color = colors.accentPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateStrip(
    selectedCalendar: Calendar,
    onDateSelected: (Calendar) -> Unit,
    colors: AppColors
) {
    val context = LocalContext.current
    val dateList = remember(selectedCalendar) {
        val list = mutableListOf<Pair<String, Calendar>>()
        val today = Calendar.getInstance()

        for (i in -3..3) {
            val cal = Calendar.getInstance().apply {
                time = today.time
                add(Calendar.DAY_OF_YEAR, i)
            }
            val label = when (i) {
                -1 -> "TEGNAP"
                0 -> "MA"
                1 -> "HOLNAP"
                else -> SimpleDateFormat("EEE", Locale("hu", "HU")).format(cal.time).uppercase()
            }
            val dateSub = SimpleDateFormat("MM.dd.", Locale.US).format(cal.time)
            list.add(Pair("$label\n$dateSub", cal))
        }
        list
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBackground)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(dateList.size) { index ->
                val pairItem = dateList[index]
                val label = pairItem.first
                val cal = pairItem.second
                val isSelected = isSameDay(cal, selectedCalendar)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.accentPrimary else colors.background)
                        .clickable { onDateSelected(cal) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        IconButton(onClick = {
            val year = selectedCalendar.get(Calendar.YEAR)
            val month = selectedCalendar.get(Calendar.MONTH)
            val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(context, { _, selectedYear, selectedMonth, selectedDay ->
                val newCal = Calendar.getInstance()
                newCal.set(selectedYear, selectedMonth, selectedDay)
                onDateSelected(newCal)
            }, year, month, day).show()
        }) {
            Text("📅", fontSize = 16.sp)
        }
    }
}

@Composable
fun FeaturedMatchBanner(
    match: StatPalMatch,
    colors: AppColors,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(1.dp, colors.accentYellow.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🌟 A NAP RANGADÓJA", color = colors.accentYellow, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(
                    text = formatToLocalTime(match.date, match.status ?: match.time ?: ""),
                    color = if (isLiveMatch(match)) colors.accentRed else colors.accentPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TeamLogo(team = match.home, colors = colors, modifier = Modifier.size(32.dp), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(match.home?.name ?: "-", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }

                val homeG = match.home?.goals?.trim()
                val awayG = match.away?.goals?.trim()
                val hasValidGoals = !homeG.isNullOrBlank() && homeG != "?" && !awayG.isNullOrBlank() && awayG != "?"

                Text(
                    text = if (hasValidGoals) "$homeG - $awayG" else "VS",
                    color = colors.accentPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TeamLogo(team = match.away, colors = colors, modifier = Modifier.size(32.dp), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(match.away?.name ?: "-", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun LeagueHeader(
    title: String,
    isCollapsed: Boolean,
    isPinned: Boolean,
    colors: AppColors,
    onToggle: () -> Unit,
    onTogglePin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBackground)
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isPinned) "📌 " else "",
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onTogglePin() }
                        .padding(4.dp)
                )
                Text(
                    text = title.uppercase(),
                    color = colors.accentPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clickable { onTogglePin() }
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (isPinned) "⭐" else "📌",
                        fontSize = 14.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clickable { onToggle() }
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (isCollapsed) "▼" else "▲",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MatchRow(
    match: StatPalMatch,
    isFavorite: Boolean,
    flashColor: Color?,
    colors: AppColors,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val rawStatus = match.status ?: match.time ?: ""
    val live = isLiveMatch(match)
    val formattedTime = formatToLocalTime(match.date, rawStatus)

    val homeG = match.home?.goals?.trim()
    val awayG = match.away?.goals?.trim()
    val hasValidGoals = !homeG.isNullOrBlank() && homeG != "?" && !awayG.isNullOrBlank() && awayG != "?"

    val events = match.events?.event.orEmpty()
    val homeName = match.home?.name.orEmpty()
    val awayName = match.away?.name.orEmpty()

    val homeYellows = events.count { e -> 
        (e.type?.lowercase()?.contains("yellow") == true) && 
        e.team.orEmpty().equals(homeName, ignoreCase = true)
    }
    val homeReds = events.count { e -> 
        (e.type?.lowercase()?.contains("red") == true) && 
        e.team.orEmpty().equals(homeName, ignoreCase = true)
    }

    val awayYellows = events.count { e -> 
        (e.type?.lowercase()?.contains("yellow") == true) && 
        e.team.orEmpty().equals(awayName, ignoreCase = true)
    }
    val awayReds = events.count { e -> 
        (e.type?.lowercase()?.contains("red") == true) && 
        e.team.orEmpty().equals(awayName, ignoreCase = true)
    }

    val isFlashing = flashColor != null
    val infiniteTransition = rememberInfiniteTransition(label = "flash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flashAlpha"
    )

    val activeFlashColor = flashColor ?: colors.accentPrimary
    val borderColor = if (isFlashing) activeFlashColor.copy(alpha = flashAlpha) else if (live) colors.accentRed.copy(alpha = 0.8f) else colors.border
    val cardBg = if (isFlashing) activeFlashColor.copy(alpha = 0.15f) else colors.background

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(
                width = if (isFlashing) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isFavorite) "⭐" else "☆",
                fontSize = 16.sp,
                color = if (isFavorite) colors.accentYellow else colors.textMuted,
                modifier = Modifier
                    .clickable { onToggleFavorite() }
                    .padding(end = 8.dp)
            )

            Text(
                text = formattedTime,
                color = if (live) colors.accentRed else colors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(50.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(team = match.home, colors = colors, modifier = Modifier.size(20.dp), fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = homeName.ifBlank { "-" },
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    if (homeYellows > 0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAB308).copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (homeYellows > 1) "🟨 $homeYellows" else "🟨",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEAB308),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (homeReds > 0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (homeReds > 1) "🟥 $homeReds" else "🟥",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(team = match.away, colors = colors, modifier = Modifier.size(20.dp), fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = awayName.ifBlank { "-" },
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    if (awayYellows > 0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAB308).copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (awayYellows > 1) "🟨 $awayYellows" else "🟨",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEAB308),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (awayReds > 0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (awayReds > 1) "🟥 $awayReds" else "🟥",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = if (hasValidGoals) homeG!! else "-",
                    color = if (live) colors.accentPrimary else colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (hasValidGoals) awayG!! else "-",
                    color = if (live) colors.accentPrimary else colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun MatchDetailScreen(
    match: StatPalMatch,
    leagueId: String?,
    navController: NavController,
    colors: AppColors,
    betSlipItems: List<BetSlipItem>,
    onToggleOdds: (BetSlipItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("H2H", "HIÁNYZÓK", "KEZDŐk", "AI TIPP", "TABELLA", "ODDSOK", "ESEMÉNYEK")

    var h2hMatches by remember { mutableStateOf<List<H2HMatch>>(emptyList()) }
    var isLoadingH2H by remember { mutableStateOf(false) }

    var homeFormStr by remember { mutableStateOf("") }
    var awayFormStr by remember { mutableStateOf("") }

    var injuryMatchData by remember { mutableStateOf<InjuryMatch?>(null) }
    var isLoadingInjuries by remember { mutableStateOf(false) }

    var lineupData by remember { mutableStateOf<StatPalLineupResponse?>(null) }
    var isLoadingLineup by remember { mutableStateOf(false) }

    var predictionData by remember { mutableStateOf<PredictionData?>(null) }
    var isLoadingPrediction by remember { mutableStateOf(false) }

    var prematchOddsMatch by remember { mutableStateOf<PrematchOddsMatch?>(null) }
    var isLoadingOdds by remember { mutableStateOf(false) }

    val homeG = match.home?.goals?.trim()
    val awayG = match.away?.goals?.trim()
    val hasValidGoals = !homeG.isNullOrBlank() && homeG != "?" && !awayG.isNullOrBlank() && awayG != "?"

    LaunchedEffect(match) {
        val apiKey = getApiKey(context)
        val homeId = match.home?.id ?: ""
        val awayId = match.away?.id ?: ""

        if (apiKey.isNotBlank() && homeId.isNotBlank() && awayId.isNotBlank()) {
            coroutineScope.launch {
                try {
                    val response = StatPalClient.service.getHeadToHead(apiKey, homeId, awayId)
                    val list = response.headToHead?.recentMeetings?.match.orEmpty()
                    h2hMatches = list

                    var hForm = ""
                    var aForm = ""
                    list.take(5).forEach { m ->
                        val hScore = m.team1Score?.toIntOrNull() ?: 0
                        val aScore = m.team2Score?.toIntOrNull() ?: 0
                        if (hScore > aScore) { hForm += "W"; aForm += "L" }
                        else if (hScore < aScore) { hForm += "L"; aForm += "W" }
                        else { hForm += "D"; aForm += "D" }
                    }
                    homeFormStr = hForm
                    awayFormStr = aForm
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(selectedTab, match) {
        val apiKey = getApiKey(context)
        val matchId = match.mainId ?: ""

        if (apiKey.isBlank()) return@LaunchedEffect

        when (selectedTab) {
            1 -> {
                val cacheKey = "injuries_${matchId}"
                val cached: InjuryMatch? = ApiCacheManager.get(cacheKey)
                if (cached != null) {
                    injuryMatchData = cached
                } else if (injuryMatchData == null) {
                    isLoadingInjuries = true
                    coroutineScope.launch {
                        try {
                            val response = StatPalClient.service.getInjuriesAndSuspensions(apiKey)
                            val allMatches = response.injuriesSuspensions?.league.orEmpty().flatMap { it.match.orEmpty() }
                            val found = allMatches.find { it.mainId == match.mainId }
                            if (found != null) {
                                injuryMatchData = found
                                ApiCacheManager.put(cacheKey, found)
                            }
                        } catch (_: Exception) {} finally {
                            isLoadingInjuries = false
                        }
                    }
                }
            }
            2 -> {
                val cacheKey = "lineup_${matchId}"
                val cached: StatPalLineupResponse? = ApiCacheManager.get(cacheKey)
                if (cached != null) {
                    lineupData = cached
                } else if (matchId.isNotBlank() && lineupData == null) {
                    isLoadingLineup = true
                    coroutineScope.launch {
                        try {
                            val response = StatPalClient.service.getTeamLineups(apiKey, matchId)
                            lineupData = response
                            ApiCacheManager.put(cacheKey, response)
                        } catch (_: Exception) {} finally {
                            isLoadingLineup = false
                        }
                    }
                }
            }
            3 -> {
                val cacheKey = "prediction_${matchId}"
                val cached: PredictionData? = ApiCacheManager.get(cacheKey)
                if (cached != null) {
                    predictionData = cached
                } else if (matchId.isNotBlank() && predictionData == null) {
                    isLoadingPrediction = true
                    coroutineScope.launch {
                        try {
                            val response = StatPalClient.service.getMatchPrediction(apiKey, matchId)
                            predictionData = response.prediction
                            response.prediction?.let { ApiCacheManager.put(cacheKey, it) }
                        } catch (_: Exception) {} finally {
                            isLoadingPrediction = false
                        }
                    }
                }
            }
            5 -> {
                val lId = leagueId ?: ""
                val cacheKey = "odds_${lId}_${matchId}"
                val cached: PrematchOddsMatch? = ApiCacheManager.get(cacheKey)
                if (cached != null) {
                    prematchOddsMatch = cached
                } else if (lId.isNotBlank() && prematchOddsMatch == null) {
                    isLoadingOdds = true
                    coroutineScope.launch {
                        try {
                            val response = StatPalClient.service.getPrematchOdds(lId, apiKey)
                            val allPrematchMatches = response.prematchOdds?.league.orEmpty().flatMap { it.match }
                            val matchFound = allPrematchMatches.find { oddMatch ->
                                oddMatch.mainId == matchId || 
                                oddMatch.fallbackId1 == matchId || 
                                oddMatch.fallbackId2 == matchId || 
                                oddMatch.fallbackId3 == matchId || 
                                (oddMatch.home?.name.equals(match.home?.name, ignoreCase = true) && 
                                 oddMatch.away?.name.equals(match.away?.name, ignoreCase = true))
                            }
                            if (matchFound != null) {
                                prematchOddsMatch = matchFound
                                ApiCacheManager.put(cacheKey, matchFound)
                            }
                        } catch (_: Exception) {} finally {
                            isLoadingOdds = false
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
    ) {
        Text(
            text = "← Vissza",
            color = colors.accentPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { navController.popBackStack() }
                .padding(bottom = 16.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatToLocalTime(match.date, match.status ?: match.time ?: ""),
                    color = colors.accentRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TeamLogo(team = match.home, colors = colors, modifier = Modifier.size(40.dp), fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(match.home?.name ?: "-", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (homeFormStr.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            FormIndicator(homeFormStr, colors)
                        }
                    }

                    Text(
                        text = if (hasValidGoals) "$homeG - $awayG" else "VS",
                        color = colors.accentPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TeamLogo(team = match.away, colors = colors, modifier = Modifier.size(40.dp), fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(match.away?.name ?: "-", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (awayFormStr.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            FormIndicator(awayFormStr, colors)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = colors.cardBackground,
            contentColor = colors.accentPrimary,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold, color = if (selectedTab == index) colors.accentPrimary else colors.textMuted) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> {
                if (isLoadingH2H) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accentPrimary)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (homeFormStr.isNotBlank() || awayFormStr.isNotBlank()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                    modifier = Modifier.fillMaxWidth().border(1.dp, colors.accentPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("📈 AKTUÁLIS FORMA ÖSSZEHASONLÍTÁS", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(match.home?.name ?: "Hazai", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            FormIndicator(homeFormStr, colors)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(match.away?.name ?: "Vendég", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            FormIndicator(awayFormStr, colors)
                                        }
                                    }
                                }
                            }
                        }

                        if (h2hMatches.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                    modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("📊 FUTÁR STATISZTIKAI ELEMZÉS", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "A hivatalos egymás elleni archívum nem érhető el ehhez a párosításhoz. A csapatok aktuális szezonbeli mutatói alapján azonban kiegyenlített, harcos összecsapásra van kilátás.",
                                            color = colors.textPrimary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            items(h2hMatches) { h2h ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(h2h.date ?: "", color = colors.textMuted, fontSize = 11.sp)
                                            Text("${h2h.team1Name} vs ${h2h.team2Name}", color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        }
                                        Text("${h2h.team1Score} - ${h2h.team2Score}", color = colors.accentPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                if (isLoadingInjuries) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accentPrimary)
                    }
                } else if (injuryMatchData == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🏥 KERET & HIÁNYZÓK ÁLLAPOTA", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nincs hivatalos bejelentett eltiltott vagy kulcsfontosságú sérült. Mindkét együttes a legerősebb keretével készülhet.",
                                color = colors.textPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { TeamInjuriesSection(injuryMatchData?.home?.name ?: "Hazai", injuryMatchData?.home?.sidelined, colors) }
                        item { TeamInjuriesSection(injuryMatchData?.away?.name ?: "Vendég", injuryMatchData?.away?.sidelined, colors) }
                    }
                }
            }
            2 -> {
                if (isLoadingLineup) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accentPrimary)
                    }
                } else if (lineupData == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("📋 KEZDŐCSAPATOK & FELÁLLÁS", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "A hivatalos kezdőcsapatok a kezdés előtt 60 perccel válnak elérhetővé.",
                                color = colors.textPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { LineupSection(lineupData?.home, colors) }
                        item { LineupSection(lineupData?.away, colors) }
                    }
                }
            }
            3 -> {
                if (isLoadingPrediction) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accentPrimary)
                    }
                } else {
                    val aiChoice: String
                    val aiReasoning: String

                    if (predictionData != null) {
                        aiChoice = translateChoice(predictionData?.choice)
                        aiReasoning = translateReasoning(predictionData?.reasoning)
                    } else {
                        val generated = generateFuturAiAnalysis(match)
                        aiChoice = generated.first
                        aiReasoning = generated.second
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.accentPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🤖 FUTÁR AI MOTOR", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("VALÓSIDEJŰ ELEMZÉS", color = colors.accentPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = aiChoice,
                                color = colors.accentPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("SZAKÉRTŐI INDOKLÁS & TIPPEK", color = colors.textMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = aiReasoning,
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            4 -> {
                StandingsTab(colors)
            }
            5 -> {
                OddsTab(match, prematchOddsMatch, isLoadingOdds, betSlipItems, colors, onToggleOdds)
            }
            6 -> {
                TimelineTab(match, colors)
            }
        }
    }
}

@Composable
fun TimelineTab(match: StatPalMatch, colors: AppColors) {
    val events = match.events?.event.orEmpty().filter { event ->
        val playerName = event.player?.trim()
        !playerName.isNullOrBlank() && playerName != "-"
    }

    if (events.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(12.dp))
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Ehhez a mérkőzéshez még nincsenek részletes események.", color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(events) { event ->
            val type = event.type?.lowercase() ?: ""
            val minute = event.minute ?: ""
            val extraMin = event.extraMin ?: ""
            val minStr = if (extraMin.isNotBlank()) "$minute+$extraMin'" else "$minute'"
            val playerName = event.player ?: "-"
            val assist = event.assistPlayer ?: ""
            val result = event.result ?: ""

            val icon = when {
                type.contains("goal") -> "⚽"
                type.contains("yellow") -> "🟨"
                type.contains("red") -> "🟥"
                type.contains("sub") -> "🔄"
                else -> "⏱️"
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(text = minStr, color = colors.accentPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(45.dp))
                        Text(text = icon, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                        Column {
                            Text(text = playerName, color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            if (assist.isNotBlank() && assist != "-") {
                                Text(text = "Gólpassz: $assist", color = colors.textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    if (result.isNotBlank()) {
                        Text(text = result, color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StandingsTab(colors: AppColors) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📊 BAJNOKI TABELLA", color = colors.accentPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "A bajnoki tabellák és helyezések adatai a következő rendszerfrissítéssel érkeznek meg ebbe a nézetbe.",
                color = colors.textMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun OddsTab(
    match: StatPalMatch,
    prematchOddsMatch: PrematchOddsMatch?,
    isLoadingOdds: Boolean,
    betSlipItems: List<BetSlipItem>,
    colors: AppColors,
    onToggleOdds: (BetSlipItem) -> Unit
) {
    val context = LocalContext.current
    val geminiKey = remember { getGeminiApiKey(context) }

    var geminiOdds by remember { mutableStateOf<GeminiMarketOdds?>(null) }
    var isGeminiLoading by remember { mutableStateOf(false) }
    var geminiTried by remember { mutableStateOf(false) }

    val homeName = match.home?.name ?: "Hazai"
    val awayName = match.away?.name ?: "Vendég"
    val matchId = match.mainId ?: "$homeName-$awayName"
    val matchTitle = "$homeName vs $awayName"

    val oneXtwoCategory = prematchOddsMatch?.odds.orEmpty().find { 
        it.name.equals("1x2", ignoreCase = true) || it.name.equals("Fulltime Result", ignoreCase = true) 
    }
    val bookmaker = oneXtwoCategory?.bookmaker?.firstOrNull()
    val oddsList = bookmaker?.odd.orEmpty()

    val apiHomeOdd = oddsList.find { it.name.equals("Home", ignoreCase = true) }?.value
    val apiDrawOdd = oddsList.find { it.name.equals("Draw", ignoreCase = true) }?.value
    val apiAwayOdd = oddsList.find { it.name.equals("Away", ignoreCase = true) }?.value

    val hasStatPalOdds = !apiHomeOdd.isNullOrBlank()

    LaunchedEffect(match, prematchOddsMatch) {
        if (!hasStatPalOdds && !geminiTried && geminiKey.isNotBlank()) {
            isGeminiLoading = true
            geminiTried = true
            val result = fetchOddsFromGemini(geminiKey, homeName, awayName)
            if (result != null) {
                geminiOdds = result
            }
            isGeminiLoading = false
        }
    }

    if (isLoadingOdds || isGeminiLoading) {
        Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = colors.accentPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isGeminiLoading) "🤖 Gemini gyűjti a valós oddsokat a netről..." else "Oddsok betöltése...",
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    val homeOddVal = apiHomeOdd ?: geminiOdds?.home ?: "-"
    val drawOddVal = apiDrawOdd ?: geminiOdds?.draw ?: "-"
    val awayOddVal = apiAwayOdd ?: geminiOdds?.away ?: "-"

    val bttsYesVal = geminiOdds?.bttsYes ?: "-"
    val bttsNoVal = geminiOdds?.bttsNo ?: "-"
    val over25Val = geminiOdds?.over25 ?: "-"
    val under25Val = geminiOdds?.under25 ?: "-"
    val comboVal = geminiOdds?.bttsOver25 ?: "-"
    
    val ht1Val = geminiOdds?.ht1 ?: "-"
    val htXVal = geminiOdds?.htX ?: "-"
    val ht2Val = geminiOdds?.ht2 ?: "-"
    val redCardVal = geminiOdds?.redCardYes ?: "-"

    val bookmakerName = if (!apiHomeOdd.isNullOrBlank()) (bookmaker?.name ?: "StatPal Odds") else (if (geminiOdds != null) "🤖 Gemini Web Odds" else "Nincs elérhető odds")

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("MÉRKŐZÉS GYŐZTES (1X2)", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(bookmakerName, color = colors.textMuted, fontSize = 9.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val is1 = betSlipItems.any { it.matchId == matchId && it.choiceName == "1 ($homeName)" }
                        val isX = betSlipItems.any { it.matchId == matchId && it.choiceName == "X (Döntetlen)" }
                        val is2 = betSlipItems.any { it.matchId == matchId && it.choiceName == "2 ($awayName)" }

                        OddsBox("1", homeOddVal, isSelected = is1, colors = colors, modifier = Modifier.weight(1f)) {
                            val v = homeOddVal.toDoubleOrNull() ?: 0.0
                            if (v > 0) onToggleOdds(BetSlipItem(matchId, matchTitle, "1 ($homeName)", v))
                        }
                        OddsBox("X", drawOddVal, isSelected = isX, colors = colors, modifier = Modifier.weight(1f)) {
                            val v = drawOddVal.toDoubleOrNull() ?: 0.0
                            if (v > 0) onToggleOdds(BetSlipItem(matchId, matchTitle, "X (Döntetlen)", v))
                        }
                        OddsBox("2", awayOddVal, isSelected = is2, colors = colors, modifier = Modifier.weight(1f)) {
                            val v = awayOddVal.toDoubleOrNull() ?: 0.0
                            if (v > 0) onToggleOdds(BetSlipItem(matchId, matchTitle, "2 ($awayName)", v))
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("⚽ GÓL PIACOK", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val isBttsYes = betSlipItems.any { it.matchId == matchId && it.choiceName == "Gól-gól (Igen)" }
                        val isBttsNo = betSlipItems.any { it.matchId == matchId && it.choiceName == "Gól-gól (Nem)" }
                        val isOver = betSlipItems.any { it.matchId == matchId && it.choiceName == "Over 2.5 gól" }
                        val isUnder = betSlipItems.any { it.matchId == matchId && it.choiceName == "Under 2.5 gól" }

                        OddsBox("GG Igen", bttsYesVal, isSelected = isBttsYes, colors = colors, modifier = Modifier.weight(1f)) {
                            val v = bttsYesVal.toDoubleOrNull() ?: 0.0
                            if (v > 0) onToggleOdds(BetSlipItem(matchId, matchTitle, "Gól-gól (Igen)", v))
                        }
                        OddsBox("GG Nem", bttsNoVal, isSelected = isBttsNo, colors = colors, modifier = Modifier.weight(1f)) {
                            val v = bttsNoVal.toDoubleOrNull() ?: 0.0
                            if (v > 0) onToggleOdds(BetSlipItem(matchId, matchTitle, "Gól-gól (Nem)", v))
                        }
                        OddsBox("Over 2.5", over25Val, isSelected = isOver, colors = colors, modifier = Modifier.weight(1f)) {
                            val v = over25Val.toDoubleOrNull() ?: 0.0
                            if (v > 0) onToggleOdds(BetSlipItem(matchId, matchTitle, "Over 2.5 gól", v))
                        }
                        OddsBox("Under 2.5", under25Val, isSelected = isUnder, colors = colors, modifier = Modifier.weight(1f)) {
                            val v = under25Val.toDoubleOrNull() ?: 0.0
                            if (v > 0) onToggleOdds(BetSlipItem(matchId, matchTitle, "Under 2.5 gól", v))
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("⏱️ FÉLIDŐ & EGYÉB", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val isHt1 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Félidő 1" }
                        val isHtX = betSlipItems.any { it.matchId == matchId && it.choiceName == "Félidő X" }
                        val isHt2 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Félidő 2" }
                        val isCombo = betSlipItems.any { it.matchId == matchId && it.choiceName == "GG + Over 2.5" }

                        OddsBox("HT 1", ht1Val, isSelected = isHt1, colors = colors, modifier = Modifier.weight(1f)) {
                            val v = ht1Val.toDoubleOrNull() ?: 0.0
                            if (v > 0) onToggleOdds(BetSlipItem(matchId, matchTitle, "Félidő 1", v))
                        }
                        OddsBox("HT X", htXVal, isSelected = isHtX, colors = colors, modifier = Modifier.weight(1f)) {
                            val v = htXVal.toDoubleOrNull() ?: 0.0
                            if (v > 0) onToggleOdds(BetSlipItem(matchId, matchTitle, "Félidő X", v))
                        }
                        OddsBox("HT 2", ht2Val, isSelected = isHt2, colors = colors, modifier = Modifier.weight(1f)) {
                            val v = ht2Val.toDoubleOrNull() ?: 0.0
                            if (v > 0) onToggleOdds(BetSlipItem(matchId, matchTitle, "Félidő 2", v))
                        }
                        OddsBox("GG+Over", comboVal, isSelected = isCombo, colors = colors, modifier = Modifier.weight(1f)) {
                            val v = comboVal.toDoubleOrNull() ?: 0.0
                            if (v > 0) onToggleOdds(BetSlipItem(matchId, matchTitle, "GG + Over 2.5", v))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OddsBox(
    title: String,
    odds: String,
    isSelected: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) colors.accentPrimary else colors.background)
            .border(1.dp, if (isSelected) colors.accentPrimary else colors.border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = if (isSelected) Color.White else colors.textMuted, fontSize = 9.sp, maxLines = 1, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(odds, color = if (isSelected) Color.White else colors.accentPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}

@Composable
fun BetSlipBar(
    items: List<BetSlipItem>,
    colors: AppColors,
    onClear: () -> Unit,
    onSaveBet: (Double) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var stakeInput by remember { mutableStateOf("1000") }

    val totalOdds = remember(items) {
        items.fold(1.0) { acc, item -> acc * item.odds }
    }
    val stake = stakeInput.toDoubleOrNull() ?: 1000.0
    val potentialWin = totalOdds * stake

    AnimatedVisibility(
        visible = items.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.accentPrimary, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isExpanded = !isExpanded }
                    ) {
                        Text("🎟️ FOGADÁSI SZELVÉNY (${items.size})", color = colors.accentPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isExpanded) "▼" else "▲", color = colors.textMuted, fontSize = 11.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = String.format(Locale.US, "Eredő odds: %.2f", totalOdds),
                            color = colors.accentYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "❌",
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { onClear() }
                        )
                    }
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${item.matchTitle}: ${item.choiceName}", color = colors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(String.format(Locale.US, "%.2f", item.odds), color = colors.accentPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Divider(color = colors.border, modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tét (Ft):", color = colors.textMuted, fontSize = 12.sp)
                            OutlinedTextField(
                                value = stakeInput,
                                onValueChange = { stakeInput = it },
                                singleLine = true,
                                modifier = Modifier.width(100.dp).height(48.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentPrimary,
                                    unfocusedBorderColor = colors.border,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    val current = stakeInput.toDoubleOrNull() ?: 0.0
                                    stakeInput = (current + 1000.0).toInt().toString()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.cardBackground),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Text("+1e", color = colors.textPrimary, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    val current = stakeInput.toDoubleOrNull() ?: 0.0
                                    stakeInput = (current + 5000.0).toInt().toString()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.cardBackground),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Text("+5e", color = colors.textPrimary, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    val balance = getVirtualBalance(context)
                                    stakeInput = balance.toInt().toString()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.cardBackground),
                                border = BorderStroke(1.dp, colors.accentYellow)
                            ) {
                                Text("MAX", color = colors.accentYellow, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Várható nyeremény:", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                String.format(Locale.US, "%,.0f Ft", potentialWin),
                                color = colors.accentPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = { onSaveBet(stake) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Fogadás elmentése (Virtuális tét)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LineupSection(lineup: LineupTeam?, colors: AppColors) {
    Column {
        Text(
            text = "${lineup?.teamName?.uppercase() ?: "CSAPAT"} (${lineup?.teamFormation ?: "-"})",
            color = colors.accentPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text("Edző: ${lineup?.coach?.name ?: "-"}", color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))

        lineup?.startingXi.orEmpty().forEach { player ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${player.number ?: ""}. ${player.name ?: ""}", color = colors.textPrimary, fontSize = 13.sp)
                Text(translatePosition(player.position).uppercase(), color = colors.textMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun TeamInjuriesSection(teamName: String, sidelined: SidelinedData?, colors: AppColors) {
    val toMiss = sidelined?.toMiss?.player.orEmpty()
    val questionable = sidelined?.questionable?.player.orEmpty()

    Column {
        Text(teamName.uppercase(), color = colors.accentPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))

        if (toMiss.isEmpty() && questionable.isEmpty()) {
            Text("Nincs bejelentett hiányzó.", color = colors.textMuted, fontSize = 12.sp)
        } else {
            toMiss.forEach { player ->
                PlayerInjuryRow(player.name ?: "", translateInjuryStatus(player.status), isQuestionable = false, colors = colors)
            }
            questionable.forEach { player ->
                PlayerInjuryRow(player.name ?: "", translateInjuryStatus(player.status), isQuestionable = true, colors = colors)
            }
        }
    }
}

@Composable
fun PlayerInjuryRow(name: String, status: String, isQuestionable: Boolean, colors: AppColors) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(name, color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(status, color = colors.textMuted, fontSize = 11.sp)
            }
            Text(
                text = if (isQuestionable) "KÉRDÉSES" else "KIMARAD",
                color = if (isQuestionable) colors.accentYellow else colors.accentRed,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ApiSettingsScreen(navController: NavController, colors: AppColors) {
    val context = LocalContext.current
    var apiKeyInput by remember { mutableStateOf(getApiKey(context)) }
    var geminiKeyInput by remember { mutableStateOf(getGeminiApiKey(context)) }

    var goalsNotif by remember { mutableStateOf(getNotificationPref(context, "notif_goals", true)) }
    var cardsNotif by remember { mutableStateOf(getNotificationPref(context, "notif_cards", true)) }
    var statusNotif by remember { mutableStateOf(getNotificationPref(context, "notif_status", true)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp)
    ) {
        Text(
            text = "← Vissza",
            color = colors.accentPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { navController.popBackStack() }
                .padding(bottom = 20.dp)
        )

        Text(
            text = "BEÁLLÍTÁSOK",
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "StatPal API Kulcs",
            color = colors.accentPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("StatPal API Key", color = colors.textMuted) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentPrimary,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Gemini API Kulcs (Web Odds kereséshez)",
            color = colors.accentPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = geminiKeyInput,
            onValueChange = { geminiKeyInput = it },
            label = { Text("Gemini API Key", color = colors.textMuted) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentPrimary,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "🔔 ÉRTESÍTÉSI PREFERENCIÁK",
            color = colors.accentPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚽ Gól értesítések", color = colors.textPrimary, fontSize = 13.sp)
                    Switch(
                        checked = goalsNotif,
                        onCheckedChange = {
                            goalsNotif = it
                            setNotificationPref(context, "notif_goals", it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.accentPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🟨🟥 Sárga és Piros lapok", color = colors.textPrimary, fontSize = 13.sp)
                    Switch(
                        checked = cardsNotif,
                        onCheckedChange = {
                            cardsNotif = it
                            setNotificationPref(context, "notif_cards", it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.accentPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⏱️ Félidő / Vége státuszok", color = colors.textPrimary, fontSize = 13.sp)
                    Switch(
                        checked = statusNotif,
                        onCheckedChange = {
                            statusNotif = it
                            setNotificationPref(context, "notif_status", it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.accentPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                saveApiKey(context, apiKeyInput)
                saveGeminiApiKey(context, geminiKeyInput)
                Toast.makeText(context, "Beállítások elmentve!", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            },
            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Mentés", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
