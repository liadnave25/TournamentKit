package com.tournamentkit.sdk

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.Participant
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.TKError
import com.tournamentkit.shared.TKErrorCode
import com.tournamentkit.shared.TKScore
import com.tournamentkit.shared.Tournament
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TournamentKitTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        TournamentKit.resetForTest()
        // Point the SDK at the mock server; deliver callbacks inline (resetForTest already did).
        TournamentKit.initForTest(apiKey = "test-key", projectId = "test-project", baseUrl = server.url("/").toString())
        TournamentKit.identify("u1", "Alice")
    }

    @After
    fun tearDown() {
        server.shutdown()
        TournamentKit.resetForTest()
    }

    // ---------- JSON fixtures matching the server contract ----------

    private fun tournamentJson(id: String = "t1", status: String = "REGISTRATION") = """
        { "id": "$id", "projectId": "test-project", "templateId": "tmpl", "name": "Night",
          "joinCode": "ABC234", "status": "$status",
          "participants": [ { "userId": "u1", "displayName": "Alice" } ],
          "rules": { "id": "tmpl", "type": "KNOCKOUT", "scoring": {"win":3,"draw":1,"loss":0},
                     "maxParticipants": 8 },
          "createdAt": 1700000000000 }
    """.trimIndent()

    private fun tournamentViewJson() = """
        { "tournament": ${tournamentJson(status = "ACTIVE")},
          "matches": [ { "id": "r1-s0", "round": 1, "slot": 0, "homeId": "u1", "awayId": "u2",
                         "score": {"home":2,"away":1}, "status": "CONFIRMED", "nextMatchId": "r2-s0" } ],
          "standings": [ { "userId": "u1", "played": 1, "won": 1, "drawn": 0, "lost": 0,
                           "pointsFor": 2, "pointsAgainst": 1, "points": 3 } ] }
    """.trimIndent()

    // Runs [block] and waits for the single callback it triggers; returns (result, error).
    private fun <T> await(block: (TKCallback<T>) -> Unit): Pair<T?, TKError?> {
        val latch = CountDownLatch(1)
        var result: T? = null
        var error: TKError? = null
        block(object : TKCallback<T> {
            override fun onSuccess(r: T) { result = r; latch.countDown() }
            override fun onError(e: TKError) { error = e; latch.countDown() }
        })
        assertTrue("callback did not fire in time", latch.await(5, TimeUnit.SECONDS))
        return result to error
    }

    // ---------- success cases ----------

    @Test
    fun createTournament_returns_tournament() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tournamentJson()))
        val (t, err) = await<Tournament> { TournamentKit.createTournament("tmpl", "Night", it) }
        assertNull(err)
        assertEquals("t1", t!!.id)
        assertEquals("ABC234", t.joinCode)
    }

    @Test
    fun joinTournament_returns_the_callers_participant() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tournamentJson()))
        val (p, err) = await<Participant> { TournamentKit.joinTournament("ABC234", it) }
        assertNull(err)
        assertEquals("u1", p!!.userId)
        assertEquals("Alice", p.displayName)
    }

    @Test
    fun reportResult_returns_a_confirmed_match_immediately() {
        // Single-writer model: one reportResult finalizes the match (CONFIRMED) — there is no confirm step.
        server.enqueue(MockResponse().setResponseCode(200).setBody(tournamentViewJson()))
        val (m, err) = await<Match> { TournamentKit.reportResult("t1", "r1-s0", TKScore(2, 1), it) }
        assertNull(err)
        assertEquals("r1-s0", m!!.id)
        assertEquals(2, m.score!!.home)
        assertEquals(MatchStatus.CONFIRMED, m.status)
        // The same call requested the report endpoint (no /confirm round-trip).
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/v1/matches/report", recorded.path)
    }

    @Test
    fun getStandings_returns_the_table() {
        val body = """[ { "userId": "u1", "played": 1, "won": 1, "drawn": 0, "lost": 0,
                         "pointsFor": 2, "pointsAgainst": 1, "points": 3 } ]"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val (rows, err) = await<List<Standing>> { TournamentKit.getStandings("t1", it) }
        assertNull(err)
        assertEquals(1, rows!!.size)
        assertEquals("u1", rows[0].userId)
    }

    @Test
    fun addScore_returns_the_updated_standing() {
        // TALLY add returns just the user's Standing row (points is the running total).
        val body = """{ "userId": "u9", "played": 0, "won": 0, "drawn": 0, "lost": 0,
                        "pointsFor": 0, "pointsAgainst": 0, "points": 7 }"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val (s, err) = await<Standing> { TournamentKit.addScore("t1", "u9", "Eve", 7, it) }
        assertNull(err)
        assertEquals("u9", s!!.userId)
        assertEquals(7, s.points)
    }

    @Test
    fun addScore_on_non_tally_maps_to_not_supported_for_type() {
        val body = """{ "code": "TK_NOT_SUPPORTED_FOR_TYPE", "message": "tally/add only applies to TALLY tournaments" }"""
        server.enqueue(MockResponse().setResponseCode(400).setBody(body))
        val (_, err) = await<Standing> { TournamentKit.addScore("t1", "u9", "Eve", 7, it) }
        assertEquals(TKErrorCode.TK_NOT_SUPPORTED_FOR_TYPE, err!!.code)
    }

    // ---------- auth headers ----------

    @Test
    fun requests_carry_auth_headers() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tournamentJson()))
        await<Tournament> { TournamentKit.createTournament("tmpl", "Night", it) }
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("test-key", recorded.getHeader("X-TK-API-KEY"))
        assertEquals("test-project", recorded.getHeader("X-TK-PROJECT-ID"))
    }

    // ---------- error mapping ----------

    @Test
    fun http_401_maps_to_not_authenticated() {
        server.enqueue(MockResponse().setResponseCode(401))
        val (_, err) = await<Tournament> { TournamentKit.getTournament("t1", it) }
        assertEquals(TKErrorCode.TK_NOT_AUTHENTICATED, err!!.code)
    }

    @Test
    fun http_404_maps_to_tournament_not_found() {
        server.enqueue(MockResponse().setResponseCode(404))
        val (_, err) = await<Tournament> { TournamentKit.getTournament("missing", it) }
        assertEquals(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, err!!.code)
    }

    @Test
    fun http_409_uses_the_server_error_body_code() {
        // Server sends a typed TKError body on conflicts — the SDK must surface that exact code.
        val body = """{ "code": "TK_ALREADY_JOINED", "message": "u1 already joined" }"""
        server.enqueue(MockResponse().setResponseCode(409).setBody(body))
        val (_, err) = await<Participant> { TournamentKit.joinTournament("ABC234", it) }
        assertEquals(TKErrorCode.TK_ALREADY_JOINED, err!!.code)
        assertEquals("u1 already joined", err.message)
    }

    @Test
    fun http_500_maps_to_unknown() {
        server.enqueue(MockResponse().setResponseCode(500))
        val (_, err) = await<Tournament> { TournamentKit.getTournament("t1", it) }
        assertEquals(TKErrorCode.TK_UNKNOWN, err!!.code)
    }

    // ---------- guards ----------

    @Test
    fun call_before_initialize_returns_not_initialized_with_no_network() {
        TournamentKit.resetForTest()   // back to uninitialized
        val (t, err) = await<Tournament> { TournamentKit.createTournament("tmpl", "Night", it) }
        assertNull(t)
        assertEquals(TKErrorCode.TK_NOT_INITIALIZED, err!!.code)
        // No request should have reached the server.
        assertEquals(0, server.requestCount)
    }

    @Test
    fun call_needing_user_before_identify_returns_not_initialized() {
        TournamentKit.resetForTest()
        TournamentKit.initForTest("k", "p", server.url("/").toString())   // initialized but no identify()
        val (_, err) = await<Tournament> { TournamentKit.createTournament("tmpl", "Night", it) }
        assertEquals(TKErrorCode.TK_NOT_INITIALIZED, err!!.code)
        assertEquals(0, server.requestCount)
    }

    // ---------- getOrCreateTournament: per-key persistence (no collision) ----------

    @Test
    fun getOrCreate_two_different_keys_persist_two_different_ids_no_collision() {
        val prefs = FakePrefs()
        TournamentKit.setPrefsForTest(prefs)

        // First key creates "t-weekly" and saves it under its own storage key.
        server.enqueue(MockResponse().setResponseCode(200).setBody(tournamentJson(id = "t-weekly")))
        val (a, errA) = await<Tournament> { TournamentKit.getOrCreateTournament("weekly_tokens", "tmpl", "Weekly", it) }
        assertNull(errA)
        assertEquals("t-weekly", a!!.id)

        // Second, DIFFERENT key creates "t-league" — it must NOT see the first key's saved id.
        server.enqueue(MockResponse().setResponseCode(200).setBody(tournamentJson(id = "t-league")))
        val (b, errB) = await<Tournament> { TournamentKit.getOrCreateTournament("football_league", "tmpl", "League", it) }
        assertNull(errB)
        assertEquals("t-league", b!!.id)

        // Both ids are persisted independently under their own per-key entries — no shared slot.
        assertEquals("t-weekly", prefs.getString("tk_tournament_id_weekly_tokens", null))
        assertEquals("t-league", prefs.getString("tk_tournament_id_football_league", null))
        // Two creates happened (one per key); the second did not reuse the first.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun getOrCreate_same_key_second_call_returns_saved_id_without_creating() {
        val prefs = FakePrefs()
        TournamentKit.setPrefsForTest(prefs)

        // First call creates and persists the tournament id for this key.
        server.enqueue(MockResponse().setResponseCode(200).setBody(tournamentJson(id = "t-saved")))
        val (first, _) = await<Tournament> { TournamentKit.getOrCreateTournament("weekly_tokens", "tmpl", "Weekly", it) }
        assertEquals("t-saved", first!!.id)

        // Second call with the SAME key must FETCH the saved id (getTournament), not create a new one.
        server.enqueue(MockResponse().setResponseCode(200).setBody(tournamentViewJson()))
        val (second, err) = await<Tournament> { TournamentKit.getOrCreateTournament("weekly_tokens", "tmpl", "Weekly", it) }
        assertNull(err)

        // The second call succeeded by fetching the saved tournament, not by creating a new one.
        assertNotNull(second)
        // Exactly two requests total: one create, one fetch. The second hit the GET endpoint for the saved id.
        assertEquals(2, server.requestCount)
        server.takeRequest(5, TimeUnit.SECONDS)   // drain the create request
        val fetch = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/v1/tournaments/t-saved", fetch.path)
    }

    @Test
    fun getOrCreate_blank_externalKey_fails_with_invalid_argument_no_network() {
        TournamentKit.setPrefsForTest(FakePrefs())
        val (t, err) = await<Tournament> { TournamentKit.getOrCreateTournament("   ", "tmpl", "Weekly", it) }
        assertNull(t)
        assertEquals(TKErrorCode.TK_INVALID_ARGUMENT, err!!.code)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun clearSession_clears_only_that_key() {
        val prefs = FakePrefs()
        prefs.edit().putString("tk_tournament_id_weekly_tokens", "t-weekly").apply()
        prefs.edit().putString("tk_tournament_id_football_league", "t-league").apply()
        TournamentKit.setPrefsForTest(prefs)

        TournamentKit.clearSession("weekly_tokens")
        assertNull(prefs.getString("tk_tournament_id_weekly_tokens", null))
        assertEquals("t-league", prefs.getString("tk_tournament_id_football_league", null))
    }

    @Test
    fun clearAllSessions_wipes_every_saved_id() {
        val prefs = FakePrefs()
        prefs.edit().putString("tk_tournament_id_weekly_tokens", "t-weekly").apply()
        prefs.edit().putString("tk_tournament_id_football_league", "t-league").apply()
        prefs.edit().putString("some_other_pref", "keep-me").apply()
        TournamentKit.setPrefsForTest(prefs)

        TournamentKit.clearAllSessions()
        assertNull(prefs.getString("tk_tournament_id_weekly_tokens", null))
        assertNull(prefs.getString("tk_tournament_id_football_league", null))
        // Unrelated prefs are untouched.
        assertEquals("keep-me", prefs.getString("some_other_pref", null))
    }
}

// In-memory SharedPreferences for JVM unit tests (no Android Context). Implements only what the
// SDK uses: getString, getAll, edit() with putString/remove/apply.
private class FakePrefs : android.content.SharedPreferences {
    private val map = mutableMapOf<String, String?>()

    override fun getString(key: String, defValue: String?): String? = if (map.containsKey(key)) map[key] else defValue
    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): android.content.SharedPreferences.Editor = FakeEditor()

    // Unused by the SDK; return harmless defaults.
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int): Int = defValue
    override fun getLong(key: String, defValue: Long): Long = defValue
    override fun getFloat(key: String, defValue: Float): Float = defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
    override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class FakeEditor : android.content.SharedPreferences.Editor {
        private val pending = mutableMapOf<String, String?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor { pending[key] = value; return this }
        override fun remove(key: String): android.content.SharedPreferences.Editor { removals.add(key); return this }
        override fun clear(): android.content.SharedPreferences.Editor { clearAll = true; return this }
        override fun apply() { commit() }
        override fun commit(): Boolean {
            if (clearAll) map.clear()
            removals.forEach { map.remove(it) }
            pending.forEach { (k, v) -> map[k] = v }
            return true
        }

        // Unused by the SDK.
        override fun putStringSet(key: String, values: MutableSet<String>?): android.content.SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor = this
        override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor = this
        override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor = this
        override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor = this
    }
}
