CREATE TABLE transaction_import_rejected_row_staging (
    id BIGSERIAL PRIMARY KEY,

    import_id BIGINT NOT NULL,
    row_number INTEGER NOT NULL,

    raw_record TEXT NOT NULL,
    failure_reason VARCHAR(1000) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transaction_import_rejected_row_staging_import
        FOREIGN KEY (import_id)
        REFERENCES transaction_import(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_transaction_import_rejected_row_staging_import_row
        UNIQUE (
            import_id,
            row_number
        ),

    CONSTRAINT chk_transaction_import_rejected_row_staging_row_number
        CHECK (
            row_number >= 2
        ),

    CONSTRAINT chk_transaction_import_rejected_row_staging_failure_reason
        CHECK (
            BTRIM(failure_reason) <> ''
        )
);