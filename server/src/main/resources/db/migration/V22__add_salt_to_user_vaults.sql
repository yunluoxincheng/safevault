-- 为 user_vaults 表添加 salt 列
-- 用于存储 Argon2id 密钥派生的盐值
-- 确保 salt 与加密数据一起存储在云端，支持跨设备解密
ALTER TABLE user_vaults ADD COLUMN IF NOT EXISTS salt VARCHAR(32) NOT NULL DEFAULT '';
