package com.tournamentkit.shared

import kotlinx.serialization.Serializable

// One player taking part in a tournament.
@Serializable
data class Participant(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val seed: Int? = null
)
