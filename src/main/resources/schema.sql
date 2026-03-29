CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id VARCHAR(255) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3, -- RetryPolicy.MAX_RETRY_COUNT
    next_retry_at TIMESTAMP NOT NULL,
    reference_event_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    CONSTRAINT uk_notification_idempotency UNIQUE (recipient_id, notification_type, reference_event_id)
);

CREATE INDEX IF NOT EXISTS idx_notifications_polling ON notifications (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications (recipient_id);
