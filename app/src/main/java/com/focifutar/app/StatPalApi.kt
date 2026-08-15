package com.focifutar.app

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * ============================================================
 * STATPAL API
 * ============================================================
 *
 * A StatPal marad a Football Futár elsődleges
 * mérkőzés- és élő adatforrása.
 *
 * StatPal:
 * - mérkőzések
 * - kezdési idő
 * - bajnokság
 * - élő állapot
 * - eredmény
 * - H2H
 * - sérülések
 * - összeállítás
 * - prediction
 * - tabella
 *
 * ODDS:
 * NEM innen érkezik.
 *
 * VIDEÓ:
 * NEM innen érkezik.
 *
 * Az oddsokat és videókat a Highlightly kezeli.
 * ============================================================
 */
interface StatPalService {

    @GET("api/v2/soccer/matches/daily")
    suspend fun getDailyMatches(
        @Query("access_key")
        apiKey: String,

        @Query("offset")
        offset: Int
    ): StatPalLiveMatchesResponse

    @GET("api/v2/soccer/h2h")
    suspend fun getHeadToHead(
        @Query("access_key")
        apiKey: String,

        @Query("team1_id")
        team1Id: String,

        @Query("team2_id")
        team2Id: String
    ): H2HResponse

    @GET("api/v2/soccer/injuries")
    suspend fun getInjuriesAndSuspensions(
        @Query("access_key")
        apiKey: String
    ): InjuriesResponse

    @GET("api/v2/soccer/lineup")
    suspend fun getTeamLineups(
        @Query("access_key")
        apiKey: String,

        @Query("main_id")
        mainId: String
    ): StatPalLineupResponse

    @GET("api/v2/soccer/prediction")
    suspend fun getMatchPrediction(
        @Query("access_key")
        apiKey: String,

        @Query("main_id")
        mainId: String
    ): PredictionResponse

    @GET("api/v2/soccer/leagues/{league_id}/standings")
    suspend fun getLeagueStandings(
        @Path("league_id")
        leagueId: String,

        @Query("access_key")
        apiKey: String
    ): StatPalStandingsResponse
}

/**
 * ============================================================
 * STATPAL CLIENT
 * ============================================================
 */
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

            .create(
                StatPalService::class.java
            )
    }
}

/**
 * ============================================================
 * HIGHLIGHTLY API
 * ============================================================
 *
 * Highlightly kezeli:
 *
 * 🎯 ODDS
 * 🎥 HIGHLIGHTS / VIDEÓ
 *
 * A The Odds API teljesen ki van véve.
 * ============================================================
 */
interface HighlightlyService {

    /**
     * Highlight videók lekérése.
     *
     * A csapatnév opcionálisan megadható.
     */
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
