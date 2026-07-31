CREATE TABLE financial_account (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'USD',

    opening_balance NUMERIC(19, 2) NOT NULL,
    current_balance NUMERIC(19, 2) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_financial_account_user
        FOREIGN KEY (user_id)
        REFERENCES fintrack_user(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_financial_account_type
        CHECK (
            account_type IN (
                'CHECKING',
                'SAVINGS',
                'CREDIT_CARD',
                'CASH',
                'INVESTMENT'
            )
        ),

    CONSTRAINT chk_financial_account_status
        CHECK (status IN ('ACTIVE', 'CLOSED')),

    CONSTRAINT chk_financial_account_currency
        CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX uq_financial_account_user_name_ci
    ON financial_account(user_id, LOWER(account_name));

CREATE INDEX idx_financial_account_user_status
    ON financial_account(user_id, status);