-- 创建联系人分享表
-- 简化后的分享系统，仅支持好友间的密码分享

CREATE TABLE contact_shares (
    -- 主键
    share_id VARCHAR(36) PRIMARY KEY,

    -- 用户关系（必须是好友）
    from_user_id VARCHAR(36) NOT NULL,
    to_user_id VARCHAR(36) NOT NULL,

    -- 密码数据
    password_id VARCHAR(255) NOT NULL,
    encrypted_data TEXT NOT NULL,

    -- 权限控制
    can_view BOOLEAN NOT NULL DEFAULT TRUE,
    can_save BOOLEAN NOT NULL DEFAULT TRUE,
    is_revocable BOOLEAN NOT NULL DEFAULT TRUE,

    -- 状态管理: PENDING, ACCEPTED, EXPIRED, REVOKED
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    accepted_at TIMESTAMP,
    revoked_at TIMESTAMP,

    -- 外键约束
    CONSTRAINT fk_contact_shares_from_user FOREIGN KEY (from_user_id)
        REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_contact_shares_to_user FOREIGN KEY (to_user_id)
        REFERENCES users(user_id) ON DELETE CASCADE,

    -- 状态约束
    CONSTRAINT chk_contact_shares_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')
    ),

    -- 过期时间必须晚于创建时间
    CONSTRAINT chk_contact_shares_expires_at CHECK (
        expires_at IS NULL OR expires_at > created_at
    )
);

-- 索引
CREATE INDEX idx_contact_shares_from_user ON contact_shares(from_user_id);
CREATE INDEX idx_contact_shares_to_user ON contact_shares(to_user_id);
CREATE INDEX idx_contact_shares_status ON contact_shares(status);
CREATE INDEX idx_contact_shares_expires_at ON contact_shares(expires_at);
CREATE INDEX idx_contact_shares_created_at ON contact_shares(created_at);

-- 添加表注释
COMMENT ON TABLE contact_shares IS '联系人密码分享表，仅支持好友间分享';
COMMENT ON COLUMN contact_shares.status IS '分享状态: PENDING=待接收, ACCEPTED=已接受, EXPIRED=已过期, REVOKED=已撤销';
