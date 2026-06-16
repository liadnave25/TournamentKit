package com.tournamentkit.sdk

import com.tournamentkit.shared.Match
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
                     "maxParticipants": 8, "requireConfirmation": false, "reportTimeoutHours": 48 },
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
    fun reportResult_returns_the_reported_match() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tournamentViewJson()))
        val (m, err) = await<Match> { TournamentKit.reportResult("t1", "r1-s0", TKScore(2, 1), it) }
        assertNull(err)
        assertEquals("r1-s0", m!!.id)
        assertEquals(2, m.score!!.home)
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
}
