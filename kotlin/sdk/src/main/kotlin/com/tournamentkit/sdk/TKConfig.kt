package com.tournamentkit.sdk

// The live production server. The developer can override the base URL in init() to point at a
// local server, but the common case needs no config — this default just works.
const val DEFAULT_BASE_URL = "https://tournamentkit-server-520238889661.europe-west1.run.app"

// Immutable config captured at init() time: which server to talk to and how to authenticate.
internal data class TKConfig(
    val apiKey: String,
    val projectId: String,
    val baseUrl: String,
    val debugLogging: Boolean
)

// The user identified via identify(); attached to calls that act on behalf of a player.
internal data class TKUser(
    val userId: String,
    val displayName: String
)
