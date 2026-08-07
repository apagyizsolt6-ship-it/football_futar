package com.focifutar.app

data class Team(
    val id: String,
    val name: String,
    val logoUrl: String
)

data class Match(
    val id: String,
    val homeTeam: Team,
    val awayTeam: Team,
    val homeScore: Int,
    val awayScore: Int,
    val timeStatus: String, // pl. '67, FT, 20:45
    val isLive: Boolean,
    val homePossession: Int = 50,
    val awayPossession: Int = 50,
    val homeShots: Int = 0,
    val awayShots: Int = 0,
    val homeXG: String = "0.0",
    val awayXG: String = "0.0"
)

data class LeagueGroup(
    val leagueName: String,
    val countryFlag: String,
    val matches: List<Match>
)
