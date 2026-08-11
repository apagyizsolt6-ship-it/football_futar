package com.focifutar.app

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

fun getAccentColor(context: Context, isDark: Boolean): Color {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val index = prefs.getInt("accent_color_index", 0)
    return when (index) {
        0 -> if (isDark) Color(0xFF00FF66) else Color(0xFF0284C7)
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFF0284C7)
        3 -> Color(0xFF8B5CF6)
        4 -> Color(0xFFFF3366)
        else -> if (isDark) Color(0xFF00FF66) else Color(0xFF0284C7)
    }
}

fun getAppColors(context: Context, isDark: Boolean): AppColors {
    val customAccent = getAccentColor(context, isDark)
    return if (isDark) {
        AppColors(
            background = Color(0xFF101214),
            cardBackground = Color(0xFF1A1D21),
            accentPrimary = customAccent,
            accentRed = Color(0xFFFF3366),
            accentYellow = Color(0xFFFFCC00),
            textPrimary = Color(0xFFFFFFFF),
            textMuted = Color(0xFF8C96A0),
            border = Color(0xFF2A2E33)
        )
    } else {
        AppColors(
            background = Color(0xFFF1F5F9),
            cardBackground = Color(0xFFFFFFFF),
            accentPrimary = customAccent,
            accentRed = Color(0xFFE11D48),
            accentYellow = Color(0xFFD97706),
            textPrimary = Color(0xFF0F172A),
            textMuted = Color(0xFF64748B),
            border = Color(0xFFE2E8F0)
        )
    }
}

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

object BetSlipManager {
    var currentItems = mutableStateOf<List<BetSlipItem>>(emptyList())
}

data class FlatMatchItem(
    val match: StatPalMatch,
    val league: StatPalLeague,
    val showHeader: Boolean
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

fun getNotificationPref(context: Context, key: String, defaultVal: Boolean): Boolean {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean(key, defaultVal)
}

fun setNotificationPref(context: Context, key: String, value: Boolean) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean(key, value).apply()
}

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
    return prefs.getString("statpal_api_key", "")?.trim()?.let {
        it.replace("\"", "").replace("'", "")
    } ?: ""
}

fun saveTheOddsApiKey(context: Context, key: String) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val cleanKey = key.trim().replace("\"", "").replace("'", "")
    prefs.edit().putString("the_odds_api_key", cleanKey).apply()
}

fun getTheOddsApiKey(context: Context): String {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getString("the_odds_api_key", "")?.trim()?.let {
        it.replace("\"", "").replace("'", "")
    } ?: ""
}

fun saveHighlightlyApiKey(context: Context, key: String) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val cleanKey = key.trim().replace("\"", "").replace("'", "")
    prefs.edit().putString("highlightly_api_key", cleanKey).apply()
}

fun getHighlightlyApiKey(context: Context): String {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getString("highlightly_api_key", "")?.trim()?.let {
        it.replace("\"", "").replace("'", "")
    } ?: ""
}

fun getTheOddsApiSportKey(leagueName: String?): String {
    val l = leagueName?.uppercase() ?: return "upcoming"
    return when {
        l.contains("PREMIER LEAGUE") -> "soccer_epl"
        l.contains("LA LIGA") -> "soccer_spain_la_liga"
        l.contains("SERIE A") -> "soccer_italy_serie_a"
        l.contains("BUNDESLIGA") -> "soccer_germany_bundesliga"
        l.contains("LIGUE 1") -> "soccer_france_ligue_one"
        l.contains("CHAMPIONS LEAGUE") -> "soccer_uefa_champs_league"
        l.contains("EUROPA LEAGUE") -> "soccer_uefa_europa_league"
        l.contains("EREDIVISIE") -> "soccer_netherlands_eredivisie"
        l.contains("PRIMEIRA LIGA") -> "soccer_portugal_primeira_liga"
        l.contains("SUPER LIG") -> "soccer_turkey_super_league"
        l.contains("MLS") -> "soccer_usa_mls"
        else -> "upcoming"
    }
}

data class OddsApiResult(
    val home: String,
    val draw: String,
    val away: String,
    val over25: String,
    val under25: String,
    val homeSpread: String,
    val awaySpread: String,
    val doubleChance1X: String,
    val doubleChance12: String,
    val doubleChanceX2: String,
    val dnbHome: String,
    val dnbAway: String,
    val bookmakerName: String
)

suspend fun fetchOddsFromTheOddsApi(apiKey: String, leagueName: String?, home: String?, away: String?): OddsApiResult? {
    return withContext(Dispatchers.IO) {
        try {
            val sportKey = getTheOddsApiSportKey(leagueName)
            val cacheKey = "the_odds_api_${sportKey}_all_markets"
            var rawJson: String? = ApiCacheManager.get(cacheKey, 15 * 60 * 1000L)

            if (rawJson == null) {
                val url = URL("https://api.the-odds-api.com/v4/sports/$sportKey/odds/?apiKey=$apiKey&regions=eu&markets=h2h,totals,spreads,double_chance,draw_no_bet")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                if (conn.responseCode == 200) {
                    rawJson = conn.inputStream.bufferedReader().use { it.readText() }
                    ApiCacheManager.put(cacheKey, rawJson)
                }
            }

            if (!rawJson.isNullOrBlank()) {
                val jsonArray = JsonParser().parse(rawJson).asJsonArray
                val homeClean = home?.lowercase()?.trim() ?: ""
                val awayClean = away?.lowercase()?.trim() ?: ""

                for (i in 0 until jsonArray.size()) {
                    val event = jsonArray.get(i).asJsonObject
                    val eHomeElem = event.get("home_team")?.asString
                    val eAwayElem = event.get("away_team")?.asString
                    val eHome = eHomeElem?.lowercase() ?: ""
                    val eAway = eAwayElem?.lowercase() ?: ""

                    val matchHome = eHome.contains(homeClean) || homeClean.contains(eHome) || checkFuzzyMatch(eHome, homeClean)
                    val matchAway = eAway.contains(awayClean) || awayClean.contains(eAway) || checkFuzzyMatch(eAway, awayClean)

                    if (matchHome && matchAway) {
                        val bookmakers = event.getAsJsonArray("bookmakers")
                        if (bookmakers != null && bookmakers.size() > 0) {
                            val bookie = bookmakers.get(0).asJsonObject
                            val bookieTitle = bookie.get("title")?.asString ?: "The Odds API"
                            val markets = bookie.getAsJsonArray("markets")

                            var hOdd = "1.95"
                            var dOdd = "3.40"
                            var aOdd = "3.75"
                            var o25Odd = "1.85"
                            var u25Odd = "1.90"
                            var hSpread = "1.90"
                            var aSpread = "1.90"
                            var dc1X = "1.25"
                            var dc12 = "1.30"
                            var dcX2 = "1.70"
                            var dnbH = "1.45"
                            var dnbA = "2.40"

                            for (m in 0 until markets.size()) {
                                val market = markets.get(m).asJsonObject
                                val mKey = market.get("key")?.asString ?: ""
                                val outcomes = market.getAsJsonArray("outcomes") ?: continue

                                when (mKey) {
                                    "h2h" -> {
                                        for (o in 0 until outcomes.size()) {
                                            val out = outcomes.get(o).asJsonObject
                                            val name = out.get("name")?.asString ?: ""
                                            val price = out.get("price")?.asDouble ?: 1.0
                                            val priceStr = String.format(Locale.US, "%.2f", price)

                                            if (name.equals(eHomeElem, ignoreCase = true)) hOdd = priceStr
                                            else if (name.equals(eAwayElem, ignoreCase = true)) aOdd = priceStr
                                            else dOdd = priceStr
                                        }
                                    }
                                    "totals" -> {
                                        for (o in 0 until outcomes.size()) {
                                            val out = outcomes.get(o).asJsonObject
                                            val name = out.get("name")?.asString ?: ""
                                            val point = out.get("point")?.asDouble ?: 2.5
                                            val price = out.get("price")?.asDouble ?: 1.0
                                            val priceStr = String.format(Locale.US, "%.2f", price)

                                            if (point == 2.5) {
                                                if (name.equals("Over", ignoreCase = true)) o25Odd = priceStr
                                                else if (name.equals("Under", ignoreCase = true)) u25Odd = priceStr
                                            }
                                        }
                                    }
                                    "spreads" -> {
                                        for (o in 0 until outcomes.size()) {
                                            val out = outcomes.get(o).asJsonObject
                                            val name = out.get("name")?.asString ?: ""
                                            val price = out.get("price")?.asDouble ?: 1.0
                                            val priceStr = String.format(Locale.US, "%.2f", price)

                                            if (name.equals(eHomeElem, ignoreCase = true)) hSpread = priceStr
                                            else aSpread = priceStr
                                        }
                                    }
                                    "double_chance" -> {
                                        for (o in 0 until outcomes.size()) {
                                            val out = outcomes.get(o).asJsonObject
                                            val name = out.get("name")?.asString ?: ""
                                            val price = out.get("price")?.asDouble ?: 1.0
                                            val priceStr = String.format(Locale.US, "%.2f", price)
                                            if (name.contains("Home/Draw", ignoreCase = true) || name.equals("$eHomeElem / Draw", ignoreCase = true)) dc1X = priceStr
                                            else if (name.contains("Home/Away", ignoreCase = true)) dc12 = priceStr
                                            else if (name.contains("Draw/Away", ignoreCase = true)) dcX2 = priceStr
                                        }
                                    }
                                    "draw_no_bet" -> {
                                        for (o in 0 until outcomes.size()) {
                                            val out = outcomes.get(o).asJsonObject
                                            val name = out.get("name")?.asString ?: ""
                                            val price = out.get("price")?.asDouble ?: 1.0
                                            val priceStr = String.format(Locale.US, "%.2f", price)
                                            if (name.equals(eHomeElem, ignoreCase = true)) dnbH = priceStr
                                            else if (name.equals(eAwayElem, ignoreCase = true)) dnbA = priceStr
                                        }
                                    }
                                }
                            }

                            return@withContext OddsApiResult(
                                home = hOdd,
                                draw = dOdd,
                                away = aOdd,
                                over25 = o25Odd,
                                under25 = u25Odd,
                                homeSpread = hSpread,
                                awaySpread = aSpread,
                                doubleChance1X = dc1X,
                                doubleChance12 = dc12,
                                doubleChanceX2 = dcX2,
                                dnbHome = dnbH,
                                dnbAway = dnbA,
                                bookmakerName = "🎯 $bookieTitle"
                            )
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}

fun checkFuzzyMatch(s1: String, s2: String): Boolean {
    if (s1.isBlank() || s2.isBlank()) return false
    val words1 = s1.split(" ")
    val words2 = s2.split(" ")
    return words1.any { w -> w.length >= 4 && s2.contains(w) } || words2.any { w -> w.length >= 4 && s1.contains(w) }
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
    return TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
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
    val statusStr = match.status?.trim()?.uppercase() ?: ""
    val timeStr = match.time?.trim()?.uppercase() ?: ""
    val rawStatus = (statusStr.ifBlank { timeStr }).uppercase()
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

    if (rawStatus.contains("'") || rawStatus == "1H" || rawStatus == "2H" || 
        rawStatus == "HT" || rawStatus == "FÉLIDŐ" || rawStatus == "LIVE" || rawStatus == "INPLAY" ||
        match.status?.toIntOrNull() != null) {
        return true
    }

    return false
}

fun isUpcomingMatch(match: StatPalMatch): Boolean {
    if (isLiveMatch(match)) return false
    val statusStr = match.status?.trim()?.uppercase() ?: ""
    val timeStr = match.time?.trim()?.uppercase() ?: ""
    val rawStatus = (statusStr.ifBlank { timeStr }).uppercase()
    
    if (rawStatus == "FT" || rawStatus == "FINISHED" || rawStatus == "VÉGE" || 
        rawStatus == "AET" || rawStatus == "AP" || rawStatus == "ENDED" ||
        rawStatus.contains("POSTP") || rawStatus.contains("PPD") || 
        rawStatus.contains("CANC") || rawStatus.contains("ABAND") ||
        rawStatus.contains("SUSP") || rawStatus.contains("INTERR") ||
        rawStatus.contains("ELHAL") || rawStatus.contains("ELMARADT")) {
        return false
    }
    return true
}

fun getMatchTimestamp(match: StatPalMatch): Long {
    if (isLiveMatch(match)) return 0L

    val statusStr = match.status?.trim()?.uppercase() ?: ""
    val timeStr = match.time?.trim()?.uppercase() ?: ""
    val rawTime = if (timeStr.contains(":")) timeStr else if (statusStr.contains(":")) statusStr else ""

    if (rawTime.isBlank()) return Long.MAX_VALUE

    return try {
        val datePart = if (!match.date.isNullOrBlank()) match.date else "01.01.2026"
        val utcFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsed = utcFormat.parse("$datePart $rawTime")
        parsed?.time ?: Long.MAX_VALUE
    } catch (e: Exception) {
        Long.MAX_VALUE
    }
}

fun isMatchWithinHours(match: StatPalMatch, hours: Int): Boolean {
    if (isLiveMatch(match)) return false

    val dateStr = match.date ?: return false
    val timeStr = match.time ?: match.status ?: return false

    if (!timeStr.contains(":")) return false

    return try {
        val datePart = if (dateStr.isNotBlank()) dateStr else "01.01.2026"
        val utcFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsedDate = utcFormat.parse("$datePart $timeStr") ?: return false
        val nowMs = System.currentTimeMillis()
        val matchMs = parsedDate.time
        val diffMs = matchMs - nowMs
        val diffHours = diffMs / (1000.0 * 60 * 60)

        diffHours in 0.0..(hours.toDouble())
    } catch (e: Exception) {
        false
    }
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
        "PRIMEIRA LIGA", "SUPER LIG", "EKSTRAKLASA", "COPPA ITALIA", "ALLSVENSKAN"
    )
    return topKeywords.any { l.contains(it) }
}

fun translateLeagueName(leagueName: String?): String {
    val name = leagueName ?: return "ISMERETLEN BAJNOKSÁG"
    if (name.isBlank()) return "ISMERETLEN BAJNOKSÁG"

    val parts = name.split(":")
    var countryPart = parts.getOrNull(0)?.trim() ?: ""
    val leaguePart = if (parts.size > 1) parts.getOrNull(1)?.trim() ?: "" else ""

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
        "SAUDI ARABIA" to "🇸🇦 SZAÚD-ARÁBIA", "SCOTLAND" to "🏴󠁧󠁢󠁥󠁮󠁧󠁿 SKÓCIA", "SERBIA" to "🇷🇸 SZERBIA",
        "SLOVAKIA" to "🇸🇰 SZLOVÁKIA",
        "SLOVENIA" to "🇸🇮 SZLOVÉNIA", "SOUTH AFRICA" to "🇿🇦 DÉL-AFRIKA",
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
                    "A csapatok játék képe kiegyenlített. A következő gól mindent eldönthet, a taktikai fegyelem kulcsfontosságú."
                )
            }
        }
    } else {
        return Pair(
            "🎯 $homeName vs $awayName - Futár Pre-Match Elemzés",
            "Mérkőzés előtti elemzés: A két csapat bajnoki formája és stílusa alapján taktikai csatára számítunk. Az első félidőben óvatosabb tapogatózás várható, de a hazai pálya és a pontrúgások döntő faktorok lehetnek a találkozó sorsában."
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
            var colors by remember { mutableStateOf(getAppColors(context, isDarkMode)) }

            var betSlipItems by BetSlipManager.currentItems

            MaterialTheme {
                val navController = rememberNavController()

                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController = navController, startDestination = "matches_list") {
                        composable("matches_list") {
                            MatchesListScreen(
                                navController = navController,
                                isDarkMode = isDarkMode,
                                colors = colors,
                                onToggleDarkMode = { newMode ->
                                    isDarkMode = newMode
                                    saveDarkMode(context, newMode)
                                    colors = getAppColors(context, newMode)
                                }
                            )
                        }
                        composable("api_settings") {
                            ApiSettingsScreen(navController, colors) {
                                colors = getAppColors(context, isDarkMode)
                            }
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
                                onRemoveItem = { matchId, choiceName ->
                                    betSlipItems = betSlipItems.filter { !(it.matchId == matchId && it.choiceName == choiceName) }
                                },
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
    
    var selectedTimeFilterHours by remember { mutableStateOf<Int?>(null) }
    var isFeaturedDismissed by remember { mutableStateOf(false) }

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
    var previousYellowMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var previousRedMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    
    var flashingMatchesState by remember { mutableStateOf<Map<String, Color>>(emptyMap()) }
    
    var videoTeamNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        val highlightKey = getHighlightlyApiKey(context)
        if (highlightKey.isNotBlank()) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val service = HighlightlyService.create()
                    val response = service.getHighlights(highlightKey)
                    val teams = mutableSetOf<String>()
                    response.data.forEach { h ->
                        teams.add(h.match.homeTeam.name.lowercase().trim())
                        teams.add(h.match.awayTeam.name.lowercase().trim())
                    }
                    videoTeamNames = teams
                } catch (_: Exception) {}
            }
        }
    }

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
            if (cached != null && cached.isNotEmpty()) {
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
                val newYellowMap = mutableMapOf<String, Int>()
                val newRedMap = mutableMapOf<String, Int>()

                validLeagues.flatMap { it.match }.forEach { m ->
                    val id = m.mainId ?: "${m.home?.name}-${m.away?.name}"
                    val scoreStr = "${m.home?.goals ?: "0"}-${m.away?.goals ?: "0"}"
                    newScoresMap[id] = scoreStr

                    val events = m.events?.event.orEmpty()
                    val yellowCount = events.count { e -> e.type?.lowercase()?.contains("yellow") == true }
                    val redCount = events.count { e -> e.type?.lowercase()?.contains("red") == true }
                    newYellowMap[id] = yellowCount
                    newRedMap[id] = redCount

                    val isFav = favoriteIds.contains(id)
                    val isLiveNow = isLiveMatch(m)

                    if (isFav && isLiveNow) {
                        // Check score / goal change
                        if (previousScoresMap.containsKey(id)) {
                            val oldScore = previousScoresMap[id] ?: "0-0"
                            if (oldScore != scoreStr && oldScore != "0-0") {
                                val oldParts = oldScore.split("-")
                                val newParts = scoreStr.split("-")
                                val oldTotal = (oldParts.getOrNull(0)?.toIntOrNull() ?: 0) + (oldParts.getOrNull(1)?.toIntOrNull() ?: 0)
                                val newTotal = (newParts.getOrNull(0)?.toIntOrNull() ?: 0) + (newParts.getOrNull(1)?.toIntOrNull() ?: 0)

                                if (newTotal > oldTotal) {
                                    coroutineScope.launch {
                                        flashingMatchesState = flashingMatchesState + (id to colors.accentPrimary)
                                        delay(20000L)
                                        flashingMatchesState = flashingMatchesState - id
                                    }
                                }
                            }
                        }

                        // Check Red Card (Flashing Red for 20s)
                        if (previousRedMap.containsKey(id)) {
                            val oldRed = previousRedMap[id] ?: 0
                            if (redCount > oldRed) {
                                coroutineScope.launch {
                                    flashingMatchesState = flashingMatchesState + (id to Color(0xFFEF4444))
                                    delay(20000L)
                                    flashingMatchesState = flashingMatchesState - id
                                }
                            }
                        }

                        // Check Yellow Card (Flashing Yellow for 20s) if no new red card
                        if (previousYellowMap.containsKey(id)) {
                            val oldYellow = previousYellowMap[id] ?: 0
                            if (yellowCount > oldYellow && redCount == (previousRedMap[id] ?: 0)) {
                                coroutineScope.launch {
                                    flashingMatchesState = flashingMatchesState + (id to Color(0xFFEAB308))
                                    delay(20000L)
                                    flashingMatchesState = flashingMatchesState - id
                                }
                            }
                        }
                    }
                }
                previousScoresMap = newScoresMap
                previousYellowMap = newYellowMap
                previousRedMap = newRedMap

                if (validLeagues.isNotEmpty()) {
                    leagues = validLeagues
                    ApiCacheManager.put(cacheKey, validLeagues)
                    errorMessage = null
                } else if (leagues.isEmpty()) {
                    errorMessage = "Ezen a napon (${formatDateForDisplay(selectedCalendar)}) nincsenek mérkőzések."
                }
            } catch (e: Exception) {
                if (leagues.isEmpty()) {
                    val errDetails = if (e is HttpException) "HTTP ${e.code()} (${e.message()})" else e.localizedMessage ?: "Ismeretlen hiba"
                    errorMessage = "API Hiba: $errDetails\nOffset: $offset"
                }
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

    val dayOffset = calculateDayOffset(selectedCalendar)
    val isPastDay = dayOffset < 0

    val flatChronologicalList = remember(leagues, isOnlyLiveFilter, isTopLeaguesFilter, isFavoriteLeaguesFilter, selectedTimeFilterHours, searchQuery, favoriteLeagueKeys, isPastDay) {
        if (selectedTimeFilterHours == null) return@remember emptyList<FlatMatchItem>()

        val allMatchesWithLeague = mutableListOf<Pair<StatPalMatch, StatPalLeague>>()

        leagues.forEach { league ->
            val leagueKey = league.id ?: league.name ?: ""
            if (!isFavoriteLeaguesFilter || favoriteLeagueKeys.contains(leagueKey)) {
                if (!isTopLeaguesFilter || isTopLeague(league.name)) {
                    league.match.forEach { match ->
                        var keep = true
                        if (isOnlyLiveFilter) {
                            if (!isLiveMatch(match)) keep = false
                        } else {
                            if (!isPastDay) {
                                if (!isUpcomingMatch(match)) keep = false
                            }
                        }
                        if (selectedTimeFilterHours != null && !isMatchWithinHours(match, selectedTimeFilterHours!!)) keep = false
                        if (searchQuery.isNotBlank()) {
                            val q = searchQuery.lowercase().trim()
                            val matchInQ = (match.home?.name?.lowercase()?.contains(q) == true) ||
                                           (match.away?.name?.lowercase()?.contains(q) == true) ||
                                           (league.name?.lowercase()?.contains(q) == true)
                            if (!matchInQ) keep = false
                        }
                        if (keep) {
                            allMatchesWithLeague.add(Pair(match, league))
                        }
                    }
                }
            }
        }

        val sortedList = allMatchesWithLeague.sortedBy { getMatchTimestamp(it.first) }

        val result = mutableListOf<FlatMatchItem>()
        var prevLeagueKey: String? = null

        sortedList.forEach { (match, league) ->
            val leagueKey = league.id ?: league.name ?: ""
            val showHeader = (leagueKey != prevLeagueKey)
            prevLeagueKey = leagueKey
            result.add(FlatMatchItem(match, league, showHeader))
        }

        result
    }

    val filteredLeagues = remember(leagues, isOnlyLiveFilter, isTopLeaguesFilter, isFavoriteLeaguesFilter, searchQuery, favoriteLeagueKeys, isPastDay) {
        if (selectedTimeFilterHours != null) return@remember emptyList<StatPalLeague>()

        val baseList = leagues.mapNotNull { league ->
            val leagueKey = league.id ?: league.name ?: ""
            if (isFavoriteLeaguesFilter && !favoriteLeagueKeys.contains(leagueKey)) {
                return@mapNotNull null
            }

            val matches = league.match
            val matchesAfterLive = if (isOnlyLiveFilter) {
                matches.filter { isLiveMatch(it) }
            } else {
                if (!isPastDay) {
                    matches.filter { isUpcomingMatch(it) }
                } else {
                    matches
                }
            }

            val matchesAfterSearch = if (searchQuery.isNotBlank()) {
                val q = searchQuery.lowercase().trim()
                matchesAfterLive.filter { match ->
                    (match.home?.name?.lowercase()?.contains(q) == true) ||
                    (match.away?.name?.lowercase()?.contains(q) == true) ||
                    (league.name?.lowercase()?.contains(q) == true)
                }
            } else matchesAfterLive

            if (matchesAfterSearch.isNotEmpty()) {
                league.copy(match = matchesAfterSearch)
            } else null
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
            val m = league.match.firstOrNull { (isTopLeague(it.home?.name) || isLiveMatch(it)) && isUpcomingMatch(it) }
            if (m != null) return@remember Pair(m, league.id ?: league.name)
        }
        val fallbackLeague = leagues.firstOrNull()
        val fallbackMatch = fallbackLeague?.match?.firstOrNull { isUpcomingMatch(it) }
        if (fallbackMatch != null) Pair(fallbackMatch, fallbackLeague.id ?: fallbackLeague.name) else null
    }

    // Kedvenc meccsek listája: élő és meccs előtti meccseket is tartalmazhat
    val favoriteMatchesList = remember(leagues, favoriteIds) {
        leagues.flatMap { league ->
            league.match.filter { match ->
                val id = match.mainId ?: "${match.home?.name}-${match.away?.name}"
                favoriteIds.contains(id)
            }.map { Pair(it, league.id ?: league.name) }
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
                            .background(if (selectedTimeFilterHours == 3) colors.accentPrimary else colors.cardBackground)
                            .clickable {
                                selectedTimeFilterHours = if (selectedTimeFilterHours == 3) null else 3
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⏱️ <3h",
                            color = if (selectedTimeFilterHours == 3) Color.White else colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selectedTimeFilterHours == 6) colors.accentPrimary else colors.cardBackground)
                            .clickable {
                                selectedTimeFilterHours = if (selectedTimeFilterHours == 6) null else 6
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⏱️ <6h",
                            color = if (selectedTimeFilterHours == 6) Color.White else colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selectedTimeFilterHours == 9) colors.accentPrimary else colors.cardBackground)
                            .clickable {
                                selectedTimeFilterHours = if (selectedTimeFilterHours == 9) null else 9
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⏱️ <9h",
                            color = if (selectedTimeFilterHours == 9) Color.White else colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                            .clickable {
                                val offset = calculateDayOffset(selectedCalendar)
                                ApiCacheManager.put("matches_offset_$offset", emptyList<StatPalLeague>())
                                fetchMatches(forceRefresh = true)
                            }
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
                if (featuredMatchPair != null && !isFeaturedDismissed && searchQuery.isBlank() && !isOnlyLiveFilter && selectedTimeFilterHours == null && !isPastDay) {
                    item {
                        FeaturedMatchBanner(
                            match = featuredMatchPair.first,
                            colors = colors,
                            onDismiss = { isFeaturedDismissed = true },
                            onClick = {
                                selectedMatchGlobal = featuredMatchPair.first
                                selectedLeagueIdGlobal = featuredMatchPair.second
                                navController.navigate("match_detail")
                            }
                        )
                    }
                }

                if (favoriteMatchesList.isNotEmpty() && searchQuery.isBlank() && !isOnlyLiveFilter && selectedTimeFilterHours == null) {
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
                        
                        val homeNameLower = match.home?.name?.lowercase()?.trim() ?: ""
                        val awayNameLower = match.away?.name?.lowercase()?.trim() ?: ""
                        val hasVideo = videoTeamNames.contains(homeNameLower) || videoTeamNames.contains(awayNameLower)

                        MatchRow(
                            match = match,
                            isFavorite = true,
                            hasVideo = hasVideo,
                            flashColor = flashColor,
                            colors = colors,
                            onToggleFavorite = {
                                toggleFavoriteMatch(context, matchId)
                                favoriteIds = getFavoriteMatchIds(context)
                            },
                            onClick = {
                                selectedMatchGlobal = match
                                selectedLeagueIdGlobal = leagueId
                                navController.navigate("match_detail")
                            }
                        )
                    }
                }

                if (selectedTimeFilterHours != null) {
                    items(flatChronologicalList) { item ->
                        val league = item.league
                        val matchItem = item.match
                        val leagueKey = league.id ?: league.name ?: ""
                        val isCollapsed = collapsedLeagueIds.contains(leagueKey)
                        val isPinned = favoriteLeagueKeys.contains(leagueKey)

                        if (item.showHeader) {
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
                            val matchId = matchItem.mainId ?: "${matchItem.home?.name}-${matchItem.away?.name}"
                            val isFav = favoriteIds.contains(matchId)
                            val flashColor = flashingMatchesState[matchId]

                            val homeNameLower = matchItem.home?.name?.lowercase()?.trim() ?: ""
                            val awayNameLower = matchItem.away?.name?.lowercase()?.trim() ?: ""
                            val hasVideo = videoTeamNames.contains(homeNameLower) || videoTeamNames.contains(awayNameLower)

                            MatchRow(
                                match = matchItem,
                                isFavorite = isFav,
                                hasVideo = hasVideo,
                                flashColor = flashColor,
                                colors = colors,
                                onToggleFavorite = {
                                    toggleFavoriteMatch(context, matchId)
                                    favoriteIds = getFavoriteMatchIds(context)
                                },
                                onClick = {
                                    selectedMatchGlobal = matchItem
                                    selectedLeagueIdGlobal = league.id ?: league.name
                                    navController.navigate("match_detail")
                                }
                            )
                        }
                    }
                } else {
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

                                val homeNameLower = matchItem.home?.name?.lowercase()?.trim() ?: ""
                                val awayNameLower = matchItem.away?.name?.lowercase()?.trim() ?: ""
                                val hasVideo = videoTeamNames.contains(homeNameLower) || videoTeamNames.contains(awayNameLower)

                                MatchRow(
                                    match = matchItem,
                                    isFavorite = isFav,
                                    hasVideo = hasVideo,
                                    flashColor = flashColor,
                                    colors = colors,
                                    onToggleFavorite = {
                                        toggleFavoriteMatch(context, matchId)
                                        favoriteIds = getFavoriteMatchIds(context)
                                    },
                                    onClick = {
                                        selectedMatchGlobal = matchItem
                                        selectedLeagueIdGlobal = league.id ?: league.name
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

                    val hG = match.home?.goals?.toIntOrNull()
                    val aG = match.away?.goals?.toIntOrNull()

                    if (hG == null || aG == null) {
                        allFinished = false
                        break
                    }

                    val events = match.events?.event.orEmpty()
                    val totalCards = events.count { e ->
                        val t = e.type?.lowercase() ?: ""
                        t.contains("yellow") || t.contains("red")
                    }

                    val choice = item.choiceName
                    val isChoiceWon = when {
                        choice.contains("1") && !choice.contains("1X") && !choice.contains("12") && !choice.contains("DNB") && !choice.contains("+") && hG > aG -> true
                        choice.contains("X") && !choice.contains("1X") && !choice.contains("X2") && !choice.contains("+") && hG == aG -> true
                        choice.contains("2") && !choice.contains("X2") && !choice.contains("12") && !choice.contains("DNB") && !choice.contains("+") && aG > hG -> true
                        
                        choice.contains("1X") && (hG > aG || hG == aG) -> true
                        choice.contains("12") && hG != aG -> true
                        choice.contains("X2") && (aG > hG || hG == aG) -> true
                        
                        (choice.contains("Gól-gól (Igen)") || choice.contains("BTS (Igen)")) && !choice.contains("GG + Over") && hG > 0 && aG > 0 -> true
                        (choice.contains("Gól-gól (Nem)") || choice.contains("BTS (Nem)")) && !(hG > 0 && aG > 0) -> true
                        
                        choice.contains("Over 2.5") && !choice.contains("GG + Over") && (hG + aG) > 2 -> true
                        choice.contains("Under 2.5") && (hG + aG) < 3 -> true
                        choice.contains("Over 0.5") && !choice.contains("Hazai") && !choice.contains("Vendég") && (hG + aG) > 0 -> true
                        choice.contains("Under 0.5") && (hG + aG) == 0 -> true
                        choice.contains("Over 1.5") && !choice.contains("Hazai") && !choice.contains("Vendég") && (hG + aG) > 1 -> true
                        choice.contains("Under 1.5") && (hG + aG) < 2 -> true
                        choice.contains("Over 3.5") && !choice.contains("Hazai") && !choice.contains("Vendég") && !choice.contains("Lapok") && (hG + aG) > 3 -> true
                        choice.contains("Under 3.5") && !choice.contains("Lapok") && (hG + aG) < 4 -> true
                        choice.contains("Over 4.5") && (hG + aG) > 4 -> true
                        choice.contains("Under 4.5") && (hG + aG) < 5 -> true
                        
                        choice.contains("GG + Over 2.5") && (hG > 0 && aG > 0 && (hG + aG) > 2) -> true

                        choice.contains("Gólszám: 0-1") && (hG + aG) in 0..1 -> true
                        choice.contains("Gólszám: 2-3") && (hG + aG) in 2..3 -> true
                        choice.contains("Gólszám: 4-5") && (hG + aG) in 4..5 -> true
                        choice.contains("Gólszám: 6+") && (hG + aG) >= 6 -> true

                        choice.contains("Hazai gól Over 0.5") && hG >= 1 -> true
                        choice.contains("Hazai gól Over 1.5") && hG >= 2 -> true
                        choice.contains("Hazai gól Over 2.5") && hG >= 3 -> true
                        choice.contains("Hazai gól Over 3.5") && hG >= 4 -> true

                        choice.contains("Vendég gól Over 0.5") && aG >= 1 -> true
                        choice.contains("Vendég gól Over 1.5") && aG >= 2 -> true
                        choice.contains("Vendég gól Over 2.5") && aG >= 3 -> true
                        choice.contains("Vendég gól Over 3.5") && aG >= 4 -> true

                        choice.contains("1 + BTS") && hG > aG && hG > 0 && aG > 0 -> true
                        choice.contains("1 + Over 1.5") && hG > aG && (hG + aG) > 1 -> true
                        choice.contains("1 + Over 2.5") && hG > aG && (hG + aG) > 2 -> true

                        choice.contains("Lapok Over 3.5") && totalCards > 3 -> true
                        choice.contains("Lapok Under 3.5") && totalCards < 4 -> true
                        choice.contains("Szögletek Over 8.5") && (hG + aG + 7) > 8 -> true

                        choice.contains("Pontos eredmény:") && choice.contains("$hG-$aG") -> true

                        choice.contains("DNB Hazai") && hG > aG -> true
                        choice.contains("DNB Vendég") && aG > hG -> true
                        choice.contains("Hazai Handikap") && (hG - 1.5) > aG -> true
                        choice.contains("Vendég Handikap") && (aG + 1.5) > hG -> true
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
                        "NYERT" -> Color(0xFF22C55E).copy(alpha = 0.25f)
                        "VESZÍTETT" -> Color(0xFFEF4444).copy(alpha = 0.25f)
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
                            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(bet.dateStr, color = colors.textMuted, fontSize = 10.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (bet.status == "FÜGGŐBEN") {
                                        Text(
                                            text = "✏️",
                                            fontSize = 13.sp,
                                            modifier = Modifier
                                                .clickable {
                                                    val currentBal = getVirtualBalance(context)
                                                    updateVirtualBalance(context, currentBal + bet.stake)
                                                    balance = getVirtualBalance(context)

                                                    val updated = savedBets.filter { it.id != bet.id }
                                                    updateSavedBetsStorage(context, updated)
                                                    savedBets = updated

                                                    BetSlipManager.currentItems.value = bet.items
                                                    Toast.makeText(context, "Szelvény betöltve szerkesztésre! Módosítsd és mentsd újra. 🎟️", Toast.LENGTH_LONG).show()
                                                    navController.popBackStack()
                                                }
                                                .padding(end = 8.dp)
                                        )
                                    }
                                    Text(
                                        text = bet.status,
                                        color = when (bet.status) {
                                            "NYERT" -> Color(0xFF15803D)
                                            "VESZÍTETT" -> Color(0xFFB91C1C)
                                            else -> colors.accentYellow
                                        },
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "❌",
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable {
                                            val updated = savedBets.filter { it.id != bet.id }
                                            updateSavedBetsStorage(context, updated)
                                            savedBets = updated
                                            Toast.makeText(context, "Fogadás törölve!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            bet.items.forEach { item ->
                                Text("• ${item.matchTitle}: ${item.choiceName} (${item.odds})", color = colors.textPrimary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Divider(color = borderColor.copy(alpha = 0.5f))
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
    onDismiss: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatToLocalTime(match.date, match.status ?: match.time ?: ""),
                        color = if (isLiveMatch(match)) colors.accentRed else colors.accentPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "❌",
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
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
                    fontSize = 28.sp,
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
    hasVideo: Boolean,
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
                    .padding(end = 6.dp)
            )

            if (hasVideo) {
                Text(
                    text = "🎥",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            Text(
                text = formattedTime,
                color = if (live) colors.accentRed else colors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(48.dp)
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
    val tabs = listOf("ODDSOK", "TABELLA", "STATISZTIKA", "ESEMÉNYEK", "AI TIPP", "H2H", "HIÁNYZÓK", "KEZDŐK", "VIDEÓK")

    var h2hMatches by remember { mutableStateOf<List<H2HMatch>>(emptyList()) }
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

    var highlights by remember { mutableStateOf<List<HighlightItem>>(emptyList()) }
    var isLoadingHighlights by remember { mutableStateOf(false) }

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

        when (tabs.getOrNull(selectedTab)) {
            "ODDSOK" -> {
                if (apiKey.isBlank()) return@LaunchedEffect
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
            "HIÁNYZÓK" -> {
                if (apiKey.isBlank()) return@LaunchedEffect
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
            "KEZDŐK" -> {
                if (apiKey.isBlank()) return@LaunchedEffect
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
            "AI TIPP" -> {
                if (apiKey.isBlank()) return@LaunchedEffect
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
            "VIDEÓK" -> {
                val highlightApiKey = getHighlightlyApiKey(context)
                if (highlightApiKey.isNotBlank() && highlights.isEmpty()) {
                    isLoadingHighlights = true
                    coroutineScope.launch {
                        try {
                            val service = HighlightlyService.create()
                            val response = service.getHighlights(highlightApiKey, teamName = match.home?.name)
                            highlights = response.data.filter { 
                                it.match.homeTeam.name.equals(match.home?.name, ignoreCase = true) ||
                                it.match.awayTeam.name.equals(match.away?.name, ignoreCase = true)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isLoadingHighlights = false
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

        when (tabs.getOrNull(selectedTab)) {
            "ODDSOK" -> {
                OddsTab(
                    match = match,
                    leagueName = leagueId,
                    prematchOddsMatch = prematchOddsMatch,
                    isLoadingOdds = isLoadingOdds,
                    betSlipItems = betSlipItems,
                    colors = colors,
                    onToggleOdds = onToggleOdds
                )
            }
            "TABELLA" -> {
                StandingsTab(leagueId = leagueId, colors = colors)
            }
            "STATISZTIKA" -> {
                MatchStatsTab(match = match, colors = colors)
            }
            "ESEMÉNYEK" -> {
                TimelineTab(match, colors)
            }
            "AI TIPP" -> {
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
            "H2H" -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (h2hMatches.isNotEmpty()) {
                        val totalCount = h2hMatches.size
                        val over25Count = h2hMatches.count { m ->
                            val s1 = m.team1Score?.toIntOrNull() ?: 0
                            val s2 = m.team2Score?.toIntOrNull() ?: 0
                            (s1 + s2) > 2
                        }
                        val btsCount = h2hMatches.count { m ->
                            val s1 = m.team1Score?.toIntOrNull() ?: 0
                            val s2 = m.team2Score?.toIntOrNull() ?: 0
                            s1 > 0 && s2 > 0
                        }
                        val over25Percent = (over25Count * 100) / totalCount
                        val btsPercent = (btsCount * 100) / totalCount

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                modifier = Modifier.fillMaxWidth().border(1.dp, colors.accentYellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("📊 GÓLSTATISZTIKAI TRENDEK ($totalCount MECCS)", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Over 2.5 gól arány: $over25Percent%", color = colors.textPrimary, fontSize = 12.sp)
                                        Text("BTS (Mindkét gól): $btsPercent%", color = colors.textPrimary, fontSize = 12.sp)
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
                                    Text("📊 EGYMÁS ELLENI ARCHÍVUM", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "A hivatalos egymás elleni archívum nem érhető el ehhez a párosításhoz.",
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
                                modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
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
            "HIÁNYZÓK" -> {
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
                            Text("🏥 KERET & HIÁNYZÓK", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Nincs bejelentett sérült vagy eltiltott.", color = colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { TeamInjuriesSection(injuryMatchData?.home?.name ?: "Hazai", injuryMatchData?.home?.sidelined, colors) }
                        item { TeamInjuriesSection(injuryMatchData?.away?.name ?: "Vendég", injuryMatchData?.away?.sidelined, colors) }
                    }
                }
            }
            "KEZDŐK" -> {
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
                            Text("📋 KEZDŐCSAPATOK", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("A kezdőcsapatok a kezdés előtt 60 perccel válnak elérhetővé.", color = colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { LineupSection(lineupData?.home, colors) }
                        item { LineupSection(lineupData?.away, colors) }
                    }
                }
            }
            "VIDEÓK" -> {
                val highlightApiKey = getHighlightlyApiKey(context)
                if (highlightApiKey.isBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🎥 VIDEÓS ÖSSZEFOGLALÓK", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("A videók megtekintéséhez kérlek add meg a Highlightly API kulcsodat a Beállításokban (⚙️)!", color = colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                } else if (isLoadingHighlights) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accentPrimary)
                    }
                } else if (highlights.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🎥 VIDEÓS ÖSSZEFOGLALÓK", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Ehhez a mérkőzéshez még nincsenek elérhető videós összefoglalók.", color = colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(highlights) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (!item.url.isNullOrBlank()) {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                            context.startActivity(intent)
                                        }
                                    }
                            ) {
                                Column {
                                    if (!item.imgUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = item.imgUrl,
                                            contentDescription = item.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(160.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = item.title,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (item.category == "goal-clip") "⚽ Gól / Videóklip" else "🎥 Mérkőzés összefoglaló",
                                            color = colors.accentPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchStatsTab(match: StatPalMatch, colors: AppColors) {
    val hasRealStats = false 

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📊 MÉRKŐZÉS STATISZTIKA", color = colors.accentPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))

            if (hasRealStats) {
                StatBarRow(label = "Labdabirtoklás", homeVal = "52%", awayVal = "48%", homeProgress = 0.52f, colors = colors)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ehhez a mérkőzéshez (vagy alsóbb osztályú bajnoksághoz) részletes statisztikai adatok nem érhetők el.",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun StatBarRow(label: String, homeVal: String, awayVal: String, homeProgress: Float, colors: AppColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(homeVal, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(label, color = colors.textMuted, fontSize = 12.sp)
            Text(awayVal, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.border),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(homeProgress.coerceIn(0.05f, 0.95f))
                    .background(colors.accentPrimary)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f - homeProgress.coerceIn(0.05f, 0.95f))
                    .background(colors.cardBackground)
            )
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
fun StandingsTab(leagueId: String?, colors: AppColors) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var standingsTeams by remember { mutableStateOf<List<StandingTeamRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(leagueId) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank() || leagueId.isNullOrBlank()) {
            isLoading = false
            errorMessage = "A tabella nem érhető el ehhez a mérkőzéshez."
            return@LaunchedEffect
        }

        val cacheKey = "standings_$leagueId"
        val cached: List<StandingTeamRow>? = ApiCacheManager.get(cacheKey)
        if (cached != null) {
            standingsTeams = cached
            isLoading = false
            return@LaunchedEffect
        }

        coroutineScope.launch {
            try {
                val response = StatPalClient.service.getLeagueStandings(leagueId, apiKey)
                val teams = response.standings?.tournament?.team.orEmpty()
                standingsTeams = teams
                if (teams.isNotEmpty()) {
                    ApiCacheManager.put(cacheKey, teams)
                } else {
                    errorMessage = "Nincs elérhető tabella adat ehhez a ligához."
                }
            } catch (e: Exception) {
                errorMessage = "Hiba történt a tabella lekérése közben."
            } finally {
                isLoading = false
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "📊 BAJNOKI TABELLA",
                color = colors.accentPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentPrimary)
                }
            } else if (!errorMessage.isNullOrBlank() || standingsTeams.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = errorMessage ?: "Nincs elérhető tabella.",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    Text("Csapat", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("M", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                    Text("GK", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                    Text("P", color = colors.accentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                }
                Divider(color = colors.border, modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(350.dp)
                ) {
                    items(standingsTeams) { row ->
                        val pos = row.position ?: "-"
                        val name = row.name ?: "-"
                        val played = row.overall?.gamesPlayed ?: "0"
                        val gd = row.total?.goalDifference ?: "0"
                        val pts = row.total?.points ?: "0"

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pos, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                            Text(name, color = colors.textPrimary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(played, color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                            Text(gd, color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                            Text(pts, color = colors.accentPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OddsTab(
    match: StatPalMatch,
    leagueName: String?,
    prematchOddsMatch: PrematchOddsMatch?,
    isLoadingOdds: Boolean,
    betSlipItems: List<BetSlipItem>,
    colors: AppColors,
    onToggleOdds: (BetSlipItem) -> Unit
) {
    val context = LocalContext.current
    val theOddsApiKey = getTheOddsApiKey(context)

    var oddsApiResult by remember { mutableStateOf<OddsApiResult?>(null) }
    var isFetchingExternalOdds by remember { mutableStateOf(false) }
    var externalTried by remember { mutableStateOf(false) }

    var selectedOddsTab by remember { mutableStateOf(0) }
    val oddsTabs = listOf("🔥 FŐ PIACOK", "⚽ GÓLOK", "⏱️ FÉLIDŐ", "🏠 CSAPATOK", "⚡ KOMBI & EGYÉB")

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
    val hasStatPalOdds = !apiHomeOdd.isNullOrBlank()

    LaunchedEffect(match, prematchOddsMatch, theOddsApiKey) {
        if (!hasStatPalOdds && !externalTried && theOddsApiKey.isNotBlank()) {
            isFetchingExternalOdds = true
            externalTried = true

            val res = fetchOddsFromTheOddsApi(theOddsApiKey, leagueName, homeName, awayName)
            if (res != null) {
                oddsApiResult = res
            }
            isFetchingExternalOdds = false
        }
    }

    if (isLoadingOdds || isFetchingExternalOdds) {
        Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = colors.accentPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🎯 Az összes fogadási piac gyűjtése...",
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    val homeOddVal = apiHomeOdd ?: oddsApiResult?.home ?: "1.95"
    val drawOddVal = oddsList.find { it.name.equals("Draw", ignoreCase = true) }?.value ?: oddsApiResult?.draw ?: "3.40"
    val awayOddVal = oddsList.find { it.name.equals("Away", ignoreCase = true) }?.value ?: oddsApiResult?.away ?: "3.75"

    val over25Val = oddsApiResult?.over25 ?: "1.85"
    val under25Val = oddsApiResult?.under25 ?: "1.90"
    val homeSpreadVal = oddsApiResult?.homeSpread ?: "1.90"
    val awaySpreadVal = oddsApiResult?.awaySpread ?: "1.90"
    
    val dc1XVal = oddsApiResult?.doubleChance1X ?: "1.25"
    val dc12Val = oddsApiResult?.doubleChance12 ?: "1.30"
    val dcX2Val = oddsApiResult?.doubleChanceX2 ?: "1.70"
    
    val dnbHVal = oddsApiResult?.dnbHome ?: "1.45"
    val dnbAVal = oddsApiResult?.dnbAway ?: "2.40"

    val bookmakerName = when {
        !apiHomeOdd.isNullOrBlank() -> bookmaker?.name ?: "StatPal Odds"
        oddsApiResult != null -> oddsApiResult?.bookmakerName ?: "🎯 The Odds API (Teljes Piac)"
        else -> "⚡ Futár Smart Odds (Okos becslés)"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedOddsTab,
            containerColor = colors.cardBackground,
            contentColor = colors.accentPrimary,
            edgePadding = 0.dp
        ) {
            oddsTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedOddsTab == index,
                    onClick = { selectedOddsTab = index },
                    text = { 
                        Text(
                            title, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 11.sp,
                            color = if (selectedOddsTab == index) colors.accentPrimary else colors.textMuted
                        ) 
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when (selectedOddsTab) {
                0 -> {
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
                                        val v = homeOddVal.toDoubleOrNull() ?: 1.95
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "1 ($homeName)", v))
                                    }
                                    OddsBox("X", drawOddVal, isSelected = isX, colors = colors, modifier = Modifier.weight(1f)) {
                                        val v = drawOddVal.toDoubleOrNull() ?: 3.40
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "X (Döntetlen)", v))
                                    }
                                    OddsBox("2", awayOddVal, isSelected = is2, colors = colors, modifier = Modifier.weight(1f)) {
                                        val v = awayOddVal.toDoubleOrNull() ?: 3.75
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "2 ($awayName)", v))
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
                                Text("🛡️ KÉTESÉLY & DNB", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val is1X = betSlipItems.any { it.matchId == matchId && it.choiceName == "1X" }
                                    val is12 = betSlipItems.any { it.matchId == matchId && it.choiceName == "12" }
                                    val isX2 = betSlipItems.any { it.matchId == matchId && it.choiceName == "X2" }

                                    OddsBox("1X", dc1XVal, isSelected = is1X, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "1X", dc1XVal.toDoubleOrNull() ?: 1.25))
                                    }
                                    OddsBox("12", dc12Val, isSelected = is12, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "12", dc12Val.toDoubleOrNull() ?: 1.30))
                                    }
                                    OddsBox("X2", dcX2Val, isSelected = isX2, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "X2", dcX2Val.toDoubleOrNull() ?: 1.70))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isDnbH = betSlipItems.any { it.matchId == matchId && it.choiceName == "DNB Hazai" }
                                    val isDnbA = betSlipItems.any { it.matchId == matchId && it.choiceName == "DNB Vendég" }

                                    OddsBox("DNB 1", dnbHVal, isSelected = isDnbH, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "DNB Hazai", dnbHVal.toDoubleOrNull() ?: 1.45))
                                    }
                                    OddsBox("DNB 2", dnbAVal, isSelected = isDnbA, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "DNB Vendég", dnbAVal.toDoubleOrNull() ?: 2.40))
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
                                Text("⚖️ HENDIKEP PIACOK", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isH1 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Hazai Handikap (-1.5)" }
                                    val isA2 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Vendég Handikap (+1.5)" }

                                    OddsBox("Hazai (-1.5)", homeSpreadVal, isSelected = isH1, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Hazai Handikap (-1.5)", homeSpreadVal.toDoubleOrNull() ?: 1.90))
                                    }
                                    OddsBox("Vendég (+1.5)", awaySpreadVal, isSelected = isA2, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Vendég Handikap (+1.5)", awaySpreadVal.toDoubleOrNull() ?: 1.90))
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                            modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("⚽ MINDKÉT CSAPAT SZEREZ GÓLT (BTS)", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isBtsI = betSlipItems.any { it.matchId == matchId && it.choiceName == "BTS (Igen)" }
                                    val isBtsN = betSlipItems.any { it.matchId == matchId && it.choiceName == "BTS (Nem)" }

                                    OddsBox("BTS (Igen)", "1.75", isSelected = isBtsI, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "BTS (Igen)", 1.75))
                                    }
                                    OddsBox("BTS (Nem)", "2.05", isSelected = isBtsN, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "BTS (Nem)", 2.05))
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
                                Text("📈 GÓLOK SZÁMA (OVER / UNDER HATÁROK)", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isO25 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Over 2.5 gól" }
                                    val isU25 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Under 2.5 gól" }

                                    OddsBox("Over 2.5", over25Val, isSelected = isO25, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Over 2.5 gól", over25Val.toDoubleOrNull() ?: 1.85))
                                    }
                                    OddsBox("Under 2.5", under25Val, isSelected = isU25, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Under 2.5 gól", under25Val.toDoubleOrNull() ?: 1.90))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isO15 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Over 1.5 gól" }
                                    val isU15 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Under 1.5 gól" }
                                    val isO35 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Over 3.5 gól" }
                                    val isU35 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Under 3.5 gól" }

                                    OddsBox("Over 1.5", "1.30", isSelected = isO15, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Over 1.5 gól", 1.30))
                                    }
                                    OddsBox("Under 1.5", "3.20", isSelected = isU15, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Under 1.5 gól", 3.20))
                                    }
                                    OddsBox("Over 3.5", "3.10", isSelected = isO35, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Over 3.5 gól", 3.10))
                                    }
                                    OddsBox("Under 3.5", "1.35", isSelected = isU35, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Under 3.5 gól", 1.35))
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
                                Text("📊 GÓLSZÁM SÁVOK & GG + OVER", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val is01 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Gólszám: 0-1" }
                                    val is23 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Gólszám: 2-3" }
                                    val is45 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Gólszám: 4-5" }
                                    val is6p = betSlipItems.any { it.matchId == matchId && it.choiceName == "Gólszám: 6+" }

                                    OddsBox("0-1", "3.10", isSelected = is01, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Gólszám: 0-1", 3.10))
                                    }
                                    OddsBox("2-3", "1.95", isSelected = is23, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Gólszám: 2-3", 1.95))
                                    }
                                    OddsBox("4-5", "3.60", isSelected = is45, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Gólszám: 4-5", 3.60))
                                    }
                                    OddsBox("6+", "11.0", isSelected = is6p, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Gólszám: 6+", 11.0))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isGgOver = betSlipItems.any { it.matchId == matchId && it.choiceName == "GG + Over 2.5" }
                                    OddsBox("GG + Over 2.5", "2.35", isSelected = isGgOver, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "GG + Over 2.5", 2.35))
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                            modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("⏱️ FÉLIDŐ / VÉGEREDMÉNY (HT/FT)", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val isHH = betSlipItems.any { it.matchId == matchId && it.choiceName == "Hazai / Hazai" }
                                    val isDH = betSlipItems.any { it.matchId == matchId && it.choiceName == "Döntetlen / Hazai" }
                                    val isVV = betSlipItems.any { it.matchId == matchId && it.choiceName == "Vendég / Vendég" }

                                    OddsBox("Hazai / Hazai", "3.10", isSelected = isHH, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Hazai / Hazai", 3.10))
                                    }
                                    OddsBox("Dönt / Hazai", "5.20", isSelected = isDH, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Döntetlen / Hazai", 5.20))
                                    }
                                    OddsBox("Vendég / Vendég", "6.50", isSelected = isVV, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Vendég / Vendég", 6.50))
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
                                Text("⏱️ 1. FÉLIDŐ 1X2 & GÓLOK", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val is1F1 = betSlipItems.any { it.matchId == matchId && it.choiceName == "1. Félidő 1" }
                                    val is1FX = betSlipItems.any { it.matchId == matchId && it.choiceName == "1. Félidő X" }
                                    val is1F2 = betSlipItems.any { it.matchId == matchId && it.choiceName == "1. Félidő 2" }

                                    OddsBox("1 (1.F.)", "2.60", isSelected = is1F1, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "1. Félidő 1", 2.60))
                                    }
                                    OddsBox("X (1.F.)", "2.10", isSelected = is1FX, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "1. Félidő X", 2.10))
                                    }
                                    OddsBox("2 (1.F.)", "4.10", isSelected = is1F2, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "1. Félidő 2", 4.10))
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                            modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("🏠 HAZAI CSAPAT GÓLJAI", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isH05 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Hazai gól Over 0.5" }
                                    val isH15 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Hazai gól Over 1.5" }
                                    val isH25 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Hazai gól Over 2.5" }

                                    OddsBox("Over 0.5", "1.22", isSelected = isH05, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Hazai gól Over 0.5", 1.22))
                                    }
                                    OddsBox("Over 1.5", "2.05", isSelected = isH15, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Hazai gól Over 1.5", 2.05))
                                    }
                                    OddsBox("Over 2.5", "4.20", isSelected = isH25, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Hazai gól Over 2.5", 4.20))
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
                                Text("✈️ VENDÉG CSAPAT GÓLJAI", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isA05 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Vendég gól Over 0.5" }
                                    val isA15 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Vendég gól Over 1.5" }
                                    val isA25 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Vendég gól Over 2.5" }

                                    OddsBox("Over 0.5", "1.45", isSelected = isA05, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Vendég gól Over 0.5", 1.45))
                                    }
                                    OddsBox("Over 1.5", "2.80", isSelected = isA15, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Vendég gól Over 1.5", 2.80))
                                    }
                                    OddsBox("Over 2.5", "6.50", isSelected = isA25, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Vendég gól Over 2.5", 6.50))
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                            modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("⚡ 1X2 + GÓLOK KOMBI", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val is1B = betSlipItems.any { it.matchId == matchId && it.choiceName == "1 + BTS" }
                                    val is1O15 = betSlipItems.any { it.matchId == matchId && it.choiceName == "1 + Over 1.5" }
                                    val is1O25 = betSlipItems.any { it.matchId == matchId && it.choiceName == "1 + Over 2.5" }

                                    OddsBox("1 + BTS", "3.40", isSelected = is1B, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "1 + BTS", 3.40))
                                    }
                                    OddsBox("1 + 1.5", "2.10", isSelected = is1O15, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "1 + Over 1.5", 2.10))
                                    }
                                    OddsBox("1 + 2.5", "2.90", isSelected = is1O25, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "1 + Over 2.5", 2.90))
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
                                Text("🟨 LAPOK & 🚩 SZÖGLETEK", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isCardO = betSlipItems.any { it.matchId == matchId && it.choiceName == "Lapok Over 3.5" }
                                    val isCardU = betSlipItems.any { it.matchId == matchId && it.choiceName == "Lapok Under 3.5" }
                                    val isCornerO = betSlipItems.any { it.matchId == matchId && it.choiceName == "Szögletek Over 8.5" }

                                    OddsBox("Lapok >3.5", "1.80", isSelected = isCardO, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Lapok Over 3.5", 1.80))
                                    }
                                    OddsBox("Lapok <3.5", "1.90", isSelected = isCardU, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Lapok Under 3.5", 1.90))
                                    }
                                    OddsBox("Szöglet >8.5", "1.85", isSelected = isCornerO, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Szögletek Over 8.5", 1.85))
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
                                Text("🎯 PONTOS VÉGEREDMÉNY", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val is10 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Pontos eredmény: 1-0" }
                                    val is20 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Pontos eredmény: 2-0" }
                                    val is21 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Pontos eredmény: 2-1" }
                                    val is11 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Pontos eredmény: 1-1" }

                                    OddsBox("1-0", "7.00", isSelected = is10, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Pontos eredmény: 1-0", 7.00))
                                    }
                                    OddsBox("2-0", "8.50", isSelected = is20, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Pontos eredmény: 2-0", 8.50))
                                    }
                                    OddsBox("2-1", "8.00", isSelected = is21, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Pontos eredmény: 2-1", 8.00))
                                    }
                                    OddsBox("1-1", "6.50", isSelected = is11, colors = colors, modifier = Modifier.weight(1f)) {
                                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Pontos eredmény: 1-1", 6.50))
                                    }
                                }
                            }
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
    onRemoveItem: (String, String) -> Unit,
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.matchTitle, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("${item.choiceName} (${item.odds})", color = colors.accentPrimary, fontSize = 11.sp)
                                }
                                Text(
                                    text = "❌",
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable { onRemoveItem(item.matchId, item.choiceName) }
                                        .padding(4.dp)
                                )
                            }
                            Divider(color = colors.border.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))
                        }

                        Spacer(modifier = Modifier.height(4.dp))

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
                            Text("Mentés (Virtuális tét)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
fun ApiSettingsScreen(navController: NavController, colors: AppColors, onAccentChanged: () -> Unit) {
    val context = LocalContext.current
    var apiKeyInput by remember { mutableStateOf(getApiKey(context)) }
    var theOddsKeyInput by remember { mutableStateOf(getTheOddsApiKey(context)) }
    var highlightlyKeyInput by remember { mutableStateOf(getHighlightlyApiKey(context)) }

    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    var selectedAccentIndex by remember { mutableStateOf(prefs.getInt("accent_color_index", 0)) }

    var goalsNotif by remember { mutableStateOf(getNotificationPref(context, "notif_goals", true)) }
    var yellowCardsNotif by remember { mutableStateOf(getNotificationPref(context, "notif_yellow_cards", true)) }
    var redCardsNotif by remember { mutableStateOf(getNotificationPref(context, "notif_red_cards", true)) }
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
            text = "🎨 Kiemelő (Accent) Szín",
            color = colors.accentPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val colorOptions = listOf(
                Pair("Neon", Color(0xFF00FF66)),
                Pair("Arany", Color(0xFFFFD700)),
                Pair("Kék", Color(0xFF0284C7)),
                Pair("Lila", Color(0xFF8B5CF6)),
                Pair("Rózsaszín", Color(0xFFFF3366))
            )
            colorOptions.forEachIndexed { index, pair ->
                val isSelected = selectedAccentIndex == index
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(pair.second)
                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else colors.border, CircleShape)
                        .clickable {
                            selectedAccentIndex = index
                            prefs.edit().putInt("accent_color_index", index).apply()
                            onAccentChanged()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

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
            text = "🎯 The Odds API Kulcs (Legjobb Odds-Forrás)",
            color = colors.accentYellow,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = theOddsKeyInput,
            onValueChange = { theOddsKeyInput = it },
            label = { Text("The Odds API Key", color = colors.textMuted) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentYellow,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "🎥 Highlightly API Kulcs (Videós összefoglalók)",
            color = colors.accentPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = highlightlyKeyInput,
            onValueChange = { highlightlyKeyInput = it },
            label = { Text("Highlightly API Key", color = colors.textMuted) },
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
                    Text("⚽ Gól & Kezdési értesítések", color = colors.textPrimary, fontSize = 13.sp)
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
                    Text("🟨 Sárga lapok", color = colors.textPrimary, fontSize = 13.sp)
                    Switch(
                        checked = yellowCardsNotif,
                        onCheckedChange = {
                            yellowCardsNotif = it
                            setNotificationPref(context, "notif_yellow_cards", it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.accentYellow)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🟥 Piros lapok", color = colors.textPrimary, fontSize = 13.sp)
                    Switch(
                        checked = redCardsNotif,
                        onCheckedChange = {
                            redCardsNotif = it
                            setNotificationPref(context, "notif_red_cards", it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.accentRed)
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
                saveTheOddsApiKey(context, theOddsKeyInput)
                saveHighlightlyApiKey(context, highlightlyKeyInput)
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
