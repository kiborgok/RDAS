# syntax=docker/dockerfile:1

# ---- Build stage ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache dependencies first (only re-downloads when pom changes). The BuildKit cache
# mount persists ~/.m2 across builds so dependencies are downloaded only once.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Run as a non-root user
RUN groupadd --system rdas && useradd --system --gid rdas --no-create-home rdas

COPY --from=build /workspace/target/rdas-*.jar app.jar
RUN chown -R rdas:rdas /app
USER rdas

EXPOSE 8080

# Container-aware JVM ergonomics; cap heap at 75% of the cgroup limit
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Honour SIGTERM for graceful shutdown (server.shutdown=graceful)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
