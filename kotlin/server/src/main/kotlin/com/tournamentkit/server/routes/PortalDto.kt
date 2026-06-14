package com.tournamentkit.server.routes

import com.tournamentkit.shared.Scoring
import com.tournamentkit.shared.TemplateType
import com.tournamentkit.shared.TKScore
import kotlinx.serialization.Serializable

// ---------- template requests ----------

// Body for creating/updating a template (id is taken from the path on update).
@Serializable
data class TemplateRequest(
    val id: String = "",
    val type: TemplateType,
    val scoring: Scoring,
    val maxParticipants: Int,
    val requireConfirmation: Boolean,
    val reportTimeoutHours: Int
)

// ---------- tournament admin ----------

// Lightweight tournament row for the management list view.
@Serializable
data class TournamentSummary(
    val id: String,
    val name: String,
    val status: String,
    val participantCount: Int,
    val createdAt: Long
)

// Body for an admin result override (reason is required and audited).
@Serializable
data class OverrideRequest(
    val score: TKScore,
    val reason: String
)

// ---------- projects ----------

// Body for creating a project (the owner comes from the verified token, never the body).
@Serializable
data class CreateProjectRequest(val name: String)

// Response to creating a project — includes the first API key, shown ONCE and never retrievable again.
@Serializable
data class CreateProjectResponse(val id: String, val name: String, val apiKey: String)

// ---------- keys ----------

// Response to a key rotation — the plaintext key is shown ONCE and never retrievable again.
@Serializable
data class RotateKeyResponse(val apiKey: String)

// ---------- analytics ----------

// Dashboard numbers for a project.
@Serializable
data class AnalyticsView(
    val tournamentsTotal: Int,
    val tournamentsByStatus: Map<String, Int>,
    val participantsTotal: Int,
    val matchesConfirmed: Int,
    val lastTournamentCreatedAt: Long?
)
