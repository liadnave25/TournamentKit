package com.tournamentkit.sdk.net

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

// One place that builds the configured ApiService for a given base URL and credentials.
internal object RetrofitProvider {

    // Tolerant JSON: ignore fields the SDK doesn't model so server additions never break clients.
    private val json = Json { ignoreUnknownKeys = true }

    // Creates an ApiService that auto-sends auth headers and (optionally) logs requests.
    fun create(baseUrl: String, apiKey: String, projectId: String, debugLogging: Boolean): ApiService {
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(apiKey, projectId))

        // Body logging only when the developer opts in — never on by default (avoids leaking data to logcat).
        if (debugLogging) {
            clientBuilder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            )
        }

        // Retrofit wants a trailing slash on the base URL; add one if the developer omitted it.
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(clientBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
