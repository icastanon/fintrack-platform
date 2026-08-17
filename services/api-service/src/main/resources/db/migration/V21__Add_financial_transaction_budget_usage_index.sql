CREATE INDEX idx_financial_transaction_budget_usage
    ON financial_transaction (
        account_id,
        category_id,
        transaction_date
    )
    INCLUDE (amount)
    WHERE transaction_type = 'EXPENSE'
      AND processing_status = 'PROCESSED';