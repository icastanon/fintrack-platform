ALTER TABLE transaction_import
    DROP CONSTRAINT chk_transaction_import_status;

ALTER TABLE transaction_import
    ADD CONSTRAINT chk_transaction_import_status
        CHECK (
            status IN (
                'QUEUED',
                'RUNNING',
                'COMPLETED',
                'FAILED',
                'ABANDONED'
            )
        );