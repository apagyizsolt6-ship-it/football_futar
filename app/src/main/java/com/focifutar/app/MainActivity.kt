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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
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
// CSAPAT LOGÓ COMPOSABLE
// ==========================================
@Composable
fun TeamLogo(logoUrl: String?, teamName: String, modifier: Modifier = Modifier.size(20.dp)) {
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
                .background(CardBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = teamName.take(1).uppercase(),
                color = AccentGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==========================================
// DÁTUM HELPEREK ÉS RUGALMAS SZŰRÉS
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

    // Gyors string-egyezés
    if (raw.contains("$yFour-$mTwo-$dTwo") ||
        raw.contains("$dTwo.$mTwo.$yFour") ||
        raw.contains("$dTwo-$mTwo-$yFour") ||
        raw.contains("$yFour.$mTwo.$dTwo")) {
        return true
    }

    // Dátum és időpont formátumok próbája
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
// OPTIMÁLIS AMATŐR SZŰRŐ (CSAK AZ IGAZI AMATŐR/UTÁNPÓTLÁS KISZŰRÉSE)
// ==========================================
fun isAllowedLeague(leagueName: String?): Boolean {
    if (leagueName.isNullOrBlank()) return false
    val nameLower = leagueName.lowercase()

    val forbiddenKeywords = listOf(
        // Női & Utánpótlás & Tartalék
        "women", "női", "wom", "femenina", "feminina", "frauen", "ladies",
        "u17", "u18", "u19", "u20", "u21", "u23", "youth", "junior", "u-19", "u-21",
        "reserve", "reserves", "(am)", "b-team", "b team", "sub-20", "sub-17",

        // Tényleges amatőr & 3-4. osztályú ligák
        "3. cfl", "3. msfl", "kakkonen", "oberliga", "landesliga",
        "torneo federal", "gaucho 2", "amazonense 2", "vtora liga",
        "calcutta", "durand cup", "cecafa", "1.liga classic",
        "cymru south", "cymru north", "iii liga", "fnl 2",
        "southern league", "isthmian", "serie d",
        "capixaba", "cearense", "goiano", "mineiro", "paranaense", "copa paulista",
        "copa governo", "nakotnes"
    )

    return forbiddenKeywords.none { nameLower.contains(it) }
}

// ==========================================
// MAGYAROSÍTÓ HELPEREK
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
        "CANADA" to "🇨🇦 KANADA", "CHILE" to "🇨🇱 CHILE", "CHINA" to "🇨🇳 KÍNA", "COLOMBIA" to "🇨🇴 KOLUMBIA",
        "CROATIA" to "🇭🇷 HORVÁTORSZÁG", "CYPRUS" to "🇨🇾 CIPRUS", "CZECH REPUBLIC" to "🇨ZEHORSZÁG", "DENMARK" to "🇩🇰 DÁNIA",
        "ECUADOR" to "🇪🇨 ECUADOR", "EGYPT" to "🇪🇬 EGYIPTOM", "ENGLAND" to "🏴󠁧󠁢󠁥󠁮󠁧󠁿 ANGLIA", "ESTONIA" to "🇪🇪 ÉSZTORSZÁG",
        "EUROPE" to "🇪🇺 EURÓPA", "FAROE ISLANDS" to "🇫🇴 FERÖER", "FINLAND" to "🇫🇮 FINNORSZÁG", "FRANCE" to "🇫🇷 FRANCIAORSZÁG",
        "GEORGIA" to "🇬🇪 GRÚZIA", "GERMANY" to "🇩🇪 NÉMETORSZÁG", "GREECE" to "🇬🇷 GÖRÖGORSZÁG", "HUNGARY" to "🇭🇺 MAGYARORSZÁG",
        "ICELAND" to "🇮🇸 IZLAND", "INDIA" to "🇮🇳 INDIA", "INDONESIA" to "🇮🇩 INDONÉZIA", "IRAN" to "🇮🇷 IRÁN",
        "IRELAND" to "🇮🇪 ÍRORSZÁG", "ISRAEL" to "🇮🇱 IZRAEL", "ITALY" to "🇮🇹 OLASZORSZÁG", "JAPAN" to "🇯🇵 JAPÁN",
        "KAZAKHSTAN" to "🇰🇿 KAZAHSZTÁN", "KUWAIT" to "🇰🇼 KUVAT", "KYRGYZSTAN" to "🇰🇬 KIRGIZISZTÁN", "LATVIA" to "🇱🇻 LETTORSZÁG",
        "LITHUANIA" to "🇱🇹 LITVÁNIA", "LUXEMBOURG" to "🇱🇺 LUXEMBURG", "MEXICO" to "🇲🇽 MEXIKÓ", "MOLDOVA" to "🇲🇩 MOLDOVA",
        "MONTENEGRO" to "🇲🇪 MONTENEGRÓ", "MOROCCO" to "🇲🇦 MAROKKÓ", "NETHERLANDS" to "🇳🇱 HOLLANDIA", "NORWAY" to "🇳🇴 NORVÉGIA",
        "POLAND" to "🇵🇱 LENGYELORSZÁG", "PORTUGAL" to "🇵🇹 PORTUGÁLIA", "QATAR" to "🇶🇦 KATAR", "ROMANIA" to "🇷🇴 ROMÁNIA",
        "RUSSIA" to "🇷🇺 OROSZORSZÁG", "SAUDI ARABIA" to "🇸🇦 SZAÚD-ARÁBIA", "SCOTLAND" to "🏴󠁧󠁢󠁳󠁣󠁴󠁿 SKÓCIA", "SERBIA" to "🇷🇸 SZERBIA",
        "SLOVAKIA" to "🇸🇰 SZLOVÁKIA", "SLOVENIA" to "🇸🇮 SZLOVÉNIA", "SOUTH AMERICA" to "🌎 DÉL-AMERIKA", "SPAIN" to "🇪🇸 SPANYOLORSZÁG",
        "SWEDEN" to "🇸🇪 SVÉDORSZÁG", "SWITZERLAND" to "🇨🇭 SVÁJC", "TURKEY" to "🇹🇷 TÖRÖKORSZÁG", "UKRAINE" to "🇺🇦 UKRAJNA",
        "USA" to "🇺🇸 USA", "URUGUAY" to "🇺🇾 URUGUAY", "WORLD" to "🌐 VILÁG"
    )

    for ((en, hu) in countryMap) {
        if (countryPart.equals(en, ignoreCase = true)) {
            countryPart = hu
            break
        }
    }

    val termMap = mapOf(
        "PLAY OFFS" to "RÁJÁTSZÁS", "PLAY-OFFS" to "RÁJÁTSZÁS",
        "PLACEMENT MATCHES" to "HELYOSZTÓK", "WINNERS STAGE" to "GYŐZTESEK SZAKASZA",
        "CHAMPIONS LEAGUE" to "BAJNOKOK LIGÁJA", "EUROPA LEAGUE" to "EURÓPA-LIGA",
        "CONFERENCE LEAGUE" to "KONFERENCIA LIGA", "FRIENDLIES" to "BARÁTSÁGOS"
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

fun translateReasoning(text: String?): String {
    if (text.isNullOrBlank()) return "-"
    var result: String = text

    val phraseReplacements = mapOf(
        "have a closely matched recent head-to-head record with many draws" to "szoros és kiegyenlített egymás elleni mérleggel rendelkeznek, sok döntetlennel",
        "have a closely matched recent head-to-head record" to "szoros egymás elleni mérleggel rendelkeznek",
        "including two 2-2 draws in their last meetings" to "beleértve két 2-2-es döntetlent a legutóbbi meccseiken",
        "in their last meetings" to "a legutóbbi összecsapásaikon",
        "Both teams are in good form" to "Mindkét csapat jó formának örvend",
        "unbeaten at home recently" to "hazai pályán veretlen mostanában",
        "showing solid away performances" to "meggyőzően szerepel vendégként",
        "The lineups are near full strength with no significant injuries" to "A keretek hiányzók nélkül, közel teljes erősségűek",
        "and the market odds also suggest a balanced contest" to "és a fogadási oddsok is szoros mérkőzést sejtetnek",
        "Given these factors, a draw is the most likely outcome" to "Ezek alapján a döntetlen a legvalószínűbb kimenetel",
        "is the most likely outcome" to "a legvalószínűbb kimenetel",
        "is playing at home with a fully fit squad" to "hazai pályán játszik teljes kerettel",
        "aims to bounce back" to "javítani szeretne",
        "after an opening day loss" to "a nyitófordulós vereség után",
        "despite a stable lineup" to "a stabil felállás ellenére",
        "has shown defensive vulnerabilities" to "védelmi sebezhetőséget mutatott",
        "has a less favorable away record" to "gyengébb vendégmérleggel rendelkezik",
        "holds an unbeaten record" to "veretlen sorozattal bír",
        "the market odds favor" to "a fogadási oddsok a következőt favorizálják:",
        "a home win with a reasonable margin" to "meggyőző hazai győzelem",
        "home win" to "hazai győzelem",
        "away win" to "vendég győzelem",
        "recent head-to-head win" to "legutóbbi egymás elleni győzelem",
        "shows stronger form" to "jobb formát mutat",
        "key attacking players fit and motivated" to "a kulcsfontosságú támadók fittek és motiváltak",
        "lacks injuries" to "nincsenek sérültjei",
        "has shown mixed recent results" to "felemás teljesítményt nyújtott mostanában",
        "döntetlens" to "döntetlen"
    )

    for ((en, hu) in phraseReplacements) {
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

    fun fetchMatches() {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            errorMessage = "Kérlek add meg a StatPal API kulcsodat a Beállításokban (⚙️)!"
            leagues = emptyList()
        } else {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    val dateParam = formatDateForApi(selectedCalendar)

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

                    leagues = rawLeagues.filter { isAllowedLeague(it.name) }.mapNotNull { league ->
                        val matchesOnDate = league.match.filter { match ->
                            isMatchOnSelectedDate(match.date, selectedCalendar)
                        }

                        if (matchesOnDate.isNotEmpty()) {
                            league
                        } else null
                    }

                    if (leagues.isEmpty()) {
                        errorMessage = "Ezen a napon (${formatDateForDisplay(selectedCalendar)}) nincsenek mérkőzések."
                    }
                } catch (e: Exception) {
                    errorMessage = "Hiba a kapcsolódáskor: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(selectedCalendar) {
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
                        .clickable { fetchMatches() }
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
                        logoUrl = match.home?.logo ?: match.home?.image,
                        teamName = match.home?.name ?: ""
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
                        logoUrl = match.away?.logo ?: match.away?.image,
                        teamName = match.away?.name ?: ""
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

    LaunchedEffect(match) {
        val apiKey = getApiKey(context)
        val homeId = match.home?.id ?: ""
        val awayId = match.away?.id ?: ""
        val matchId = match.mainId ?: ""

        if (apiKey.isNotBlank()) {
            if (homeId.isNotBlank() && awayId.isNotBlank()) {
                isLoadingH2H = true
                coroutineScope.launch {
                    try {
                        val response = StatPalClient.service.getHeadToHead(apiKey, homeId, awayId)
                        h2hMatches = response.headToHead?.recentMeetings?.match ?: emptyList()
                    } catch (_: Exception) {} finally {
                        isLoadingH2H = false
                    }
                }
            }

            isLoadingInjuries = true
            coroutineScope.launch {
                try {
                    val response = StatPalClient.service.getInjuriesAndSuspensions(apiKey)
                    val allMatches = response.injuriesSuspensions?.league?.flatMap { it.match ?: emptyList() }
                    injuryMatchData = allMatches?.find { it.mainId == match.mainId }
                } catch (_: Exception) {} finally {
                    isLoadingInjuries = false
                }
            }

            if (matchId.isNotBlank()) {
                isLoadingLineup = true
                coroutineScope.launch {
                    try {
                        lineupData = StatPalClient.service.getTeamLineups(apiKey, matchId)
                    } catch (_: Exception) {} finally {
                        isLoadingLineup = false
                    }
                }

                isLoadingPrediction = true
                coroutineScope.launch {
                    try {
                        val response = StatPalClient.service.getMatchPrediction(apiKey, matchId)
                        predictionData = response.prediction
                    } catch (_: Exception) {} finally {
                        isLoadingPrediction = false
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
                        TeamLogo(logoUrl = match.home?.logo ?: match.home?.image, teamName = match.home?.name ?: "", modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(match.home?.name ?: "-", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Text("${match.home?.goals ?: "0"} - ${match.away?.goals ?: "0"}", color = AccentGreen, fontSize = 28.sp, fontWeight = FontWeight.Black)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TeamLogo(logoUrl = match.away?.logo ?: match.away?.image, teamName = match.away?.name ?: "", modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(4.dp))
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
