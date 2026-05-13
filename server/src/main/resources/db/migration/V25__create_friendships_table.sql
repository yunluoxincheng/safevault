-- 好友关系（与 Friendship 实体对齐；此前无迁移，避免 Hibernate validate 缺表）
SET search_path TO public;

CREATE TABLE IF NOT EXISTS friendships (
    id VARCHAR(36) PRIMARY KEY,
    user_id_a VARCHAR(36) NOT NULL,
    user_id_b VARCHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_friendships_user_a FOREIGN KEY (user_id_a) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_friendships_user_b FOREIGN KEY (user_id_b) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_friendships_created_by FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_friendships_user_a ON friendships(user_id_a);
CREATE INDEX IF NOT EXISTS idx_friendships_user_b ON friendships(user_id_b);
CREATE INDEX IF NOT EXISTS idx_friendships_users_pair ON friendships(user_id_a, user_id_b);
