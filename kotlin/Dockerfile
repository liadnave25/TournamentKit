# Multi-stage build for the TournamentKit Ktor server.
# Build context is the kotlin/ folder (where the Gradle build lives).
#
#   docker build -t tournamentkit-server -f Dockerfile .
#   docker run -p 8080:8080 tournamentkit-server
#
# -PserverOnly excludes the Android modules (:sdk, :demo-app) so no Android SDK is needed here.

# ---------- Stage 1: build the fat jar with a full JDK 17 ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy the Gradle wrapper + build configuration first so dependency downloads cache across source changes.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared/build.gradle.kts ./shared/
COPY server/build.gradle.kts ./server/

# Warm the dependency cache (server-only graph). Ignore failure: there are no sources yet to assemble.
RUN chmod +x gradlew && ./gradlew -PserverOnly :server:dependencies --no-daemon || true

# Now copy the actual sources for the two server-side modules and build the fat jar.
COPY shared/src ./shared/src
COPY server/src ./server/src
RUN ./gradlew -PserverOnly :server:shadowJar --no-daemon

# ---------- Stage 2: slim runtime with a JRE only ----------
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Run as a non-root user.
RUN useradd --system --uid 10001 appuser
USER appuser

# Copy just the built fat jar from the build stage.
COPY --from=build /app/server/build/libs/server-all.jar app.jar

# Cloud Run sends traffic to $PORT (defaults to 8080); the app reads it via Env.port.
ENV PORT=8080
EXPOSE 8080

# Launch the server.
ENTRYPOINT ["java", "-jar", "app.jar"]
