# SafeVault v2.2 迁移指南

## 概述

本指南帮助您从 SafeVault v2.1（旧版分享系统）迁移到 v2.2（简化的联系人分享系统）。

**主要变更：**
- 移除直接链接分享 (DIRECT)
- 移除附近用户分享 (NEARBY)
- 仅支持基于好友关系的联系人分享
- 简化数据库表结构

---

## 迁移前准备

### 1. 数据备份

**生产环境必须先备份数据库！**

```bash
# MySQL
mysqldump -u username -p safevault > safevault_backup_$(date +%Y%m%d).sql

# PostgreSQL
pg_dump safevault > safevault_backup_$(date +%Y%m%d).sql
```

### 2. 检查当前版本

```bash
# 查看当前数据库版本
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

### 3. 检查活跃分享

```sql
-- 检查活跃的 USER_TO_USER 分享数量
SELECT COUNT(*) FROM password_shares
WHERE share_type = 'USER_TO_USER'
  AND status IN ('PENDING', 'ACTIVE', 'ACCEPTED')
  AND (expires_at IS NULL OR expires_at > NOW());

-- 检查即将过期的分享
SELECT * FROM password_shares
WHERE expires_at BETWEEN NOW() AND NOW() + INTERVAL 7 DAY;
```

---

## 迁移步骤

### 步骤 1: 部署新版本后端

1. 停止后端服务
2. 部署新的后端代码（包含新的迁移脚本）
3. **不要立即启动服务**

### 步骤 2: 执行数据库迁移

Flyway 会自动执行以下迁移脚本：

#### V10__create_contact_shares_table.sql
创建新的 `contact_shares` 表

#### V11__migrate_to_contact_shares.sql
迁移活跃的 USER_TO_USER 分享

#### V12__drop_old_share_tables.sql
删除旧的表

**手动执行迁移（推荐）：**

```bash
cd safevault-backend
./mvnw flyway:migrate
```

### 步骤 3: 验证迁移结果

```sql
-- 检查新表是否创建
SHOW TABLES LIKE 'contact_shares';

-- 检查数据是否迁移成功
SELECT COUNT(*) FROM contact_shares;

-- 检查旧表是否删除
-- SHOW TABLES LIKE 'password_shares';  -- 应该返回空

-- 验证外键约束
SELECT COUNT(*) FROM contact_shares cs
JOIN friendships f ON (
    (f.user_id_a = cs.from_user_id AND f.user_id_b = cs.to_user_id)
    OR (f.user_id_a = cs.to_user_id AND f.user_id_b = cs.from_user_id)
)
WHERE f.status = 'ACCEPTED';
```

### 步骤 4: 启动后端服务

```bash
# 启动服务
./mvnw spring-boot:run

# 或使用部署脚本
./deploy.sh start
```

### 步骤 5: 验证 API 功能

```bash
# 测试创建分享
curl -X POST http://localhost:8080/api/v1/shares/contact \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "toUserId": "usr_target123",
    "passwordId": "pwd_test",
    "title": "Test",
    "encryptedPassword": "BASE64_PASSWORD",
    "expiresInMinutes": 1440,
    "permission": {
      "canView": true,
      "canSave": true,
      "isRevocable": true
    }
  }'

# 测试获取分享列表
curl -X GET http://localhost:8080/api/v1/shares/sent \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## API 变更对照表

### 创建分享

| v2.1 | v2.2 | 说明 |
|------|------|------|
| `POST /v1/shares`<br>`{..., "shareType": "DIRECT"}` | ❌ 已移除 | 直接链接分享已移除 |
| `POST /v1/shares`<br>`{..., "shareType": "NEARBY"}` | ❌ 已移除 | 附近用户分享已移除 |
| `POST /v1/shares`<br>`{..., "shareType": "USER_TO_USER"}` | `POST /v1/shares/contact` | 现在需要好友关系 |

### 附近发现

| v2.1 | v2.2 | 说明 |
|------|------|------|
| `POST /v1/discovery/register` | ❌ 已移除 | 不再需要位置注册 |
| `GET /v1/discovery/nearby` | ❌ 已移除 | 不再支持附近用户发现 |

---

## 前端变更

### 必需的代码更改

1. **移除分享类型选择**
   - 删除分享方式选择 UI（离线/云端）
   - 默认使用联系人分享

2. **更新 API 调用**
   ```java
   // 旧代码
   CreateShareRequest request = CreateShareRequest.builder()
       .shareType(ShareType.USER_TO_USER)  // 移除此字段
       .toUserId(userId)
       .build();

   // 新代码
   CreateContactShareRequest request = CreateContactShareRequest.builder()
       .toUserId(userId)  // 确保是好友
       .build();
   ```

3. **处理好友验证错误**
   ```java
   // 新增错误处理
   if (errorCode.equals("NOT_FRIENDS")) {
       // 提示用户先添加好友
       showAddFriendDialog();
   }
   ```

---

## 回滚计划

如果迁移失败，按以下步骤回滚：

### 1. 停止服务

```bash
./deploy.sh stop
```

### 2. 恢复数据库

```bash
# MySQL
mysql -u username -p safevault < safevault_backup_YYYYMMDD.sql

# PostgreSQL
psql safevault < safevault_backup_YYYYMMDD.sql
```

### 3. 回滚代码版本

```bash
git checkout tags/v2.1.0
./mvnw clean package
./deploy.sh start
```

### 4. 验证回滚

```bash
# 测试旧 API 端点
curl -X POST http://localhost:8080/api/v1/shares \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"shareType": "USER_TO_USER", ...}'
```

---

## 常见问题

### Q1: 迁移后用户可以访问旧分享吗？

**A:** 可以。所有活跃的 USER_TO_USER 分享都已迁移到 contact_shares 表。但 DIRECT 和 NEARBY 类型的分享不会被迁移，会丢失。

### Q2: 迁移过程中服务会中断吗？

**A:** 会的。迁移期间需要停止服务，预计中断时间 5-15 分钟，取决于数据量。

### Q3: 如何处理活跃的 DIRECT 分享？

**A:** DIRECT 分享不会被迁移。建议：
1. 迁移前通知所有用户
2. 建议用户重新分享（使用联系人分享）
3. 给予足够的缓冲期（建议 2 周）

### Q4: 迁移后前端需要更新吗？

**A:** 强烈建议更新。旧版本前端会尝试调用已删除的 API，导致错误。

### Q5: 如何验证迁移是否成功？

**A:** 执行以下验证：
```sql
-- 1. 检查新表数据量
SELECT COUNT(*) FROM contact_shares;

-- 2. 验证好友关系约束
SELECT cs.* FROM contact_shares cs
LEFT JOIN friendships f ON (
    (f.user_id_a = cs.from_user_id AND f.user_id_b = cs.to_user_id)
    OR (f.user_id_b = cs.from_user_id AND f.user_id_a = cs.to_user_id)
)
WHERE f.status != 'ACCEPTED';
-- 应该返回空结果

-- 3. 检查外键约束
SELECT TABLE_NAME, CONSTRAINT_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_NAME = 'contact_shares';
```

---

## 数据清理（可选）

迁移完成后，可以清理旧数据：

```sql
-- 删除旧的 DIRECT 分享（如果还在）
-- 注意：V12 迁移脚本会删除 password_shares 表

-- 清理孤立的好友关系（可选）
DELETE FROM friendships
WHERE status = 'PENDING'
  AND created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
```

---

## 监控建议

迁移后，监控以下指标：

1. **错误率**
   ```bash
   # 监控 API 错误
   curl -s http://localhost:8080/actuator/metrics/api.requests
   ```

2. **数据库性能**
   ```sql
   -- 检查慢查询
   SHOW PROCESSLIST;
   ```

3. **分享成功率**
   ```sql
   -- 统计分享状态分布
   SELECT status, COUNT(*) FROM contact_shares GROUP BY status;
   ```

---

## 联系支持

如果遇到迁移问题：

1. 查看日志文件：`logs/safevault.log`
2. 检查 Flyway 历史：`SELECT * FROM flyway_schema_history;`
3. 联系技术支持：support@safevault.app
4. 提交 GitHub Issue：https://github.com/your-repo/safevault/issues

---

**版本**: 2.2.0
**最后更新**: 2026-01-22
**适用于**: SafeVault Backend v2.1.x → v2.2.0
