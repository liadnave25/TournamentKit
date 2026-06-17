// :server — Ktor application. The `application` plugin gives us :server:run; shadow builds the fat jar.
// (We deliberately do NOT use io.ktor.plugin: it bundles an older shadow that conflicts with com.gradleup.shadow.)
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    alias(libs.plugins.shadow)
}

application {
    // Entry point Ktor launches.
    mainClass.set("com.tournamentkit.server.ApplicationKt")
}

// Fat jar settings: one predictable file (server-all.jar) the Docker runtime stage runs with `java -jar`.
tasks.shadowJar {
    archiveBaseName.set("server")
    archiveClassifier.set("all")
    archiveVersion.set("")
    // Merge service-provider files so Firebase/Netty/SLF4J providers survive the merge.
    mergeServiceFiles()
}

dependencies {
    // Shared data models (single source of truth).
    implementation(project(":shared"))

    // Ktor server runtime + JSON content negotiation.
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.logback.classic)

    // Firebase Admin SDK — server-side Firestore access (used by future game logic).
    implementation(libs.firebase.admin)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.testhost)
}

// Use the JUnit 4 runner for :server tests.
tasks.test {
    useJUnit()
}
