package com.focifutar.app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

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

    val service: StatPalApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StatPalApiService::class.java)
    }
}
