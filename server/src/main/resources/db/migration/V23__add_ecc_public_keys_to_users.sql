-- 为 users 表添加 X25519/Ed25519 椭圆曲线公钥字段
-- 用于支持现代加密算法（协议版本 3.0）
-- 保持与现有 RSA public_key 字段的向后兼容

-- 添加 X25519 公钥字段（用于 ECDH 密钥交换）
ALTER TABLE users
ADD COLUMN IF NOT EXISTS x25519_public_key TEXT;

-- 添加 Ed25519 公钥字段（用于数字签名）
ALTER TABLE users
ADD COLUMN IF NOT EXISTS ed25519_public_key TEXT;

-- 添加公钥版本标识字段
ALTER TABLE users
ADD COLUMN IF NOT EXISTS key_version VARCHAR(10) DEFAULT 'v1';

-- 添加公钥更新时间
ALTER TABLE users
ADD COLUMN IF NOT EXISTS public_keys_updated_at TIMESTAMP;

-- 创建索引用于快速查询
CREATE INDEX IF NOT EXISTS idx_users_x25519_key ON users(x25519_public_key) WHERE x25519_public_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_ed25519_key ON users(ed25519_public_key) WHERE ed25519_public_key IS NOT NULL;

-- 更新现有用户的 key_version 为 'v1'（RSA 密钥）
UPDATE users SET key_version = 'v1' WHERE key_version IS NULL;

-- 创建触发器更新 public_keys_updated_at
CREATE OR REPLACE FUNCTION update_public_keys_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.x25519_public_key IS DISTINCT FROM OLD.x25519_public_key
       OR NEW.ed25519_public_key IS DISTINCT FROM OLD.ed25519_public_key THEN
        NEW.public_keys_updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_public_keys_timestamp
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_public_keys_timestamp();
