// Root Gradle settings: names the build and registers the four modules.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tournamentkit"

// Always part of the server build.
include(":shared")
include(":server")

// The Android modules need the Android SDK, which a server-only build (e.g. the Docker image) lacks.
// Pass -PserverOnly to skip them: ./gradlew -PserverOnly :server:shadowJar
if (!settings.providers.gradleProperty("serverOnly").isPresent) {
    include(":sdk")
    include(":demo-app")
}
