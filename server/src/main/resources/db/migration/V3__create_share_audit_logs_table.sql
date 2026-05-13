-- Create share_audit_logs table
CREATE TABLE IF NOT EXISTS share_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    share_id VARCHAR(36) NOT NULL,
    action VARCHAR(100) NOT NULL,
    action_performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    performed_by VARCHAR(36),
    CONSTRAINT fk_audit_share
        FOREIGN KEY(share_id)
        REFERENCES password_shares(share_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_audit_performed_by
        FOREIGN KEY(performed_by)
        REFERENCES users(user_id)
        ON DELETE SET NULL
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_audit_logs_share_id ON share_audit_logs(share_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON share_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_performed_at ON share_audit_logs(action_performed_at);
