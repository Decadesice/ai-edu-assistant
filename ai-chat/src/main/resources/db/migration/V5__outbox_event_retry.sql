ALTER TABLE outbox_event
    ADD COLUMN next_retry_at DATETIME NULL;

CREATE INDEX idx_outbox_event_status_next_retry_created ON outbox_event(status, next_retry_at, created_at);

