## Context

当前邮箱注册流程分为三个阶段：
1. 发起注册（`registerWithEmail`）：用户信息存入 Redis（`PendingUser`），发送验证邮件
2. 验证邮箱（`verifyEmail`）：**当前立即创建数据库用户记录**
3. 完成注册（`completeRegistration`）：设置主密码和加密密钥

**问题**：第2步验证成功后立即创建用户记录，如果用户不再完成第3步，数据库中会留下不完整的用户记录（只有邮箱验证状态，没有主密码）。

**约束**：
- 必须兼容 Android 10+ 前端
- 前端通过 BackendService 接口与后端通信
- 使用 Redis 存储待验证用户（30分钟过期）
- 使用 PostgreSQL 存储正式用户数据

## Goals / Non-Goals

**Goals**:
- 数据库中的用户记录状态明确可追溯
- 自动清理超时未完成注册的用户
- 支持配置化超时时间
- 提供清晰的错误提示引导用户重新注册

**Non-Goals**:
- 不修改前端 Android 代码（错误处理除外）
- 不改变现有的加密密钥管理方式
- 不实现"注册提醒"功能（可作为未来扩展）

## Decisions

### Decision 1: 在数据库中引入注册状态字段

**选择**：在 `users` 表中新增 `registration_status`、`verified_at`、`registration_completed_at` 字段。

**原因**：
- 状态明确可追溯，便于数据分析
- 支持灵活的超时清理策略
- 可以计算验证转化率

**Alternatives considered**:
- **延迟数据库写入**：验证成功后只更新 Redis 状态，设置主密码时才写入数据库
  - 优点：数据库中没有不完整记录
  - 缺点：Redis 数据可能过期，用户无法继续注册；需要重构 Redis 数据结构
- **只修改完成注册逻辑**：保持当前流程，在 `completeRegistration` 中检查是否已设置主密码
  - 优点：改动最小
  - 缺点：数据库中仍会有不完整记录，需要额外的清理机制

### Decision 2: 使用定时任务清理超时用户

**选择**：使用 Spring `@Scheduled` 注解创建定时任务，每5分钟清理一次超时用户。

**原因**：
- 实现简单，Spring 原生支持
- 可配置化执行间隔
- 不需要外部依赖

**Alternatives considered**:
- **数据库事件触发器**：使用 pg_cron 扩展或 PostgreSQL 触发器
  - 优点：性能好
  - 缺点：需要安装扩展，难以记录日志和错误处理
- **消息队列延迟任务**：使用 RabbitMQ 或 Redis 延迟队列
  - 优点：精确控制每个用户的超时时间
  - 缺点：引入额外复杂度，当前场景不需要

### Decision 3: 定义明确的注册状态枚举

**选择**：使用字符串枚举 `EMAIL_VERIFIED`、`ACTIVE`。

**原因**：
- 清晰表达用户在注册流程中的位置
- 便于未来扩展（如 `SUSPENDED` 状态）
- 数据库查询友好

**状态转换流程**：
```
Redis: PendingUser → verifyEmail() → DB: EMAIL_VERIFIED → completeRegistration() → DB: ACTIVE
                                  ↓
                          超时5分钟后清理
```

### Decision 4: 超时用户直接删除

**选择**：超时用户直接从数据库中删除，而非软删除。

**原因**：
- 这些用户未完成注册，属于无效数据
- 没有业务价值保留
- 简化实现

**Alternatives considered**:
- **软删除**：标记为 `DELETED` 状态
  - 优点：可以恢复，便于审计
  - 缺点：增加数据库维护成本，未完成注册的用户数据价值低

## Risks / Trade-offs

### Risk 1: 并发场景下用户正在完成注册时被清理任务删除

**缓解措施**：
- `completeRegistration` 方法使用 `@Transactional` 事务保证原子性
- 清理任务删除前再次检查用户状态（确保仍是 `EMAIL_VERIFIED`）
- 超时时间设置为5分钟，给用户足够时间完成注册

### Risk 2: 数据库迁移可能影响现有用户

**缓解措施**：
- 新增字段设置默认值（`registration_status = 'ACTIVE'` 假设现有用户已完成注册）
- 迁移脚本先备份数据
- 迁移前在测试环境验证

### Risk 3: 定时任务可能影响数据库性能

**缓解措施**：
- 查询使用索引（`registration_status` 和 `verified_at`）
- 执行间隔可配置（默认5分钟）
- 监控清理任务的执行时间和影响行数

## Migration Plan

### 数据库迁移步骤

1. **新增字段**：
```sql
ALTER TABLE users
ADD COLUMN registration_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
ADD COLUMN verified_at TIMESTAMP NULL,
ADD COLUMN registration_completed_at TIMESTAMP NULL;
```

2. **创建索引**：
```sql
CREATE INDEX idx_users_registration_status ON users(registration_status);
CREATE INDEX idx_users_verified_at ON users(verified_at);
```

3. **处理现有数据**：
- 现有用户假设已完成注册，设置 `registration_status = 'ACTIVE'`
- 有 `email_verified = true` 且 `password_verifier IS NULL` 的用户设置为 `EMAIL_VERIFIED`

### 回滚计划

如果需要回滚：
1. 停止定时清理任务（配置 `registration.cleanup-scheduled-enabled = false`）
2. 代码回滚到修改前的版本
3. 可选：删除新增的字段和索引

## Open Questions

- [ ] 是否需要为超时用户发送"完成注册"提醒邮件？（暂不实现）
- [ ] 超时时间默认5分钟是否合适？（可配置）
- [ ] 是否需要记录清理任务的详细日志用于分析？（建议记录）
