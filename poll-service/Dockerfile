# Multi-stage build for poll-service
# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Copy parent pom and module poms first for layer caching
COPY pom.xml ../pom.xml
COPY poll-service/pom.xml ./pom.xml

# Download dependencies (cached if pom unchanged)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f ../pom.xml dependency:go-offline -pl poll-service -am -q 2>/dev/null || true

COPY poll-service/src ./src
COPY ../pom.xml ../pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f ../pom.xml clean package -pl poll-service -am -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/target/poll-service-*.jar app.jar

EXPOSE 8080

# JVM tuning: container-aware memory settings
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
