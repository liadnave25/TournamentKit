package com.tournamentkit.shared

import kotlinx.serialization.Serializable

// The machine-readable error codes the SDK and server can return (spec §3).
@Serializable
enum class TKErrorCode {
    TK_NOT_INITIALIZED,
    TK_INVALID_API_KEY,
    TK_NETWORK_ERROR,
    TK_TOURNAMENT_FULL,
    TK_MATCH_ALREADY_REPORTED,
    TK_NOT_PARTICIPANT,
    TK_TOURNAMENT_LOCKED,
    TK_INVALID_SCORE,
    TK_NOT_AUTHENTICATED,
    TK_TOURNAMENT_NOT_FOUND,
    TK_ALREADY_JOINED,
    TK_UNKNOWN,
    TK_FORBIDDEN,
    TK_TOURNAMENT_FROZEN,
    TK_RATE_LIMITED
}

// A typed error: a code plus a human-readable message.
@Serializable
data class TKError(
    val code: TKErrorCode,
    val message: String
)
