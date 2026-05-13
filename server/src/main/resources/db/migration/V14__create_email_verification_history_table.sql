-- 创建邮箱验证历史记录表
DROP TABLE IF EXISTS email_verification_history CASCADE;
-- 用于追踪用户的验证历史,支持统计和分析
CREATE TABLE email_verification_history (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36),
    email VARCHAR(255) NOT NULL,
    verification_code VARCHAR(10),
    verification_token VARCHAR(255),
    request_type VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    device_fingerprint VARCHAR(255),
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    delivery_status VARCHAR(50),
    provider VARCHAR(100),
    CONSTRAINT fk_verification_history_user
        FOREIGN KEY(user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE
);

-- 创建索引优化查询性能
CREATE INDEX idx_email_verification_history_user_id ON email_verification_history(user_id);
CREATE INDEX idx_email_verification_history_email ON email_verification_history(email);
CREATE INDEX idx_email_verification_history_code ON email_verification_history(verification_code);
CREATE INDEX idx_email_verification_history_token ON email_verification_history(verification_token) WHERE verification_token IS NOT NULL;
CREATE INDEX idx_email_verification_history_sent_at ON email_verification_history(sent_at DESC);
CREATE INDEX idx_email_verification_history_expires_at ON email_verification_history(expires_at);
CREATE INDEX idx_email_verification_history_is_used ON email_verification_history(is_used);

-- 添加复合索引用于常见查询场景
CREATE INDEX idx_email_verification_history_user_sent ON email_verification_history(user_id, sent_at DESC);
CREATE INDEX idx_email_verification_history_email_expires ON email_verification_history(email, expires_at);

-- 添加表和字段注释
COMMENT ON TABLE email_verification_history IS '邮箱验证历史记录表,追踪验证码/令牌的完整生命周期';
COMMENT ON COLUMN email_verification_history.user_id IS '用户ID (外键关联users表)';
COMMENT ON COLUMN email_verification_history.email IS '接收验证的邮箱地址';
COMMENT ON COLUMN email_verification_history.verification_code IS '6位数字验证码 (可选)';
COMMENT ON COLUMN email_verification_history.verification_token IS '验证令牌 (可选)';
COMMENT ON COLUMN email_verification_history.request_type IS '请求类型: REGISTRATION, PASSWORD_RESET, EMAIL_CHANGE, RESEND';
COMMENT ON COLUMN email_verification_history.ip_address IS '请求来源IP地址';
COMMENT ON COLUMN email_verification_history.device_fingerprint IS '设备指纹 (用于识别设备)';
COMMENT ON COLUMN email_verification_history.sent_at IS '验证发送时间';
COMMENT ON COLUMN email_verification_history.verified_at IS '验证完成时间';
COMMENT ON COLUMN email_verification_history.is_used IS '验证码是否已使用';
COMMENT ON COLUMN email_verification_history.expires_at IS '验证过期时间';
COMMENT ON COLUMN email_verification_history.delivery_status IS '邮件投递状态: SENT, DELIVERED, FAILED, BOUNCED, OPENED';
COMMENT ON COLUMN email_verification_history.provider IS '邮件服务提供商 (如: aliyun)';
