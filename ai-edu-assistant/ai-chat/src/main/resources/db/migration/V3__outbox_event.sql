CREATE TABLE IF NOT EXISTS outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    topic VARCHAR(120) NOT NULL,
    message_key VARCHAR(120) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL,
    created_at DATETIME NOT NULL,
    sent_at DATETIME NULL,
    last_error TEXT NULL,
    INDEX idx_outbox_event_status_created_at (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
