package com.focifutar.app

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
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
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    accentPrimary = Color(0xFF00FF66), // Neon Zöld
    accentRed = Color(0xFFFF3366),
    accentYellow = Color(0xFFFFCC00),
    textPrimary = Color(0xFFFFFFFF),
    textMuted = Color(0xFF8C96A0),
    border = Color(0xFF2A2E33)
)

val LightColors = AppColors(
    background = Color(0xFFF1F5F9), // Letisztult világosszürke
    cardBackground = Color(0xFFFFFFFF), // Tiszta fehér
    accentPrimary = Color(0xFF0284C7), // Királykék / Sportkék
    accentRed = Color(0xFFE11D48),
    accentYellow = Color(0xFFD97706),
    textPrimary = Color(0xFF0F172A), // Sötét pala fekete
    textMuted = Color(0xFF64748B),
    border = Color(0xFFE2E8F0)
)

data class BetSlipItem(
    val matchId: String,
    val matchTitle: String,
    val choiceName: String,
    val odds: Double
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
    prefs.edit().putString("statpal_api_key", key.trim()).apply()
}

fun getApiKey(context: Context): String {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getString("statpal_api_key", "")?.trim() ?: ""
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

fun formatDateForApi(cal: Calendar): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

fun formatDateForDisplay(cal: Calendar): String {
    val sdf = SimpleDateFormat("yyyy.MM.dd. (EEE)", Locale("hu", "HU"))
    return sdf.format(cal.time).uppercase()
}

fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun isToday(cal: Calendar): Boolean {
    return isSameDay(cal, Calendar.getInstance())
}

fun isMatchOnSelectedDate(matchDateStr: String?, targetCal: Calendar): Boolean {
    if (matchDateStr.isNullOrBlank()) {
        return isToday(targetCal)
    }

    val targetY = targetCal.get(Calendar.YEAR)
    val targetM = targetCal.get(Calendar.MONTH) + 1
    val targetD = targetCal.get(Calendar.DAY_OF_MONTH)

    val dTwo = String.format("%02d", targetD)
    val mTwo = String.format("%02d", targetM)
    val yFour = targetY.toString()

    val raw = matchDateStr.trim()

    if (raw.contains("$yFour-$mTwo-$dTwo") ||
        raw.contains("$dTwo.$mTwo.$yFour") ||
        raw.contains("$dTwo-$mTwo-$yFour") ||
        raw.contains("$yFour.$mTwo.$dTwo")) {
        return true
    }

    val patterns = listOf(
        "dd.MM.yyyy HH:mm", "yyyy-MM-dd HH:mm", "dd.MM.yyyy", "yyyy-MM-dd",
        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'", "MM/dd/yyyy"
    )

    for (pattern in patterns) {
        try {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            val parsed = sdf.parse(raw)
            if (parsed != null) {
                val matchCal = Calendar.getInstance().apply { time = parsed }
                return matchCal.get(Calendar.YEAR) == targetY &&
                       (matchCal.get(Calendar.MONTH) + 1) == targetM &&
                       matchCal.get(Calendar.DAY_OF_MONTH) == targetD
            }
        } catch (_: Exception) {}
    }

    return false
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
    if (leagueName.isNullOrBlank()) return false
    val nameLower = leagueName.lowercase()

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
    if (leagueName.isNullOrBlank()) return false
    val l = leagueName.uppercase()
    val topKeywords = listOf(
        "NB I", "PREMIER LEAGUE", "LA LIGA", "SERIE A", "BUNDESLIGA", "LIGUE 1",
        "CHAMPIONS LEAGUE", "EUROPA LEAGUE", "CONFERENCE LEAGUE", "EREDIVISIE",
        "PRIMEIRA LIGA", "SUPER LIG", "EKSTRAKLASA", "COPPA ITALIA"
    )
    return topKeywords.any { l.contains(it) }
}

fun translateLeagueName(leagueName: String?): String {
    if (leagueName.isNullOrBlank()) return "ISMERETLEN BAJNOKSÁG"

    val parts = leagueName.split(":")
    var countryPart = parts[0].trim()
    var leaguePart = if (parts.size > 1) parts[1].trim() else ""

    val countryMap = mapOf(
        "AFRICA" to "🌍 AFRIKA", "ALBANIA" to "🇦🇱 ALBÁNIA", "ALGERIA" to "🇩🇿 ALGÉRIA", "ANDORRA" to "🇦🇩 ANDORRA",
        "ANGOLA" to "🇦🇴 ANGOLA", "ARGENTINA" to "🇦🇷 ARGENTÍNA", "ARMENIA" to "🇦🇲 ÖRMÉNYORSZÁG", "ASIA" to "🌏 ÁZSIA",
        "AUSTRALIA" to "🇦🇺 AUSZTRÁLIA", "AUSTRIA" to "🇦🇹 AUSZTRIA", "AZERBAIJAN" to "🇦🇿 AZERBAJDZSÁN",
        "BELARUS" to "🇧🇾 FEHÉROROSZORSZÁG", "BELGIUM" to "🇧🇪 BELGIUM", "BHUTAN" to "🇧🇹 BHUTÁN", "BOLIVIA" to "🇧🇴 BOLÍVIA",
        "BOSNIA AND HERZEGOVINA" to "🇧🇦 BOSZNIA-HERCEGOVINA", "BRAZIL" to "🇧🇷 BRAZÍLIA", "BULGARIA" to "🇧🇬 BULGÁRIA",
        "BURUNDI" to "🇧🇮 BURUNDI", "CANADA" to "🇨🇦 KANADA", "CHILE" to "🇨🇱 CHILE", "CHINA" to "🇨🇳 KÍNA",
        "COLOMBIA" to "🇨🇴 KOLUMBIA", "COSTA RICA" to "🇨🇷 COSTA RICA", "CROATIA" to "🇭🇷 HORVÁTORSZÁG",
        "CYPRUS" to "🇨🇾 CIPRUS", "CZECH REPUBLIC" to "🇨🇿 CSEHORSZÁG", "DENMARK" to "🇩🇰 DÁNIA",
        "ECUADOR" to "🇪🇨 ECUADOR", "EGYPT" to "🇪🇬 EGYIPTOM", "EL SALVADOR" to "🇸🇻 EL SALVADOR",
        "ENGLAND" to "🏴󠁧󠁢󠁥󠁮󠁧󠁿 ANGLIA", "ESTONIA" to "🇪🇪 ÉSZTORSZÁG", "EUROPE" to "🇪🇺 EURÓPA",
        "FAROE ISLANDS" to "🇫🇴 FERÖER", "FIJI" to "🇫🇯 FIJI", "FINLAND" to "🇫🇮 FINNORSZÁG",
        "FRANCE" to "🇫🇷 FRANCIAORSZÁG", "GEORGIA" to "🇬🇪 GRÚZIA", "GERMANY" to "🇩🇪 NÉMETORSZÁG",
        "GREECE" to "🇬🇷 GÖRÖGORSZÁG", "GUATEMALA" to "🇬🇹 GUATEMALA", "HONDURAS" to "🇭🇳 HONDURAS",
        "HUNGARY" to "🇭🇺 MAGYARORSZÁG", "ICELAND" to "🇮🇸 IZLAND", "INDIA" to "🇮🇳 INDIA", "INDONESIA" to "🇮🇩 INDONÉZIA",
        "IRAN" to "🇮🇷 IRÁN", "IRELAND" to "🇮🇪 ÍRORSZÁG", "ISRAEL" to "🇮🇱 IZRAEL", "ITALY" to "🇮🇹 OLASZORSZÁG",
        "JAPAN" to "🇯🇵 JAPÁN", "KAZAKHSTAN" to "🇰🇿 KAZAHSZTÁN", "KUWAIT" to "🇰🇼 KUVAT", "KYRGYZSTAN" to "🇰🇬 KIRGIZISZTÁN",
        "LATVIA" to "🇱🇻 LETTORSZÁG", "LITHUANIA" to "🇱🇹 LITVÁNIA", "LUXEMBOURG" to "🇱🇺 LUXEMBURG",
        "MEXICO" to "🇲🇽 MEXIKÓ", "MOLDOVA" to "🇲🇩 MOLDOVA", "MONTENEGRO" to "🇲🇪 MONTENEGRÓ", "MOROCCO" to "🇲🇦 MAROKKÓ",
        "NETHERLANDS" to "🇳🇱 HOLLANDIA", "NICARAGUA" to "🇳🇮 NICARAGUA", "NORTH MACEDONIA" to "🇲🇰 ÉSZAK-MAKEDÓNIA",
        "NORWAY" to "🇳🇴 NORVÉGIA", "PANAMA" to "🇵🇦 PANAMA", "PARAGUAY" to "🇵🇾 PARAGUAY", "PERU" to "🇵🇪 PERU",
        "POLAND" to "🇵🇱 LENGYELORSZÁG", "PORTUGAL" to "🇵🇹 PORTUGÁLIA", "QATAR" to "🇶🇦 KATAR", "ROMANIA" to "🇷🇴 ROMÁNIA",
        "RUSSIA" to "🇷🇺 OROSZORSZÁG", "SAUDI ARABIA" to "🇸🇦 SZAÚD-ARÁBIA", "SCOTLAND" to "🏴󠁧󠁢󠁳󠁣󠁴󠁿 SKÓCIA",
        "SERBIA" to "🇷🇸 SZERBIA", "SLOVAKIA" to "🇸🇰 SZLOVÁKIA", "SLOVENIA" to "🇸🇮 SZLOVÉNIA",
        "SOUTH AFRICA" to "🇿🇦 DÉL-AFRIKA", "SOUTH AMERICA" to "🌎 DÉL-AMERIKA", "SOUTH KOREA" to "🇰🇷 DÉL-KOREA",
        "SPAIN" to "🇪🇸 SPANYOLORSZÁG", "SRI LANKA" to "🇱🇰 SRI LANKA", "SWEDEN" to "🇸🇪 SVÉDORSZÁG",
        "SWITZERLAND" to "🇨🇭 SVÁJC", "TURKEY" to "🇹🇷 TÖRÖKORSZÁG", "UKRAINE" to "🇺🇦 UKRAJNA",
        "USA" to "🇺🇸 USA", "URUGUAY" to "🇺🇾 URUGUAY", "WORLD" to "🌐 VILÁG"
    )

    for ((en, hu) in countryMap) {
        if (countryPart.equals(en, ignoreCase = true)) {
            countryPart = hu
            break
        }
    }

    val termMap = mapOf(
        "PROMOTION GROUP" to "RÁJÁTSZÁS", "RELEGATION GROUP" to "ALSÓHÁZ",
        "PLAY OFFS" to "RÁJÁTSZÁS", "PLAY-OFFS" to "RÁJÁTSZÁS",
        "PLACEMENT MATCHES" to "HELYOSZTÓK", "WINNERS STAGE" to "GYŐZTESEK SZAKASZA",
        "NATIONAL LEAGUE SOUTH" to "NATIONALLIGA DÉL", "NATIONAL LEAGUE NORTH" to "NATIONALLIGA ÉSZAK",
        "CHAMPIONS LEAGUE" to "BAJNOKOK LIGÁJA", "EUROPA LEAGUE" to "EURÓPA-LIGA",
        "CONFERENCE LEAGUE" to "KONFERENCIA LIGA", "FRIENDLIES" to "BARÁTSÁGOS",
        "GROUP A" to "A CSOPORT", "GROUP B" to "B CSOPORT", "GROUP C" to "C CSOPORT",
        "GROUP D" to "D CSOPORT", "GROUP E" to "E CSOPORT", "GROUP F" to "F CSOPORT",
        "GROUP 1" to "1. CSOPORT", "GROUP 2" to "2. CSOPORT", "GROUP 3" to "3. CSOPORT",
        "GROUP 4" to "4. CSOPORT", "GROUP 5" to "5. CSOPORT", "GROUP 6" to "6. CSOPORT",
        "1ST DIVISION" to "1. OSZTÁLY", "2ND DIVISION" to "2. OSZTÁLY", "3RD DIVISION" to "3. OSZTÁLY"
    )

    for ((en, hu) in termMap) {
        leaguePart = leaguePart.replace(Regex("(?i)" + Regex.escape(en)), hu)
    }

    return if (leaguePart.isNotBlank()) "$countryPart: $leaguePart" else countryPart
}

fun translateStatus(status: String?): String {
    if (status.isNullOrBlank()) return ""
    val s = status.uppercase().trim()
    return when {
        s == "FT" || s == "FINISHED" -> "VÉGE"
        s == "AET" -> "H.U. VÉGE"
        s == "AP" -> "BÜNT. VÉGE"
        s == "HT" -> "FÉLIDŐ"
        s == "PEN" || s == "PEN." -> "BÜNTETŐK"
        s.startsWith("POSTP") || s == "PPD" -> "ELHAL."
        s.startsWith("CANC") || s.startsWith("ABAND") -> "ELMARADT"
        s.startsWith("SUSP") || s.startsWith("INTERR") -> "FÉLBESZ."
        else -> status
    }
}

fun translatePosition(pos: String?): String {
    if (pos.isNullOrBlank()) return ""
    return when (pos.lowercase().trim()) {
        "goalkeeper" -> "Kapus"
        "defender" -> "Védő"
        "midfielder" -> "Középpályás"
        "attacker", "forward" -> "Támadó"
        else -> pos
    }
}

fun translateInjuryStatus(status: String?): String {
    if (status.isNullOrBlank()) return ""
    var result: String = status
    val translations = mapOf(
        "hamstring injury" to "Combhajlító-sérülés",
        "knee injury" to "Térdsérülés",
        "ankle injury" to "Bokasérülés",
        "shoulder injury" to "Vállsérülés",
        "heel injury" to "Sarksérülés",
        "yellow cards" to "Sárga lapos eltiltás",
        "inactive" to "Inaktív",
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
    if (choice.isNullOrBlank()) return "-"
    val c = choice.trim()
    return when {
        c.equals("Home win", ignoreCase = true) || c.equals("Home to win", ignoreCase = true) -> "Hazai győzelem"
        c.equals("Away win", ignoreCase = true) || c.equals("Away to win", ignoreCase = true) -> "Vendég győzelem"
        c.equals("Draw", ignoreCase = true) -> "Döntetlen"
        c.lowercase().endsWith(" to win") -> "${c.substring(0, c.length - 7)} győzelme"
        else -> c
    }
}

fun translateReasoning(text: String?): String {
    if (text.isNullOrBlank()) return "-"
    var result: String = text.trim()

    val regexRules = listOf(
        Regex("(?i)([A-Za-z0-9\\s]+) has the home advantage and a stronger recent form in the ([A-Za-z0-9\\s]+) compared to ([A-Za-z0-9\\s]+)") to "A(z) $1 csapata élvezi a hazai pálya előnyét és jobb formában van a(z) $2 bajnokságban a(z) $3 csapatához képest",
        Regex("(?i)who have drawn their last two league matches") to "akik döntetlent játszottak a legutóbbi két bajnoki mérkőzésükön",
        Regex("(?i)showed offensive strength recently, scoring multiple goals at home") to "támadásban meggyőző teljesítményt nyújtott mostanában, több gólt szerezve hazai pályán",
        Regex("(?i)while ([A-Za-z0-9\\s]+) are missing a key defender due to suspension") to "míg a(z) $1 csapatából eltiltás miatt hiányzik egy kulcsfontossági védő",
        Regex("(?i)Despite some fatigue and squad rotation concerns for ([A-Za-z0-9\\s]+) after recent European fixtures") to "Noha a legutóbbi európai kupamérkőzések után némi fáradtság és rotáció várható a(z) $1 csapatánál",
        Regex("(?i)their home form and historical head-to-head edge support a home victory") to "a hazai formájuk és a korábbi egymás elleni mérlegük is hazai győzelmet valószínűsít",
        Regex("(?i)([A-Za-z0-9\\s]+) is unbeaten so far this season") to "A(z) $1 ebben a szezonban még veretlen",
        Regex("(?i)playing confidently in their new ([A-Za-z0-9\\s]+)") to "magabiztosan játszik az új $1 stadionban",
        Regex("(?i)with strong recent form including a (\\d+-\\d+) win in their last home match") to "meggyőző formában van, beleértve a legutóbbi hazai $1-es győzelmét",
        Regex("(?i)and a (\\d+-\\d+) away win against ([A-Za-z0-9\\s]+) recently") to "és a legutóbbi $1-es vendéggyőzelmét a(z) $2 ellen",
        Regex("(?i)Tactical insights suggest ([A-Za-z0-9\\s']+)'s solid defense and home advantage will be key against ([A-Za-z0-9\\s']+)'s possession-based but less effective away performances") to "A taktikai elemzés szerint a hazaiak stabil védelme és a pályaelőny kulcsfontos lesz a vendégek labdabirtoklásra épülő, de idegenben kevésbé hatékony játéka ellen",
        Regex("(?i)([A-Za-z0-9\\s]+) has a strong historical head-to-head advantage over ([A-Za-z0-9\\s]+)") to "A(z) $1 jelentős egymás elleni előnnyel rendelkezik a(z) $2 ellen",
        Regex("(?i)Given these factors, a draw is the most likely outcome") to "Ezek alapján a döntetlen a legvalószínűbb kimenetel",
        Regex("(?i)([A-Za-z0-9\\s]+) is playing at home with a fully fit squad") to "A(z) $1 hazai pályán játszik teljes kerettel",
        Regex("(?i)Market odds also support a ([A-Za-z0-9\\s]+) win") to "A fogadási oddsok is a(z) $1 győzelmét támogatják"
    )

    for ((regex, replacement) in regexRules) {
        result = result.replace(regex, replacement)
    }

    val dictionary = mapOf(
        "has the home advantage and a stronger recent form" to "hazai pályán játszik és jobb formát mutat",
        "in the top Hungarian league compared to" to "a magyar élvonalban a következőkkel szemben:",
        "who have drawn their last two league matches" to "akik az utolsó két meccsükön döntetlent játszottak",
        "showed offensive strength mostanában" to "jó támadójátékot mutatott mostanában",
        "scoring multiple goals at home" to "több gólt szerezve hazai pályán",
        "missing a key defender due to suspension" to "eltiltás miatt hiányzik egy kulcsvédője",
        "Despite some fatigue and squad rotation concerns for" to "Némi fáradtság és rotáció ellenére a következőknek:",
        "after recent European fixtures" to "a legutóbbi európai meccsek után",
        "their home form and historical head-to-head edge support a home victory" to "hazai formájuk és az egymás elleni mérlegük is hazai győzelmet sejtet.",
        "unbeaten so far this season" to "ebben a szezonban még veretlen",
        "playing confidently in their new" to "magabiztosan játszik az új",
        "with strong recent form" to "jó formában van",
        "in their last home match" to "a legutóbbi hazai meccsükön",
        "away win against" to "vendéggyőzelem a következők ellen:",
        "away win" to "vendéggyőzelem",
        "home win" to "hazai győzelem",
        "injury doubts" to "sérülési kétségek",
        "key attackers sidelined" to "kulcstámadók hiányoznak",
        "struggled away" to "szenvednek idegenben",
        "this season" to "ebben a szezonban",
        "head-to-head record" to "egymás elleni mérleg",
        "head-to-head edge" to "egymás elleni előny",
        "slightly at home" to "kissé hazai pályán",
        "market odds" to "fogadási oddsok",
        "strongly support" to "határozottan támogatják",
        "tactical insights suggest" to "a taktikai elemzés szerint",
        "solid defense" to "stabil védelem",
        "home advantage" to "hazai pálya előnye",
        "possession-based" to "labdabirtoklásra épülő",
        "less effective" to "kevésbé hatékony",
        "away performances" to "idegenbeli teljesítmény",
        "squad rotation concerns" to "keret-rotációs aggályok",
        "European fixtures" to "európai kupameccsek",
        "home victory" to "hazai győzelem",
        "away victory" to "vendéggyőzelem",
        "recently" to "mostanában",
        "against" to "ellen",
        "favors" to "favorizálja",
        "has the" to "rendelkezik a",
        "and a" to "és egy",
        "due to" to "miatt",
        "while" to "míg",
        "who have" to "akik",
        "win" to "győzelem",
        "draw" to "döntetlen"
    )

    val sortedDict = dictionary.entries.sortedByDescending { it.key.length }
    for ((en, hu) in sortedDict) {
        result = result.replace(Regex("(?i)" + Regex.escape(en)), hu)
    }

    return result
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

var selectedMatchGlobal: StatPalMatch? = null

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        composable("match_detail") {
                            selectedMatchGlobal?.let { match ->
                                MatchDetailScreen(match, navController, colors, betSlipItems) { item ->
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

                    // FOGADÁSI SZELVÉNY ALSÓ SÁV
                    if (betSlipItems.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            BetSlipBar(
                                items = betSlipItems,
                                colors = colors,
                                onClear = { betSlipItems = emptyList() }
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
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var favoriteIds by remember { mutableStateOf(getFavoriteMatchIds(context)) }
    var favoriteLeagueKeys by remember { mutableStateOf(getFavoriteLeagueIds(context)) }

    var leagues by remember { mutableStateOf<List<StatPalLeague>>(emptyList()) }
    var collapsedLeagueIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var previousScoresMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun fetchMatches(forceRefresh: Boolean = false) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            errorMessage = "Kérlek add meg a StatPal API kulcsodat a Beállításokban (⚙️)!"
            leagues = emptyList()
            return
        }

        val dateParam = formatDateForApi(selectedCalendar)
        val cacheKey = "matches_$dateParam"

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
                val response = if (isToday(selectedCalendar)) {
                    StatPalClient.service.getLiveMatches(apiKey, dateParam)
                } else {
                    try {
                        StatPalClient.service.getRecentUpcomingMatches(apiKey, dateParam)
                    } catch (_: Exception) {
                        StatPalClient.service.getLiveMatches(apiKey, dateParam)
                    }
                }

                val rawLeagues = response.liveMatches?.league ?: emptyList()

                val validLeagues = rawLeagues.filter { isAllowedLeague(it.name) }.mapNotNull { league ->
                    val matchesOnDate = league.match.filter { match ->
                        isMatchOnSelectedDate(match.date, selectedCalendar)
                    }
                    if (matchesOnDate.isNotEmpty()) league else null
                }

                // GÓL ÉRTESÍTÉSEK ELLENŐRZÉSE
                val newScoresMap = mutableMapOf<String, String>()
                validLeagues.flatMap { it.match }.forEach { m ->
                    val id = m.mainId ?: "${m.home?.name}-${m.away?.name}"
                    val scoreStr = "${m.home?.goals ?: "0"}-${m.away?.goals ?: "0"}"
                    newScoresMap[id] = scoreStr

                    if (previousScoresMap.containsKey(id) && isLiveMatch(m)) {
                        val oldScore = previousScoresMap[id]
                        if (oldScore != null && oldScore != scoreStr) {
                            Toast.makeText(
                                context,
                                "⚽ GÓL! ${m.home?.name} $scoreStr ${m.away?.name}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                previousScoresMap = newScoresMap

                leagues = validLeagues
                if (validLeagues.isNotEmpty()) {
                    ApiCacheManager.put(cacheKey, validLeagues)
                } else {
                    errorMessage = "Ezen a napon (${formatDateForDisplay(selectedCalendar)}) nincsenek mérkőzések."
                }
            } catch (e: Exception) {
                errorMessage = "Hiba a kapcsolódáskor: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedCalendar) {
        isLoading = true
        delay(400L)
        fetchMatches()
    }

    val totalLiveCount = remember(leagues) {
        leagues.sumOf { league -> league.match.count { isLiveMatch(it) } }
    }

    val filteredLeagues = remember(leagues, isOnlyLiveFilter, isTopLeaguesFilter, searchQuery, favoriteLeagueKeys) {
        val baseList = leagues.mapNotNull { league ->
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

    val featuredMatch = remember(leagues) {
        leagues.flatMap { it.match }.firstOrNull { isTopLeague(it.home?.name) || isLiveMatch(it) }
            ?: leagues.flatMap { it.match }.firstOrNull()
    }

    val favoriteMatchesList = remember(leagues, favoriteIds) {
        leagues.flatMap { league ->
            league.match.filter { match ->
                val id = match.mainId ?: "${match.home?.name}-${match.away?.name}"
                favoriteIds.contains(id)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // FELSŐ SÁV
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FOOTBALL FUTÁR",
                color = colors.accentPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )

            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSearchOpen) colors.accentPrimary else colors.cardBackground)
                        .clickable { isSearchOpen = !isSearchOpen }
                        .padding(6.dp)
                ) {
                    Text(text = "🔍", fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isOnlyLiveFilter) colors.accentRed else colors.cardBackground)
                        .clickable { isOnlyLiveFilter = !isOnlyLiveFilter }
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🔴", fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (totalLiveCount > 0) "ÉLŐ ($totalLiveCount)" else "ÉLŐ",
                            color = if (isOnlyLiveFilter) Color.White else colors.textPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isTopLeaguesFilter) colors.accentPrimary else colors.cardBackground)
                        .clickable { isTopLeaguesFilter = !isTopLeaguesFilter }
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "🏆 TOP",
                        color = if (isTopLeaguesFilter) Color.White else colors.textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // VISSZATETT KINYIT / ÖSSZECSUK IKON (📂 / 📁)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .clickable {
                            collapsedLeagueIds = if (collapsedLeagueIds.size == leagues.size && leagues.isNotEmpty()) {
                                emptySet()
                            } else {
                                leagues.mapNotNull { it.id ?: it.name }.toSet()
                            }
                        }
                        .padding(6.dp)
                ) {
                    Text(text = if (collapsedLeagueIds.isNotEmpty() && collapsedLeagueIds.size == leagues.size) "📂" else "📁", fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .clickable { onToggleDarkMode(!isDarkMode) }
                        .padding(6.dp)
                ) {
                    Text(text = if (isDarkMode) "☀️" else "🌙", fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .clickable { fetchMatches(forceRefresh = true) }
                        .padding(6.dp)
                ) {
                    Text(text = "🔄", fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .clickable { navController.navigate("api_settings") }
                        .padding(6.dp)
                ) {
                    Text(text = "⚙️", fontSize = 12.sp)
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

        // VÍZSZINTES DÁTUMVÁLASZTÓ CSÍK (DATE STRIP)
        DateStrip(
            selectedCalendar = selectedCalendar,
            onDateSelected = { selectedCalendar = it },
            colors = colors
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accentPrimary)
            }
        } else if (errorMessage != null) {
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
                // A NAP RANGADÓJA BANNER
                if (featuredMatch != null && searchQuery.isBlank() && !isOnlyLiveFilter) {
                    item {
                        FeaturedMatchBanner(featuredMatch, colors) {
                            selectedMatchGlobal = featuredMatch
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
                    items(favoriteMatchesList) { match ->
                        val matchId = match.mainId ?: "${match.home?.name}-${match.away?.name}"
                        MatchRow(
                            match,
                            true,
                            colors,
                            {
                                toggleFavoriteMatch(context, matchId)
                                favoriteIds = getFavoriteMatchIds(context)
                            },
                            {
                                selectedMatchGlobal = match
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
                        val matches = league.match.filter { match ->
                            isMatchOnSelectedDate(match.date, selectedCalendar)
                        }

                        items(matches) { match ->
                            val matchId = match.mainId ?: "${match.home?.name}-${match.away?.name}"
                            val isFav = favoriteIds.contains(matchId)

                            MatchRow(
                                match,
                                isFav,
                                colors,
                                {
                                    toggleFavoriteMatch(context, matchId)
                                    favoriteIds = getFavoriteMatchIds(context)
                                },
                                {
                                    selectedMatchGlobal = match
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

// ==========================================
// VÍZSZINTES DÁTUMVÁLASZTÓ COMPOSABLE
// ==========================================
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
                val (label, cal) = dateList[index]
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

// ==========================================
// A NAP RANGADÓJA BANNER
// ==========================================
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
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
                    modifier = Modifier.clickable { onTogglePin() }
                )
                Text(
                    text = title.uppercase(),
                    color = colors.accentPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isPinned) "⭐" else "📌",
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onTogglePin() }
                        .padding(end = 8.dp)
                )
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

@Composable
fun MatchRow(
    match: StatPalMatch,
    isFavorite: Boolean,
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

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.background),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(
                width = 1.dp,
                color = if (live) colors.accentRed.copy(alpha = 0.8f) else colors.border,
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
                    TeamLogo(
                        team = match.home,
                        colors = colors,
                        modifier = Modifier.size(20.dp),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = match.home?.name ?: "-",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(
                        team = match.away,
                        colors = colors,
                        modifier = Modifier.size(20.dp),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = match.away?.name ?: "-",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
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

// ==========================================
// MECCS RÉSZLETEI
// ==========================================
@Composable
fun MatchDetailScreen(
    match: StatPalMatch,
    navController: NavController,
    colors: AppColors,
    betSlipItems: List<BetSlipItem>,
    onToggleOdds: (BetSlipItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("H2H", "HIÁNYZÓK", "KEZDŐK", "AI TIPP", "TABELLA", "ODDSOK")

    var h2hMatches by remember { mutableStateOf<List<H2HMatch>>(emptyList()) }
    var isLoadingH2H by remember { mutableStateOf(false) }

    var injuryMatchData by remember { mutableStateOf<InjuryMatch?>(null) }
    var isLoadingInjuries by remember { mutableStateOf(false) }

    var lineupData by remember { mutableStateOf<StatPalLineupResponse?>(null) }
    var isLoadingLineup by remember { mutableStateOf(false) }

    var predictionData by remember { mutableStateOf<PredictionData?>(null) }
    var isLoadingPrediction by remember { mutableStateOf(false) }

    val homeG = match.home?.goals?.trim()
    val awayG = match.away?.goals?.trim()
    val hasValidGoals = !homeG.isNullOrBlank() && homeG != "?" && !awayG.isNullOrBlank() && awayG != "?"

    LaunchedEffect(selectedTab, match) {
        val apiKey = getApiKey(context)
        val homeId = match.home?.id ?: ""
        val awayId = match.away?.id ?: ""
        val matchId = match.mainId ?: ""

        if (apiKey.isBlank()) return@LaunchedEffect

        when (selectedTab) {
            0 -> {
                val cacheKey = "h2h_${homeId}_${awayId}"
                val cached: List<H2HMatch>? = ApiCacheManager.get(cacheKey)
                if (cached != null) {
                    h2hMatches = cached
                } else if (homeId.isNotBlank() && awayId.isNotBlank() && h2hMatches.isEmpty()) {
                    isLoadingH2H = true
                    coroutineScope.launch {
                        try {
                            val response = StatPalClient.service.getHeadToHead(apiKey, homeId, awayId)
                            val list = response.headToHead?.recentMeetings?.match ?: emptyList()
                            h2hMatches = list
                            ApiCacheManager.put(cacheKey, list)
                        } catch (_: Exception) {} finally {
                            isLoadingH2H = false
                        }
                    }
                }
            }
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
                            val allMatches = response.injuriesSuspensions?.league?.flatMap { it.match ?: emptyList() }
                            val found = allMatches?.find { it.mainId == match.mainId }
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
                        Spacer(modifier = Modifier.height(4.dp))
                        FormIndicator("WWDLW", colors)
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
                        Spacer(modifier = Modifier.height(4.dp))
                        FormIndicator("DLWWL", colors)
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
                    CircularProgressIndicator(color = colors.accentPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (h2hMatches.isEmpty()) {
                    Text("Nincsenek korábbi egymás elleni meccsadatok.", color = colors.textMuted, fontSize = 14.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            1 -> {
                if (isLoadingInjuries) {
                    CircularProgressIndicator(color = colors.accentPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (injuryMatchData == null) {
                    Text("Nincs információ sérültekről vagy eltiltottakról ehhez a meccshez.", color = colors.textMuted, fontSize = 14.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            TeamInjuriesSection(injuryMatchData?.home?.name ?: "Hazai", injuryMatchData?.home?.sidelined, colors)
                        }
                        item {
                            TeamInjuriesSection(injuryMatchData?.away?.name ?: "Vendég", injuryMatchData?.away?.sidelined, colors)
                        }
                    }
                }
            }
            2 -> {
                if (isLoadingLineup) {
                    CircularProgressIndicator(color = colors.accentPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (lineupData == null) {
                    Text("Még nem állnak rendelkezésre a kezdőcsapatok.", color = colors.textMuted, fontSize = 14.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            LineupSection(lineupData?.home, colors)
                        }
                        item {
                            LineupSection(lineupData?.away, colors)
                        }
                    }
                }
            }
            3 -> {
                if (isLoadingPrediction) {
                    CircularProgressIndicator(color = colors.accentPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (predictionData == null) {
                    Text("Ehhez a meccshez még nem érhető el AI elemzés.", color = colors.textMuted, fontSize = 14.sp)
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("VÁRHATÓ KIMENETEL", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = translateChoice(predictionData?.choice),
                                color = colors.accentPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ELEMZÉS & INDOKLÁS", color = colors.textMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = translateReasoning(predictionData?.reasoning),
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            4 -> {
                StandingsTab(match.home?.name ?: "Hazai", match.away?.name ?: "Vendég", colors)
            }
            5 -> {
                OddsTab(match, betSlipItems, colors, onToggleOdds)
            }
        }
    }
}

// ==========================================
// TABELLA KILISTÁZÓ COMPOSABLE
// ==========================================
@Composable
fun StandingsTab(homeTeam: String, awayTeam: String, colors: AppColors) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("BAJNOKI TABELLA", color = colors.accentPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("#", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                Text("CSAPAT", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("M", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                Text("GY", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                Text("D", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                Text("V", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                Text("P", color = colors.accentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
            }

            val mockTable = listOf(
                Triple("1", homeTeam, Triple("22", "15", "45")),
                Triple("2", "Ferencváros", Triple("22", "14", "43")),
                Triple("3", "Puskás Akadémia", Triple("22", "12", "38")),
                Triple("4", awayTeam, Triple("22", "10", "32")),
                Triple("5", "Fehérvár FC", Triple("22", "9", "29"))
            )

            mockTable.forEach { (pos, team, stats) ->
                val isTarget = team == homeTeam || team == awayTeam
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(pos, color = if (isTarget) colors.accentPrimary else colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                    Text(team, color = if (isTarget) colors.accentPrimary else colors.textPrimary, fontSize = 12.sp, fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    Text(stats.first, color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Text(stats.second, color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Text("3", color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Text("4", color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Text(stats.third, color = colors.accentPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                }
            }
        }
    }
}

// ==========================================
// ODDS ÉS FOGADÁSI SZELVÉNY COMPOSABLE
// ==========================================
@Composable
fun OddsTab(
    match: StatPalMatch,
    betSlipItems: List<BetSlipItem>,
    colors: AppColors,
    onToggleOdds: (BetSlipItem) -> Unit
) {
    val matchId = match.mainId ?: "${match.home?.name}-${match.away?.name}"
    val matchTitle = "${match.home?.name} vs ${match.away?.name}"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("MÉRKŐZÉS GYŐZTES (1X2)", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val is1 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Hazai (1)" }
                    val isX = betSlipItems.any { it.matchId == matchId && it.choiceName == "Döntetlen (X)" }
                    val is2 = betSlipItems.any { it.matchId == matchId && it.choiceName == "Vendég (2)" }

                    OddsBox("1 (Hazai)", "2.10", isSelected = is1, colors = colors, modifier = Modifier.weight(1f)) {
                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Hazai (1)", 2.10))
                    }
                    OddsBox("X (Döntetlen)", "3.40", isSelected = isX, colors = colors, modifier = Modifier.weight(1f)) {
                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Döntetlen (X)", 3.40))
                    }
                    OddsBox("2 (Vendég)", "3.20", isSelected = is2, colors = colors, modifier = Modifier.weight(1f)) {
                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Vendég (2)", 3.20))
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("GÓLOK SZÁMA (OVER / UNDER 2.5)", color = colors.accentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isOver = betSlipItems.any { it.matchId == matchId && it.choiceName == "Több mint 2.5 gól" }
                    val isUnder = betSlipItems.any { it.matchId == matchId && it.choiceName == "Kevesebb mint 2.5" }

                    OddsBox("Több mint 2.5 gól", "1.85", isSelected = isOver, colors = colors, modifier = Modifier.weight(1f)) {
                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Több mint 2.5 gól", 1.85))
                    }
                    OddsBox("Kevesebb mint 2.5", "1.95", isSelected = isUnder, colors = colors, modifier = Modifier.weight(1f)) {
                        onToggleOdds(BetSlipItem(matchId, matchTitle, "Kevesebb mint 2.5", 1.95))
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
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) colors.accentPrimary else colors.background)
            .border(1.dp, if (isSelected) colors.accentPrimary else colors.border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = if (isSelected) Color.White else colors.textMuted, fontSize = 10.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(odds, color = if (isSelected) Color.White else colors.accentPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
    }
}

@Composable
fun BetSlipBar(
    items: List<BetSlipItem>,
    colors: AppColors,
    onClear: () -> Unit
) {
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

        lineup?.startingXi?.forEach { player ->
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
    val toMiss = sidelined?.toMiss?.player ?: emptyList()
    val questionable = sidelined?.questionable?.player ?: emptyList()

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
            text = "API BEÁLLÍTÁSOK",
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Illeszd be a StatPal Starter API kulcsodat az élő adatok lekéréséhez.",
            color = colors.textMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

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

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                saveApiKey(context, apiKeyInput)
                Toast.makeText(context, "API Kulcs elmentve!", Toast.LENGTH_SHORT).show()
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
