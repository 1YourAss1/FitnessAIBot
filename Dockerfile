# ============================================================================
# Multi-stage Dockerfile для FitnessAIBot.
#
# Использование:
#   docker build -t fitnessaibot:latest .
#   docker run --rm --env-file .env fitnessaibot:latest
#
# Или через docker compose:
#   docker compose up --build
# ============================================================================

# ---------- Этап 1: сборка ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package

# ---------- Этап 2: рантайм ----------
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN groupadd --system --gid 1001 spring \
    && useradd --system --uid 1001 --gid spring --home-dir /app spring

COPY --from=build /workspace/target/FitnessAIBot-*.jar /app/app.jar
RUN chown spring:spring /app/app.jar

USER spring

HEALTHCHECK NONE

ENTRYPOINT ["java", \
    "--enable-native-access=ALL-UNNAMED", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]
