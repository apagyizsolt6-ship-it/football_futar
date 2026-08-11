package com.focifutar.app

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface StatPalService {
    @GET("api/v2/soccer/matches/daily")
    suspend fun getDailyMatches(
        @Query("access_key") apiKey: String,
        @Query("offset") offset: Int
    ): StatPalLiveMatchesResponse

    @GET("api/v2/soccer/h2h")
    suspend fun getHeadToHead(
        @Query("access_key") apiKey: String,
        @Query("team1_id") team1Id: String,
        @Query("team2_id") team2Id: String
    ): H2HResponse

    @GET("api/v2/soccer/injuries")
    suspend fun getInjuriesAndSuspensions(
        @Query("access_key") apiKey: String
    ): InjuriesResponse

    @GET("api/v2/soccer/lineup")
    suspend fun getTeamLineups(
        @Query("access_key") apiKey: String,
        @Query("main_id") mainId: String
    ): StatPalLineupResponse

    @GET("api/v2/soccer/prediction")
    suspend fun getMatchPrediction(
        @Query("access_key") apiKey: String,
        @Query("main_id") mainId: String
    ): PredictionResponse

    @GET("api/v2/soccer/leagues/{league_id}/odds/prematch")
    suspend fun getPrematchOdds(
        @Path("league_id") leagueId: String,
        @Query("access_key") apiKey: String
    ): StatPalPrematchOddsResponse

    @GET("api/v2/soccer/leagues/{league_id}/standings")
    suspend fun getLeagueStandings(
        @Path("league_id") leagueId: String,
        @Query("access_key") apiKey: String
    ): StatPalStandingsResponse
}

object StatPalClient {
    private const val BASE_URL = "https://statpal.io/"

    private val gson = GsonBuilder()
        .registerTypeAdapter(
            object : TypeToken<List<StatPalMatch>>() {}.type,
            MatchListDeserializer()
        )
        .registerTypeAdapter(
            object : TypeToken<List<StatPalLeague>>() {}.type,
            LeagueListDeserializer()
        )
        .create()

    val service: StatPalService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(StatPalService::class.java)
    }
}
