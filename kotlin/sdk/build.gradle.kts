// :sdk — Android library (AAR). Public TournamentKit API + Retrofit/OkHttp network layer
// + OPTIONAL Jetpack Compose UI components (com.tournamentkit.sdk.ui) that a developer may ignore.
// Published as an Android library via JitPack (see jitpack.yml + the publishing block below).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

// Publication coordinates. On JitPack the version is the git tag; locally it defaults via -PsdkVersion.
// JitPack multi-module group for github.com/liadnave25/TournamentKit.
group = "com.github.liadnave25.TournamentKit"
version = (findProperty("sdkVersion") as String?) ?: "0.1.0"

android {
    namespace = "com.tournamentkit.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        // Needed only for the optional ui/ components; a pure-API consumer never references them.
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Publish the `release` variant only, and include a sources jar in the publication.
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        // Let unit tests run on the JVM; un-stubbed Android calls return defaults instead of crashing.
        unitTests.isReturnDefaultValues = true
    }
}

// Wire the Android `release` component into a Maven publication (AAR + POM + sources).
// afterEvaluate is required: the AGP `release` software component only exists after evaluation.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "tournamentkit"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

dependencies {
    // Shared data models — the public API speaks these types, never DTOs.
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)

    // Coroutines run the network work; the -android artifact provides the main dispatcher.
    implementation(libs.kotlinx.coroutines.android)

    // Retrofit/OkHttp networking with a kotlinx-serialization JSON converter.
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Optional Compose UI components (com.tournamentkit.sdk.ui). Present in the single AAR but unused
    // by pure-API consumers — R8 strips the unreferenced ui/ classes from a release app that never imports them.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Tests: MockWebServer (offline, deterministic) + coroutines test helpers.
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
