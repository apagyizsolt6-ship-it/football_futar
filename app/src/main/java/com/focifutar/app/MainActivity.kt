package com.focifutar.app

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

val BackgroundDark = Color(0xFF101214)
val CardBackground = Color(0xFF1A1D21)
val AccentGreen = Color(0xFF00FF66)
val AccentRed = Color(0xFFFF3366)
val AccentYellow = Color(0xFFFFCC00)
val TextWhite = Color(0xFFFFFFFF)
val TextMuted = Color(0xFF8C96A0)

// ==========================================
// API TÁROLÓ (CACHE MANAGER)
// ==========================================
object ApiCacheManager {
    private val cacheMap = mutableMapOf<String, Pair<Long, Any>>()
    private const val DEFAULT_TTL_MS = 5 * 60 * 1000L // 5 perc tárolás

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

    fun remove(key: String) {
        cacheMap.remove(key)
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

// ==========================================
// KEDVENCEK TÁROLÁSA
// ==========================================
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

// ==========================================
// HIVATALOS STATPAL LOGÓ URL GENERÁLÓ
// ==========================================
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

// ==========================================
// CSAPAT LOGÓ COMPOSABLE
// ==========================================
@Composable
fun TeamLogo(
    team: StatPalTeam?,
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
                .background(CardBackground)
                .border(1.dp, AccentGreen.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = teamName.take(1).uppercase(),
                color = AccentGreen,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==========================================
// DÁTUM HELPEREK ÉS SZŰRÉS
// ==========================================
fun formatDateForApi(cal: Calendar): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

fun formatDateForDisplay(cal: Calendar): String {
    val sdf = SimpleDateFormat("yyyy.MM.dd. (EEE)", Locale("hu", "HU"))
    return sdf.format(cal.time).uppercase()
}

fun isToday(cal: Calendar): Boolean {
    val today = Calendar.getInstance()
    return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
           cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
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

// ==========================================
// ÉLŐ MECCS FELISMERŐ
// ==========================================
fun isLiveMatch(match: StatPalMatch): Boolean {
    val rawStatus = (match.status ?: match.time ?: "").trim().uppercase()
    if (rawStatus.isBlank()) return false
    if (rawStatus == "FT" || rawStatus == "VÉGE") return false
    if (rawStatus == "POSTPONED" || rawStatus == "ELHALASZTVA") return false
    if (rawStatus == "CANCELLED" || rawStatus == "ELMARADT") return false
    if (rawStatus.contains(":") && !rawStatus.contains("'")) return false
    return true
}

// ==========================================
// OPTIMÁLIS AMATŐR SZŰRŐ
// ==========================================
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

// ==========================================
// ORSZÁG ÉS LIGA MAGYARÍTÓ
// ==========================================
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
    return when (status.uppercase().trim()) {
        "FT" -> "VÉGE"
        "HT" -> "FÉLIDŐ"
        "POSTPONED" -> "ELHALASZTVA"
        "CANCELLED" -> "ELMARADT"
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

// ==========================================
// MINTAILLESZTŐ ÉS DYNAMIKUS AI ELEMZÉS FORDÍTÓ
// ==========================================
fun translateReasoning(text: String?): String {
    if (text.isNullOrBlank()) return "-"
    var result: String = text.trim()

    val regexRules = listOf(
        Regex("(?i)([A-Za-z0-9\\s]+) is unbeaten so far this season") to "A(z) $1 ebben a szezonban még veretlen",
        Regex("(?i)playing confidently in their new ([A-Za-z0-9\\s]+)") to "magabiztosan játszik az új $1 stadionban",
        Regex("(?i)with strong recent form including a (\\d+-\\d+) win in their last home match") to "meggyőző formában van, beleértve a legutóbbi hazai $1-es győzelmét",
        Regex("(?i)and a (\\d+-\\d+) away win against ([A-Za-z0-9\\s]+) recently") to "és a legutóbbi $1-es vendéggyőzelmét a(z) $2 ellen",
        Regex("(?i)and a (\\d+-\\d+) vendég győzelem against ([A-Za-z0-9\\s]+) recently") to "és a legutóbbi $1-es vendéggyőzelmét a(z) $2 ellen",
        Regex("(?i)([A-Za-z0-9\\s]+) has injury doubts for their goalkeeper and key attackers sidelined") to "A(z) $1 csapatánál a kapus játéka kérdéses, a kulcstámadók pedig sérültek",
        Regex("(?i)and they have struggled away with only one away win this season") to "és idegenben szenvednek, mindössze egy vendéggyőzelemmel ebben a szezonban",
        Regex("(?i)and they have struggled away with only one vendég győzelem this season") to "és idegenben szenvednek, mindössze egy vendéggyőzelemmel ebben a szezonban",
        Regex("(?i)The head-to-head record favors ([A-Za-z0-9\\s]+) slightly at home") to "Az egymás elleni mérleg kissé a(z) $1 csapatának kedvez hazai pályán",
        Regex("(?i)and market odds strongly support a ([A-Za-z0-9\\s]+) win") to "és a fogadási oddsok is határozottan a(z) $1 győzelmét támogatják",
        Regex("(?i)Tactical insights suggest ([A-Za-z0-9\\s']+)'s solid defense and home advantage will be key against ([A-Za-z0-9\\s']+)'s possession-based but less effective away performances") to "A taktikai elemzés szerint a hazaiak stabil védelme és a pályaelőny kulcsfontos lesz a vendégek labdabirtoklásra épülő, de idegenben kevésbé hatékony játéka ellen",
        Regex("(?i)([A-Za-z0-9\\s]+) has a strong historical head-to-head advantage over ([A-Za-z0-9\\s]+)") to "A(z) $1 jelentős egymás elleni előnnyel rendelkezik a(z) $2 ellen",
        Regex("(?i)([A-Za-z0-9\\s]+) and ([A-Za-z0-9\\s]+) have a closely matched recent head-to-head record") to "A(z) $1 és a(z) $2 kiegyenlített egymás elleni mérleggel rendelkeznek",
        Regex("(?i)Given these factors, a draw is the most likely outcome") to "Ezek alapján a döntetlen a legvalószínűbb kimenetel",
        Regex("(?i)([A-Za-z0-9\\s]+) is playing at home with a fully fit squad") to "A(z) $1 hazai pályán játszik teljes kerettel",
        Regex("(?i)Market odds also support a ([A-Za-z0-9\\s]+) win") to "A fogadási oddsok is a(z) $1 győzelmét támogatják"
    )

    for ((regex, replacement) in regexRules) {
        result = result.replace(regex, replacement)
    }

    val dictionary = mapOf(
        "unbeaten so far this season" to "ebben a szezonban még veretlen",
        "playing confidently in their new" to "magabiztosan játszik az új",
        "with strong recent form" to "jó formában van",
        "in their last home match" to "a legutóbbi hazai meccsükön",
        "away win against" to "vendéggyőzelem a következők ellen:",
        "away win" to "vendéggyőzelem",
        "home win" to "hazai győzelem",
        "vendég győzelem" to "vendéggyőzelem",
        "injury doubts" to "sérülési kétségek",
        "key attackers sidelined" to "kulcstámadók hiányoznak",
        "struggled away" to "szenvednek idegenben",
        "this season" to "ebben a szezonban",
        "head-to-head record" to "egymás elleni mérleg",
        "slightly at home" to "kissé hazai pályán",
        "market odds" to "fogadási oddsok",
        "strongly support" to "határozottan támogatják",
        "tactical insights suggest" to "a taktikai elemzés szerint",
        "solid defense" to "stabil védelem",
        "home advantage" to "hazai pálya előnye",
        "possession-based" to "labdabirtoklásra épülő",
        "less effective" to "kevésbé hatékony",
        "away performances" to "idegenbeli teljesítmény",
        "recently" to "mostanában",
        "against" to "ellen",
        "favors" to "favorizálja",
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
            MaterialTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "matches_list") {
                    composable("matches_list") {
                        MatchesListScreen(navController)
                    }
                    composable("api_settings") {
                        ApiSettingsScreen(navController)
                    }
                    composable("match_detail") {
                        selectedMatchGlobal?.let { match ->
                            MatchDetailScreen(match, navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchesListScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var isOnlyLiveFilter by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var favoriteIds by remember { mutableStateOf(getFavoriteMatchIds(context)) }

    var leagues by remember { mutableStateOf<List<StatPalLeague>>(emptyList()) }
    var collapsedLeagueIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    // 400ms KÉSLELTETÉS DÁTUMVÁLTÁSNÁL (DEBOUNCE)
    LaunchedEffect(selectedCalendar) {
        isLoading = true
        delay(400L)
        fetchMatches()
    }

    val totalLiveCount = remember(leagues) {
        leagues.sumOf { league -> league.match.count { isLiveMatch(it) } }
    }

    val filteredLeagues = remember(leagues, isOnlyLiveFilter, searchQuery) {
        leagues.mapNotNull { league ->
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
            .background(BackgroundDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FOOTBALL FUTÁR",
                color = AccentGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSearchOpen) AccentGreen else CardBackground)
                        .clickable { isSearchOpen = !isSearchOpen }
                        .padding(8.dp)
                ) {
                    Text(text = "🔍", fontSize = 14.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isOnlyLiveFilter) AccentRed else CardBackground)
                        .clickable { isOnlyLiveFilter = !isOnlyLiveFilter }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🔴", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (totalLiveCount > 0) "ÉLŐ ($totalLiveCount)" else "ÉLŐ",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CardBackground)
                        .clickable {
                            collapsedLeagueIds = if (collapsedLeagueIds.size == leagues.size) {
                                emptySet()
                            } else {
                                leagues.mapNotNull { it.id ?: it.name }.toSet()
                            }
                        }
                        .padding(8.dp)
                ) {
                    Text(text = if (collapsedLeagueIds.size == leagues.size) "📂" else "📁", fontSize = 14.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CardBackground)
                        .clickable { fetchMatches(forceRefresh = true) } // Kényszerített frissítés
                        .padding(8.dp)
                ) {
                    Text(text = "🔄", fontSize = 14.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CardBackground)
                        .clickable { navController.navigate("api_settings") }
                        .padding(8.dp)
                ) {
                    Text(text = "⚙️", fontSize = 14.sp)
                }
            }
        }

        if (isSearchOpen) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Keresés csapat vagy bajnokság alapján...", color = TextMuted, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = CardBackground,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val newCal = Calendar.getInstance().apply {
                        time = selectedCalendar.time
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                    selectedCalendar = newCal
                }) {
                    Text("◄", color = AccentGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BackgroundDark)
                        .clickable {
                            val year = selectedCalendar.get(Calendar.YEAR)
                            val month = selectedCalendar.get(Calendar.MONTH)
                            val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)

                            DatePickerDialog(context, { _, selectedYear, selectedMonth, selectedDay ->
                                val newCal = Calendar.getInstance()
                                newCal.set(selectedYear, selectedMonth, selectedDay)
                                selectedCalendar = newCal
                            }, year, month, day).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📅", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatDateForDisplay(selectedCalendar),
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isToday(selectedCalendar)) {
                        Text(
                            text = "MA",
                            color = AccentYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CardBackground)
                                .clickable { selectedCalendar = Calendar.getInstance() }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(onClick = {
                        val newCal = Calendar.getInstance().apply {
                            time = selectedCalendar.time
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                        selectedCalendar = newCal
                    }) {
                        Text("►", color = AccentGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentGreen)
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
                    color = AccentRed,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (favoriteMatchesList.isNotEmpty() && searchQuery.isBlank() && !isOnlyLiveFilter) {
                    item {
                        LeagueHeader(title = "⭐ KEDVENC MECCSEK", isCollapsed = false, onToggle = {})
                    }
                    items(favoriteMatchesList) { match ->
                        val matchId = match.mainId ?: "${match.home?.name}-${match.away?.name}"
                        MatchRow(
                            match,
                            true,
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

                    item {
                        LeagueHeader(
                            title = translateLeagueName(league.name),
                            isCollapsed = isCollapsed,
                            onToggle = {
                                collapsedLeagueIds = if (isCollapsed) {
                                    collapsedLeagueIds - leagueKey
                                } else {
                                    collapsedLeagueIds + leagueKey
                                }
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

@Composable
fun LeagueHeader(title: String, isCollapsed: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground)
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                color = AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isCollapsed) "▼" else "▲",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MatchRow(
    match: StatPalMatch,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val rawStatus = match.status ?: match.time ?: ""
    val live = isLiveMatch(match)
    val formattedTime = formatToLocalTime(match.date, rawStatus)

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundDark),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(
                width = 1.dp,
                color = if (live) AccentRed.copy(alpha = 0.6f) else Color.Transparent,
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
                modifier = Modifier
                    .clickable { onToggleFavorite() }
                    .padding(end = 8.dp)
            )

            Text(
                text = formattedTime,
                color = if (live) AccentRed else TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(50.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(
                        team = match.home,
                        modifier = Modifier.size(20.dp),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = match.home?.name ?: "-",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(
                        team = match.away,
                        modifier = Modifier.size(20.dp),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = match.away?.name ?: "-",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = match.home?.goals ?: "0",
                    color = if (live) AccentGreen else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = match.away?.goals ?: "0",
                    color = if (live) AccentGreen else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

// ==========================================
// MECCS RÉSZLETEI - LUSTA BETÖLTÉS ÉS CACHE
// ==========================================
@Composable
fun MatchDetailScreen(match: StatPalMatch, navController: NavController) {
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

    // LUSTA BETÖLTÉS (LAZY LOADING): CSAK AKKOR TÖLT, AMIKOR AZ ADOTT FÜLRE LÉPSZ
    LaunchedEffect(selectedTab, match) {
        val apiKey = getApiKey(context)
        val homeId = match.home?.id ?: ""
        val awayId = match.away?.id ?: ""
        val matchId = match.mainId ?: ""

        if (apiKey.isBlank()) return@LaunchedEffect

        when (selectedTab) {
            0 -> { // H2H
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
            1 -> { // HIÁNYZÓK
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
            2 -> { // KEZDŐK
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
            3 -> { // AI TIPP
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
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        Text(
            text = "← Vissza",
            color = AccentGreen,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { navController.popBackStack() }
                .padding(bottom = 16.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatToLocalTime(match.date, match.status ?: match.time ?: ""),
                    color = AccentRed,
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
                        TeamLogo(
                            team = match.home,
                            modifier = Modifier.size(40.dp),
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(match.home?.name ?: "-", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Text("${match.home?.goals ?: "0"} - ${match.away?.goals ?: "0"}", color = AccentGreen, fontSize = 28.sp, fontWeight = FontWeight.Black)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TeamLogo(
                            team = match.away,
                            modifier = Modifier.size(40.dp),
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(match.away?.name ?: "-", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardBackground,
            contentColor = AccentGreen,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> {
                if (isLoadingH2H) {
                    CircularProgressIndicator(color = AccentGreen, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (h2hMatches.isEmpty()) {
                    Text("Nincsenek korábbi egymás elleni meccsadatok.", color = TextMuted, fontSize = 14.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(h2hMatches) { h2h ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(h2h.date ?: "", color = TextMuted, fontSize = 11.sp)
                                        Text("${h2h.team1Name} vs ${h2h.team2Name}", color = TextWhite, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    }
                                    Text("${h2h.team1Score} - ${h2h.team2Score}", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                if (isLoadingInjuries) {
                    CircularProgressIndicator(color = AccentGreen, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (injuryMatchData == null) {
                    Text("Nincs információ sérültekről vagy eltiltottakról ehhez a meccshez.", color = TextMuted, fontSize = 14.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            TeamInjuriesSection(injuryMatchData?.home?.name ?: "Hazai", injuryMatchData?.home?.sidelined)
                        }
                        item {
                            TeamInjuriesSection(injuryMatchData?.away?.name ?: "Vendég", injuryMatchData?.away?.sidelined)
                        }
                    }
                }
            }
            2 -> {
                if (isLoadingLineup) {
                    CircularProgressIndicator(color = AccentGreen, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (lineupData == null) {
                    Text("Még nem állnak rendelkezésre a kezdőcsapatok.", color = TextMuted, fontSize = 14.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            LineupSection(lineupData?.home)
                        }
                        item {
                            LineupSection(lineupData?.away)
                        }
                    }
                }
            }
            3 -> {
                if (isLoadingPrediction) {
                    CircularProgressIndicator(color = AccentGreen, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (predictionData == null) {
                    Text("Ehhez a meccshez még nem érhető el AI elemzés.", color = TextMuted, fontSize = 14.sp)
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("VÁRHATÓ KIMENETEL", color = AccentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = translateChoice(predictionData?.choice),
                                color = AccentGreen,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ELEMZÉS & INDOKLÁS", color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = translateReasoning(predictionData?.reasoning),
                                color = TextWhite,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            4 -> {
                Text("A tabella adatok betöltése folyamatban...", color = TextMuted, fontSize = 14.sp)
            }
            5 -> {
                Text("Oddsok betöltése...", color = TextMuted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun LineupSection(lineup: LineupTeam?) {
    Column {
        Text(
            text = "${lineup?.teamName?.uppercase() ?: "CSAPAT"} (${lineup?.teamFormation ?: "-"})",
            color = AccentGreen,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text("Edző: ${lineup?.coach?.name ?: "-"}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))

        lineup?.startingXi?.forEach { player ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${player.number ?: ""}. ${player.name ?: ""}", color = TextWhite, fontSize = 13.sp)
                Text(translatePosition(player.position).uppercase(), color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun TeamInjuriesSection(teamName: String, sidelined: SidelinedData?) {
    val toMiss = sidelined?.toMiss?.player ?: emptyList()
    val questionable = sidelined?.questionable?.player ?: emptyList()

    Column {
        Text(teamName.uppercase(), color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))

        if (toMiss.isEmpty() && questionable.isEmpty()) {
            Text("Nincs bejelentett hiányzó.", color = TextMuted, fontSize = 12.sp)
        } else {
            toMiss.forEach { player ->
                PlayerInjuryRow(player.name ?: "", translateInjuryStatus(player.status), isQuestionable = false)
            }
            questionable.forEach { player ->
                PlayerInjuryRow(player.name ?: "", translateInjuryStatus(player.status), isQuestionable = true)
            }
        }
    }
}

@Composable
fun PlayerInjuryRow(name: String, status: String, isQuestionable: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(name, color = TextWhite, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(status, color = TextMuted, fontSize = 11.sp)
            }
            Text(
                text = if (isQuestionable) "KÉRDÉSES" else "KIMARAD",
                color = if (isQuestionable) AccentYellow else AccentRed,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ApiSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    var apiKeyInput by remember { mutableStateOf(getApiKey(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
    ) {
        Text(
            text = "← Vissza",
            color = AccentGreen,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { navController.popBackStack() }
                .padding(bottom = 20.dp)
        )

        Text(
            text = "API BEÁLLÍTÁSOK",
            color = TextWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Illeszd be a StatPal Starter API kulcsodat az élő adatok lekéréséhez.",
            color = TextMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("StatPal API Key", color = TextMuted) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = CardBackground,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
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
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Mentés", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
