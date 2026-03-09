ALTER TABLE ingest_task
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at DATETIME NULL,
    ADD COLUMN last_error LONGTEXT NULL;

CREATE INDEX idx_ingest_task_status_next_retry ON ingest_task(status, next_retry_at);

CREATE TABLE IF NOT EXISTS ingest_task_transition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(36) NOT NULL,
    from_status VARCHAR(40),
    to_status VARCHAR(40) NOT NULL,
    attempt_count INT,
    message TEXT,
    created_at DATETIME NOT NULL,
    INDEX idx_ingest_task_transition_task_id (task_id),
    CONSTRAINT fk_ingest_task_transition_task FOREIGN KEY (task_id) REFERENCES ingest_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

