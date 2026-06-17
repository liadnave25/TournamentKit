package com.tournamentkit.server.service

import com.google.cloud.firestore.Firestore
import com.tournamentkit.server.data.AuditRepository
import com.tournamentkit.server.data.ProjectRepository
import com.tournamentkit.server.data.TournamentRepository
import com.tournamentkit.server.engine.TKException
import com.tournamentkit.shared.Match
import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.Participant
import com.tournamentkit.shared.Scoring
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.Template
import com.tournamentkit.shared.TemplateType
import com.tournamentkit.shared.TKErrorCode
import com.tournamentkit.shared.Tournament
import com.tournamentkit.shared.TournamentStatus
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Tests PortalService.deleteTournament against in-memory repository fakes (no real Firestore):
// ownership/existence enforcement, the audit-before-delete ordering, and that all owned data is removed.
class PortalDeleteTournamentTest {

    // The repo constructors only store the Firestore handle (delete logic is overridden), so a no-op
    // proxy satisfies the type without any connection.
    private val noDb = Proxy.newProxyInstance(
        Firestore::class.java.classLoader, arrayOf(Firestore::class.java)
    ) { _, _, _ -> null } as Firestore

    private val rules = Template("tmpl", TemplateType.KNOCKOUT, Scoring(3, 1, 0), 8)

    // A standing row with only the fields a test cares about (the rest default to 0).
    private fun standing(userId: String, points: Int = 0) =
        Standing(userId, played = 0, won = 0, drawn = 0, lost = 0, pointsFor = 0, pointsAgainst = 0, points = points)

    private fun tournament(id: String, projectId: String, type: TemplateType = TemplateType.KNOCKOUT) =
        Tournament(
            id = id, projectId = projectId, templateId = "tmpl", name = "Night",
            joinCode = "ABC234", status = TournamentStatus.ACTIVE,
            participants = listOf(Participant("u1", "Alice", null, null), Participant("u2", "Bob", null, null)),
            rules = rules.copy(type = type), createdAt = 1L, startedAt = null
        )

    // In-memory ProjectRepository: only ownerUid matters here.
    private inner class FakeProjects(private val owners: Map<String, String?>) : ProjectRepository(noDb) {
        override fun ownerUid(projectId: String): String? = owners[projectId]
    }

    // In-memory TournamentRepository tracking what exists and recording deletes.
    private inner class FakeTournaments(
        private val tournaments: MutableMap<String, Tournament>,
        private val matches: MutableMap<String, MutableList<Match>> = mutableMapOf(),
        private val standings: MutableMap<String, MutableList<Standing>> = mutableMapOf()
    ) : TournamentRepository(noDb) {
        val deleted = mutableListOf<String>()
        override fun get(tournamentId: String): Tournament? = tournaments[tournamentId]
        override fun getMatches(tournamentId: String): List<Match> = matches[tournamentId].orEmpty()
        override fun getStandings(tournamentId: String): List<Standing> = standings[tournamentId].orEmpty()
        // Mirror the real batched delete: remove the tournament doc AND its sub-collections together.
        override fun delete(tournamentId: String) {
            deleted += tournamentId
            tournaments.remove(tournamentId)
            matches.remove(tournamentId)
            standings.remove(tournamentId)
        }
    }

    // In-memory AuditRepository capturing project-level entries (where the delete record must land).
    private inner class FakeAudit : AuditRepository(noDb) {
        val projectEntries = mutableListOf<Pair<String, Map<String, Any?>>>()
        override fun appendProject(projectId: String, entry: Map<String, Any?>) {
            projectEntries += projectId to entry
        }
    }

    // Builds a PortalService with the given fakes; reports/tournamentService are unused by delete.
    private fun service(projects: ProjectRepository, tournaments: TournamentRepository, audit: AuditRepository): PortalService =
        PortalService(
            projects = projects,
            tournaments = tournaments,
            audit = audit,
            reports = ReportService(noDb),
            tournamentService = TournamentService(projects, tournaments)
        )

    @Test
    fun delete_removes_tournament_and_its_matches_and_standings() {
        val t = tournament("t1", "p1")
        val tournaments = FakeTournaments(
            tournaments = mutableMapOf("t1" to t),
            matches = mutableMapOf("t1" to mutableListOf(
                Match("r1-s0", 1, 0, "u1", "u2", null, MatchStatus.PENDING, "r2-s0")
            )),
            standings = mutableMapOf("t1" to mutableListOf(standing("u1"), standing("u2")))
        )
        val audit = FakeAudit()
        service(FakeProjects(mapOf("p1" to "owner")), tournaments, audit).deleteTournament("p1", "t1", "owner")

        // The tournament (and with it its participants field) plus its sub-collections are gone.
        assertTrue("tournament should be deleted", tournaments.deleted.contains("t1"))
        assertNull(tournaments.get("t1"))
        assertTrue(tournaments.getMatches("t1").isEmpty())
        assertTrue(tournaments.getStandings("t1").isEmpty())
    }

    @Test
    fun delete_writes_project_audit_entry_before_deleting() {
        val t = tournament("t1", "p1")
        val tournaments = FakeTournaments(mutableMapOf("t1" to t))
        val audit = FakeAudit()
        service(FakeProjects(mapOf("p1" to "owner")), tournaments, audit).deleteTournament("p1", "t1", "admin-9")

        // The DELETE_TOURNAMENT record lives on the PROJECT log (survives the tournament) with the admin + tid.
        assertEquals(1, audit.projectEntries.size)
        val (pid, entry) = audit.projectEntries.single()
        assertEquals("p1", pid)
        assertEquals("DELETE_TOURNAMENT", entry["action"])
        assertEquals("t1", entry["tournamentId"])
        assertEquals("admin-9", entry["adminUid"])
        assertTrue(entry["timestamp"] is Long)
        // It was deleted too.
        assertTrue(tournaments.deleted.contains("t1"))
    }

    @Test
    fun delete_missing_tournament_is_404_and_writes_no_audit() {
        val tournaments = FakeTournaments(mutableMapOf())   // empty
        val audit = FakeAudit()
        val svc = service(FakeProjects(mapOf("p1" to "owner")), tournaments, audit)
        try {
            svc.deleteTournament("p1", "ghost", "owner")
            fail("expected TK_TOURNAMENT_NOT_FOUND")
        } catch (e: TKException) {
            assertEquals(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, e.code)
        }
        // Nothing was logged or deleted on a 404.
        assertTrue(audit.projectEntries.isEmpty())
        assertTrue(tournaments.deleted.isEmpty())
    }

    @Test
    fun delete_tournament_in_another_project_is_404() {
        // The tournament exists but belongs to p2; asking under p1 must 404 (not leak across projects).
        val t = tournament("t1", "p2")
        val tournaments = FakeTournaments(mutableMapOf("t1" to t))
        val audit = FakeAudit()
        val svc = service(FakeProjects(mapOf("p1" to "owner")), tournaments, audit)
        try {
            svc.deleteTournament("p1", "t1", "owner")
            fail("expected TK_TOURNAMENT_NOT_FOUND")
        } catch (e: TKException) {
            assertEquals(TKErrorCode.TK_TOURNAMENT_NOT_FOUND, e.code)
        }
        assertFalse(tournaments.deleted.contains("t1"))
    }

    @Test
    fun delete_tally_tournament_with_participants_and_standings_deletes_cleanly() {
        // TALLY owns participants + standings but NO matches; delete must not assume matches exist.
        val t = tournament("tally1", "p1", type = TemplateType.TALLY)
        val tournaments = FakeTournaments(
            tournaments = mutableMapOf("tally1" to t),
            matches = mutableMapOf(),   // no matches at all
            standings = mutableMapOf("tally1" to mutableListOf(standing("u1", points = 7), standing("u2", points = 3)))
        )
        val audit = FakeAudit()
        service(FakeProjects(mapOf("p1" to "owner")), tournaments, audit).deleteTournament("p1", "tally1", "owner")

        assertNull(tournaments.get("tally1"))
        assertTrue(tournaments.getStandings("tally1").isEmpty())
        assertEquals(1, audit.projectEntries.size)
    }
}
