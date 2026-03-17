# Multi-stage Dockerfile for LucentFlow Spring Boot Application
# Stage 1: Build Stage - Maven compilation and packaging
FROM maven:3.9.6-eclipse-temurin-21 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml files first to leverage Docker layer caching
# Copy root pom.xml
COPY pom.xml .
# Copy module pom.xml files in order of dependency
COPY lucentflow-common/pom.xml ./lucentflow-common/
COPY lucentflow-chain-sdk/pom.xml ./lucentflow-chain-sdk/
COPY lucentflow-indexer/pom.xml ./lucentflow-indexer/
COPY lucentflow-analyzer/pom.xml ./lucentflow-analyzer/
COPY lucentflow-api/pom.xml ./lucentflow-api/

# Download dependencies and cache them (layer optimization)
RUN mvn dependency:go-offline -B

# Copy source code
COPY lucentflow-common/src ./lucentflow-common/src/
COPY lucentflow-chain-sdk/src ./lucentflow-chain-sdk/src/
COPY lucentflow-indexer/src ./lucentflow-indexer/src/
COPY lucentflow-analyzer/src ./lucentflow-analyzer/src/
COPY lucentflow-api/src ./lucentflow-api/src/

# Build the application - skip tests, Git checks, and Antrun tasks for Docker build
RUN mvn clean package -DskipTests -Dmaven.antrun.skip=true -Dgit.skip=true

# Stage 2: Runtime Stage - Lightweight JRE
FROM eclipse-temurin:21-jre-jammy

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create application directory
WORKDIR /app

# Create non-root user for security compliance
RUN groupadd -r lucentflow && useradd -r -g lucentflow lucentflow

# Copy the built JAR from build stage
COPY --from=build /app/lucentflow-api/target/lucentflow-api-1.0.0-RELEASE.jar app.jar

# Set JVM options optimized for L2 monitoring and Virtual Threads
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx2g"

# Switch to non-root user
USER lucentflow

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Application entrypoint
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
