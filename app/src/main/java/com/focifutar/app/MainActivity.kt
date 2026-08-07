package com.focifutar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Neonos színpaletta
val BackgroundDark = Color(0xFF121212)
val CardBackground = Color(0xFF1E1E1E)
val NeonGreen = Color(0xFF00FF66)
val NeonRed = Color(0xFFFF3366)
val TextWhite = Color(0xFFFFFFFF)
val TextMuted = Color(0xFFA0A0A0)

data class Match(
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int,
    val awayScore: Int,
    val time: String,
    val isLive: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SoccerAppMainScreen()
            }
        }
    }
}

@Composable
fun SoccerAppMainScreen() {
    val sampleMatches = listOf(
        Match("Manchester City", "Arsenal", 3, 1, "67'", true),
        Match("Ferencváros", "Újpest FC", 1, 0, "88'", true),
        Match("Real Madrid", "Barcelona", 2, 2, "FT", false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        Text(
            text = "ÉLŐ EREDMÉNYEK",
            color = TextWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sampleMatches) { match ->
                MatchCard(match)
            }
        }
    }
}

@Composable
fun MatchCard(match: Match) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (match.isLive) NeonRed else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            alignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = match.homeTeam, color = TextWhite, fontWeight = FontWeight.Medium)
                Text(text = match.awayTeam, color = TextWhite, fontWeight = FontWeight.Medium)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${match.homeScore} - ${match.awayScore}",
                    color = if (match.isLive) NeonGreen else TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = match.time,
                    color = if (match.isLive) NeonRed else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
