package com.tournamentkit.server.config

// Thin accessors for the environment variables the server reads. One place for all of them.
object Env {
    // Set by the Firebase emulator (e.g. "localhost:8080"); when present we run in emulator mode.
    val firestoreEmulatorHost: String? get() = System.getenv("FIRESTORE_EMULATOR_HOST")

    // Explicit project id, if provided. GCP_PROJECT is ours; GOOGLE_CLOUD_PROJECT is set by Cloud Run.
    // Null on Cloud Run when neither is set — then the ADC path lets the SDK discover the project itself.
    val gcpProject: String? get() = System.getenv("GCP_PROJECT") ?: System.getenv("GOOGLE_CLOUD_PROJECT")

    // Project id for emulator mode, where the emulator just needs SOME non-empty id.
    val emulatorProject: String get() = gcpProject ?: "tournamentkit-local"

    // Path to a service-account JSON for local real Firestore. Null on Cloud Run (built-in identity / ADC).
    val googleCredentials: String? get() = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")

    // TCP port for the Ktor server (Cloud Run injects PORT). Defaults to 8080.
    val port: Int get() = System.getenv("PORT")?.toIntOrNull() ?: 8080

    // When "true", the /dev/seed route is enabled. Never set this in production.
    val devMode: Boolean get() = System.getenv("DEV_MODE") == "true"

    // Browser origins allowed by CORS, comma-separated (e.g. "https://portal.example.com").
    // Defaults to the local portal dev origins so dev works with no config; set CORS_ORIGINS in prod.
    val corsOrigins: List<String>
        get() = (System.getenv("CORS_ORIGINS") ?: "http://localhost:5173,http://127.0.0.1:5173")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    // Max /v1 requests allowed per API key (or IP) per minute. Defaults to 120; configurable in prod.
    val rateLimitPerMin: Int get() = System.getenv("RATE_LIMIT_PER_MIN")?.toIntOrNull() ?: 120
}
