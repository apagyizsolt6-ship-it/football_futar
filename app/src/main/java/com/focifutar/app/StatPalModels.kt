package com.focifutar.app

import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

// Deserializer: Átalakítja az 1 elemű objektumot ({}) is listává ([{}])
class FlexibleMatchDeserializer : JsonDeserializer<List<StatPalMatch>> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): List<StatPalMatch> {
        if (json == null || json.isJsonNull) return emptyList()
        val list = mutableListOf<StatPalMatch>()
        if (json.isJsonArray) {
            json.asJsonArray.forEach { elem ->
                context?.deserialize<StatPalMatch>(elem, StatPalMatch::class.java)?.let { list.add(it) }
            }
        } else if (json.isJsonObject) {
            context?.deserialize<StatPalMatch>(json, StatPalMatch::class.java)?.let { list.add(it) }
        }
        return list
    }
}

class FlexibleLeagueDeserializer : JsonDeserializer<List<StatPalLeague>> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): List<StatPalLeague> {
        if (json == null || json.isJsonNull) return emptyList()
        val list = mutableListOf<StatPalLeague>()
        if (json.isJsonArray) {
            json.asJsonArray.forEach { elem ->
                context?.deserialize<StatPalLeague>(elem, StatPalLeague::class.java)?.let { list.add(it) }
            }
        } else if (json.isJsonObject) {
            context?.deserialize<StatPalLeague>(json, StatPalLeague::class.java)?.let { list.add(it) }
        }
        return list
    }
}

// 1. ÉLŐ MECCSEK
data class StatPalResponse(
    @SerializedName("live_matches") val liveMatches: LiveMatchesData?
)

data class LiveMatchesData(
    val updated: String?,
    @SerializedName("updated_ts") val updatedTs: Long?,
    @JsonAdapter(FlexibleLeagueDeserializer::class) val league: List<StatPalLeague>?
)

data class StatPalLeague(
    val id: String?,
    val name: String?,
    val country: String?,
    val cup: String?,
    @JsonAdapter(FlexibleMatchDeserializer::class) val match: List<StatPalMatch>?
)

data class StatPalMatch(
    @SerializedName("main_id") val mainId: String?,
    val status: String?,
    val date: String?,
    val time: String?,
    val home: StatPalTeam?,
    val away: StatPalTeam?
)

data class StatPalTeam(
    val id: String?,
    val name: String?,
    val goals: String?
)

// 2. EGYMÁS ELLENI (H2H)
data class StatPalH2HResponse(
    @SerializedName("head-to-head") val headToHead: StatPalH2HData?
)

data class StatPalH2HData(
    @SerializedName("team1_id") val team1Id: String?,
    @SerializedName("team2_id") val team2Id: String?,
    @SerializedName("recent_meetings") val recentMeetings: H2HMatchList?
)

data class H2HMatchList(
    val match: List<H2HMatch>?
)

data class H2HMatch(
    @SerializedName("main_id") val mainId: String?,
    val date: String?,
    val league: String?,
    @SerializedName("team1_name") val team1Name: String?,
    @SerializedName("team2_name") val team2Name: String?,
    @SerializedName("team1_score") val team1Score: String?,
    @SerializedName("team2_score") val team2Score: String?
)

// 3. HIÁNYZÓK
data class StatPalInjuriesResponse(
    @SerializedName("injuries_suspensions") val injuriesSuspensions: InjuriesData?
)

data class InjuriesData(
    val updated: String?,
    val league: List<InjuryLeague>?
)

data class InjuryLeague(
    val id: String?,
    val name: String?,
    val match: List<InjuryMatch>?
)

data class InjuryMatch(
    @SerializedName("main_id") val mainId: String?,
    val date: String?,
    val home: InjuryTeam?,
    val away: InjuryTeam?
)

data class InjuryTeam(
    val id: String?,
    val name: String?,
    val sidelined: SidelinedData?
)

data class SidelinedData(
    @SerializedName("to_miss") val toMiss: PlayerGroup?,
    val questionable: PlayerGroup?
)

data class PlayerGroup(
    val player: List<InjuryPlayer>?
)

data class InjuryPlayer(
    val id: String?,
    val name: String?,
    val status: String?
)

// 4. TABELLA
data class StatPalStandingsResponse(
    val standings: StandingsWrapper?
)

data class StandingsWrapper(
    val country: String?,
    val tournament: TournamentData?
)

data class TournamentData(
    val id: String?,
    val league: String?,
    val season: String?,
    val team: List<StandingTeam>?
)

data class StandingTeam(
    val position: String?,
    val name: String?,
    val id: String?,
    @SerializedName("recent_form") val recentForm: String?,
    val overall: OverallStats?,
    val total: TotalStats?
)

data class OverallStats(
    @SerializedName("games_played") val gamesPlayed: String?,
    val wins: String?,
    val draws: String?,
    val losses: String?,
    @SerializedName("goals_scored") val goalsScored: String?,
    @SerializedName("goals_allowed") val goalsAllowed: String?
)

data class TotalStats(
    @SerializedName("goal_difference") val goalDifference: String?,
    val points: String?
)

// 5. KEZDŐCSAPATOK
data class StatPalLineupResponse(
    @SerializedName("main_id") val mainId: String?,
    val status: String?,
    val home: LineupTeam?,
    val away: LineupTeam?
)

data class LineupTeam(
    @SerializedName("team_id") val teamId: String?,
    @SerializedName("team_name") val teamName: String?,
    val coach: CoachData?,
    @SerializedName("team_formation") val teamFormation: String?,
    @SerializedName("starting_xi") val startingXi: List<PlayerLineup>?,
    val bench: List<PlayerLineup>?,
    val sidelined: List<SidelinedLineupPlayer>?,
    val confidence: Int?
)

data class CoachData(
    val name: String?,
    val id: String?
)

data class PlayerLineup(
    val id: String?,
    val name: String?,
    val number: String?,
    val position: String?
)

data class SidelinedLineupPlayer(
    val id: String?,
    val name: String?,
    val number: String?,
    val position: String?,
    val status: String?,
    val reason: String?
)

// 6. ODDAK
data class StatPalOddsResponse(
    @SerializedName("prematch_odds") val prematchOdds: PrematchOddsData?
)

data class PrematchOddsData(
    val league: OddsLeague?
)

data class OddsLeague(
    val id: String?,
    val name: String?,
    val match: List<OddsMatch>?
)

data class OddsMatch(
    @SerializedName("main_id") val mainId: String?,
    val date: String?,
    val time: String?,
    val home: StatPalTeam?,
    val away: StatPalTeam?,
    val odds: List<OddsMarket>?
)

data class OddsMarket(
    val id: String?,
    val name: String?,
    val bookmaker: List<BookmakerData>?
)

data class BookmakerData(
    val id: String?,
    val name: String?,
    val odd: List<OddValue>?
)

data class OddValue(
    val name: String?,
    val value: String?
)

// 7. AI TIPPEK
data class StatPalPredictionResponse(
    val meta: PredictionMeta?,
    val prediction: PredictionData?
)

data class PredictionMeta(
    @SerializedName("main_id") val mainId: String?,
    val date: String?,
    val time: String?,
    @SerializedName("home_team") val homeTeam: StatPalTeam?,
    @SerializedName("away_team") val awayTeam: StatPalTeam?
)

data class PredictionData(
    val choice: String?,
    val reasoning: String?,
    @SerializedName("prematch_odds") val prematchOdds: PredictionPrematchOdds?
)

data class PredictionPrematchOdds(
    val market: String?,
    val modifier: String?,
    val selection: String?,
    val odd: String?
)
