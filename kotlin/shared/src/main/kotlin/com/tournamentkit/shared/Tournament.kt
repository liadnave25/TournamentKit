package com.tournamentkit.shared

import kotlinx.serialization.Serializable

// The lifecycle stage of a tournament.
@Serializable
enum class TournamentStatus {
    REGISTRATION,
    ACTIVE,
    FINISHED,
    FROZEN
}

// A single tournament: its participants and a snapshot of the rules it started with.
@Serializable
data class Tournament(
    val id: String,
    val projectId: String,
    val templateId: String,
    val name: String,
    val joinCode: String,
    val status: TournamentStatus,
    val participants: List<Participant> = emptyList(),
    val rules: Template,
    val createdAt: Long,
    val startedAt: Long? = null
)
