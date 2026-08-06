CREATE TABLE processed_message (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_processed_message_consumer_event
        UNIQUE (consumer_name, event_id),

    CONSTRAINT chk_processed_message_consumer_name_not_blank
        CHECK (BTRIM(consumer_name) <> ''),

    CONSTRAINT chk_processed_message_event_type_not_blank
        CHECK (BTRIM(event_type) <> ''),

    CONSTRAINT chk_processed_message_event_version_positive
        CHECK (event_version > 0)
);