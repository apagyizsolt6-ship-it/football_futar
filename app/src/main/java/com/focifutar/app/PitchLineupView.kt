package com.focifutar.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * Prémium 2.5D pályanézet a kezdőcsapatokhoz.
 * Formáció alapján helyezi el a játékosokat, árnyékkal és mez-szerű megjelenítéssel.
 */
@Composable
fun PitchLineupView(
    home: LineupTeam?,
    away: LineupTeam?,
    colors: AppColors,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Fejléc
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = home?.teamName?.uppercase() ?: "HAZAI",
                    color = colors.accentPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = home?.teamFormation ?: "-",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }
            Text(
                text = "VS",
                color = colors.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = away?.teamName?.uppercase() ?: "VENDÉG",
                    color = colors.accentPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = away?.teamFormation ?: "-",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Pálya
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
        ) {
            FootballPitchCanvas(colors = colors)

            // Játékosok
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                // Hazai (lent, támad felfelé)
                home?.let { team ->
                    val positions = calculatePositions(
                        players = team.startingXi.orEmpty(),
                        formation = team.teamFormation,
                        isHome = true
                    )
                    positions.forEach { (player, pos) ->
                        PlayerOnPitch(
                            player = player,
                            xFraction = pos.first,
                            yFraction = pos.second,
                            isHome = true,
                            colors = colors
                        )
                    }
                }

                // Vendég (fent, támad lefelé)
                away?.let { team ->
                    val positions = calculatePositions(
                        players = team.startingXi.orEmpty(),
                        formation = team.teamFormation,
                        isHome = false
                    )
                    positions.forEach { (player, pos) ->
                        PlayerOnPitch(
                            player = player,
                            xFraction = pos.first,
                            yFraction = pos.second,
                            isHome = false,
                            colors = colors
                        )
                    }
                }
            }
        }

        // Edzők
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Edző: ${home?.coach?.name ?: "-"}",
                color = colors.textMuted,
                fontSize = 11.sp
            )
            Text(
                text = "Edző: ${away?.coach?.name ?: "-"}",
                color = colors.textMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun FootballPitchCanvas(colors: AppColors) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Csíkozott fű
        val stripeCount = 12
        val stripeH = h / stripeCount
        for (i in 0 until stripeCount) {
            val color = if (i % 2 == 0) Color(0xFF2E7D32) else Color(0xFF388E3C)
            drawRect(
                color = color,
                topLeft = Offset(0f, i * stripeH),
                size = androidx.compose.ui.geometry.Size(w, stripeH)
            )
        }

        val lineColor = Color.White.copy(alpha = 0.85f)
        val stroke = 2.5f

        // Külső vonal
        drawRect(
            color = lineColor,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(w - stroke, h - stroke),
            style = Stroke(width = stroke)
        )

        // Középvonal
        drawLine(
            color = lineColor,
            start = Offset(0f, h / 2),
            end = Offset(w, h / 2),
            strokeWidth = stroke
        )

        // Középpont
        drawCircle(
            color = lineColor,
            radius = min(w, h) * 0.09f,
            center = Offset(w / 2, h / 2),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = lineColor,
            radius = 4f,
            center = Offset(w / 2, h / 2)
        )

        // Tizenhatosok (egyszerűsített)
        val penaltyWidth = w * 0.55f
        val penaltyHeight = h * 0.16f

        // Alsó (hazai)
        drawRect(
            color = lineColor,
            topLeft = Offset((w - penaltyWidth) / 2, h - penaltyHeight),
            size = androidx.compose.ui.geometry.Size(penaltyWidth, penaltyHeight),
            style = Stroke(width = stroke)
        )
        // Felső (vendég)
        drawRect(
            color = lineColor,
            topLeft = Offset((w - penaltyWidth) / 2, 0f),
            size = androidx.compose.ui.geometry.Size(penaltyWidth, penaltyHeight),
            style = Stroke(width = stroke)
        )

        // Kapuk
        val goalWidth = w * 0.18f
        val goalDepth = h * 0.035f
        // Alsó kapu
        drawRect(
            color = lineColor,
            topLeft = Offset((w - goalWidth) / 2, h - goalDepth),
            size = androidx.compose.ui.geometry.Size(goalWidth, goalDepth),
            style = Stroke(width = stroke)
        )
        // Felső kapu
        drawRect(
            color = lineColor,
            topLeft = Offset((w - goalWidth) / 2, 0f),
            size = androidx.compose.ui.geometry.Size(goalWidth, goalDepth),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
private fun PlayerOnPitch(
    player: LineupPlayer,
    xFraction: Float,
    yFraction: Float,
    isHome: Boolean,
    colors: AppColors
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val xOffset = (maxWidth * xFraction) - 28.dp
        val yOffset = (maxHeight * yFraction) - 24.dp

        Column(
            modifier = Modifier
                .offset(x = xOffset, y = yOffset)
                .width(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (isHome) Color(0xFF1565C0) else Color(0xFFC62828))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = player.number ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = (player.name ?: "").substringBefore(" ").take(9),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

/**
 * Formáció alapján kiszámolja a játékosok relatív pozícióját a pályán.
 * Visszaadja a (x, y) frakciókat (0..1).
 * isHome = true → lent (y nagyobb), false → fent (y kisebb).
 */
private fun calculatePositions(
    players: List<LineupPlayer>,
    formation: String?,
    isHome: Boolean
): List<Pair<LineupPlayer, Pair<Float, Float>>> {
    if (players.isEmpty()) return emptyList()

    val result = mutableListOf<Pair<LineupPlayer, Pair<Float, Float>>>()

    // Y szintek: hazai lent (nagyobb y), vendég fent (kisebb y)
    val yLevels = if (isHome) {
        listOf(0.90f, 0.74f, 0.56f, 0.40f, 0.28f) // GK, DEF, DM, AM, FWD
    } else {
        listOf(0.10f, 0.26f, 0.44f, 0.60f, 0.72f)
    }

    val gk = players.filter { isGoalkeeper(it.position) }
    val defenders = players.filter { isDefender(it.position) && !isGoalkeeper(it.position) }
    val midfielders = players.filter { isMidfielder(it.position) }
    val forwards = players.filter { isForward(it.position) }

    // GK mindig középen
    gk.firstOrNull()?.let { result += it to (0.5f to yLevels[0]) }

    // Védők
    placeLineEvenly(defenders, yLevels[1], result)

    // Középpályások (ha sokan vannak, két sorba)
    if (midfielders.size > 5) {
        val half = midfielders.size / 2
        placeLineEvenly(midfielders.take(half), yLevels[2], result)
        placeLineEvenly(midfielders.drop(half), yLevels[3], result)
    } else {
        placeLineEvenly(midfielders, yLevels[2], result)
    }

    // Támadók
    placeLineEvenly(forwards, yLevels[if (midfielders.size > 5) 4 else 3], result)

    // Kimaradt játékosok
    val placed = result.map { it.first }.toSet()
    val remaining = players.filter { it !in placed }
    if (remaining.isNotEmpty()) {
        placeLineEvenly(remaining, if (isHome) 0.50f else 0.50f, result)
    }

    return result
}

private fun placeLineEvenly(
    linePlayers: List<LineupPlayer>,
    y: Float,
    result: MutableList<Pair<LineupPlayer, Pair<Float, Float>>>
) {
    if (linePlayers.isEmpty()) return
    val count = linePlayers.size
    val margin = 0.12f
    val usable = 1f - 2 * margin
    val spacing = if (count == 1) 0f else usable / (count - 1)

    linePlayers.forEachIndexed { index, player ->
        val x = if (count == 1) 0.5f else margin + spacing * index
        result += player to (x to y)
    }
}

private fun isGoalkeeper(pos: String?) =
    pos?.uppercase()?.let { it.contains("GK") || it == "G" || it.contains("KAPUS") } == true

private fun isDefender(pos: String?) =
    pos?.uppercase()?.let {
        it.contains("D") || it.contains("VÉD") || it.contains("CB") || it.contains("LB") || it.contains("RB")
    } == true

private fun isMidfielder(pos: String?) =
    pos?.uppercase()?.let {
        it.contains("M") || it.contains("KÖZÉP") || it.contains("CM") || it.contains("CDM") || it.contains("CAM")
    } == true

private fun isForward(pos: String?) =
    pos?.uppercase()?.let {
        it.contains("F") || it.contains("TÁMAD") || it.contains("ST") || it.contains("CF") || it.contains("W")
    } == true
