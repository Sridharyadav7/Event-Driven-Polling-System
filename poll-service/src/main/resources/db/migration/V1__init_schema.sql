-- V1: Initial schema for the Poll Service
-- PostgreSQL with JSONB for poll options and UUID primary keys

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   TEXT        NOT NULL,
    role            VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- Polls table with JSONB options column
CREATE TABLE IF NOT EXISTS polls (
    poll_id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question        VARCHAR(500) NOT NULL,
    options         JSONB       NOT NULL,
    start_time      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    end_time        TIMESTAMPTZ NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_polls_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT chk_polls_times  CHECK (end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_polls_creator_id ON polls (creator_id);
CREATE INDEX IF NOT EXISTS idx_polls_end_time   ON polls (end_time);
CREATE INDEX IF NOT EXISTS idx_polls_status     ON polls (status);

-- Votes table with unique constraint enforcing one-vote-per-user-per-poll
CREATE TABLE IF NOT EXISTS votes (
    vote_id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id         UUID        NOT NULL REFERENCES polls(poll_id) ON DELETE CASCADE,
    voter_id        UUID        NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    option_id       VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- This UNIQUE constraint is the strongest guard against double-voting
    CONSTRAINT uk_votes_poll_voter UNIQUE (poll_id, voter_id)
);

CREATE INDEX IF NOT EXISTS idx_votes_poll_id      ON votes (poll_id);
CREATE INDEX IF NOT EXISTS idx_votes_poll_option  ON votes (poll_id, option_id);
