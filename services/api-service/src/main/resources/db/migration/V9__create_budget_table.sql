CREATE TABLE budget (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    budget_month DATE NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    warning_threshold_percentage INTEGER NOT NULL DEFAULT 80,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_budget_user
        FOREIGN KEY (user_id) REFERENCES fintrack_user(id),

    CONSTRAINT fk_budget_category
        FOREIGN KEY (category_id) REFERENCES category(id),

    CONSTRAINT chk_budget_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_budget_warning_threshold
        CHECK (warning_threshold_percentage BETWEEN 1 AND 99),

    CONSTRAINT chk_budget_month_first_day
        CHECK (EXTRACT(DAY FROM budget_month) = 1)
);

CREATE UNIQUE INDEX uq_budget_user_category_month ON budget (user_id, category_id, budget_month);

CREATE INDEX idx_budget_user_month ON budget (user_id, budget_month DESC, id DESC);