package com.focifutar.app

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
import kotlinx.coroutines.launch

val BackgroundDark = Color(0xFF101214)
val CardBackground = Color(0xFF1A1D21)
val AccentGreen = Color(0xFF00FF66)
val AccentRed = Color(0xFFFF3366)
val AccentYellow = Color(0xFFFFCC00)
val TextWhite = Color(0xFFFFFFFF)
val TextMuted = Color(0xFF8C96A0)

fun saveApiKey(context: Context, key: String) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    prefs.edit().putString("statpal_api_key", key).apply()
}

fun getApiKey(context: Context): String {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getString("statpal_api_key", "") ?: ""
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

    var leagues by remember { mutableStateOf<List<StatPalLeague>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun fetchLiveMatches() {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            errorMessage = "Kérlek add meg a StatPal API kulcsodat a Beállításokban (⚙️)!"
            leagues = emptyList()
        } else {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    val response = StatPalClient.service.getLiveMatches(apiKey)
                    leagues = response.liveMatches?.league ?: emptyList()
                    if (leagues.isEmpty()) {
                        errorMessage = "Jelenleg nincsenek élő vagy mára ütemezett meccsek."
                    }
                } catch (e: Exception) {
                    errorMessage = "Hiba a kapcsolódáskor: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchLiveMatches()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FOOTBALL FUTÁR",
                color = AccentGreen,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CardBackground)
                        .clickable { fetchLiveMatches() }
                        .padding(10.dp)
                ) {
                    Text(text = "🔄", fontSize = 16.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CardBackground)
                        .clickable { navController.navigate("api_settings") }
                        .padding(10.dp)
                ) {
                    Text(text = "⚙️", fontSize = 16.sp)
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
                leagues.forEach { league ->
                    item {
                        LeagueHeader(league.name ?: "Ismeretlen bajnokság")
                    }
                    items(league.match ?: emptyList()) { match ->
                        MatchRow(match) {
                            selectedMatchGlobal = match
                            navController.navigate("match_detail")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchDetailScreen(match: StatPalMatch, navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("H2H", "HIÁNYZÓK", "KEZDŐK", "AI TIPP")

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
            }

            if (matchId.isNotBlank()) {
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
                    text = match.status ?: match.time ?: "",
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
                    Text(match.home?.name ?: "-", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${match.home?.goals ?: "0"} - ${match.away?.goals ?: "0"}", color = AccentGreen, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text(match.away?.name ?: "-", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                            Text(predictionData?.choice ?: "-", color = AccentGreen, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(vertical = 4.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ELEMZÉS & INDOKLÁS", color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(predictionData?.reasoning ?: "-", color = TextWhite, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
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
                Text(player.position?.uppercase() ?: "", color = TextMuted, fontSize = 11.sp)
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
                PlayerInjuryRow(player.name ?: "", player.status ?: "", isQuestionable = false)
            }
            questionable.forEach { player ->
                PlayerInjuryRow(player.name ?: "", player.status ?: "", isQuestionable = true)
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

@Composable
fun LeagueHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title.uppercase(),
            color = AccentGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MatchRow(match: StatPalMatch, onClick: () -> Unit) {
    val isLive = match.status != "FT" && match.status != "CANCELLED" && match.status != "POSTPONED"

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundDark),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(
                width = 1.dp,
                color = if (isLive) AccentRed.copy(alpha = 0.5f) else Color.Transparent,
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
                text = match.status ?: match.time ?: "",
                color = if (isLive) AccentRed else TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(55.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(text = match.home?.name ?: "-", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = match.away?.name ?: "-", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = match.home?.goals ?: "0",
                    color = if (isLive) AccentGreen else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = match.away?.goals ?: "0",
                    color = if (isLive) AccentGreen else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
