package com.focifutar.app

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
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

    @GET("api/v2/soccer/leagues/{league_id}/standings")
    suspend fun getLeagueStandings(
        @Path("league_id") leagueId: String,
        @Query("access_key") apiKey: String
    ): StatPalStandingsResponse
}


object StatPalClient {

    private const val BASE_URL =
        "https://statpal.io/"

    private val gson =
        GsonBuilder()
            .registerTypeAdapter(
                object :
                    TypeToken<List<StatPalMatch>>() {}.type,
                MatchListDeserializer()
            )
            .registerTypeAdapter(
                object :
                    TypeToken<List<StatPalLeague>>() {}.type,
                LeagueListDeserializer()
            )
            .create()

    val service: StatPalService by lazy {

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(
                GsonConverterFactory.create(gson)
            )
            .build()
            .create(StatPalService::class.java)
    }
}


// ============================================================
// HIGHLIGHTLY API
// ============================================================
//
// Highlightly:
//
// 🎯 ODDS
// 🎥 VIDEÓ / HIGHLIGHTS
//
// The Odds API NINCS használatban.
// StatPal odds NINCS használatban.
// ============================================================

interface HighlightlyService {

    @GET("highlights")
    suspend fun getHighlights(
        @Header("x-rapidapi-key")
        apiKey: String,

        @Header("x-rapidapi-host")
        apiHost: String =
            "football-highlights-api.p.rapidapi.com",

        @Query("team")
        teamName: String? = null
    ): HighlightResponse


    @GET("matches")
    suspend fun getMatches(
        @Header("x-rapidapi-key")
        apiKey: String,

        @Header("x-rapidapi-host")
        apiHost: String =
            "football-highlights-api.p.rapidapi.com",

        @Query("date")
        date: String? = null,

        @Query("homeTeamName")
        homeTeamName: String? = null,

        @Query("awayTeamName")
        awayTeamName: String? = null,

        @Query("timezone")
        timezone: String =
            "Europe/Budapest",

        @Query("limit")
        limit: Int = 40,

        @Query("offset")
        offset: Int = 0
    ): HighlightlyMatchesResponse


    @GET("odds")
    suspend fun getOdds(
        @Header("x-rapidapi-key")
        apiKey: String,

        @Header("x-rapidapi-host")
        apiHost: String =
            "football-highlights-api.p.rapidapi.com",

        @Query("matchId")
        matchId: Long,

        @Query("oddsType")
        oddsType: String =
            "prematch",

        @Query("timezone")
        timezone: String =
            "Europe/Budapest",

        @Query("limit")
        limit: Int = 5,

        @Query("offset")
        offset: Int = 0
    ): HighlightlyOddsResponse


    companion object {

        private const val BASE_URL =
            "https://football-highlights-api.p.rapidapi.com/"


        fun create(): HighlightlyService {

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(
                    GsonConverterFactory.create()
                )
                .build()
                .create(
                    HighlightlyService::class.java
                )
        }
    }
}
