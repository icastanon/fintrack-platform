CREATE TABLE transaction_import (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL,

    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,

    source_object_key VARCHAR(1024) NOT NULL,
    rejected_object_key VARCHAR(1024),

    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',

    total_rows BIGINT,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    successful_rows BIGINT NOT NULL DEFAULT 0,
    skipped_rows BIGINT NOT NULL DEFAULT 0,
    failed_rows BIGINT NOT NULL DEFAULT 0,

    failure_summary VARCHAR(1000),

    version BIGINT NOT NULL DEFAULT 0,

    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transaction_import_account
        FOREIGN KEY (account_id)
        REFERENCES financial_account(id),

    CONSTRAINT uq_transaction_import_source_object
        UNIQUE (source_object_key),

    CONSTRAINT chk_transaction_import_original_file_name
        CHECK (BTRIM(original_file_name) <> ''),

    CONSTRAINT chk_transaction_import_content_type
        CHECK (BTRIM(content_type) <> ''),

    CONSTRAINT chk_transaction_import_file_size
        CHECK (file_size_bytes > 0),

    CONSTRAINT chk_transaction_import_status
        CHECK (
            status IN (
                'QUEUED',
                'RUNNING',
                'COMPLETED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_transaction_import_total_rows
        CHECK (total_rows IS NULL OR total_rows >= 0),

    CONSTRAINT chk_transaction_import_row_counts
        CHECK (
            processed_rows >= 0
            AND successful_rows >= 0
            AND skipped_rows >= 0
            AND failed_rows >= 0
            AND processed_rows =
                successful_rows + skipped_rows + failed_rows
        ),

    CONSTRAINT chk_transaction_import_processed_not_above_total
        CHECK (
            total_rows IS NULL
            OR processed_rows <= total_rows
        ),

    CONSTRAINT chk_transaction_import_failure_summary
        CHECK (
            failure_summary IS NULL
            OR BTRIM(failure_summary) <> ''
        )
);

CREATE INDEX idx_transaction_import_account_created
    ON transaction_import (
        account_id,
        created_at DESC,
        id DESC
    );

CREATE INDEX idx_transaction_import_status_updated
    ON transaction_import (
        status,
        updated_at,
        id
    );