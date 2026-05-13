-- 迁移活跃的 USER_TO_USER 分享到 contact_shares 表
-- 将现有的 password_shares 数据迁移到新的 contact_shares 表
-- 迁移活跃的 USER_TO_USER 分享
INSERT INTO contact_shares (
    share_id,
    from_user_id,
    to_user_id,
    password_id,
    encrypted_data,
    can_view,
    can_save,
    is_revocable,
    status,
    created_at,
    expires_at
)
SELECT
    share_id,
    from_user_id,
    to_user_id,
    password_id,
    encrypted_data,
    can_view,
    can_save,
    is_revocable,
    CASE status
        WHEN 'ACTIVE' THEN 'ACCEPTED'
        WHEN 'PENDING' THEN 'PENDING'
        ELSE status
    END,
    created_at,
    expires_at
FROM password_shares
WHERE share_type = 'USER_TO_USER'
  AND to_user_id IS NOT NULL
  AND status IN ('PENDING', 'ACTIVE', 'ACCEPTED')
  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP);

-- 注意: 从审计日志获取 accepted_at 时间需要单独处理，这里先迁移基础数据
-- 后续可以通过更新脚本补充 accepted_at 时间
