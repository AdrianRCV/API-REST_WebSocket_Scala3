CREATE TABLE events (
    id          UUID PRIMARY KEY,
    event_type  TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source      TEXT NOT NULL,
    payload     JSONB NOT NULL
);

CREATE INDEX idx_events_event_type  ON events (event_type);
CREATE INDEX idx_events_occurred_at ON events (occurred_at);
