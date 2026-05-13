-- 添加加密版本字段到 contact_shares 表
-- 用于支持 RSA 填充的双版本：v1=PKCS1（不安全），v2=OAEP（安全）
-- 这是安全加固第二阶段的一部分

-- 添加 encryption_version 字段，默认值为 'v1'（兼容现有数据）
ALTER TABLE contact_shares
ADD COLUMN encryption_version VARCHAR(10) NOT NULL DEFAULT 'v1';

-- 添加版本约束，只允许 v1 或 v2
ALTER TABLE contact_shares
ADD CONSTRAINT chk_encryption_version CHECK (
    encryption_version IN ('v1', 'v2')
);

-- 创建索引以加速按版本查询
CREATE INDEX idx_contact_shares_encryption_version ON contact_shares(encryption_version);

-- 添加表和字段注释
COMMENT ON COLUMN contact_shares.encryption_version IS 'RSA加密版本: v1=PKCS1Padding（不安全，仅兼容）, v2=OAEP（安全）';
COMMENT ON TABLE contact_shares IS '联系人密码分享表（v2: 支持RSA填充双版本）';
