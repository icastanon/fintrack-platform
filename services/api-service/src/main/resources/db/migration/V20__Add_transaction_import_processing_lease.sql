ALTER TABLE transaction_import
    ADD COLUMN processing_owner VARCHAR(100),
    ADD COLUMN processing_lease_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN processing_fencing_token BIGINT NOT NULL DEFAULT 0,

    ADD CONSTRAINT chk_transaction_import_processing_owner
        CHECK (
            processing_owner IS NULL
            OR BTRIM(processing_owner) <> ''
        ),

    ADD CONSTRAINT chk_transaction_import_processing_lease_pair
        CHECK (
            (
                processing_owner IS NULL
                AND processing_lease_expires_at IS NULL
            )
            OR (
                processing_owner IS NOT NULL
                AND processing_lease_expires_at IS NOT NULL
            )
        ),

    ADD CONSTRAINT chk_transaction_import_processing_fencing_token
        CHECK (processing_fencing_token >= 0);

CREATE INDEX idx_transaction_import_running_lease
    ON transaction_import (
        processing_lease_expires_at,
        id
    )
    WHERE status = 'RUNNING';