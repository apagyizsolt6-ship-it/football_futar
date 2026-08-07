package com.focifutar.app

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

// Rugalmas feldolgozó: Kezeli ha 1 elem jön ({}) és ha lista is ([{}])
class FlexibleListDeserializer<T> : JsonDeserializer<List<T>> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): List<T> {
        if (json == null || json.isJsonNull) return emptyList()
        val list = mutableListOf<T>()
        val itemType = (typeOfT as ParameterizedType).actualTypeArguments[0]

        if (json.isJsonArray) {
            for (element in json.asJsonArray) {
                val item: T = context!!.deserialize(element, itemType)
                list.add(item)
            }
        } else if (json.isJsonObject) {
            val item: T = context!!.deserialize(json, itemType)
            list.add(item)
        }
        return list
    }
}

interface StatPalApiService {
    @GET("api/v2/soccer/matches/live")
    suspend fun getLiveMatches(
        @Query("access_key") accessKey: String
    ): StatPalResponse

    @GET("api/v2/soccer/head-to-head")
    suspend fun getHeadToHead(
        @Query("access_key") accessKey: String,
        @Query("team1_id") team1Id: String,
        @Query("team2_id") team2Id: String
    ): StatPalH2HResponse

    @GET("api/v2/soccer/injuries-suspensions")
    suspend fun getInjuriesAndSuspensions(
        @Query("access_key") accessKey: String
    ): StatPalInjuriesResponse

    @GET("api/v2/soccer/leagues/{league_id}/standings")
    suspend fun getStandings(
        @Path("league_id") leagueId: String,
        @Query("access_key") accessKey: String
    ): StatPalStandingsResponse

    @GET("api/v2/soccer/team-lineups")
    suspend fun getTeamLineups(
        @Query("access_key") accessKey: String,
        @Query("match_id") matchId: String
    ): StatPalLineupResponse

    @GET("api/v2/soccer/leagues/{league_id}/odds/prematch")
    suspend fun getPrematchOdds(
        @Path("league_id") leagueId: String,
        @Query("access_key") accessKey: String
    ): StatPalOddsResponse

    @GET("api/v2/soccer/predictions")
    suspend fun getMatchPrediction(
        @Query("access_key") accessKey: String,
        @Query("match_id") matchId: String
    ): StatPalPredictionResponse
}

object StatPalClient {
    private const val BASE_URL = "https://statpal.io/"

    private inline fun <reified T> createListTypeToken(): Type {
        return object : TypeToken<List<T>>() {}.type
    }

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(createListTypeToken<StatPalLeague>(), FlexibleListDeserializer<StatPalLeague>())
        .registerTypeAdapter(createListTypeToken<StatPalMatch>(), FlexibleListDeserializer<StatPalMatch>())
        .registerTypeAdapter(createListTypeToken<H2HMatch>(), FlexibleListDeserializer<H2HMatch>())
        .registerTypeAdapter(createListTypeToken<InjuryLeague>(), FlexibleListDeserializer<InjuryLeague>())
        .registerTypeAdapter(createListTypeToken<InjuryMatch>(), FlexibleListDeserializer<InjuryMatch>())
        .registerTypeAdapter(createListTypeToken<InjuryPlayer>(), FlexibleListDeserializer<InjuryPlayer>())
        .registerTypeAdapter(createListTypeToken<StandingTeam>(), FlexibleListDeserializer<StandingTeam>())
        .registerTypeAdapter(createListTypeToken<PlayerLineup>(), FlexibleListDeserializer<PlayerLineup>())
        .registerTypeAdapter(createListTypeToken<SidelinedLineupPlayer>(), FlexibleListDeserializer<SidelinedLineupPlayer>())
        .registerTypeAdapter(createListTypeToken<OddsMatch>(), FlexibleListDeserializer<OddsMatch>())
        .registerTypeAdapter(createListTypeToken<OddsMarket>(), FlexibleListDeserializer<OddsMarket>())
        .registerTypeAdapter(createListTypeToken<BookmakerData>(), FlexibleListDeserializer<BookmakerData>())
        .registerTypeAdapter(createListTypeToken<OddValue>(), FlexibleListDeserializer<OddValue>())
        .create()

    val service: StatPalApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(StatPalApiService::class.java)
    }
}
