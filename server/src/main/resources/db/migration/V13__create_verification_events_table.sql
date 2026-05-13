-- 创建邮箱验证事件追踪表
DROP TABLE IF EXISTS verification_events CASCADE;
-- 用于记录所有验证相关的操作事件,支持审计和问题排查
CREATE TABLE verification_events (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36),
    email VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    verification_token VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason VARCHAR(255),
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata TEXT,
    CONSTRAINT fk_verification_events_user
        FOREIGN KEY(user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE
);

-- 创建索引优化查询性能
CREATE INDEX idx_verification_events_user_id ON verification_events(user_id);
CREATE INDEX idx_verification_events_email ON verification_events(email);
CREATE INDEX idx_verification_events_type ON verification_events(event_type);
CREATE INDEX idx_verification_events_timestamp ON verification_events(event_timestamp);
CREATE INDEX idx_verification_events_token ON verification_events(verification_token) WHERE verification_token IS NOT NULL;
CREATE INDEX idx_verification_events_success ON verification_events(success);

-- 添加复合索引用于常见查询场景
CREATE INDEX idx_verification_events_user_type ON verification_events(user_id, event_type);
CREATE INDEX idx_verification_events_user_timestamp ON verification_events(user_id, event_timestamp DESC);

-- 添加表和字段注释
COMMENT ON TABLE verification_events IS '邮箱验证事件追踪表,记录所有验证相关操作';
COMMENT ON COLUMN verification_events.user_id IS '用户ID (外键关联users表)';
COMMENT ON COLUMN verification_events.email IS '用户邮箱地址';
COMMENT ON COLUMN verification_events.event_type IS '事件类型: TOKEN_GENERATED, TOKEN_VERIFIED, TOKEN_EXPIRED, EMAIL_SENT, EMAIL_FAILED, RESEND_LIMIT_EXCEEDED, PAGE_VISIT, DEEP_LINK_CLICK, VERIFICATION_SUCCESS, VERIFICATION_FAILED';
COMMENT ON COLUMN verification_events.verification_token IS '验证令牌 (用于关联特定验证流程)';
COMMENT ON COLUMN verification_events.ip_address IS '客户端IP地址';
COMMENT ON COLUMN verification_events.user_agent IS '客户端User-Agent';
COMMENT ON COLUMN verification_events.success IS '操作是否成功';
COMMENT ON COLUMN verification_events.failure_reason IS '失败原因 (当success=false时)';
COMMENT ON COLUMN verification_events.event_timestamp IS '事件发生时间戳';
COMMENT ON COLUMN verification_events.metadata IS '额外元数据 (JSON格式)';
