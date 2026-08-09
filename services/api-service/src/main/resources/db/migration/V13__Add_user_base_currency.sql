ALTER TABLE fintrack_user
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD';

UPDATE financial_account
SET currency = 'USD';

ALTER TABLE fintrack_user
    ALTER COLUMN currency DROP DEFAULT;

ALTER TABLE fintrack_user
    ADD CONSTRAINT chk_fintrack_user_currency
        CHECK (currency IN ('USD', 'EUR', 'GBP', 'CAD', 'AUD'));