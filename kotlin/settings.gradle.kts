// Root Gradle settings: names the build and registers the modules.
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

// :shared is needed by everything (server, sdk).
include(":shared")

val serverOnly = settings.providers.gradleProperty("serverOnly").isPresent
val sdkOnly = settings.providers.gradleProperty("sdkOnly").isPresent

// -PsdkOnly: publish/build only the Android library (e.g. on JitPack) — include :shared + :sdk, skip
//   :server (heavy server deps).
// -PserverOnly: the Docker image build — include :server, skip the Android modules (no Android SDK).
// Default (no flag): the full build with every module.
if (!sdkOnly) {
    include(":server")
}
if (!serverOnly) {
    include(":sdk")
}
