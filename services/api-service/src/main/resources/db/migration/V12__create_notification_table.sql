CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    budget_id BIGINT,

    category_id BIGINT NOT NULL,

    transaction_id BIGINT NOT NULL,

    budget_month DATE NOT NULL,

    notification_type VARCHAR(20) NOT NULL,

    budget_amount NUMERIC(19, 2) NOT NULL,

    spent_amount NUMERIC(19, 2) NOT NULL,

    message VARCHAR(500) NOT NULL,

    read_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id)
        REFERENCES fintrack_user(id),

    CONSTRAINT fk_notification_budget
        FOREIGN KEY (budget_id)
        REFERENCES budget(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_notification_category
        FOREIGN KEY (category_id)
        REFERENCES category(id),

    CONSTRAINT fk_notification_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES financial_transaction(id),

    CONSTRAINT uq_notification_threshold
        UNIQUE (
            user_id,
            category_id,
            budget_month,
            notification_type
        ),

    CONSTRAINT chk_notification_type
        CHECK (
            notification_type IN (
                'WARNING',
                'EXCEEDED'
            )
        ),

    CONSTRAINT chk_notification_budget_month
        CHECK (
            EXTRACT(DAY FROM budget_month) = 1
        ),

    CONSTRAINT chk_notification_budget_amount
        CHECK (budget_amount > 0),

    CONSTRAINT chk_notification_spent_amount
        CHECK (spent_amount >= 0),

    CONSTRAINT chk_notification_message_not_blank
        CHECK (BTRIM(message) <> '')
);

CREATE INDEX idx_notification_user_created
    ON notification (
        user_id,
        created_at DESC,
        id DESC
    );

CREATE INDEX idx_notification_user_unread
    ON notification (
        user_id,
        created_at DESC,
        id DESC
    )
    WHERE read_at IS NULL;