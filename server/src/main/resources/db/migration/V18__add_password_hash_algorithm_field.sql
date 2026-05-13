-- 添加密码哈希算法字段
-- 用于追踪用户使用的是哪种密码哈希算法
-- 开发环境：所有用户统一使用 Argon2id

-- 添加 password_hash_algorithm 字段
-- 默认值为 'ARGON2ID'，所有新用户使用 Argon2id
ALTER TABLE users
ADD COLUMN IF NOT EXISTS password_hash_algorithm VARCHAR(20) NOT NULL DEFAULT 'ARGON2ID';

-- 创建索引以优化查询性能
CREATE INDEX IF NOT EXISTS idx_users_password_hash_algorithm ON users(password_hash_algorithm);

-- 添加注释
COMMENT ON COLUMN users.password_hash_algorithm IS '密码哈希算法: ARGON2ID（统一使用）';

-- 处理现有数据：设置为 ARGON2ID
UPDATE users
SET password_hash_algorithm = 'ARGON2ID'
WHERE password_hash_algorithm IS NULL;

-- 添加约束确保只能使用 Argon2id
ALTER TABLE users
ADD CONSTRAINT chk_password_hash_algorithm
CHECK (password_hash_algorithm = 'ARGON2ID');
