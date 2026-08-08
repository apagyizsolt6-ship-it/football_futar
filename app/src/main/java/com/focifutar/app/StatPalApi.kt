package com.focifutar.app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface StatPalApiService {

    // Élő és mai meccsek lekérése
    @Headers("Accept: application/json", "User-Agent: FootballFutar/1.0")
    @GET("api/v2/soccer/matches/live")
    suspend fun getLiveMatches(
        @Query("access_key") accessKey: String
    ): StatPalResponse

    // Múltbeli és jövőbeli meccsek lekérése dátum alapján
    @Headers("Accept: application/json", "User-Agent: FootballFutar/1.0")
    @GET("api/v2/soccer/matches/recent-upcoming")
    suspend fun getRecentUpcomingMatches(
        @Query("access_key") accessKey: String,
        @Query("date") date: String? = null
    ): StatPalResponse

    // Egymás elleni eredmények (H2H)
    @Headers("Accept: application/json", "User-Agent: FootballFutar/1.0")
    @GET("api/v2/soccer/head-to-head")
    suspend fun getHeadToHead(
        @Query("access_key") accessKey: String,
        @Query("team1_id") team1Id: String,
        @Query("team2_id") team2Id: String
    ): StatPalH2HResponse

    // Sérültek és eltiltottak
    @Headers("Accept: application/json", "User-Agent: FootballFutar/1.0")
    @GET("api/v2/soccer/injuries-suspensions")
    suspend fun getInjuriesAndSuspensions(
        @Query("access_key") accessKey: String
    ): StatPalInjuriesResponse

    // Bajnokság tabella
    @Headers("Accept: application/json", "User-Agent: FootballFutar/1.0")
    @GET("api/v2/soccer/leagues/{league_id}/standings")
    suspend fun getStandings(
        @Path("league_id") leagueId: String,
        @Query("access_key") accessKey: String
    ): StatPalStandingsResponse

    // Kezdőcsapatok és felállások
    @Headers("Accept: application/json", "User-Agent: FootballFutar/1.0")
    @GET("api/v2/soccer/team-lineups")
    suspend fun getTeamLineups(
        @Query("access_key") accessKey: String,
        @Query("match_id") matchId: String
    ): StatPalLineupResponse

    // Meccs előtti oddsok
    @Headers("Accept: application/json", "User-Agent: FootballFutar/1.0")
    @GET("api/v2/soccer/leagues/{league_id}/odds/prematch")
    suspend fun getPrematchOdds(
        @Path("league_id") leagueId: String,
        @Query("access_key") accessKey: String
    ): StatPalOddsResponse

    // AI Meccselemzés és tippek
    @Headers("Accept: application/json", "User-Agent: FootballFutar/1.0")
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
