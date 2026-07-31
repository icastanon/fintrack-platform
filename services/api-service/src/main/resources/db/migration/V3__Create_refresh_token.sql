CREATE TABLE refresh_token (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    token_hash VARCHAR(64) NOT NULL UNIQUE,

    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,

    revoked_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
        REFERENCES fintrack_user(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_user_id
    ON refresh_token(user_id);

CREATE INDEX idx_refresh_token_expires_at
    ON refresh_token(expires_at);