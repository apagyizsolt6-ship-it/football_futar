package com.focifutar.app

import java.text.SimpleDateFormat
import java.util.*

object HighlightlyRepository {

    private val api = HighlightlyService.api

    suspend fun getMatchesForDate(
        apiKey: String,
        date: Date = Date(),
        limit: Int = 50
    ): List<HighlightlyMatchItem> {
        if (apiKey.isBlank()) return emptyList()

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

        return try {
            val response = api.getMatches(
                apiKey = apiKey,
                date = dateStr,
                timezone = "Europe/Budapest",
                limit = limit
            )
            response.data
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLiveMatches(apiKey: String): List<HighlightlyMatchItem> {
        val all = getMatchesForDate(apiKey)
        return all.filter { match ->
            val state = match.state?.name?.lowercase() ?: ""
            state.contains("half") || 
            state.contains("live") || 
            state.contains("progress") ||
            state.contains("1st") ||
            state.contains("2nd") ||
            (match.state?.clock != null && match.state.clock > 0)
        }
    }

    suspend fun getOddsSummary(
        apiKey: String,
        matchId: Long?
    ): HighlightlyMatchOddsSummary? {
        if (apiKey.isBlank() || matchId == null) return null

        return try {
            val response = api.getOdds(
                apiKey = apiKey,
                matchId = matchId,
                oddsType = "prematch"
            )
            val matchOdds = response.data.firstOrNull { it.matchId == matchId }
                ?: response.data.firstOrNull() ?: return null

            val fullTime = matchOdds.odds.firstOrNull { market ->
                market.market.equals("Full Time Result", ignoreCase = true) ||
                market.market.equals("1X2", ignoreCase = true) ||
                market.market.equals("Match Result", ignoreCase = true)
            } ?: return null

            var home: Double? = null
            var draw: Double? = null
            var away: Double? = null

            fullTime.values.forEach { v ->
                when (v.value?.trim()?.lowercase()) {
                    "home", "1" -> home = v.odd
                    "draw", "x" -> draw = v.odd
                    "away", "2" -> away = v.odd
                }
            }

            HighlightlyMatchOddsSummary(
                home = home,
                draw = draw,
                away = away,
                bookmakerName = fullTime.bookmakerName
            )
        } catch (e: Exception) {
            null
        }
    }
}
