# Change: 添加用户注册状态追踪与超时清理机制

## Why

当前邮箱注册流程存在数据一致性问题：用户验证邮箱后立即在数据库中创建用户记录，但如果用户验证后未完成主密码设置，数据库中会留下不完整的用户记录。这些记录占用数据库空间、污染用户数据，且缺乏清理机制。

## What Changes

- 在 `users` 表中新增注册状态字段（`registration_status`、`verified_at`、`registration_completed_at`）
- 修改邮箱验证流程，验证成功后设置状态为 `EMAIL_VERIFIED` 而非立即激活
- 修改完成注册流程，增加状态验证和超时检查
- 新增定时清理任务，自动清理超时未完成注册的用户
- 新增配置参数，支持自定义超时时间
- 新增错误码 `REGISTRATION_TIMEOUT`，用于提示用户重新注册

## Impact

- **Affected specs**: `auth-refresh`
- **Affected code**:
  - `safevault-backend/src/main/java/org/ttt/safevaultbackend/entity/User.java` - 新增状态字段
  - `safevault-backend/src/main/java/org/ttt/safevaultbackend/service/AuthService.java` - 修改验证和注册流程
  - `safevault-backend/src/main/java/org/ttt/safevaultbackend/config/ScheduledTasks.java` - 新增清理任务
  - `safevault-backend/src/main/resources/application.yml` - 新增配置项
