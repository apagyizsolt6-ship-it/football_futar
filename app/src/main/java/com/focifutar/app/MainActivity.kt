package com.focifutar.app

import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.coil-kt.compose.AsyncImage

// Színpaletta
val BackgroundDark = Color(0xFF101214)
val CardBackground = Color(0xFF1A1D21)
val AccentGreen = Color(0xFF00FF66)
val AccentRed = Color(0xFFFF3366)
val TextWhite = Color(0xFFFFFFFF)
val TextMuted = Color(0xFF8C96A0)
val ProgressTrack = Color(0xFF2A2E35)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                
                // Minta adatok (StatPal API-ból jönnek majd)
                val mockData = listOf(
                    LeagueGroup(
                        leagueName = "Premier League",
                        countryFlag = "https://media.api-sports.io/flags/gb.svg",
                        matches = listOf(
                            Match("1", Team("mci", "Manchester City", "https://media.api-sports.io/football/teams/50.png"), Team("ars", "Arsenal", "https://media.api-sports.io/football/teams/42.png"), 3, 1, "67'", true, 62, 38, 14, 5, "2.14", "0.85"),
                            Match("2", Team("liv", "Liverpool", "https://media.api-sports.io/football/teams/40.png"), Team("che", "Chelsea", "https://media.api-sports.io/football/teams/49.png"), 0, 0, "21:00", false)
                        )
                    ),
                    LeagueGroup(
                        leagueName = "OTP Bank Liga",
                        countryFlag = "https://media.api-sports.io/flags/hu.svg",
                        matches = listOf(
                            Match("3", Team("ftc", "Ferencváros", "https://media.api-sports.io/football/teams/631.png"), Team("ujp", "Újpest FC", "https://media.api-sports.io/football/teams/638.png"), 1, 0, "88'", true, 55, 45, 9, 3, "1.20", "0.40")
                        )
                    )
                )

                NavHost(navController = navController, startDestination = "matches_list") {
                    composable("matches_list") {
                        MatchesListScreen(mockData, navController)
                    }
                    composable("match_detail/{matchId}") { backStackEntry ->
                        val matchId = backStackEntry.arguments?.getString("matchId")
                        val match = mockData.flatMap { it.matches }.find { it.id == matchId }
                        match?.let { MatchDetailScreen(it, navController) }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchesListScreen(leagues: List<LeagueGroup>, navController: NavController) {
    var selectedDate by remember { mutableStateOf("MA") }
    val dates = listOf("TEGNAP", "MA", "HOLNAP")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Felső Fejléc & Dátumválasztó Bar
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "FOOTBALL FUTÁR",
                color = AccentGreen,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dates) { date ->
                    val isSelected = date == selectedDate
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) AccentGreen else CardBackground)
                            .clickable { selectedDate = date }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = date,
                            color = if (isSelected) Color.Black else TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Bajnokságok és meccsek listája
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            leagues.forEach { league ->
                item {
                    LeagueHeader(league.leagueName)
                }
                items(league.matches) { match ->
                    MatchRow(match) {
                        navController.navigate("match_detail/${match.id}")
                    }
                }
            }
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
fun MatchRow(match: Match, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundDark),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(
                width = 1.dp,
                color = if (match.isLive) AccentRed.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Idő / Állapot
            Text(
                text = match.timeStatus,
                color = if (match.isLive) AccentRed else TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(45.dp)
            )

            // Csapatok & Címerek
            Column(modifier = Modifier.weight(1f)) {
                TeamItem(match.homeTeam)
                Spacer(modifier = Modifier.height(6.dp))
                TeamItem(match.awayTeam)
            }

            // Eredmény
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${match.homeScore}",
                    color = if (match.isLive) AccentGreen else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${match.awayScore}",
                    color = if (match.isLive) AccentGreen else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun TeamItem(team: Team) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = team.logoUrl,
            contentDescription = team.name,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = team.name,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MatchDetailScreen(match: Match, navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("ÖSSZEFOGLALÓ", "STATISZTIKA")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Fejléc vissza gombbal
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Vissza",
                color = AccentGreen,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { navController.popBackStack() }
            )
        }

        // Scoreboard Kártya
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = match.timeStatus,
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
                    TeamColumn(match.homeTeam)
                    Text(
                        text = "${match.homeScore} - ${match.awayScore}",
                        color = TextWhite,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    TeamColumn(match.awayTeam)
                }
            }
        }

        // Tabok
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardBackground,
            contentColor = AccentGreen
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) }
                )
            }
        }

        // Tab Tartalmak
        if (selectedTab == 1) {
            Column(modifier = Modifier.padding(16.dp)) {
                StatBar("Labdabirtoklás (%)", match.homePossession, match.awayPossession)
                StatBar("Kapura lövések", match.homeShots, match.awayShots)
                StatBar("Várható gólok (xG)", (match.homeXG.toFloat() * 10).toInt(), (match.awayXG.toFloat() * 10).toInt(), "${match.homeXG} - ${match.awayXG}")
            }
        }
    }
}

@Composable
fun TeamColumn(team: Team) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = team.logoUrl,
            contentDescription = team.name,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(team.name, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatBar(label: String, homeValue: Int, awayValue: Int, displayCustom: String? = null) {
    val total = (homeValue + awayValue).coerceAtLeast(1)
    val progress = homeValue.toFloat() / total.toFloat()

    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(if (displayCustom != null) "" else "$homeValue", color = AccentGreen, fontWeight = FontWeight.Bold)
            Text(label, color = TextMuted, fontSize = 12.sp)
            Text(if (displayCustom != null) "" else "$awayValue", color = AccentRed, fontWeight = FontWeight.Bold)
        }
        if (displayCustom != null) {
            Text(
                text = displayCustom,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = AccentGreen,
            trackColor = AccentRed
        )
    }
}
