# Multi-stage build: compile in a full JDK image, run in a slim JRE image
# This keeps the final image small (~200MB vs ~600MB)

# --- Stage 1: Build ---
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

# Download dependencies first (cached if pom.xml unchanged)
RUN apk add --no-cache maven && \
    mvn dependency:go-offline --no-transfer-progress && \
    mvn package -DskipTests --no-transfer-progress

# --- Stage 2: Run ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S devpulse && adduser -S devpulse -G devpulse
USER devpulse

COPY --from=build /app/target/devpulse-*.jar app.jar

EXPOSE 8080

# Tuned JVM flags for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
