# --- Build stage: compile and package with the Maven wrapper ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Cache dependencies first: copy only what resolves the build, then go offline.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

# Now the sources; package skipping tests (CI runs tests separately).
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# --- Runtime stage: slim JRE with just the fat jar ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# wget backs the compose healthcheck; add it, then run as a non-root user.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 hireflow
USER hireflow

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
