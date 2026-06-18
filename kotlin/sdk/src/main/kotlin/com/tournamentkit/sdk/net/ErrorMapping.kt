package com.tournamentkit.sdk.net

import com.tournamentkit.shared.TKError
import com.tournamentkit.shared.TKErrorCode
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

// Lenient parser for the server's JSON error bodies (ignores any extra fields).
private val errorJson = Json { ignoreUnknownKeys = true }

// Turns a failed HTTP response into a typed TKError, preferring the server's body and falling back to a status-code mapping.
internal fun errorFromResponse(response: Response<*>): TKError {
    val raw = response.errorBody()?.string()

    // The server returns a JSON TKError on most failures — use it verbatim when present.
    if (!raw.isNullOrBlank()) {
        runCatching { errorJson.decodeFromString<TKError>(raw) }.getOrNull()?.let { return it }
    }

    // No usable body: map the HTTP status to a sensible code.
    val code = when (response.code()) {
        401 -> TKErrorCode.TK_NOT_AUTHENTICATED
        404 -> TKErrorCode.TK_TOURNAMENT_NOT_FOUND
        in 500..599 -> TKErrorCode.TK_UNKNOWN
        else -> TKErrorCode.TK_UNKNOWN
    }
    return TKError(code, "HTTP ${response.code()}")
}

// Turns a transport-level failure (no connection, timeout) into a network TKError.
internal fun errorFromException(t: Throwable): TKError = when (t) {
    is IOException -> TKError(TKErrorCode.TK_NETWORK_ERROR, t.message ?: "network error")
    else -> TKError(TKErrorCode.TK_UNKNOWN, t.message ?: "unexpected error")
}
