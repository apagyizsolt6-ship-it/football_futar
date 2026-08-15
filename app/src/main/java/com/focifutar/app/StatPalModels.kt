package com.focifutar.app

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type


// ============================================================
// STATPAL - ÁLTALÁNOS DESERIALIZÁLÓK
// ============================================================

class MatchListDeserializer :
    JsonDeserializer<List<StatPalMatch>> {

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): List<StatPalMatch> {

        if (json == null || json.isJsonNull) {
            return emptyList()
        }

        val list =
            mutableListOf<StatPalMatch>()

        if (json.isJsonArray) {

            for (element in json.asJsonArray) {

                val match =
                    context?.deserialize<StatPalMatch>(
                        element,
                        StatPalMatch::class.java
                    )

                if (match != null) {
                    list.add(match)
                }
            }

        } else if (json.isJsonObject) {

            val match =
                context?.deserialize<StatPalMatch>(
                    json.asJsonObject,
                    StatPalMatch::class.java
                )

            if (match != null) {
                list.add(match)
            }
        }

        return list
    }
}


class LeagueListDeserializer :
    JsonDeserializer<List<StatPalLeague>> {

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): List<StatPalLeague> {

        if (json == null || json.isJsonNull) {
            return emptyList()
        }

        val list =
            mutableListOf<StatPalLeague>()

        if (json.isJsonArray) {

            for (element in json.asJsonArray) {

                val league =
                    context?.deserialize<StatPalLeague>(
                        element,
                        StatPalLeague::class.java
                    )

                if (league != null) {
                    list.add(league)
                }
            }

        } else if (json.isJsonObject) {

            val league =
                context?.deserialize<StatPalLeague>(
                    json.asJsonObject,
                    StatPalLeague::class.java
                )

            if (league != null) {
                list.add(league)
            }
        }

        return list
    }
}


class StatPalLiveMatchesResponseDeserializer :
    JsonDeserializer<StatPalLiveMatchesResponse> {

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): StatPalLiveMatchesResponse {

        if (
            json == null ||
            !json.isJsonObject
        ) {
            return StatPalLiveMatchesResponse(null)
        }

        val obj =
            json.asJsonObject

        val containerEntry =
            obj.entrySet()
                .find {
                    it.key.startsWith("matches_")
                }
                ?: obj.entrySet()
                    .firstOrNull()

        var container:
            StatPalLiveMatchesContainer? = null

        if (
            containerEntry != null &&
            containerEntry.value.isJsonObject
        ) {

            container =
                context?.deserialize(
                    containerEntry.value,
                    StatPalLiveMatchesContainer::class.java
                )
        }

        return StatPalLiveMatchesResponse(
            liveMatches = container
        )
    }
}


@JsonAdapter(
    StatPalLiveMatchesResponseDeserializer::class
)
data class StatPalLiveMatchesResponse(
    val liveMatches:
        StatPalLiveMatchesContainer? = null
)


data class StatPalLiveMatchesContainer(
    val updated: String? = null,

    @SerializedName("updated_ts")
    val updatedTs: Long? = null,

    @JsonAdapter(
        LeagueListDeserializer::class
    )
    @SerializedName("league")
    val league:
        List<StatPalLeague>? = emptyList()
)


data class StatPalLeague(
    val id: String? = null,

    val name: String? = null,

    val country: String? = null,

    val cup: String? = null,

    @JsonAdapter(
        MatchListDeserializer::class
    )
    @SerializedName("match")
    val match:
        List<StatPalMatch> = emptyList()
)


data class StatPalMatch(

    @SerializedName("main_id")
    val mainId: String? = null,

    val status: String? = null,

    val date: String? = null,

    val time: String? = null,

    val venue: String? = null,

    val home:
        StatPalTeam? = null,

    val away:
        StatPalTeam? = null,

    val events:
        MatchEventsContainer? = null,

    val ht:
        StatPalScore? = null,

    val ft:
        StatPalScore? = null,

    val et:
        StatPalScore? = null,

    val penalties:
        StatPalPenaltyData? = null,

    @SerializedName("has_live_stats")
    val hasLiveStats: String? = null,

    @SerializedName("inplay_odds_running")
    val inplayOddsRunning: String? = null,

    @SerializedName("match_context")
    val matchContext:
        StatPalContext? = null
)


data class MatchEventsContainer(
    val event:
        List<MatchEventItem>? = emptyList()
)


data class MatchEventItem(
    val id: String? = null,

    val type: String? = null,

    val team: String? = null,

    val minute: String? = null,

    @SerializedName("extra_min")
    val extraMin: String? = null,

    val player: String? = null,

    @SerializedName("assist_player")
    val assistPlayer: String? = null,

    val result: String? = null
)


data class StatPalTeam(
    val id: String? = null,

    val name: String? = null,

    val goals: String? = null,

    val logo: String? = null,

    val image: String? = null,

    @SerializedName("win_on_agg")
    val winOnAgg: String? = null
)


data class StatPalScore(

    @SerializedName("home_goals")
    val homeGoals: Any? = null,

    @SerializedName("away_goals")
    val awayGoals: Any? = null
)


data class StatPalPenaltyData(

    @SerializedName("home_pen")
    val homePen: Any? = null,

    @SerializedName("away_pen")
    val awayPen: Any? = null
)


data class StatPalContext(

    @SerializedName("live_storylines")
    val liveStorylines: Boolean? = false,

    @SerializedName("weather_forecast")
    val weatherForecast: Boolean? = false,

    @SerializedName("team_lineups")
    val teamLineups: Boolean? = false,

    val predictions: Boolean? = false
)


// ============================================================
// H2H
// ============================================================

data class H2HResponse(
    @SerializedName("head_to_head")
    val headToHead:
        H2HData? = null
)


data class H2HData(
    @SerializedName("recent_meetings")
    val recentMeetings:
        H2HMeetings? = null
)


data class H2HMeetings(
    val match:
        List<H2HMatch>? = emptyList()
)


data class H2HMatch(

    val date: String? = null,

    @SerializedName("team1_name")
    val team1Name: String? = null,

    @SerializedName("team2_name")
    val team2Name: String? = null,

    @SerializedName("team1_score")
    val team1Score: String? = null,

    @SerializedName("team2_score")
    val team2Score: String? = null
)


// ============================================================
// INJURIES / SUSPENSIONS
// ============================================================

data class InjuriesResponse(
    @SerializedName("injuries_suspensions")
    val injuriesSuspensions:
        InjuryLeagueContainer? = null
)


data class InjuryLeagueContainer(
    val league:
        List<InjuryLeague>? = emptyList()
)


data class InjuryLeague(
    val match:
        List<InjuryMatch>? = emptyList()
)


data class InjuryMatch(

    @SerializedName("main_id")
    val mainId: String? = null,

    val home:
        InjuryTeam? = null,

    val away:
        InjuryTeam? = null
)


data class InjuryTeam(
    val name: String? = null,

    val sidelined:
        SidelinedData? = null
)


data class SidelinedData(

    @SerializedName("to_miss")
    val toMiss:
        PlayerListContainer? = null,

    val questionable:
        PlayerListContainer? = null
)


data class PlayerListContainer(
    val player:
        List<InjuryPlayer>? = emptyList()
)


data class InjuryPlayer(
    val name: String? = null,

    val status: String? = null
)


// ============================================================
// LINEUPS
// ============================================================

data class StatPalLineupResponse(
    val home:
        LineupTeam? = null,

    val away:
        LineupTeam? = null
)


data class LineupTeam(

    @SerializedName("team_name")
    val teamName: String? = null,

    @SerializedName("team_formation")
    val teamFormation: String? = null,

    val coach:
        CoachData? = null,

    @SerializedName("starting_xi")
    val startingXi:
        List<LineupPlayer>? = emptyList()
)


data class CoachData(
    val name: String? = null
)


data class LineupPlayer(
    val name: String? = null,

    val number: String? = null,

    val position: String? = null
)


// ============================================================
// PREDICTION
// ============================================================

data class PredictionResponse(
    val prediction:
        PredictionData? = null
)


data class PredictionData(
    val choice: String? = null,

    val reasoning: String? = null
)


// ============================================================
// STATPAL PREMATCH ODDS
// ============================================================
//
// Ezek a modellek a régi StatPal odds struktúrához tartoznak.
// A Football Futár új verziójában az odds forrása Highlightly,
// ezért ezeket később akár teljesen eltávolíthatjuk.
// Egyelőre kompatibilitás miatt megtartjuk őket.
//
// ============================================================

class PrematchOddsLeagueListDeserializer :
    JsonDeserializer<List<PrematchOddsLeague>> {

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): List<PrematchOddsLeague> {

        if (
            json == null ||
            json.isJsonNull
        ) {
            return emptyList()
        }

        val list =
            mutableListOf<PrematchOddsLeague>()

        if (json.isJsonArray) {

            for (element in json.asJsonArray) {

                val item =
                    context?.deserialize<PrematchOddsLeague>(
                        element,
                        PrematchOddsLeague::class.java
                    )

                if (item != null) {
                    list.add(item)
                }
            }

        } else if (json.isJsonObject) {

            val item =
                context?.deserialize<PrematchOddsLeague>(
                    json.asJsonObject,
                    PrematchOddsLeague::class.java
                )

            if (item != null) {
                list.add(item)
            }
        }

        return list
    }
}


data class StatPalPrematchOddsResponse(
    @SerializedName("prematch_odds")
    val prematchOdds:
        PrematchOddsContainer? = null
)


data class PrematchOddsContainer(

    @JsonAdapter(
        PrematchOddsLeagueListDeserializer::class
    )
    val league:
        List<PrematchOddsLeague>? = emptyList()
)


data class PrematchOddsLeague(

    val id: String? = null,

    val name: String? = null,

    val country: String? = null,

    @JsonAdapter(
        MatchListDeserializer::class
    )
    @SerializedName("match")
    val match:
        List<PrematchOddsMatch> = emptyList()
)


data class PrematchOddsMatch(

    @SerializedName("main_id")
    val mainId: String? = null,

    @SerializedName("fallback_id_1")
    val fallbackId1: String? = null,

    @SerializedName("fallback_id_2")
    val fallbackId2: String? = null,

    @SerializedName("fallback_id_3")
    val fallbackId3: String? = null,

    val date: String? = null,

    val time: String? = null,

    val home:
        StatPalTeam? = null,

    val away:
        StatPalTeam? = null,

    val odds:
        List<OddsCategory>? = emptyList()
)


data class OddsCategory(
    val name: String? = null,

    val bookmaker:
        List<BookmakerData>? = emptyList()
)


data class BookmakerData(
    val name: String? = null,

    val odd:
        List<OddValue>? = emptyList()
)


data class OddValue(
    val name: String? = null,

    val value: String? = null
)


// ============================================================
// TABELLA / STANDINGS
// ============================================================

data class StatPalStandingsResponse(
    val standings:
        StandingsContainer? = null
)


data class StandingsContainer(

    val updated: String? = null,

    @SerializedName("updated_ts")
    val updatedTs: Any? = null,

    val country: String? = null,

    val tournament:
        TournamentData? = null
)


data class TournamentData(

    val id: String? = null,

    val league: String? = null,

    val season: String? = null,

    @SerializedName("stage_id")
    val stageId: String? = null,

    @SerializedName("is_current")
    val isCurrent: String? = null,

    val team:
        List<StandingTeamRow>? = emptyList()
)


data class StandingTeamRow(

    val position: String? = null,

    val name: String? = null,

    val id: String? = null,

    val status: String? = null,

    @SerializedName("recent_form")
    val recentForm: String? = null,

    val overall:
        StatScoreOverall? = null,

    val total:
        StatScoreTotal? = null,

    val description:
        StatDescription? = null
)


data class StatScoreOverall(

    @SerializedName("games_played")
    val gamesPlayed: String? = null,

    val wins: String? = null,

    val draws: String? = null,

    val losses: String? = null,

    @SerializedName("goals_scored")
    val goalsScored: String? = null,

    @SerializedName("goals_allowed")
    val goalsAllowed: String? = null
)


data class StatScoreTotal(

    @SerializedName("goal_difference")
    val goalDifference: String? = null,

    val points: String? = null
)


data class StatDescription(
    val value: String? = null
)


// ============================================================
// HIGHLIGHTLY API - VIDEÓ / HIGHLIGHTS
// ============================================================

data class HighlightResponse(

    val data:
        List<HighlightItem> = emptyList(),

    val pagination:
        Pagination? = null,

    val plan:
        HighlightlyPlan? = null
)


data class HighlightItem(

    val id: Int? = null,

    val type: String? = null,

    val imgUrl: String? = null,

    val title: String? = null,

    val description: String? = null,

    val url: String? = null,

    val embedUrl: String? = null,

    val category: String? = null,

    val channel: String? = null,

    val source: String? = null,

    val match:
        HighlightMatch? = null
)


data class HighlightMatch(

    val id: Long? = null,

    val round: String? = null,

    val date: String? = null,

    val homeTeam:
        TeamInfo? = null,

    val awayTeam:
        TeamInfo? = null,

    val league:
        LeagueInfo? = null
)


data class TeamInfo(

    val id: Int? = null,

    val name: String? = null,

    val logo: String? = null
)


data class LeagueInfo(

    val id: Int? = null,

    val name: String? = null,

    val logo: String? = null
)


data class Pagination(

    val totalCount: Int? = null,

    val offset: Int? = null,

    val limit: Int? = null
)


data class HighlightlyPlan(

    val tier: String? = null,

    val message: String? = null
)


// ============================================================
// HIGHLIGHTLY API - MÉRKŐZÉSEK
// ============================================================
//
// A kap/mérkőzések válaszban a dokumentáció általad küldött
// mintája:
//
// {
//   "data": [ {} ],
//   "pagination": {...},
//   "plan": {...}
// }
//
// A mezők teljes struktúráját a dokumentációs mintában nem
// bontották ki, ezért a részletes mezőket opcionálisra tesszük.
//
// ============================================================

data class HighlightlyMatchesResponse(

    val data:
        List<HighlightlyMatch> = emptyList(),

    val pagination:
        Pagination? = null,

    val plan:
        HighlightlyPlan? = null
)


data class HighlightlyMatch(

    val id: Long? = null,

    val date: String? = null,

    val time: String? = null,

    val status: String? = null,

    val round: String? = null,

    val homeTeam:
        TeamInfo? = null,

    val awayTeam:
        TeamInfo? = null,

    val league:
        LeagueInfo? = null
)


// ============================================================
// HIGHLIGHTLY API - ODDS
// ============================================================
//
// FONTOS:
//
// A kap/esély dokumentációban a válaszminta jelenleg:
//
// {
//   "data": [ {} ],
//   "pagination": {...},
//   "plan": {...}
// }
//
// Emiatt itt nem találunk ki olyan mezőket, amelyek nem szerepelnek
// a kapott dokumentációban.
//
// A raw JsonElement segítségével a teljes odds JSON megmarad,
// és a következő lépésben a valódi API-válasz alapján tudjuk
// pontosan leképezni a piacokat.
//
// ============================================================

data class HighlightlyOddsResponse(

    val data:
        List<JsonElement> = emptyList(),

    val pagination:
        Pagination? = null,

    val plan:
        HighlightlyPlan? = null
)


// ============================================================
// HIGHLIGHTLY ODDS - SEGÉDMODELLEK
// ============================================================
//
// Ezek a modellek lehetővé teszik, hogy később egységesen kezeljük
// az 1X2 oddsokat és a további piacokat.
//
// ============================================================

data class HighlightlyOddsSummary(

    val home: Double? = null,

    val draw: Double? = null,

    val away: Double? = null,

    val bookmaker: String? = null
)


data class HighlightlyOddsMarket(

    val id: String? = null,

    val name: String? = null,

    val bookmaker: String? = null,

    val outcomes:
        List<HighlightlyOddsOutcome> = emptyList()
)


data class HighlightlyOddsOutcome(

    val name: String? = null,

    val value: Double? = null,

    val handicap: Double? = null,

    val label: String? = null
)


// ============================================================
// HIGHLIGHTLY RAW ODDS SEGÉD
// ============================================================
//
// Ha az API olyan odds-választ küld, amelynek szerkezete eltér,
// ezt használhatjuk a teljes JSON megtartására.
//
// ============================================================

data class HighlightlyRawOdds(

    val raw:
        JsonElement? = null
)


// ============================================================
// KÖZÖS ODDS UI MODEL
// ============================================================
//
// A felület ezt a modellt használhatja függetlenül attól,
// hogy a Highlightly válaszban hány bookmaker/piac érkezik.
//
// ============================================================

data class MatchOdds(

    val home: Double? = null,

    val draw: Double? = null,

    val away: Double? = null,

    val bookmaker: String? = null
)


// ============================================================
// HIGHLIGHTLY MATCH + ODDS KAPCSOLAT
// ============================================================

data class HighlightlyMatchWithOdds(

    val match:
        HighlightlyMatch? = null,

    val odds:
        MatchOdds? = null
)


// ============================================================
// HIGHLIGHTLY VIDEO + MATCH KAPCSOLAT
// ============================================================

data class HighlightlyMatchVideo(

    val highlight:
        HighlightItem? = null,

    val matchId: Long? = null
)
