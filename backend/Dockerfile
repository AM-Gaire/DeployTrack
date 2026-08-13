# Multi-stage build. Compiling needs Maven, a full JDK, and a few hundred MB of
# downloaded dependencies; running needs a JRE and one jar. Splitting the two
# keeps all of that out of the shipped image -- roughly 250MB instead of 800MB,
# and build tooling that is not present cannot be exploited in production.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# pom.xml is copied and dependencies resolved before the source is added. Docker
# caches each layer, so editing a Java file reuses the dependency layer instead
# of re-downloading the world. Copying everything at once would invalidate that
# cache on every single code change.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Tests are skipped here on purpose. They need a Docker daemon for
# Testcontainers, which is not available inside a build container, and CI runs
# them separately in Phase 9 -- which is the right place for a gate anyway.
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# A container process that is root is root on the host if it escapes. Running
# as an unprivileged user costs nothing and removes that class of problem.
RUN addgroup -S deploytrack && adduser -S -G deploytrack deploytrack

COPY --from=build /build/target/*.jar app.jar
RUN chown -R deploytrack:deploytrack /app
USER deploytrack

EXPOSE 8080

# Container memory is not the same as JVM heap. Without this the JVM sizes its
# heap from the host's total RAM, ignores the container limit, and gets killed
# by the OOM reaper under load. MaxRAMPercentage makes it respect the cgroup.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
