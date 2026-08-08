package com.focifutar.app

import com.google.gson.annotations.SerializedName

data class StatPalResponse(
    @SerializedName("live_matches") val liveMatches: StatPalLiveMatches?
)

data class StatPalLiveMatches(
    @SerializedName("league") val league: List<StatPalLeague>?
)

data class StatPalLeague(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("match") val match: List<StatPalMatch>?
)

data class StatPalMatch(
    @SerializedName("id") val id: String?,
    @SerializedName("main_id") val mainId: String?,
    @SerializedName("date") val date: String?,
    @SerializedName("time") val time: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("home") val home: StatPalTeam?,
    @SerializedName("away") val away: StatPalTeam?
)

data class StatPalTeam(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("goals") val goals: String?,
    @SerializedName("logo") val logo: String?,
    @SerializedName("image") val image: String?
)

// H2H
data class StatPalH2HResponse(
    @SerializedName("head_to_head") val headToHead: HeadToHeadData?
)

data class HeadToHeadData(
    @SerializedName("recent_meetings") val recentMeetings: RecentMeetings?
)

data class RecentMeetings(
    @SerializedName("match") val match: List<H2HMatch>?
)

data class H2HMatch(
    @SerializedName("date") val date: String?,
    @SerializedName("team1_name") val team1Name: String?,
    @SerializedName("team2_name") val team2Name: String?,
    @SerializedName("team1_score") val team1Score: String?,
    @SerializedName("team2_score") val team2Score: String?
)

// INJURIES
data class StatPalInjuriesResponse(
    @SerializedName("injuries_suspensions") val injuriesSuspensions: InjuriesData?
)

data class InjuriesData(
    @SerializedName("league") val league: List<InjuryLeague>?
)

data class InjuryLeague(
    @SerializedName("match") val match: List<InjuryMatch>?
)

data class InjuryMatch(
    @SerializedName("main_id") val mainId: String?,
    @SerializedName("home") val home: InjuryTeam?,
    @SerializedName("away") val away: InjuryTeam?
)

data class InjuryTeam(
    @SerializedName("name") val name: String?,
    @SerializedName("sidelined") val sidelined: SidelinedData?
)

data class SidelinedData(
    @SerializedName("to_miss") val toMiss: PlayerList?,
    @SerializedName("questionable") val questionable: PlayerList?
)

data class PlayerList(
    @SerializedName("player") val player: List<InjuryPlayer>?
)

data class InjuryPlayer(
    @SerializedName("name") val name: String?,
    @SerializedName("status") val status: String?
)

// LINEUPS
data class StatPalLineupResponse(
    @SerializedName("home") val home: LineupTeam?,
    @SerializedName("away") val away: LineupTeam?
)

data class LineupTeam(
    @SerializedName("team_name") val teamName: String?,
    @SerializedName("team_formation") val teamFormation: String?,
    @SerializedName("coach") val coach: CoachData?,
    @SerializedName("starting_xi") val startingXi: List<LineupPlayer>?
)

data class CoachData(
    @SerializedName("name") val name: String?
)

data class LineupPlayer(
    @SerializedName("number") val number: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("position") val position: String?
)

// PREDICTIONS
data class StatPalPredictionResponse(
    @SerializedName("prediction") val prediction: PredictionData?
)

data class PredictionData(
    @SerializedName("choice") val choice: String?,
    @SerializedName("reasoning") val reasoning: String?
)

// STANDINGS
data class StatPalStandingsResponse(
    @SerializedName("standings") val standings: Any?
)

// ODDS
data class StatPalOddsResponse(
    @SerializedName("odds") val odds: Any?
)
