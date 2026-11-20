-- V1: Schema for VoteProcessor
-- Shares the same PostgreSQL database as poll-service (same schema, different migrations)

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Users table (read-only from VoteProcessor perspective)
CREATE TABLE IF NOT EXISTS users (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   TEXT        NOT NULL,
    role            VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Polls table (read-only from VoteProcessor perspective)
CREATE TABLE IF NOT EXISTS polls (
    poll_id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question        VARCHAR(500) NOT NULL,
    options         JSONB       NOT NULL,
    start_time      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    end_time        TIMESTAMPTZ NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Votes table — VoteProcessor writes here with ON CONFLICT DO NOTHING
CREATE TABLE IF NOT EXISTS votes (
    vote_id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id         UUID        NOT NULL REFERENCES polls(poll_id) ON DELETE CASCADE,
    voter_id        UUID        NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    option_id       VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_votes_poll_voter UNIQUE (poll_id, voter_id)
);

CREATE INDEX IF NOT EXISTS idx_votes_poll_id      ON votes (poll_id);
CREATE INDEX IF NOT EXISTS idx_votes_poll_option  ON votes (poll_id, option_id);

-- ProcessedEvent: idempotency table for Kafka consumer
-- eventId is the UUID from VoteCastEvent — once stored, the event will not be reprocessed
CREATE TABLE IF NOT EXISTS processed_events (
    event_id        UUID        PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
