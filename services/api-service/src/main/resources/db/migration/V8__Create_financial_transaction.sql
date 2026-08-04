CREATE TABLE financial_transaction (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL,

    category_id BIGINT,

    transaction_type VARCHAR(20) NOT NULL,

    amount NUMERIC(19, 2) NOT NULL,

    merchant VARCHAR(200),

    description VARCHAR(500),

    transaction_date DATE NOT NULL,

    processing_status VARCHAR(20) NOT NULL
        DEFAULT 'PENDING',

    source VARCHAR(20) NOT NULL
        DEFAULT 'MANUAL',

    manual_category_override BOOLEAN NOT NULL
        DEFAULT FALSE,

    version BIGINT NOT NULL
        DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_financial_transaction_account
        FOREIGN KEY (account_id)
        REFERENCES financial_account(id),

    CONSTRAINT fk_financial_transaction_category
        FOREIGN KEY (category_id)
        REFERENCES category(id),

    CONSTRAINT chk_financial_transaction_type
        CHECK (
            transaction_type IN (
                'INCOME',
                'EXPENSE'
            )
        ),

    CONSTRAINT chk_financial_transaction_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_financial_transaction_status
        CHECK (
            processing_status IN (
                'PENDING',
                'PROCESSED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_financial_transaction_source
        CHECK (
            source IN (
                'MANUAL',
                'IMPORT'
            )
        ),

    CONSTRAINT chk_financial_transaction_merchant_not_blank
        CHECK (
            merchant IS NULL
            OR BTRIM(merchant) <> ''
        ),

    CONSTRAINT chk_financial_transaction_description_not_blank
        CHECK (
            description IS NULL
            OR BTRIM(description) <> ''
        )
);

CREATE INDEX idx_financial_transaction_account_date
    ON financial_transaction (
        account_id,
        transaction_date DESC,
        id DESC
    );

CREATE INDEX idx_financial_transaction_category_date
    ON financial_transaction (
        category_id,
        transaction_date DESC,
        id DESC
    );

CREATE INDEX idx_financial_transaction_status
    ON financial_transaction (
        processing_status,
        id
    );