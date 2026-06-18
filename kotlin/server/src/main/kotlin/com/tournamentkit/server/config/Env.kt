package com.tournamentkit.server.config

// One place for thin accessors to every environment variable the server reads.
object Env {
    // Set by the Firebase emulator (e.g. "localhost:8080"); when present we run in emulator mode.
    val firestoreEmulatorHost: String? get() = System.getenv("FIRESTORE_EMULATOR_HOST")

    // Explicit project id from GCP_PROJECT (ours) or GOOGLE_CLOUD_PROJECT (Cloud Run); null lets ADC discover it.
    val gcpProject: String? get() = System.getenv("GCP_PROJECT") ?: System.getenv("GOOGLE_CLOUD_PROJECT")

    // Project id for emulator mode, where the emulator just needs SOME non-empty id.
    val emulatorProject: String get() = gcpProject ?: "tournamentkit-local"

    // Path to a service-account JSON for local real Firestore; null on Cloud Run (built-in identity / ADC).
    val googleCredentials: String? get() = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")

    // TCP port for the Ktor server (Cloud Run injects PORT), defaulting to 8080.
    val port: Int get() = System.getenv("PORT")?.toIntOrNull() ?: 8080

    // When "true", enables the /dev/seed route; never set this in production.
    val devMode: Boolean get() = System.getenv("DEV_MODE") == "true"

    // Comma-separated CORS origins, defaulting to the local portal dev origins (set CORS_ORIGINS in prod).
    val corsOrigins: List<String>
        get() = (System.getenv("CORS_ORIGINS") ?: "http://localhost:5173,http://127.0.0.1:5173")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    // Max /v1 requests per API key (or IP) per minute, defaulting to 120.
    val rateLimitPerMin: Int get() = System.getenv("RATE_LIMIT_PER_MIN")?.toIntOrNull() ?: 120
}
