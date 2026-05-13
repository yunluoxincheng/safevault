-- User 实体中有定义但此前 Flyway 未添加的列（与 org.ttt.safevaultbackend.entity.User 对齐）
SET search_path TO public;

ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_verification_email_sent_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS private_key_encrypted TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS private_key_iv VARCHAR(24);

-- 与 @Column(unique = true) 一致：允许多个 NULL，非空 email 唯一
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_unique ON users(email) WHERE email IS NOT NULL;

COMMENT ON COLUMN users.email IS '登录/找回邮箱（可空，兼容仅用户名用户）';
COMMENT ON COLUMN users.last_verification_email_sent_at IS '上次发送验证邮件时间（频率限制）';
COMMENT ON COLUMN users.private_key_encrypted IS 'RSA 私钥密文（可选，服务端托管场景）';
COMMENT ON COLUMN users.private_key_iv IS '私钥加密 IV';
