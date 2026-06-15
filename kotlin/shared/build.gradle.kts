// :shared — pure Kotlin/JVM library holding the data models shared by server and SDK.
// Published alongside :sdk so the SDK's transitive dependency on it resolves for JitPack consumers.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

// Same coordinates family + version as :sdk, so the SDK's POM reference to :shared resolves on JitPack.
// JitPack multi-module group for github.com/liadnave25/TournamentKit.
group = "com.github.liadnave25.TournamentKit"
version = (findProperty("sdkVersion") as String?) ?: "0.1.0"

dependencies {
    // `api` (not implementation) so :shared's models are visible on the SDK's public API surface.
    api(libs.kotlinx.serialization.json)
}

// Publish :shared as a normal Maven jar (component "java") with a sources jar.
java {
    withSourcesJar()
}
publishing {
    publications {
        register<MavenPublication>("maven") {
            artifactId = "shared"
            from(components["java"])
        }
    }
}
