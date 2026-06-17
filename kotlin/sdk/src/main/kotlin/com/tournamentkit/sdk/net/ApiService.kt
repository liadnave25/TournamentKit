package com.tournamentkit.sdk.net

import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.Tournament
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Retrofit mapping of the public /v1 endpoints. Every call is suspend and returns a raw Response<T>
// so the error layer can read the status code and parse a TKError body uniformly.
internal interface ApiService {

    // Create a tournament from a template (creator auto-joins).
    @POST("v1/tournaments")
    suspend fun createTournament(@Body body: CreateTournamentBody): Response<Tournament>

    // Join a tournament by its code; the server returns the updated tournament.
    @POST("v1/tournaments/join")
    suspend fun joinTournament(@Body body: JoinBody): Response<Tournament>

    // Start a tournament (creator only): draws the matches and goes ACTIVE.
    @POST("v1/tournaments/{id}/start")
    suspend fun startTournament(@Path("id") tournamentId: String, @Body body: StartBody): Response<Tournament>

    // Report a match result (final immediately); returns the full updated tournament view.
    @POST("v1/matches/report")
    suspend fun reportResult(@Body body: ReportBody): Response<TournamentViewDto>

    // Add points to a person on a TALLY leaderboard; returns that user's updated standing.
    @POST("v1/tally/add")
    suspend fun addScore(@Body body: TallyAddBody): Response<Standing>

    // Fetch the full tournament view (tournament + matches + standings).
    @GET("v1/tournaments/{id}")
    suspend fun getTournament(@Path("id") tournamentId: String): Response<TournamentViewDto>

    // Fetch just the standings, already sorted by the engine's tiebreaker order.
    @GET("v1/tournaments/{id}/standings")
    suspend fun getStandings(@Path("id") tournamentId: String): Response<List<Standing>>

    // Fetch a user's cumulative ELO rating in this project.
    @GET("v1/ratings/{userId}")
    suspend fun getUserRating(@Path("userId") userId: String): Response<RatingDto>
}
