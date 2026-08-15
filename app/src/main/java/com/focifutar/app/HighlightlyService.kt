package com.focifutar.app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface HighlightlyApi {

    // Meccsek listája (dátum + liga szerint)
    @GET("matches")
    suspend fun getMatches(
        @Header("x-rapidapi-key") apiKey: String,
        @Header("x-rapidapi-host") apiHost: String = HOST,
        @Query("date") date: String? = null,
        @Query("leagueId") leagueId: Int? = null,
        @Query("timezone") timezone: String = "Europe/Budapest",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): HighlightlyMatchesResponse

    // Egy meccs részletei
    @GET("matches/{matchId}")
    suspend fun getMatchById(
        @Header("x-rapidapi-key") apiKey: String,
        @Header("x-rapidapi-host") apiHost: String = HOST,
        @Path("matchId") matchId: Long
    ): HighlightlyMatchDetail?

    // Odds
    @GET("odds")
    suspend fun getOdds(
        @Header("x-rapidapi-key") apiKey: String,
        @Header("x-rapidapi-host") apiHost: String = HOST,
        @Query("matchId") matchId: Long,
        @Query("oddsType") oddsType: String = "prematch",
        @Query("limit") limit: Int = 10
    ): HighlightlyOddsResponse

    // Highlights
    @GET("highlights")
    suspend fun getHighlights(
        @Header("x-rapidapi-key") apiKey: String,
        @Header("x-rapidapi-host") apiHost: String = HOST,
        @Query("matchId") matchId: Long? = null,
        @Query("date") date: String? = null,
        @Query("limit") limit: Int = 20
    ): HighlightResponse

    // Lineups
    @GET("lineups/{matchId}")
    suspend fun getLineups(
        @Header("x-rapidapi-key") apiKey: String,
        @Header("x-rapidapi-host") apiHost: String = HOST,
        @Path("matchId") matchId: Long
    ): Any?   // később pontosítjuk a modelt

    companion object {
        const val HOST = "football-highlights-api.p.rapidapi.com"
        private const val BASE_URL = "https://football-highlights-api.p.rapidapi.com/"

        fun create(): HighlightlyApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(HighlightlyApi::class.java)
        }
    }
}

object HighlightlyService {
    val api: HighlightlyApi by lazy { HighlightlyApi.create() }
}
