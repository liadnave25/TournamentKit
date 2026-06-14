package com.tournamentkit.sdk

import com.tournamentkit.shared.TKError

// Result callback for every public SDK call. We use the interface form from spec §3 (not a sealed
// result) so the contract matches the spec verbatim and reads naturally from Java as well as Kotlin.
interface TKCallback<T> {
    // Called on the main thread with the successful result.
    fun onSuccess(result: T)

    // Called on the main thread with a typed error (code + human-readable message).
    fun onError(error: TKError)
}
