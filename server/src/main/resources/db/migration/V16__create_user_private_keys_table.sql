-- Create user_private_keys table
-- 用于云端存储用户加密的私钥，支持版本控制
CREATE TABLE IF NOT EXISTS user_private_keys (
    user_id VARCHAR(36) PRIMARY KEY,
    encrypted_private_key TEXT NOT NULL,
    iv VARCHAR(255) NOT NULL,
    salt VARCHAR(255) NOT NULL,
    version VARCHAR(50) DEFAULT 'v1',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_private_keys_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Create index on version for future compatibility
CREATE INDEX IF NOT EXISTS idx_user_private_keys_version ON user_private_keys(version);

-- Create trigger to update updated_at
CREATE TRIGGER update_user_private_keys_updated_at
    BEFORE UPDATE ON user_private_keys
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
