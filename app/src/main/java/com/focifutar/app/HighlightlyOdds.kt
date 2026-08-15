package com.focifutar.app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Football Futár
 *
 * Highlightly Football API - ODDS
 *
 * A Football Futárban az oddsok kizárólag
 * a Highlightly Football API-ból érkeznek.
 *
 * The Odds API NINCS használatban.
 */
interface HighlightlyOddsApi {

    @GET("odds")
    suspend fun getOdds(
        @Header("x-rapidapi-key")
        apiKey: String,

        @Header("x-rapidapi-host")
        apiHost: String = HIGHLIGHTLY_RAPID_HOST,

        @Query("matchId")
        matchId: Long,

        @Query("oddsType")
        oddsType: String = "prematch",

        @Query("limit")
        limit: Int = 5,

        @Query("offset")
        offset: Int = 0
    ): HighlightlyOddsResponse

    companion object {

        const val HIGHLIGHTLY_RAPID_HOST =
            "football-highlights-api.p.rapidapi.com"

        private const val BASE_URL =
            "https://football-highlights-api.p.rapidapi.com/"

        fun create(): HighlightlyOddsApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(
                    GsonConverterFactory.create()
                )
                .build()
                .create(HighlightlyOddsApi::class.java)
        }
    }
}

/**
 * Highlightly odds válasz.
 */
data class HighlightlyOddsResponse(
    val data: List<HighlightlyMatchOdds> = emptyList(),
    val pagination: HighlightlyPagination? = null,
    val plan: HighlightlyPlan? = null
)

/**
 * Egy mérkőzés teljes odds adata.
 */
data class HighlightlyMatchOdds(
    val matchId: Long? = null,
    val odds: List<HighlightlyOddMarket> = emptyList()
)

/**
 * Egy odds piac.
 */
data class HighlightlyOddMarket(
    val bookmakerId: Int? = null,
    val bookmakerName: String? = null,
    val type: String? = null,
    val market: String? = null,
    val values: List<HighlightlyOddValue> = emptyList()
)

/**
 * Egy odds érték.
 */
data class HighlightlyOddValue(
    val odd: Double? = null,
    val value: String? = null
)

/**
 * Lapozási adatok.
 */
data class HighlightlyPagination(
    val totalCount: Int? = null,
    val offset: Int? = null,
    val limit: Int? = null
)

/**
 * Highlightly csomaginformáció.
 */
data class HighlightlyPlan(
    val tier: String? = null,
    val message: String? = null
)

/**
 * A mérkőzéskártyán megjelenő 1X2 összefoglaló.
 */
data class HighlightlyMatchOddsSummary(
    val home: Double? = null,
    val draw: Double? = null,
    val away: Double? = null,
    val bookmakerName: String? = null,
    val oddsType: String = "prematch"
) {

    val hasAny: Boolean
        get() = home != null ||
                draw != null ||
                away != null
}

/**
 * Highlightly odds repository.
 */
object HighlightlyOddsRepository {

    private val service by lazy {
        HighlightlyOddsApi.create()
    }

    /**
     * Egy konkrét mérkőzés oddsainak lekérése.
     */
    suspend fun getMatchOdds(
        apiKey: String,
        matchId: String?,
        oddsType: String = "prematch"
    ): HighlightlyMatchOdds? {

        val numericMatchId =
            matchId
                ?.trim()
                ?.toLongOrNull()
                ?: return null

        if (apiKey.isBlank()) {
            return null
        }

        return try {

            val response = service.getOdds(
                apiKey = apiKey,
                matchId = numericMatchId,
                oddsType = oddsType,
                limit = 5,
                offset = 0
            )

            response.data.firstOrNull {
                it.matchId == numericMatchId
            } ?: response.data.firstOrNull()

        } catch (_: Exception) {

            null
        }
    }

    /**
     * 1X2 odds összefoglaló lekérése.
     *
     * Home = 1
     * Draw = X
     * Away = 2
     */
    suspend fun getMatchOddsSummary(
        apiKey: String,
        matchId: String?,
        oddsType: String = "prematch"
    ): HighlightlyMatchOddsSummary? {

        val match =
            getMatchOdds(
                apiKey = apiKey,
                matchId = matchId,
                oddsType = oddsType
            ) ?: return null

        val fullTime =
            match.odds.firstOrNull { market ->

                market.market.equals(
                    "Full Time Result",
                    ignoreCase = true
                ) ||

                market.market.equals(
                    "1X2",
                    ignoreCase = true
                ) ||

                market.market.equals(
                    "Match Result",
                    ignoreCase = true
                )
            } ?: return null

        var home: Double? = null
        var draw: Double? = null
        var away: Double? = null

        fullTime.values.forEach { value ->

            when (
                value.value
                    ?.trim()
                    ?.lowercase()
            ) {

                "home",
                "1" -> {
                    home = value.odd
                }

                "draw",
                "x" -> {
                    draw = value.odd
                }

                "away",
                "2" -> {
                    away = value.odd
                }
            }
        }

        return HighlightlyMatchOddsSummary(
            home = home,
            draw = draw,
            away = away,
            bookmakerName = fullTime.bookmakerName,
            oddsType = oddsType
        )
    }
}

/**
 * Odds megjelenítési formázása.
 *
 * Ha nincs adat:
 * —
 */
fun formatHighlightlyOdd(
    value: Double?
): String {

    return value?.let {

        String.format(
            java.util.Locale.US,
            "%.2f",
            it
        )

    } ?: "—"
}
