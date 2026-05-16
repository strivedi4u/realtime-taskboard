# Build stage
FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# Copy gradle files first for better caching
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application
RUN gradle bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install curl for health check.
# Acquire flags below tolerate up to ~1 day of host/container clock skew so
# the build does not break on machines whose system clock is slightly off
# relative to the Ubuntu mirror's InRelease Date field.
RUN apt-get update \
      -o Acquire::Check-Valid-Until=false \
      -o Acquire::Max-FutureTime=86400 \
    && apt-get install -y curl \
    && rm -rf /var/lib/apt/lists/*

# Add non-root user for security
RUN addgroup --system spring && adduser --system --ingroup spring spring

# Copy the built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown -R spring:spring /app

USER spring

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
