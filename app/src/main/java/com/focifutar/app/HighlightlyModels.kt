package com.focifutar.app

import com.google.gson.annotations.SerializedName

// ========== MATCHES ==========

data class HighlightlyMatchesResponse(
    val data: List<HighlightlyMatchItem> = emptyList(),
    val pagination: Pagination? = null,
    val plan: HighlightlyPlan? = null
)

data class HighlightlyMatchItem(
    val id: Long? = null,
    val round: String? = null,
    val date: String? = null,
    val country: HighlightlyCountry? = null,
    val awayTeam: HighlightlyTeam? = null,
    val homeTeam: HighlightlyTeam? = null,
    val league: HighlightlyLeagueInfo? = null,
    val state: HighlightlyMatchState? = null,
    val score: HighlightlyScore? = null
)

data class HighlightlyCountry(
    val code: String? = null,
    val name: String? = null,
    val logo: String? = null
)

data class HighlightlyTeam(
    val id: Long? = null,
    val name: String? = null,
    val logo: String? = null
)

data class HighlightlyLeagueInfo(
    val id: Long? = null,
    val name: String? = null,
    val logo: String? = null,
    val season: Int? = null
)

data class HighlightlyMatchState(
    val id: Int? = null,
    val name: String? = null,      // pl. "Not started", "1st half", "Finished"
    val description: String? = null,
    val clock: Int? = null         // perc
)

data class HighlightlyScore(
    val current: String? = null,   // pl. "2-1"
    val penalties: String? = null
)

// ========== ODDS (ha még nincs) ==========

data class HighlightlyOddsResponse(
    val data: List<HighlightlyMatchOdds> = emptyList(),
    val pagination: Pagination? = null,
    val plan: HighlightlyPlan? = null
)

data class HighlightlyMatchOdds(
    val matchId: Long? = null,
    val odds: List<HighlightlyOddMarket> = emptyList()
)

data class HighlightlyOddMarket(
    val bookmakerId: Int? = null,
    val bookmakerName: String? = null,
    val type: String? = null,
    val market: String? = null,
    val values: List<HighlightlyOddValue> = emptyList()
)

data class HighlightlyOddValue(
    val odd: Double? = null,
    val value: String? = null
)

data class HighlightlyMatchOddsSummary(
    val home: Double? = null,
    val draw: Double? = null,
    val away: Double? = null,
    val bookmakerName: String? = null
) {
    val hasAny: Boolean get() = home != null || draw != null || away != null
}

// ========== HIGHLIGHTS (ha már van a StatPalModels-ben, ne ismételd) ==========
// A HighlightResponse, HighlightMatch stb. maradhat a StatPalModels.kt-ban,
// ha már ott van.
