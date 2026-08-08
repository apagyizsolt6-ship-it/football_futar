package com.focifutar.app

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

data class StatPalResponse(
    @SerializedName("live_matches") val liveMatches: StatPalLiveMatches?
)

data class StatPalLiveMatches(
    @SerializedName("league") private val leagueElement: JsonElement?
) {
    val league: List<StatPalLeague>
        get() {
            if (leagueElement == null || leagueElement.isJsonNull) return emptyList()
            val gson = Gson()
            return try {
                if (leagueElement.isJsonArray) {
                    val listType = object : TypeToken<List<StatPalLeague>>() {}.type
                    gson.fromJson(leagueElement, listType) ?: emptyList()
                } else if (leagueElement.isJsonObject) {
                    val obj = leagueElement.asJsonObject
                    if (obj.has("match") || obj.has("name") || obj.has("id")) {
                        listOfNotNull(gson.fromJson(obj, StatPalLeague::class.java))
                    } else {
                        obj.entrySet().mapNotNull { entry ->
                            try { gson.fromJson(entry.value, StatPalLeague::class.java) } catch (_: Exception) { null }
                        }
                    }
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
}

data class StatPalLeague(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("match") private val matchElement: JsonElement?
) {
    val match: List<StatPalMatch>
        get() {
            if (matchElement == null || matchElement.isJsonNull) return emptyList()
            val gson = Gson()
            return try {
                if (matchElement.isJsonArray) {
                    val listType = object : TypeToken<List<StatPalMatch>>() {}.type
                    gson.fromJson(matchElement, listType) ?: emptyList()
                } else if (matchElement.isJsonObject) {
                    val obj = matchElement.asJsonObject
                    if (obj.has("id") || obj.has("home") || obj.has("away")) {
                        listOfNotNull(gson.fromJson(obj, StatPalMatch::class.java))
                    } else {
                        obj.entrySet().mapNotNull { entry ->
                            try { gson.fromJson(entry.value, StatPalMatch::class.java) } catch (_: Exception) { null }
                        }
                    }
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
}

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
    @SerializedName("match") private val matchElement: JsonElement?
) {
    val match: List<H2HMatch>
        get() {
            if (matchElement == null || matchElement.isJsonNull) return emptyList()
            val gson = Gson()
            return try {
                if (matchElement.isJsonArray) {
                    val listType = object : TypeToken<List<H2HMatch>>() {}.type
                    gson.fromJson(matchElement, listType) ?: emptyList()
                } else if (matchElement.isJsonObject) {
                    val obj = matchElement.asJsonObject
                    listOfNotNull(gson.fromJson(obj, H2HMatch::class.java))
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
}

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
