# SafeVault 数据库架构文档

## 概述

SafeVault 后端使用关系型数据库存储用户数据。本文档描述了 v2.2.0 版本的数据库表结构。

**数据库类型**: MySQL / PostgreSQL
**迁移工具**: Flyway

---

## 核心表

### users - 用户表

存储用户基本信息和密钥数据。

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| user_id | VARCHAR(36) | PRIMARY KEY | 用户ID |
| device_id | VARCHAR(255) | UNIQUE | 设备ID（可选） |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 用户名 |
| display_name | VARCHAR(100) | NOT NULL | 显示名称 |
| email | VARCHAR(255) | UNIQUE | 邮箱地址 |
| email_verified | BOOLEAN | NOT NULL, DEFAULT FALSE | 邮箱是否已验证 |
| verification_token | VARCHAR(255) | | 邮箱验证令牌 |
| verification_expires_at | TIMESTAMP | | 验证令牌过期时间 |
| public_key | TEXT | NOT NULL | RSA公钥（PEM格式） |
| private_key_encrypted | TEXT | | 加密的私钥 |
| private_key_iv | VARCHAR(24) | | 私钥加密IV |
| password_verifier | TEXT | | SRP密码验证器 |
| password_salt | VARCHAR(64) | | 密码盐值 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

**索引：**
- PRIMARY KEY (user_id)
- UNIQUE KEY (device_id)
- UNIQUE KEY (username)
- UNIQUE KEY (email)

---

### contact_shares - 联系人分享表

存储好友间的密码分享记录。

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| share_id | VARCHAR(36) | PRIMARY KEY | 分享ID |
| from_user_id | VARCHAR(36) | NOT NULL, FOREIGN KEY | 发送方用户ID |
| to_user_id | VARCHAR(36) | NOT NULL, FOREIGN KEY | 接收方用户ID |
| password_id | VARCHAR(255) | NOT NULL | 密码ID |
| encrypted_data | TEXT | NOT NULL | 加密的密码数据 |
| can_view | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否允许查看 |
| can_save | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否允许保存 |
| is_revocable | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否可撤销 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | 分享状态 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| expires_at | TIMESTAMP | | 过期时间 |
| accepted_at | TIMESTAMP | | 接受时间 |
| revoked_at | TIMESTAMP | | 撤销时间 |

**状态值：**
- `PENDING` - 待接收
- `ACCEPTED` - 已接受
- `EXPIRED` - 已过期
- `REVOKED` - 已撤销

**外键：**
- FOREIGN KEY (from_user_id) REFERENCES users(user_id) ON DELETE CASCADE
- FOREIGN KEY (to_user_id) REFERENCES users(user_id) ON DELETE CASCADE

**约束：**
- CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED'))
- CHECK (expires_at IS NULL OR expires_at > created_at)

**索引：**
- INDEX idx_contact_shares_from_user (from_user_id)
- INDEX idx_contact_shares_to_user (to_user_id)
- INDEX idx_contact_shares_status (status)
- INDEX idx_contact_shares_expires_at (expires_at)
- INDEX idx_contact_shares_created_at (created_at)

---

### friendships - 好友关系表

存储用户间的好友关系。

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | VARCHAR(36) | PRIMARY KEY | 关系ID |
| user_id_a | VARCHAR(36) | NOT NULL, FOREIGN KEY | 用户A的ID |
| user_id_b | VARCHAR(36) | NOT NULL, FOREIGN KEY | 用户B的ID |
| status | VARCHAR(20) | NOT NULL | 好友状态 |
| created_by | VARCHAR(36) | NOT NULL | 创建者用户ID |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |

**状态值：**
- `PENDING` - 待接受
- `ACCEPTED` - 已接受
- `DECLINED` - 已拒绝
- `BLOCKED` - 已拉黑

**外键：**
- FOREIGN KEY (user_id_a) REFERENCES users(user_id) ON DELETE CASCADE
- FOREIGN KEY (user_id_b) REFERENCES users(user_id) ON DELETE CASCADE

**唯一约束：**
- UNIQUE KEY (user_id_a, user_id_b)

**索引：**
- INDEX idx_friendships_user_a (user_id_a)
- INDEX idx_friendships_user_b (user_id_b)
- INDEX idx_friendships_status (status)

---

### friend_requests - 好友请求表

存储好友请求记录。

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| request_id | VARCHAR(36) | PRIMARY KEY | 请求ID |
| from_user_id | VARCHAR(36) | NOT NULL, FOREIGN KEY | 发送方用户ID |
| to_user_id | VARCHAR(36) | NOT NULL, FOREIGN KEY | 接收方用户ID |
| status | VARCHAR(20) | NOT NULL | 请求状态 |
| message | TEXT | | 请求消息 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

**状态值：**
- `PENDING` - 待处理
- `ACCEPTED` - 已接受
- `DECLINED` - 已拒绝

**外键：**
- FOREIGN KEY (from_user_id) REFERENCES users(user_id) ON DELETE CASCADE
- FOREIGN KEY (to_user_id) REFERENCES users(user_id) ON DELETE CASCADE

**唯一约束：**
- UNIQUE KEY (from_user_id, to_user_id)

---

### user_vaults - 用户密码库表

存储用户的加密密码数据。

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| vault_id | VARCHAR(36) | PRIMARY KEY | 密码库条目ID |
| user_id | VARCHAR(36) | NOT NULL, FOREIGN KEY | 用户ID |
| encrypted_data | TEXT | NOT NULL | 加密的密码数据 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

**外键：**
- FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE

**索引：**
- INDEX idx_user_vaults_user_id (user_id)

---

### revoked_tokens - 撤销令牌表

存储已撤销的 JWT 令牌。

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| token_id | VARCHAR(36) | PRIMARY KEY | 令牌ID |
| token | VARCHAR(500) | NOT NULL, UNIQUE | JWT令牌 |
| revoked_at | TIMESTAMP | NOT NULL | 撤销时间 |
| expires_at | TIMESTAMP | NOT NULL | 过期时间 |

**索引：**
- INDEX idx_revoked_tokens_expires_at (expires_at)

---

## v2.2.0 数据库变更

### 新增表

#### contact_shares
- **用途**: 替代旧的 `password_shares` 表
- **主要变更**:
  - 移除了 `share_type` 字段（仅支持联系人分享）
  - 移除了 `audit_logs` 关联
  - 添加了 `accepted_at` 和 `revoked_at` 时间戳
  - 简化了状态枚举

### 删除表

#### password_shares
- **原因**: 被 `contact_shares` 替代
- **迁移**: 活跃的 USER_TO_USER 分享已迁移到 `contact_shares`

#### online_users
- **原因**: 附近用户功能完全移除
- **影响**: 不再需要位置信息存储

#### share_audit_logs
- **原因**: 简化后审计日志不再需要
- **替代**: 使用 `created_at`、`accepted_at`、`revoked_at` 时间戳

---

## 数据迁移脚本

### V10__create_contact_shares_table.sql
创建 `contact_shares` 表

### V11__migrate_to_contact_shares.sql
将活跃的 USER_TO_USER 分享迁移到 `contact_shares`

### V12__drop_old_share_tables.sql
删除旧的分享相关表

---

## ER 图

```
┌─────────────┐
│   users     │
└─────────────┘
       │
       ├──────────────────────────────────────┐
       │                                      │
       ▼                                      ▼
┌─────────────┐                      ┌─────────────┐
│friendships  │                      │contact_shares│
└─────────────┘                      └─────────────┘
       │                                      │
       ▼                                      │
┌─────────────┐                              │
│friend_requests│                             │
└─────────────┘                              │
                                              │
┌─────────────┐                              │
│ user_vaults │◄─────────────────────────────┘
└─────────────┘
```

---

## 数据完整性规则

1. **级联删除**: 当用户被删除时，所有相关的分享、好友关系、密码库数据都会被删除
2. **状态约束**: 分享状态必须符合预定义的枚举值
3. **时间约束**: 过期时间必须晚于创建时间
4. **唯一性**: 用户名和邮箱必须唯一
5. **外键约束**: 确保引用完整性

---

## 性能优化建议

1. **定期清理过期分享**: 使用定时任务清理已过期的分享记录
2. **索引维护**: 定期分析和优化索引
3. **数据归档**: 将历史数据归档到单独的表
4. **查询优化**: 使用索引字段进行查询

---

## 安全注意事项

1. **加密数据**: `encrypted_data` 字段必须始终包含加密数据
2. **私钥保护**: `private_key_encrypted` 必须使用强加密
3. **令牌撤销**: 定期清理过期的撤销令牌
4. **访问控制**: 应用层必须验证用户权限
5. **审计日志**: 考虑添加操作审计日志

---

**版本**: 2.2.0
**最后更新**: 2026-01-22
**数据库引擎**: MySQL 8.0+ / PostgreSQL 12+
