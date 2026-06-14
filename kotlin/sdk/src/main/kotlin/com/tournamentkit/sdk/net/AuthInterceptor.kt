package com.tournamentkit.sdk.net

import okhttp3.Interceptor
import okhttp3.Response

// Adds the API key + project id headers to every request, so individual calls never repeat auth.
internal class AuthInterceptor(
    private val apiKey: String,
    private val projectId: String
) : Interceptor {

    // Attaches the two TournamentKit auth headers and proceeds with the request.
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-TK-API-KEY", apiKey)
            .header("X-TK-PROJECT-ID", projectId)
            .build()
        return chain.proceed(request)
    }
}
