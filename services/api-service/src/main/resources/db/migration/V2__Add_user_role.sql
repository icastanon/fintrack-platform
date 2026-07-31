ALTER TABLE fintrack_user
    ADD COLUMN user_role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE fintrack_user
    ADD CONSTRAINT chk_fintrack_user_role
        CHECK (user_role IN ('USER', 'ADMIN'));