# =============================================================================
# Backend Dockerfile — multi-stage Maven build → slim JRE runtime.
# =============================================================================
# Stage 1 ("build"):
#   - Pulls Eclipse Temurin's Maven+JDK image (Java 21).
#   - Copies pom.xml and the project-local settings.xml FIRST so Docker's
#     layer cache can reuse the dependency-download step on every code-only
#     rebuild. Without this trick, every `mvn package` run would re-fetch
#     the entire dependency tree.
#   - Runs `mvn dependency:go-offline` to populate the local repo.
#   - Then copies src/ and runs `mvn package` — the dependency layer is
#     reused unless pom.xml itself changes.
#
# Stage 2 ("runtime"):
#   - Pulls a much smaller JRE-only image (no compiler, no Maven).
#   - Copies the built jar from Stage 1.
#   - Runs as an unprivileged user, exposes the app port.
#
# Result: ~190MB final image, while the build stage (which we throw away)
# is closer to 600MB. Multi-stage is the standard pattern for compiled-
# language services — your runtime image carries only the runtime, none
# of the build tooling.
# =============================================================================

# ----- BUILD STAGE -----------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build

# Maven needs a writable home for its local repo. /root/.m2 is the default;
# we set it explicitly so it's obvious which layer we're caching.
ENV MAVEN_HOME=/usr/share/maven \
    M2_HOME=/root/.m2

# Install Maven (the JDK image doesn't ship with it).
# --no-cache: don't keep the apk index, saves ~3MB.
RUN apk add --no-cache maven

WORKDIR /app

# --- Layer 1: dependencies. Only re-runs when pom.xml or settings.xml changes.
COPY pom.xml settings.xml ./
COPY .mvn ./.mvn

# go-offline pulls every dependency referenced in the POM into the local
# repo. After this layer, building source changes is offline.
# -B: batch mode (no interactive prompts).
# -s settings.xml: use the project-local settings (bypasses corporate Nexus).
RUN mvn -B -s settings.xml dependency:go-offline

# --- Layer 2: source + build. Re-runs whenever any file under src/ changes.
COPY src ./src
RUN mvn -B -s settings.xml -DskipTests package

# ----- RUNTIME STAGE ---------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create a non-root user. Running Java as root inside a container isn't
# strictly dangerous (containers are isolated), but it's good hygiene
# and some platforms enforce it. UID 1000 is the common convention for
# "first non-system user."
RUN addgroup -S app && adduser -S -G app -u 1000 app

WORKDIR /app

# Copy the jar produced in Stage 1. We use a glob so we don't have to
# bump the filename when the version in pom.xml changes.
COPY --from=build --chown=app:app /app/target/*.jar /app/app.jar

USER app

# Spring Boot reads server.port from application.yml; we don't override
# here. EXPOSE is purely documentation — Docker doesn't actually open
# the port unless you `-p` it at run time.
EXPOSE 9090

# Spring Boot 3.x has a "buildpacks" image alternative (./mvnw spring-boot:build-image)
# that produces a more optimized layered image; this hand-rolled Dockerfile
# is more transparent for learning. The trade-off is documented in the
# accompanying .md.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
