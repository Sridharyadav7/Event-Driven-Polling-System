# Multi-stage build for vote-processor
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml ../pom.xml
COPY vote-processor/pom.xml ./pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f ../pom.xml dependency:go-offline -pl vote-processor -am -q 2>/dev/null || true

COPY vote-processor/src ./src
COPY ../pom.xml ../pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f ../pom.xml clean package -pl vote-processor -am -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/target/vote-processor-*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
