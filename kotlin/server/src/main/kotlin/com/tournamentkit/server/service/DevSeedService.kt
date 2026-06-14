package com.tournamentkit.server.service

import com.tournamentkit.server.auth.ApiKey
import com.tournamentkit.server.data.ProjectRepository
import com.tournamentkit.server.routes.SeedResponse
import com.tournamentkit.shared.Scoring
import com.tournamentkit.shared.Template
import com.tournamentkit.shared.TemplateType

// Seeds a known project + templates so the full flow is testable before the portal exists (DEV_MODE only).
class DevSeedService(private val projects: ProjectRepository) {

    // Creates project "dev-project" with API key "dev-key" plus a knockout and a league template.
    // An optional ownerUid lets a portal smoke test claim ownership of the seeded project.
    fun seed(ownerUid: String? = null): SeedResponse {
        val projectId = "dev-project"
        val apiKey = "dev-key"
        projects.upsertProject(projectId, name = "Dev Project", apiKeyHash = ApiKey.sha256(apiKey), ownerUid = ownerUid)

        // Knockout template — decisive results, no confirmation step.
        val knockout = Template(
            id = "tmpl-knockout",
            type = TemplateType.KNOCKOUT,
            scoring = Scoring(win = 3, draw = 1, loss = 0),
            maxParticipants = 16,
            requireConfirmation = false,
            reportTimeoutHours = 48
        )
        // League template — standard 3/1/0 scoring, no confirmation step.
        val league = Template(
            id = "tmpl-league",
            type = TemplateType.LEAGUE,
            scoring = Scoring(win = 3, draw = 1, loss = 0),
            maxParticipants = 16,
            requireConfirmation = false,
            reportTimeoutHours = 48
        )
        projects.putTemplate(projectId, knockout)
        projects.putTemplate(projectId, league)

        return SeedResponse(projectId, apiKey, knockout.id, league.id)
    }
}
