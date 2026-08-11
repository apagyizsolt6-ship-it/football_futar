package com.focifutar.app

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

class MatchListDeserializer : JsonDeserializer<List<StatPalMatch>> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): List<StatPalMatch> {
        if (json == null || json.isJsonNull) return emptyList()
        val list = mutableListOf<StatPalMatch>()
        if (json.isJsonArray) {
            for (element in json.asJsonArray) {
                val m = context?.deserialize<StatPalMatch>(element, StatPalMatch::class.java)
                if (m != null) list.add(m)
            }
        } else if (json.isJsonObject) {
            val m = context?.deserialize<StatPalMatch>(json.asJsonObject, StatPalMatch::class.java)
            if (m != null) list.add(m)
        }
        return list
    }
}

class LeagueListDeserializer : JsonDeserializer<List<StatPalLeague>> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): List<StatPalLeague> {
        if (json == null || json.isJsonNull) return emptyList()
        val list = mutableListOf<StatPalLeague>()
        if (json.isJsonArray) {
            for (element in json.asJsonArray) {
                val l = context?.deserialize<StatPalLeague>(element, StatPalLeague::class.java)
                if (l != null) list.add(l)
            }
        } else if (json.isJsonObject) {
            val l = context?.deserialize<StatPalLeague>(json.asJsonObject, StatPalLeague::class.java)
            if (l != null) list.add(l)
        }
        return list
    }
}

class StatPalLiveMatchesResponseDeserializer : JsonDeserializer<StatPalLiveMatchesResponse> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): StatPalLiveMatchesResponse {
        if (json == null || !json.isJsonObject) return StatPalLiveMatchesResponse(null)
        val obj = json.asJsonObject
        val containerEntry = obj.entrySet().find { it.key.startsWith("matches_") } ?: obj.entrySet().firstOrNull()
        var container: StatPalLiveMatchesContainer? = null
        if (containerEntry != null && containerEntry.value.isJsonObject) {
            container = context?.deserialize(containerEntry.value, StatPalLiveMatchesContainer::class.java)
        }
        return StatPalLiveMatchesResponse(liveMatches = container)
    }
}

@JsonAdapter(StatPalLiveMatchesResponseDeserializer::class)
data class StatPalLiveMatchesResponse(
    val liveMatches: StatPalLiveMatchesContainer? = null
)

data class StatPalLiveMatchesContainer(
    val updated: String? = null,
    @SerializedName("updated_ts") val updatedTs: Long? = null,
    @JsonAdapter(LeagueListDeserializer::class)
    @SerializedName("league") val league: List<StatPalLeague>? = emptyList()
)

data class StatPalLeague(
    val id: String? = null,
    val name: String? = null,
    val country: String? = null,
    val cup: String? = null,
    @JsonAdapter(MatchListDeserializer::class)
    @SerializedName("match") val match: List<StatPalMatch> = emptyList()
)

data class StatPalMatch(
    @SerializedName("main_id") val mainId: String? = null,
    val status: String? = null,
    val date: String? = null,
    val time: String? = null,
    val venue: String? = null,
    val home: StatPalTeam? = null,
    val away: StatPalTeam? = null,
    val events: MatchEventsContainer? = null,
    val ht: StatPalScore? = null,
    val ft: StatPalScore? = null,
    val et: StatPalScore? = null,
    val penalties: StatPalPenaltyData? = null,
    @SerializedName("has_live_stats") val hasLiveStats: String? = null,
    @SerializedName("inplay_odds_running") val inplayOddsRunning: String? = null,
    @SerializedName("match_context") val matchContext: StatPalContext? = null
)

data class MatchEventsContainer(
    val event: List<MatchEventItem>? = emptyList()
)

data class MatchEventItem(
    val id: String? = null,
    val type: String? = null,
    val team: String? = null,
    val minute: String? = null,
    @SerializedName("extra_min") val extraMin: String? = null,
    val player: String? = null,
    @SerializedName("assist_player") val assistPlayer: String? = null,
    val result: String? = null
)

data class StatPalTeam(
    val id: String? = null,
    val name: String? = null,
    val goals: String? = null,
    val logo: String? = null,
    val image: String? = null,
    @SerializedName("win_on_agg") val winOnAgg: String? = null
)

data class StatPalScore(
    @SerializedName("home_goals") val homeGoals: Any? = null,
    @SerializedName("away_goals") val awayGoals: Any? = null
)

data class StatPalPenaltyData(
    @SerializedName("home_pen") val homePen: Any? = null,
    @SerializedName("away_pen") val awayPen: Any? = null
)

data class StatPalContext(
    @SerializedName("live_storylines") val liveStorylines: Boolean? = false,
    @SerializedName("weather_forecast") val weatherForecast: Boolean? = false,
    @SerializedName("team_lineups") val teamLineups: Boolean? = false,
    val predictions: Boolean? = false
)

data class H2HResponse(
    @SerializedName("head_to_head") val headToHead: H2HData? = null
)

data class H2HData(
    @SerializedName("recent_meetings") val recentMeetings: H2HMeetings? = null
)

data class H2HMeetings(
    val match: List<H2HMatch>? = emptyList()
)

data class H2HMatch(
    val date: String? = null,
    @SerializedName("team1_name") val team1Name: String? = null,
    @SerializedName("team2_name") val team2Name: String? = null,
    @SerializedName("team1_score") val team1Score: String? = null,
    @SerializedName("team2_score") val team2Score: String? = null
)

data class InjuriesResponse(
    @SerializedName("injuries_suspensions") val injuriesSuspensions: InjuryLeagueContainer? = null
)

data class InjuryLeagueContainer(
    val league: List<InjuryLeague>? = emptyList()
)

data class InjuryLeague(
    val match: List<InjuryMatch>? = emptyList()
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
    @SerializedName("to_miss") val toMiss: PlayerListContainer? = null,
    val questionable: PlayerListContainer? = null
)

data class PlayerListContainer(
    val player: List<InjuryPlayer>? = emptyList()
)

data class InjuryPlayer(
    val name: String? = null,
    val status: String? = null
)

data class StatPalLineupResponse(
    val home: LineupTeam? = null,
    val away: LineupTeam? = null
)

data class LineupTeam(
    @SerializedName("team_name") val teamName: String? = null,
    @SerializedName("team_formation") val teamFormation: String? = null,
    val coach: CoachData? = null,
    @SerializedName("starting_xi") val startingXi: List<LineupPlayer>? = emptyList()
)

data class CoachData(
    val name: String? = null
)

data class LineupPlayer(
    val name: String? = null,
    val number: String? = null,
    val position: String? = null
)

data class PredictionResponse(
    val prediction: PredictionData? = null
)

data class PredictionData(
    val choice: String? = null,
    val reasoning: String? = null
)

// ==========================================
// BIZTONSÁGOS PREMATCH ODDS MODELLEK
// ==========================================
class PrematchOddsLeagueListDeserializer : JsonDeserializer<List<PrematchOddsLeague>> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): List<PrematchOddsLeague> {
        if (json == null || json.isJsonNull) return emptyList()
        val list = mutableListOf<PrematchOddsLeague>()
        if (json.isJsonArray) {
            for (element in json.asJsonArray) {
                val item = context?.deserialize<PrematchOddsLeague>(element, PrematchOddsLeague::class.java)
                if (item != null) list.add(item)
            }
        } else if (json.isJsonObject) {
            val item = context?.deserialize<PrematchOddsLeague>(json.asJsonObject, PrematchOddsLeague::class.java)
            if (item != null) list.add(item)
        }
        return list
    }
}

data class StatPalPrematchOddsResponse(
    @SerializedName("prematch_odds") val prematchOdds: PrematchOddsContainer? = null
)

data class PrematchOddsContainer(
    @JsonAdapter(PrematchOddsLeagueListDeserializer::class)
    val league: List<PrematchOddsLeague>? = emptyList()
)

data class PrematchOddsLeague(
    val id: String? = null,
    val name: String? = null,
    val country: String? = null,
    @JsonAdapter(MatchListDeserializer::class)
    @SerializedName("match") val match: List<PrematchOddsMatch> = emptyList()
)

data class PrematchOddsMatch(
    @SerializedName("main_id") val mainId: String? = null,
    @SerializedName("fallback_id_1") val fallbackId1: String? = null,
    @SerializedName("fallback_id_2") val fallbackId2: String? = null,
    @SerializedName("fallback_id_3") val fallbackId3: String? = null,
    val date: String? = null,
    val time: String? = null,
    val home: StatPalTeam? = null,
    val away: StatPalTeam? = null,
    val odds: List<OddsCategory>? = emptyList()
)

data class OddsCategory(
    val name: String? = null,
    val bookmaker: List<BookmakerData>? = emptyList()
)

data class BookmakerData(
    val name: String? = null,
    val odd: List<OddValue>? = emptyList()
)

data class OddValue(
    val name: String? = null,
    val value: String? = null
)

// ==========================================
// TABELLA (STANDINGS) MODELLEK
// ==========================================
data class StatPalStandingsResponse(
    val standings: StandingsContainer? = null
)

data class StandingsContainer(
    val updated: String? = null,
    @SerializedName("updated_ts") val updatedTs: Any? = null,
    val country: String? = null,
    val tournament: TournamentData? = null
)

data class TournamentData(
    val id: String? = null,
    val league: String? = null,
    val season: String? = null,
    @SerializedName("stage_id") val stageId: String? = null,
    @SerializedName("is_current") val isCurrent: String? = null,
    val team: List<StandingTeamRow>? = emptyList()
)

data class StandingTeamRow(
    val position: String? = null,
    val name: String? = null,
    val id: String? = null,
    val status: String? = null,
    @SerializedName("recent_form") val recentForm: String? = null,
    val overall: StatScoreOverall? = null,
    val total: StatScoreTotal? = null,
    val description: StatDescription? = null
)

data class StatScoreOverall(
    @SerializedName("games_played") val gamesPlayed: String? = null,
    val wins: String? = null,
    val draws: String? = null,
    val losses: String? = null,
    @SerializedName("goals_scored") val goalsScored: String? = null,
    @SerializedName("goals_allowed") val goalsAllowed: String? = null
)

data class StatScoreTotal(
    @SerializedName("goal_difference") val goalDifference: String? = null,
    val points: String? = null
)

data class StatDescription(
    val value: String? = null
)

// ==========================================
// HIGHLIGHTLY API MODELLEK
// ==========================================
data class HighlightResponse(
    val data: List<HighlightItem>,
    val pagination: Pagination
)

data class HighlightItem(
    val id: Int,
    val type: String,
    val imgUrl: String?,
    val title: String,
    val url: String?,
    val embedUrl: String?,
    val category: String,
    val match: HighlightMatch
)

data class HighlightMatch(
    val id: Long,
    val round: String,
    val date: String,
    val homeTeam: TeamInfo,
    val awayTeam: TeamInfo,
    val league: LeagueInfo
)

data class TeamInfo(
    val id: Int,
    val name: String,
    val logo: String?
)

data class LeagueInfo(
    val id: Int,
    val name: String,
    val logo: String?
)

data class Pagination(
    val totalCount: Int,
    val offset: Int,
    val limit: Int
)
