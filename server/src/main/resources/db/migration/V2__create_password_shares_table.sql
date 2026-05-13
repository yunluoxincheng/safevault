-- Create share types enum (idempotent)
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'share_type') THEN
        CREATE TYPE share_type AS ENUM ('DIRECT', 'USER_TO_USER', 'NEARBY');
    END IF;
END $$;

-- Create share status enum (idempotent)
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'share_status') THEN
        CREATE TYPE share_status AS ENUM ('PENDING', 'ACTIVE', 'ACCEPTED', 'EXPIRED', 'REVOKED');
    END IF;
END $$;

-- Create password_shares table
CREATE TABLE IF NOT EXISTS password_shares (
    share_id VARCHAR(36) PRIMARY KEY,
    password_id VARCHAR(255) NOT NULL,
    from_user_id VARCHAR(36) NOT NULL,
    to_user_id VARCHAR(36),
    encrypted_data TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    can_view BOOLEAN NOT NULL DEFAULT TRUE,
    can_save BOOLEAN NOT NULL DEFAULT TRUE,
    is_revocable BOOLEAN NOT NULL DEFAULT TRUE,
    status share_status NOT NULL DEFAULT 'PENDING',
    share_type share_type NOT NULL,
    CONSTRAINT fk_from_user
        FOREIGN KEY(from_user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_to_user
        FOREIGN KEY(to_user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_password_shares_from_user ON password_shares(from_user_id);
CREATE INDEX IF NOT EXISTS idx_password_shares_to_user ON password_shares(to_user_id);
CREATE INDEX IF NOT EXISTS idx_password_shares_status ON password_shares(status);
CREATE INDEX IF NOT EXISTS idx_password_shares_expires_at ON password_shares(expires_at);
CREATE INDEX IF NOT EXISTS idx_password_shares_share_type ON password_shares(share_type);
