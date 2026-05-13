-- Create user_vaults table
-- 零知识架构：存储加密的密码库，服务器无法解密
CREATE TABLE IF NOT EXISTS user_vaults (
    vault_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    encrypted_data TEXT NOT NULL,
    data_iv VARCHAR(24) NOT NULL,
    data_auth_tag VARCHAR(32),
    version BIGINT NOT NULL DEFAULT 0,
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_vaults_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Create index on user_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_user_vaults_user_id ON user_vaults(user_id);

-- Create index on version for sync queries
CREATE INDEX IF NOT EXISTS idx_user_vaults_version ON user_vaults(version);

-- Create index on last_synced_at
CREATE INDEX IF NOT EXISTS idx_user_vaults_last_synced ON user_vaults(last_synced_at);

-- Create trigger to update updated_at
CREATE TRIGGER update_user_vaults_updated_at
    BEFORE UPDATE ON user_vaults
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
