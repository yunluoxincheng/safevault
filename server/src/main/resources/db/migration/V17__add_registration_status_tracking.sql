-- 首先添加缺失的密码字段（这些字段在User实体中定义但之前没有迁移脚本）
ALTER TABLE users
ADD COLUMN IF NOT EXISTS password_verifier TEXT,
ADD COLUMN IF NOT EXISTS password_salt VARCHAR(64);

-- 添加注册状态追踪字段
ALTER TABLE users
ADD COLUMN IF NOT EXISTS registration_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP NULL,
ADD COLUMN IF NOT EXISTS registration_completed_at TIMESTAMP NULL;

-- 创建索引以优化查询性能
CREATE INDEX IF NOT EXISTS idx_users_registration_status ON users(registration_status);
CREATE INDEX IF NOT EXISTS idx_users_verified_at ON users(verified_at);

-- 处理现有数据：
-- 现有用户假设已完成注册，设置 registration_status = 'ACTIVE'
-- 有 email_verified = true 且 password_verifier IS NULL 的用户设置为 EMAIL_VERIFIED
UPDATE users
SET registration_status = CASE
    WHEN email_verified = true AND password_verifier IS NULL THEN 'EMAIL_VERIFIED'
    ELSE 'ACTIVE'
END
WHERE registration_status = 'ACTIVE';

-- 对于状态为 EMAIL_VERIFIED 的用户，设置 verified_at 为当前时间
UPDATE users
SET verified_at = created_at
WHERE registration_status = 'EMAIL_VERIFIED' AND verified_at IS NULL;

-- 对于状态为 ACTIVE 的用户，设置 registration_completed_at 为当前时间
UPDATE users
SET registration_completed_at = created_at
WHERE registration_status = 'ACTIVE' AND registration_completed_at IS NULL;

-- 添加注释
COMMENT ON COLUMN users.registration_status IS '用户注册状态: EMAIL_VERIFIED(邮箱已验证), ACTIVE(注册完成)';
COMMENT ON COLUMN users.verified_at IS '邮箱验证时间';
COMMENT ON COLUMN users.registration_completed_at IS '注册完成时间';
