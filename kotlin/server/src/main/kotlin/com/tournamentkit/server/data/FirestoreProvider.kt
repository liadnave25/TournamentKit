package com.tournamentkit.server.data

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import com.tournamentkit.server.config.Env
import java.io.FileInputStream

// Builds and holds the single Firestore client, in either emulator or real-credentials mode.
object FirestoreProvider {

    // Lazily initialized once; reused for the whole process.
    val firestore: Firestore by lazy { connect() }

    // Builds Firestore in one of three modes: emulator, local service-account JSON, or Cloud Run ADC.
    private fun connect(): Firestore {
        val options = when {
            // 1) EMULATOR: FIRESTORE_EMULATOR_HOST set -> no real credentials; any project id works.
            Env.firestoreEmulatorHost != null ->
                FirebaseOptions.builder()
                    .setProjectId(Env.emulatorProject)
                    .setCredentials(emulatorCredentials())
                    .build()

            // 2) SERVICE ACCOUNT: GOOGLE_APPLICATION_CREDENTIALS points at a JSON key file (local prod testing).
            Env.googleCredentials != null ->
                FirebaseOptions.builder()
                    .setCredentials(FileInputStream(Env.googleCredentials!!).use { GoogleCredentials.fromStream(it) })
                    .apply { Env.gcpProject?.let { setProjectId(it) } }
                    .build()

            // 3) ADC (Cloud Run): built-in identity, no JSON. Let the SDK discover the project unless we
            //    were given one explicitly — forcing a wrong project id would break Firestore access.
            else ->
                FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .apply { Env.gcpProject?.let { setProjectId(it) } }
                    .build()
        }

        // Initialize the default FirebaseApp only once.
        if (FirebaseApp.getApps().isEmpty()) FirebaseApp.initializeApp(options)
        return FirestoreClient.getFirestore()
    }

    // The Admin SDK still wants *some* credentials object even against the emulator; this is a no-op token.
    private fun emulatorCredentials(): GoogleCredentials =
        GoogleCredentials.create(com.google.auth.oauth2.AccessToken("owner", null))
}
