# Event-Driven Polling System

A production-grade real-time polling/voting platform demonstrating modern Java backend skills:
**Spring Boot 3 · Kafka · Redis · PostgreSQL · JWT · Testcontainers · Docker · Kubernetes**

---

## Architecture

```
┌─────────────┐    JWT     ┌──────────────────────────────────────────┐
│   Client    │──────────▶│            Poll Service (8080)            │
│ (Postman /  │           │  ┌────────────┐  ┌───────────────────┐   │
│  cURL)      │           │  │ AuthCtrl   │  │   PollController  │   │
└─────────────┘           │  └────────────┘  └────────┬──────────┘   │
                          │                           │               │
                          │  ┌────────────────────────▼──────────┐   │
                          │  │          PollService               │   │
                          │  │  - Create/list/get polls           │   │
                          │  │  - Validate vote & publish event   │   │
                          │  └──┬─────────────────────┬──────────┘   │
                          │     │                     │               │
                          │  ┌──▼──────┐    ┌────────▼──────────┐   │
                          │  │PostgreSQL│    │   KafkaTemplate   │   │
                          │  └─────────┘    └────────┬──────────┘   │
                          └───────────────────────────┼──────────────┘
                                                      │  vote_cast topic
                          ┌───────────────────────────▼──────────────┐
                          │         Vote Processor (8082)             │
                          │  ┌─────────────────────────────────────┐ │
                          │  │  @KafkaListener (vote_cast)          │ │
                          │  │  1. Check processed_events (dedup)   │ │
                          │  │  2. INSERT INTO votes ON CONFLICT DO │ │
                          │  │     NOTHING (unique constraint)      │ │
                          │  │  3. HINCRBY poll:{id}:results        │ │
                          │  │  4. Mark event as processed          │ │
                          │  └──┬───────────────────┬──────────────┘ │
                          │     │                   │                 │
                          │  ┌──▼──────┐    ┌──────▼──────┐         │
                          │  │PostgreSQL│    │    Redis    │         │
                          │  └─────────┘    └─────────────┘         │
                          └──────────────────────────────────────────┘
```

### Key Design Decisions

| Concern | Solution |
|---------|----------|
| One vote per user | `UNIQUE(poll_id, voter_id)` DB constraint |
| Kafka redelivery | `processed_events` table (eventId idempotency) |
| Live tallies | Redis `HINCRBY poll:{id}:results {option} 1` |
| Auth | Stateless JWT (HS256, 24h expiry) |
| Fault tolerance | Resilience4j circuit breakers on Redis/DB |
| Observability | Actuator + Micrometer + Prometheus + JSON logs |

---

## Prerequisites

- **Java 17+**
- **Maven 3.9+**
- **Docker & Docker Compose** (for local dev)
- **Git**

---

## Quick Start

### Option A: Docker Compose (recommended)

```bash
cd polling-system

# Start all services (Postgres, Kafka, Redis, poll-api, vote-processor)
docker-compose up -d

# Watch logs
docker-compose logs -f poll-api vote-processor

# Stop everything
docker-compose down -v
```

Services:
- Poll API:        http://localhost:8080
- Vote Processor:  http://localhost:8082
- Health check:    http://localhost:8080/actuator/health
- Prometheus:      http://localhost:8080/actuator/prometheus

### Option B: Run from source (requires local Postgres/Kafka/Redis)

```bash
cd polling-system

# Build everything
mvn clean package -DskipTests

# Start poll-service (dev profile seeds sample data)
java -jar poll-service/target/poll-service-*.jar \
  --spring.profiles.active=dev \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/polldb \
  --spring.datasource.username=polluser \
  --spring.datasource.password=pollpassword \
  --spring.kafka.bootstrap-servers=localhost:9092 \
  --spring.data.redis.host=localhost

# Start vote-processor
java -jar vote-processor/target/vote-processor-*.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/polldb \
  --spring.datasource.username=polluser \
  --spring.datasource.password=pollpassword \
  --spring.kafka.bootstrap-servers=localhost:9092 \
  --spring.data.redis.host=localhost
```

---

## Demo Walkthrough (cURL)

### 1. Register a user

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@demo.com","password":"password123"}' | jq .
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": "uuid-here",
  "email": "alice@demo.com"
}
```

### 2. Save the token

```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."
```

### 3. Create a poll

```bash
curl -s -X POST http://localhost:8080/api/polls \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Best programming language?",
    "options": ["Java", "Python", "Go", "Rust"],
    "endTime": "2027-01-01T00:00:00Z"
  }' | jq .
```

**Response:**
```json
{
  "pollId": "poll-uuid",
  "question": "Best programming language?",
  "options": [
    {"optionId": "opt-uuid-1", "text": "Java"},
    {"optionId": "opt-uuid-2", "text": "Python"},
    ...
  ],
  "status": "ACTIVE"
}
```

### 4. Save the pollId and an optionId

```bash
POLL_ID="poll-uuid"
OPTION_ID="opt-uuid-1"
```

### 5. Cast a vote

```bash
curl -s -X POST http://localhost:8080/api/polls/$POLL_ID/vote \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"optionId\": \"$OPTION_ID\"}"
# → 200 OK
```

### 6. Try voting again (duplicate — should return 409)

```bash
curl -s -X POST http://localhost:8080/api/polls/$POLL_ID/vote \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"optionId\": \"$OPTION_ID\"}"
# → 409 Conflict
```

### 7. View live results (from Redis)

```bash
curl -s http://localhost:8080/api/polls/$POLL_ID | jq '.voteCounts'
```

**Response:**
```json
{
  "opt-uuid-1": 1,
  "opt-uuid-2": 0,
  "opt-uuid-3": 0,
  "opt-uuid-4": 0
}
```

### 8. Check health & metrics

```bash
curl -s http://localhost:8080/actuator/health | jq .
curl -s http://localhost:8080/actuator/prometheus | grep "votes_"
```

---

## API Reference

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | No | Register (returns JWT) |
| POST | `/api/auth/login` | No | Login (returns JWT) |
| POST | `/api/polls` | Yes | Create poll |
| GET | `/api/polls` | No | List polls (paginated) |
| GET | `/api/polls/{id}` | No | Get poll + live counts |
| POST | `/api/polls/{id}/vote` | Yes | Cast vote |
| GET | `/api/polls/{id}/results` | No | Get results |
| GET | `/actuator/health` | No | Health check |
| GET | `/actuator/prometheus` | No | Prometheus metrics |

---

## Running Tests

```bash
cd polling-system

# All tests (requires Docker for Testcontainers)
mvn clean verify

# Unit tests only (no Docker needed)
mvn test -pl poll-service -Dtest="**/*Test" -DfailIfNoTests=false
mvn test -pl vote-processor -Dtest="**/*Test" -DfailIfNoTests=false

# Integration tests only
mvn test -pl poll-service -Dtest="**/*IntegrationTest"
mvn test -pl vote-processor -Dtest="**/*IntegrationTest"

# Coverage report (target/site/jacoco/index.html)
mvn clean verify
open poll-service/target/site/jacoco/index.html
```

---

## Dev Profile Sample Data

When running with `--spring.profiles.active=dev` (or `APP_SEED_ENABLED=true`), the following data is seeded:

| Field | Value |
|-------|-------|
| User 1 | alice@example.com / password123 |
| User 2 | bob@example.com / password123 |
| Poll 1 | "What is your favorite programming language?" (7 days) |
| Poll 2 | "Best pet for a software engineer?" (3 days) |

---

## Environment Variables

### poll-service

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/polldb` | PostgreSQL URL |
| `SPRING_DATASOURCE_USERNAME` | `polluser` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `pollpassword` | DB password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | *(required in prod)* | HS256 key (≥32 chars) |
| `JWT_EXPIRATION_MS` | `86400000` | Token TTL (ms) |

### vote-processor

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | Same as above | PostgreSQL URL |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `REDIS_HOST` | `localhost` | Redis host |
| `KAFKA_CONSUMER_GROUP` | `vote-processor-group` | Kafka group ID |

---

## Kubernetes Deployment

```bash
# Apply all manifests
kubectl apply -k k8s/base/

# Check pods
kubectl get pods -n polling-system

# Port-forward for local access
kubectl port-forward -n polling-system svc/poll-service 8080:80
```

Before deploying, update `k8s/base/secrets.yaml` with real base64-encoded values:
```bash
echo -n "your-real-jwt-secret-32-chars-min" | base64
echo -n "your-db-password" | base64
```

---

## Observability

### Prometheus + Grafana

The poll-service exposes metrics at `/actuator/prometheus`.

Custom metrics:
- `polls.created.total` — total polls created
- `votes.submitted.total` — votes submitted to Kafka
- `votes.processed.total` — votes processed by VoteProcessor
- `votes.skipped.duplicate.total` — Kafka duplicate events skipped
- `votes.redis.fallback.total` — Redis circuit breaker activations

### Structured JSON Logging

In non-dev profiles, all logs are JSON with MDC fields:
```json
{
  "timestamp": "2026-11-20T10:30:00Z",
  "level": "INFO",
  "logger": "com.polling.service.PollService",
  "message": "Published VoteCastEvent ...",
  "traceId": "a1b2c3d4",
  "userId": "user-uuid",
  "service": "poll-service"
}
```

---

## Project Structure

```
polling-system/
├── pom.xml                          # Parent POM (multi-module)
├── docker-compose.yml               # Local dev stack
├── poll-service/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/polling/
│       │   ├── config/              # Security, Kafka, Redis, DataSeeder
│       │   ├── controller/          # AuthController, PollController
│       │   ├── dto/                 # Request/Response DTOs
│       │   ├── entity/              # User, Poll, Vote
│       │   ├── event/               # VoteCastEvent
│       │   ├── exception/           # Custom exceptions + GlobalExceptionHandler
│       │   ├── repository/          # Spring Data JPA repos
│       │   ├── security/            # JwtTokenProvider, JwtAuthenticationFilter
│       │   └── service/             # AuthService, PollService, ResultsService
│       └── test/
│           ├── service/             # Unit tests (Mockito)
│           ├── security/            # JWT tests
│           └── integration/         # Testcontainers integration tests
├── vote-processor/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/voteprocessor/
│       │   ├── config/              # Kafka consumer config, Redis
│       │   ├── consumer/            # VoteKafkaConsumer
│       │   ├── entity/              # Vote, ProcessedEvent
│       │   ├── event/               # VoteCastEvent (deserialization)
│       │   ├── repository/          # VoteRepository, ProcessedEventRepository
│       │   └── service/             # VoteProcessingService
│       └── test/
│           ├── service/             # Unit tests
│           └── integration/         # Testcontainers integration tests
├── k8s/
│   └── base/                        # Kubernetes manifests (Kustomize)
└── .github/
    └── workflows/ci.yml             # GitHub Actions CI pipeline
```
