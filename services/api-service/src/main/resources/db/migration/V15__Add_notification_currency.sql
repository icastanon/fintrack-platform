ALTER TABLE notification
    ADD COLUMN currency VARCHAR(3);

UPDATE notification AS notification
SET currency = fintrack_user.currency
FROM fintrack_user
WHERE notification.user_id = fintrack_user.id;

ALTER TABLE notification
    ALTER COLUMN currency SET NOT NULL;

ALTER TABLE notification
    ADD CONSTRAINT chk_notification_currency
        CHECK (currency IN ('USD', 'EUR', 'GBP', 'CAD', 'AUD'));