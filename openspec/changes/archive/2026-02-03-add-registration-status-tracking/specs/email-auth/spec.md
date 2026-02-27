# email-auth Specification (Delta)

## ADDED Requirements

### Requirement: Email Verification Status Tracking

系统 SHALL 在用户验证邮箱后设置明确的注册状态，而不是立即激活账户。

#### Scenario: 验证邮箱后设置 EMAIL_VERIFIED 状态

- **WHEN** 用户点击验证邮件链接并提供有效的验证令牌
- **THEN** 系统应在数据库中创建用户记录
- **AND** 设置 `registration_status = 'EMAIL_VERIFIED'`
- **AND** 记录 `verified_at` 为当前时间
- **AND** 清除 Redis 中的 `PendingUser` 记录
- **AND** 返回成功响应提示用户设置主密码

#### Scenario: 验证邮箱时检测到邮箱已注册

- **WHEN** 验证令牌有效但邮箱已在数据库中注册
- **THEN** 系统应返回验证失败响应
- **AND** 清除 Redis 中的 `PendingUser` 记录
- **AND** 提示用户该邮箱已被注册

#### Scenario: 验证邮箱时检测到用户名已存在

- **WHEN** 验证令牌有效但用户名已被其他用户使用
- **THEN** 系统应返回验证失败响应
- **AND** 清除 Redis 中的 `PendingUser` 记录
- **AND** 提示用户该用户名已被使用

### Requirement: Registration Completion with Status Validation

系统 SHALL 在用户完成注册时验证其注册状态并更新为激活状态。

#### Scenario: 成功完成注册并更新状态

- **WHEN** 用户提供有效的邮箱、用户名和主密码信息
- **AND** 用户当前状态为 `EMAIL_VERIFIED`
- **AND** 验证时间未超时（默认5分钟）
- **THEN** 系统应保存主密码验证器和盐值
- **AND** 保存公钥和加密的私钥
- **AND** 设置 `registration_status = 'ACTIVE'`
- **AND** 记录 `registration_completed_at` 为当前时间
- **AND** 返回访问令牌和刷新令牌

#### Scenario: 完成注册时状态已为 ACTIVE

- **WHEN** 用户尝试完成注册
- **AND** 用户当前状态为 `ACTIVE`
- **THEN** 系统应返回 `REGISTRATION_ALREADY_COMPLETED` 错误
- **AND** 提示用户注册已完成，请直接登录

#### Scenario: 完成注册时状态不正确

- **WHEN** 用户尝试完成注册
- **AND** 用户当前状态不是 `EMAIL_VERIFIED` 或 `ACTIVE`
- **THEN** 系统应返回 `INVALID_REGISTRATION_STATUS` 错误
- **AND** 提示用户注册状态异常，请重新注册

#### Scenario: 完成注册时超时

- **WHEN** 用户尝试完成注册
- **AND** 用户当前状态为 `EMAIL_VERIFIED`
- **AND** `verified_at` 时间距当前时间超过配置的超时时间（默认5分钟）
- **THEN** 系统应删除该用户记录
- **AND** 返回 `REGISTRATION_TIMEOUT` 错误
- **AND** 提示用户注册已超时，请重新发起注册流程

#### Scenario: 完成注册时用户名不匹配

- **WHEN** 用户提供的主密码信息中的用户名与验证时的用户名不一致
- **THEN** 系统应返回 `USERNAME_MISMATCH` 错误
- **AND** 提示用户用户名不匹配

### Requirement: Registration Timeout Cleanup

系统 SHALL 定期清理超时未完成注册的用户记录。

#### Scenario: 定时任务清理超时用户

- **WHEN** 定时任务执行（默认每5分钟）
- **THEN** 系统应查询 `registration_status = 'EMAIL_VERIFIED'` 且 `verified_at` 超过配置超时时间的用户
- **AND** 删除这些用户记录
- **AND** 删除关联的 `user_private_keys` 记录（如存在）
- **AND** 记录清理日志（包括用户ID、邮箱、验证时间）
- **AND** 记录清理事件到 `verification_events` 表

#### Scenario: 清理任务执行失败不影响其他用户

- **WHEN** 清理任务执行过程中某个用户删除失败
- **THEN** 系统应记录错误日志
- **AND** 继续处理其他待清理的用户
- **AND** 不中断清理任务

#### Scenario: 清理任务可配置

- **WHEN** 管理员修改配置参数
- **THEN** 清理任务的超时时间（`registration.cleanup-timeout-minutes`）应生效
- **AND** 清理任务的执行间隔（`registration.cleanup-scheduled-interval-ms`）应生效
- **AND** 清理任务的开关（`registration.cleanup-scheduled-enabled`）应生效

### Requirement: Registration Configuration

系统 SHALL 支持配置化注册超时和清理参数。

#### Scenario: 使用默认配置

- **WHEN** 系统启动且未提供自定义配置
- **THEN** 邮箱验证 token 有效期应为 10 分钟
- **AND** 待验证用户 Redis 过期时间应为 30 分钟
- **AND** 注册完成超时时间应为 5 分钟
- **AND** 定时清理任务应启用
- **AND** 定时清理任务执行间隔应为 300000 毫秒（5分钟）

#### Scenario: 使用自定义配置

- **WHEN** 管理员在 `application.yml` 中设置自定义配置
- **THEN** 系统应读取并应用自定义配置值
- **AND** 验证、超时和清理行为应符合配置值
