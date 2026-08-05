CREATE TABLE outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMP WITH TIME ZONE,
    lock_owner VARCHAR(100),
    published_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_outbox_event_event_id UNIQUE (event_id),

    CONSTRAINT chk_outbox_event_aggregate_type_not_blank
        CHECK (BTRIM(aggregate_type) <> ''),

    CONSTRAINT chk_outbox_event_event_type_not_blank
        CHECK (BTRIM(event_type) <> ''),

    CONSTRAINT chk_outbox_event_version_positive
        CHECK (event_version > 0),

    CONSTRAINT chk_outbox_event_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),

    CONSTRAINT chk_outbox_event_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_outbox_event_pending
    ON outbox_event (available_at, id)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_event_processing_recovery
    ON outbox_event (locked_at, id)
    WHERE status = 'PROCESSING';