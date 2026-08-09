package com.focifutar.app

import com.google.gson.annotations.SerializedName

// ==========================================
// ÉLŐ ÉS KÖZELGŐ MECCSEK
// ==========================================
data class StatPalLiveMatchesResponse(
    @SerializedName("live_matches") val liveMatches: LiveMatchesContainer? = null
)

data class LiveMatchesContainer(
    val league: List<StatPalLeague>? = null
)

data class StatPalLeague(
    val id: String? = null,
    val name: String? = null,
    val country: String? = null,
    val match: List<StatPalMatch> = emptyList()
)

data class StatPalMatch(
    @SerializedName("main_id") val mainId: String? = null,
    val id: String? = null,
    val date: String? = null,
    val time: String? = null,
    val status: String? = null,
    val home: StatPalTeam? = null,
    val away: StatPalTeam? = null
)

data class StatPalTeam(
    val id: String? = null,
    val name: String? = null,
    val logo: String? = null,
    val image: String? = null,
    val goals: String? = null
)

// ==========================================
// H2H (EGYMÁS ELLENI MÉRLEGEK)
// ==========================================
data class H2HResponse(
    @SerializedName("head_to_head") val headToHead: HeadToHeadContainer? = null
)

data class HeadToHeadContainer(
    @SerializedName("recent_meetings") val recentMeetings: RecentMeetings? = null
)

data class RecentMeetings(
    val match: List<H2HMatch>? = null
)

data class H2HMatch(
    val date: String? = null,
    @SerializedName("team1_name") val team1Name: String? = null,
    @SerializedName("team2_name") val team2Name: String? = null,
    @SerializedName("team1_score") val team1Score: String? = null,
    @SerializedName("team2_score") val team2Score: String? = null
)

// ==========================================
// SÉRÜLTEK ÉS ELTILTOOTTAK
// ==========================================
data class InjuriesResponse(
    @SerializedName("injuries_suspensions") val injuriesSuspensions: InjuriesContainer? = null
)

data class InjuriesContainer(
    val league: List<InjuryLeague>? = null
)

data class InjuryLeague(
    val match: List<InjuryMatch>? = null
)

data class InjuryMatch(
    @SerializedName("main_id") val mainId: String? = null,
    val home: InjuryTeam? = null,
    val away: InjuryTeam? = null
)

data class InjuryTeam(
    val name: String? = null,
    val sidelined: SidelinedData? = null
)

data class SidelinedData(
    @SerializedName("to_miss") val toMiss: SidelinedGroup? = null,
    val questionable: SidelinedGroup? = null
)

data class SidelinedGroup(
    val player: List<PlayerItem>? = null
)

data class PlayerItem(
    val name: String? = null,
    val status: String? = null
)

// ==========================================
// KEZDŐCSAPATOK
// ==========================================
data class StatPalLineupResponse(
    val home: LineupTeam? = null,
    val away: LineupTeam? = null
)

data class LineupTeam(
    @SerializedName("team_name") val teamName: String? = null,
    @SerializedName("team_formation") val teamFormation: String? = null,
    val coach: Coach? = null,
    @SerializedName("starting_xi") val startingXi: List<StartingPlayer>? = null
)

data class Coach(val name: String? = null)

data class StartingPlayer(
    val number: String? = null,
    val name: String? = null,
    val position: String? = null
)

// ==========================================
// AI MECCS TIPP ÉS ELEMZÉS
// ==========================================
data class PredictionResponse(
    val prediction: PredictionData? = null
)

data class PredictionData(
    val choice: String? = null,
    val reasoning: String? = null
)

// ==========================================
// PRE-MATCH ODDS (VALÓDI MECCS ELŐTTI SZORZÓK)
// ==========================================
data class StatPalPrematchOddsResponse(
    @SerializedName("prematch_odds") val prematchOdds: PrematchOddsContainer? = null
)

data class PrematchOddsContainer(
    val updated: String? = null,
    @SerializedName("updated_ts") val updatedTs: Long? = null,
    val league: PrematchOddsLeague? = null
)

data class PrematchOddsLeague(
    val id: String? = null,
    val name: String? = null,
    val country: String? = null,
    val match: List<PrematchOddsMatch>? = null
)

data class PrematchOddsMatch(
    @SerializedName("main_id") val mainId: String? = null,
    val date: String? = null,
    val time: String? = null,
    val home: StatPalTeam? = null,
    val away: StatPalTeam? = null,
    val odds: List<PrematchOddsCategory>? = null
)

data class PrematchOddsCategory(
    val id: String? = null,
    val name: String? = null,
    val stop: String? = null,
    val bookmaker: List<PrematchBookmaker>? = null
)

data class PrematchBookmaker(
    val id: String? = null,
    val name: String? = null,
    val timestamp: String? = null,
    val odd: List<PrematchOddValue>? = null
)

data class PrematchOddValue(
    val name: String? = null,
    val value: String? = null
)
