-- 创建已撤销令牌表
CREATE TABLE revoked_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(255),
    token VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NOT NULL,
    revoke_reason VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_revoked_token ON revoked_tokens(token);
CREATE INDEX idx_revoked_user_device ON revoked_tokens(user_id, device_id);
CREATE INDEX idx_revoked_expires_at ON revoked_tokens(expires_at);

-- 添加注释
COMMENT ON TABLE revoked_tokens IS '已撤销的 JWT 令牌黑名单';
COMMENT ON COLUMN revoked_tokens.token IS 'SHA-256 哈希值';
COMMENT ON COLUMN revoked_tokens.expires_at IS '令牌过期时间，用于清理';
COMMENT ON COLUMN revoked_tokens.revoked_at IS '撤销时间';
COMMENT ON COLUMN revoked_tokens.revoke_reason IS '撤销原因: LOGOUT, PASSWORD_CHANGE, ACCOUNT_DELETE';