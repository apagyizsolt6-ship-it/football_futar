package com.focifutar.app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface StatPalService {
    @GET("api/v2/soccer/live")
    suspend fun getLiveMatches(
        @Query("access_key") apiKey: String
    ): StatPalLiveMatchesResponse

    @GET("api/v2/soccer/fixtures")
    suspend fun getFixtures(
        @Query("access_key") apiKey: String,
        @Query("date") date: String? = null
    ): StatPalLiveMatchesResponse

    @GET("api/v2/soccer/matches")
    suspend fun getMatches(
        @Query("access_key") apiKey: String,
        @Query("date") date: String? = null
    ): StatPalLiveMatchesResponse

    @GET("api/v2/soccer/recent-upcoming")
    suspend fun getRecentUpcomingMatches(
        @Query("access_key") apiKey: String,
        @Query("date") date: String? = null
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
}

object StatPalClient {
    private const val BASE_URL = "https://statpal.io/"

    val service: StatPalService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StatPalService::class.java)
    }
}
