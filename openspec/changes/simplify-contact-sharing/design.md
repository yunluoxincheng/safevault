# 简化联系人分享系统 - 设计文档

## 架构决策

### 决策 1: 删除直接链接分享
**原因**：
- 安全风险：链接可能被转发或截获
- 无好友限制：任何人都可以访问链接
- 与产品定位不符：SafeVault 定位为安全密码管理器

**替代方案**：用户只能分享给已添加的好友

### 决策 2: 删除附近用户功能
**原因**：
- 需要位置权限，增加隐私风险
- 使用频率低（基于假设）
- 与联系人分享功能重叠

**替代方案**：通过搜索用户名或扫码添加好友后分享

### 决策 3: 新表使用好友外键约束
**原因**：
- 确保数据一致性
- 数据库层面强制好友关系验证
- 避免应用层逻辑错误

## 数据库设计

### contact_shares 表结构

```sql
CREATE TABLE contact_shares (
    -- 主键
    share_id VARCHAR(36) PRIMARY KEY,

    -- 用户关系（必须是好友）
    from_user_id VARCHAR(36) NOT NULL,
    to_user_id VARCHAR(36) NOT NULL,

    -- 密码数据
    password_id VARCHAR(255) NOT NULL,
    encrypted_data TEXT NOT NULL,

    -- 权限控制
    can_view BOOLEAN NOT NULL DEFAULT TRUE,
    can_save BOOLEAN NOT NULL DEFAULT TRUE,
    is_revocable BOOLEAN NOT NULL DEFAULT TRUE,

    -- 状态管理
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    accepted_at TIMESTAMP,
    revoked_at TIMESTAMP,

    -- 外键约束
    FOREIGN KEY (from_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (to_user_id) REFERENCES users(user_id) ON DELETE CASCADE,

    -- 确保只能分享给好友
    CHECK (
        EXISTS (
            SELECT 1 FROM friendships
            WHERE ((user_id_a = from_user_id AND user_id_b = to_user_id)
                OR (user_id_a = to_user_id AND user_id_b = from_user_id))
            AND status = 'ACCEPTED'
        )
    ),

    -- 状态流转约束
    CHECK (
        status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')
    ),

    -- 过期时间必须晚于创建时间
    CHECK (expires_at IS NULL OR expires_at > created_at)
);

-- 索引
CREATE INDEX idx_contact_shares_from_user ON contact_shares(from_user_id);
CREATE INDEX idx_contact_shares_to_user ON contact_shares(to_user_id);
CREATE INDEX idx_contact_shares_status ON contact_shares(status);
CREATE INDEX idx_contact_shares_expires_at ON contact_shares(expires_at);
```

### 删除的表

| 表名 | 删除原因 |
|------|----------|
| `password_shares` | 功能被 contact_shares 替代 |
| `online_users` | 附近用户功能完全移除 |
| `share_audit_logs` | 简化后审计日志不需要 |

## 状态机设计

```
         创建分享
             ↓
        ┌─────────┐
        │ PENDING │ ◄──────┐
        └─────────┘        │
             │             │
        ┌───┴────┐         │
        ↓        ↓         │
    接收      过期/撤销    │
        │        │         │
        ↓        ↓         │
   ┌─────────┐ ┌──────────┤
   │ ACCEPTED│ │ EXPIRED  │
   └─────────┘ │ REVOKED  │
               └──────────┘
```

### 状态转换规则

| 当前状态 | 可转换到 | 触发条件 |
|----------|----------|----------|
| PENDING | ACCEPTED | 接收者保存分享 |
| PENDING | EXPIRED | 超过过期时间 |
| PENDING | REVOKED | 发送者撤销 |
| ACCEPTED | REVOKED | 发送者撤销（可选） |

## API 设计

### 修改后的 API

#### 创建联系人分享
```http
POST /api/v1/shares/contact
Authorization: Bearer {token}
Content-Type: application/json

{
  "toUserId": "user-123",
  "passwordId": "pwd-456",
  "encryptedData": "base64...",
  "canView": true,
  "canSave": true,
  "isRevocable": true,
  "expiresInMinutes": 1440
}

Response 201:
{
  "shareId": "share-789",
  "status": "PENDING",
  "createdAt": "2025-01-22T10:00:00Z",
  "expiresAt": "2025-01-23T10:00:00Z"
}
```

#### 获取发给联系人的分享列表
```http
GET /api/v1/shares/sent
Authorization: Bearer {token}

Response 200:
{
  "shares": [
    {
      "shareId": "share-789",
      "toUser": {
        "userId": "user-123",
        "displayName": "张三"
      },
      "passwordId": "pwd-456",
      "status": "PENDING",
      "createdAt": "2025-01-22T10:00:00Z",
      "expiresAt": "2025-01-23T10:00:00Z"
    }
  ]
}
```

#### 获取从联系人接收的分享列表
```http
GET /api/v1/shares/received
Authorization: Bearer {token}

Response 200:
{
  "shares": [
    {
      "shareId": "share-789",
      "fromUser": {
        "userId": "user-456",
        "displayName": "李四"
      },
      "passwordData": {...},
      "status": "PENDING",
      "permission": {...},
      "createdAt": "2025-01-22T10:00:00Z"
    }
  ]
}
```

#### 接受分享
```http
POST /api/v1/shares/{shareId}/accept
Authorization: Bearer {token}

Response 200:
{
  "shareId": "share-789",
  "status": "ACCEPTED",
  "acceptedAt": "2025-01-22T11:00:00Z"
}
```

#### 撤销分享
```http
DELETE /api/v1/shares/{shareId}
Authorization: Bearer {token}

Response 200:
{
  "shareId": "share-789",
  "status": "REVOKED",
  "revokedAt": "2025-01-22T12:00:00Z"
}
```

### 删除的 API

| 端点 | 说明 |
|------|------|
| `POST /api/v1/shares/direct` | 直接链接分享 |
| `POST /api/v1/discovery/register` | 注册位置 |
| `GET /api/v1/discovery/nearby` | 查找附近用户 |
| `DELETE /api/v1/discovery/register` | 注销位置 |

## 前端适配

### ShareActivity 修改

**移除**：
- 分享方式选择（离线/云端）
- 附近用户入口
- 直接分享选项

**保留**：
- 联系人选择
- 权限配置
- 过期时间选择
- 身份验证流程

### NearByUsersActivity
**完全删除**

### ShareViewModel 简化

**移除**：
- `ShareType` 枚举
- `isOfflineShare` 字段
- 直接分享相关方法

**保留**：
- 联系人分享方法
- 权限配置
- 过期时间管理

## 数据迁移策略

### 迁移脚本 `VX__migrate_to_contact_shares.sql`

```sql
-- 1. 创建新表
-- （见上方 contact_shares 定义）

-- 2. 迁移活跃的 USER_TO_USER 分享
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
    expires_at,
    accepted_at
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
    expires_at,
    NULL -- accepted_at 需要从审计日志获取
FROM password_shares
WHERE share_type = 'USER_TO_USER'
  AND to_user_id IS NOT NULL
  AND status IN ('PENDING', 'ACTIVE', 'ACCEPTED');

-- 3. 备份旧表（可选）
RENAME TABLE password_shares TO password_shares_backup;
RENAME TABLE online_users TO online_users_backup;

-- 4. 验证数据后删除备份（手动执行）
-- DROP TABLE password_shares_backup;
-- DROP TABLE online_users_backup;
```

## 测试策略

### 单元测试
- `ContactShareService` 创建分享
- 好友关系验证
- 状态转换逻辑
- 权限验证

### 集成测试
- API 端到端测试
- 数据库约束验证
- WebSocket 通知测试

### 前端测试
- UI 测试
- ViewModel 测试
- 分享流程测试

## 回滚计划

如果需要回滚：
1. 恢复 `password_shares` 表（从备份）
2. 恢复旧代码版本
3. 重新部署后端和前端
4. 通知用户更新应用
