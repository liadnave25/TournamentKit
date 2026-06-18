package com.tournamentkit.sdk

import com.tournamentkit.shared.TKError

// Result callback for every public SDK call, using the spec §3 interface form so it reads naturally from Java and Kotlin.
interface TKCallback<T> {
    // Called on the main thread with the successful result.
    fun onSuccess(result: T)

    // Called on the main thread with a typed error (code + human-readable message).
    fun onError(error: TKError)
}
