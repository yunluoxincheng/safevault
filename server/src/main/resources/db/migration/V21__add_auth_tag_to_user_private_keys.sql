-- 为 user_private_keys 表添加 auth_tag 列
-- 用于存储 AES-GCM 加密的认证标签
ALTER TABLE user_private_keys ADD COLUMN IF NOT EXISTS auth_tag VARCHAR(32) NOT NULL DEFAULT '';
