-- 删除旧的分享相关表
-- 这些表已被 contact_shares 表替代

-- 先删除审计日志表（依赖于 password_shares）
DROP TABLE IF EXISTS share_audit_logs CASCADE;

-- 删除在线用户表（附近用户功能已移除）

-- 删除旧的密码分享表
-- 注意: 在生产环境执行前应先备份数据
DROP TABLE IF EXISTS password_shares CASCADE;

-- 注意: ShareType 和 ShareStatus 枚举类将在代码重构中删除
