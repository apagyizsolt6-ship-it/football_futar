package com.focifutar.app

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

// ==========================================
// RUGALMAS GSON KONVERTER A "MATCH" MEZŐHÖZ
// ==========================================
class MatchListDeserializer : JsonDeserializer<List<StatPalMatch>> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): List<StatPalMatch> {
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

// ==========================================
// RUGALMAS GSON KONVERTER A "LEAGUE" MEZŐHÖZ
// ==========================================
class LeagueListDeserializer : JsonDeserializer<List<StatPalLeague>> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): List<StatPalLeague> {
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

// ==========================================
// RUGALMAS DESZERIALIZÁTOR A DINAMIKUS "matches_DD_MM_YYYY" GYÖKÉRHEZ
// ==========================================
class StatPalLiveMatchesResponseDeserializer : JsonDeserializer<StatPalLiveMatchesResponse> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): StatPalLiveMatchesResponse {
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

// ==========================================
// FŐ RESPONSE DOKUMENTUM
// ==========================================
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

// ==========================================
// MECCS ESEMÉNYEK (TIMELINE)
// ==========================================
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

// ==========================================
// H2H (EGYMÁS ELLENI)
// ==========================================
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

// ==========================================
// HIÁNYZÓK / SÉRÜLTEK
// ==========================================
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

// ==========================================
// KEZDŐCSAPATOK (LINEUP)
// ==========================================
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

// ==========================================
// PREDICTION (AI ELEMZÉS)
// ==========================================
data class PredictionResponse(
    val prediction: PredictionData? = null
)

data class PredictionData(
    val choice: String? = null,
    val reasoning: String? = null
)

// ==========================================
// PREMATCH ODDS
// ==========================================
data class StatPalPrematchOddsResponse(
    @SerializedName("prematch_odds") val prematchOdds: PrematchOddsContainer? = null
)

data class PrematchOddsContainer(
    val league: PrematchOddsLeague? = null
)

data class PrematchOddsLeague(
    val match: List<PrematchOddsMatch>? = emptyList()
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
