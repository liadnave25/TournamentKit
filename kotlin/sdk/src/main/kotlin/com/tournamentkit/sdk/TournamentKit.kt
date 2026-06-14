package com.tournamentkit.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.tournamentkit.sdk.net.ApiService
import com.tournamentkit.sdk.net.ConfirmBody
import com.tournamentkit.sdk.net.CreateTournamentBody
import com.tournamentkit.sdk.net.JoinBody
import com.tournamentkit.sdk.net.ReportBody
import com.tournamentkit.sdk.net.RetrofitProvider
import com.tournamentkit.sdk.net.StartBody
import com.tournamentkit.sdk.net.errorFromException
import com.tournamentkit.sdk.net.errorFromResponse
import com.tournamentkit.shared.Match
import com.tournamentkit.shared.Participant
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.TKError
import com.tournamentkit.shared.TKErrorCode
import com.tournamentkit.shared.TKScore
import com.tournamentkit.shared.Tournament
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Response

// The single public entry point an app developer uses. Callback-style API (spec §3) backed by
// coroutines: network runs on Dispatchers.IO, results are delivered on the main thread.
object TournamentKit {

    // Set once by initialize(); null until then so calls can fail fast with TK_NOT_INITIALIZED.
    private var config: TKConfig? = null
    private var api: ApiService? = null

    // The current user from identify(); needed by calls that act on behalf of a player.
    private var user: TKUser? = null

    // All network work runs here; SupervisorJob keeps one failed call from cancelling the others.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Posts callback results to the Android main thread so developers can touch UI directly.
    // Injectable so unit tests can run callbacks inline (no Android looper on the JVM).
    internal var resultDispatcher: (Runnable) -> Unit = { r -> Handler(Looper.getMainLooper()).post(r) }

    // ---------- lifecycle ----------

    // Stores config and builds the network client. No network call. Idempotent (re-init replaces config).
    // We keep the APPLICATION context only — never an Activity — to avoid leaking a screen.
    fun init(
        context: Context,
        apiKey: String,
        projectId: String,
        baseUrl: String = DEFAULT_BASE_URL,
        debugLogging: Boolean = false
    ) {
        @Suppress("UNUSED_VARIABLE")
        val appContext = context.applicationContext  // held for future use (offline store, etc.)
        val cfg = TKConfig(apiKey = apiKey, projectId = projectId, baseUrl = baseUrl, debugLogging = debugLogging)
        config = cfg
        api = RetrofitProvider.create(cfg.baseUrl, cfg.apiKey, cfg.projectId, cfg.debugLogging)
    }

    // Records who the current user is for subsequent calls. No network.
    fun identify(userId: String, displayName: String) {
        user = TKUser(userId, displayName)
    }

    // Test seam: same wiring as init() but without an Android Context (unit tests have no looper/context).
    internal fun initForTest(apiKey: String, projectId: String, baseUrl: String) {
        config = TKConfig(apiKey, projectId, baseUrl, debugLogging = false)
        api = RetrofitProvider.create(baseUrl, apiKey, projectId, debugLogging = false)
    }

    // Test seam: clears all state so each test starts from a clean, uninitialized SDK.
    internal fun resetForTest() {
        config = null
        api = null
        user = null
        resultDispatcher = { r -> r.run() }
    }

    // ---------- public API (spec §3) ----------

    // Creates a tournament from a portal template; the caller auto-joins as creator.
    fun createTournament(templateId: String, name: String, callback: TKCallback<Tournament>) {
        val u = requireReady(callback) ?: return
        call(callback) { it.createTournament(CreateTournamentBody(templateId, name, u.userId, u.displayName)) }
    }

    // Joins a tournament by code; returns the caller's Participant entry (spec §3 shape).
    fun joinTournament(joinCode: String, callback: TKCallback<Participant>) {
        val u = requireReady(callback) ?: return
        callMapped(callback, { it.joinTournament(JoinBody(joinCode, u.userId, u.displayName)) }) { tournament ->
            // The server returns the whole tournament; hand back just this user's participant row.
            tournament.participants.firstOrNull { p -> p.userId == u.userId }
                ?: throw IllegalStateException("joined tournament is missing this user")
        }
    }

    // Starts a tournament (creator only): the server draws the matches.
    fun startTournament(tournamentId: String, callback: TKCallback<Tournament>) {
        val u = requireReady(callback) ?: return
        call(callback) { it.startTournament(tournamentId, StartBody(u.userId)) }
    }

    // Reports a match result; returns the reported Match from the updated tournament view.
    fun reportResult(tournamentId: String, matchId: String, score: TKScore, callback: TKCallback<Match>) {
        val u = requireReady(callback) ?: return
        callMapped(callback, { it.reportResult(ReportBody(tournamentId, matchId, u.userId, score)) }) { view ->
            view.matches.firstOrNull { m -> m.id == matchId }
                ?: throw IllegalStateException("reported match not found in response")
        }
    }

    // Confirms a previously reported result (the other player); returns the confirmed Match.
    fun confirmResult(tournamentId: String, matchId: String, callback: TKCallback<Match>) {
        val u = requireReady(callback) ?: return
        callMapped(callback, { it.confirmResult(ConfirmBody(tournamentId, matchId, u.userId)) }) { view ->
            view.matches.firstOrNull { m -> m.id == matchId }
                ?: throw IllegalStateException("confirmed match not found in response")
        }
    }

    // Fetches the current tournament state.
    fun getTournament(tournamentId: String, callback: TKCallback<Tournament>) {
        if (requireInitialized(callback) == null) return
        callMapped(callback, { it.getTournament(tournamentId) }) { view -> view.tournament }
    }

    // Fetches the standings table, sorted by the engine's tiebreaker order.
    fun getStandings(tournamentId: String, callback: TKCallback<List<Standing>>) {
        if (requireInitialized(callback) == null) return
        call(callback) { it.getStandings(tournamentId) }
    }

    // Fetches the identified user's cumulative ELO rating.
    fun getUserRating(callback: TKCallback<Int>) {
        val u = requireReady(callback) ?: return
        callMapped(callback, { it.getUserRating(u.userId) }) { dto -> dto.rating }
    }

    // ---------- internals: guards, threading, error mapping ----------

    // Returns the config or fails the callback with TK_NOT_INITIALIZED (no network).
    private fun requireInitialized(callback: TKCallback<*>): TKConfig? {
        val cfg = config
        if (cfg == null) {
            deliverError(callback, TKError(TKErrorCode.TK_NOT_INITIALIZED, "call TournamentKit.init() first"))
            return null
        }
        return cfg
    }

    // Returns the current user, or fails the callback if init() or identify() was skipped.
    private fun requireReady(callback: TKCallback<*>): TKUser? {
        if (requireInitialized(callback) == null) return null
        val u = user
        if (u == null) {
            deliverError(callback, TKError(TKErrorCode.TK_NOT_INITIALIZED, "call TournamentKit.identify() first"))
            return null
        }
        return u
    }

    // Runs a call whose HTTP body is already the public type and delivers it directly.
    private fun <T> call(callback: TKCallback<T>, request: suspend (ApiService) -> Response<T>) {
        callMapped(callback, request) { it }
    }

    // Runs a call, maps the (success) body to the public type, and delivers the result/error.
    private fun <Body, Out> callMapped(
        callback: TKCallback<Out>,
        request: suspend (ApiService) -> Response<Body>,
        map: (Body) -> Out
    ) {
        val service = api ?: run {
            deliverError(callback, TKError(TKErrorCode.TK_NOT_INITIALIZED, "call TournamentKit.init() first"))
            return
        }
        scope.launch {
            try {
                val response = request(service)
                if (response.isSuccessful) {
                    val body = response.body() ?: throw IllegalStateException("empty response body")
                    val result = map(body)
                    deliverSuccess(callback, result)
                } else {
                    deliverError(callback, errorFromResponse(response))
                }
            } catch (t: Throwable) {
                deliverError(callback, errorFromException(t))
            }
        }
    }

    // Delivers a success on the main thread.
    private fun <T> deliverSuccess(callback: TKCallback<T>, result: T) {
        resultDispatcher.invoke(Runnable { callback.onSuccess(result) })
    }

    // Delivers an error on the main thread.
    private fun deliverError(callback: TKCallback<*>, error: TKError) {
        resultDispatcher.invoke(Runnable { callback.onError(error) })
    }
}
