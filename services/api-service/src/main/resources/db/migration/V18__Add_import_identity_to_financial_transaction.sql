ALTER TABLE financial_transaction
    ADD COLUMN import_id BIGINT,
    ADD COLUMN import_row_number INTEGER,

    ADD CONSTRAINT fk_financial_transaction_import
        FOREIGN KEY (import_id)
        REFERENCES transaction_import(id),

    ADD CONSTRAINT chk_financial_transaction_import_identity
        CHECK (
            (
                source = 'IMPORT'
                AND import_id IS NOT NULL
                AND import_row_number IS NOT NULL
            )
            OR
            (
                source = 'MANUAL'
                AND import_id IS NULL
                AND import_row_number IS NULL
            )
        ),

    ADD CONSTRAINT chk_financial_transaction_import_row_number
        CHECK (
            import_row_number IS NULL
            OR import_row_number >= 2
        ),

    ADD CONSTRAINT uq_financial_transaction_import_row
        UNIQUE (
            import_id,
            import_row_number
        );