-- V2: Add processed_events table (read-only from poll-service perspective)
-- VoteProcessor writes here; poll-service just needs the table to exist for schema validation

CREATE TABLE IF NOT EXISTS processed_events (
    event_id        UUID        PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
