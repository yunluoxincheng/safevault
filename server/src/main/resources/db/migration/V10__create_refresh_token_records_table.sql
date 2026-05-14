CREATE TABLE refresh_token_records (
    id              BIGSERIAL PRIMARY KEY,
    jti             VARCHAR(64) NOT NULL UNIQUE,
    family          VARCHAR(64) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    rotated         BOOLEAN NOT NULL DEFAULT FALSE,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rtr_family ON refresh_token_records (family);
CREATE INDEX idx_rtr_user_family ON refresh_token_records (user_id, family);
CREATE INDEX idx_rtr_expires_at ON refresh_token_records (expires_at);
