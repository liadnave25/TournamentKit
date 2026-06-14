package com.tournamentkit.server.auth

import com.google.firebase.auth.FirebaseAuth
import com.tournamentkit.server.engine.TKException
import com.tournamentkit.shared.TKErrorCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.util.AttributeKey

// Call attribute holding the authenticated portal user's Firebase uid.
val PORTAL_UID_KEY = AttributeKey<String>("portalUid")

// Convenience accessor: the authenticated portal user id for this request.
val ApplicationCall.portalUid: String get() = attributes[PORTAL_UID_KEY]

// Ktor plugin that authenticates portal requests with a Firebase ID token (Authorization: Bearer <token>).
// Emulator: when FIREBASE_AUTH_EMULATOR_HOST is set, the Admin SDK verifies against the Auth emulator
// (no real Google certificates needed) — mirrors the FirestoreProvider emulator behaviour.
fun portalAuthPlugin() = createRouteScopedPlugin("PortalAuth") {
    onCall { call ->
        // The bearer token is required.
        val header = call.request.headers["Authorization"]
            ?: throw TKException(TKErrorCode.TK_NOT_AUTHENTICATED, "missing Authorization header")
        if (!header.startsWith("Bearer ")) {
            throw TKException(TKErrorCode.TK_NOT_AUTHENTICATED, "Authorization must be a Bearer token")
        }
        val idToken = header.removePrefix("Bearer ").trim()

        // Verify the token with Firebase; an invalid/expired token throws -> 401.
        val uid = try {
            FirebaseAuth.getInstance().verifyIdToken(idToken).uid
        } catch (e: Exception) {
            throw TKException(TKErrorCode.TK_NOT_AUTHENTICATED, "invalid id token")
        }

        // Authenticated: expose the uid to downstream routes.
        call.attributes.put(PORTAL_UID_KEY, uid)
    }
}
