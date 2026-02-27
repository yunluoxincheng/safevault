## 1. 数据库模型更新

- [x] 1.1 在 `User` 实体中新增 `registrationStatus` 字段（VARCHAR，默认 "EMAIL_VERIFIED"）
- [x] 1.2 在 `User` 实体中新增 `verifiedAt` 字段（LocalDateTime）
- [x] 1.3 在 `User` 实体中新增 `registrationCompletedAt` 字段（LocalDateTime）
- [x] 1.4 创建数据库迁移脚本（Flyway 或 Liquibase）

## 2. 认证服务修改

- [x] 2.1 修改 `verifyEmail` 方法，设置 `registrationStatus = "EMAIL_VERIFIED"` 和 `verifiedAt`
- [x] 2.2 修改 `completeRegistration` 方法，增加状态验证逻辑
- [x] 2.3 修改 `completeRegistration` 方法，增加超时检查逻辑
- [x] 2.4 修改 `completeRegistration` 方法，设置状态为 `ACTIVE` 和 `registrationCompletedAt`
- [x] 2.5 新增错误码 `REGISTRATION_TIMEOUT` 和 `INVALID_REGISTRATION_STATUS`
- [x] 2.6 更新 `VerifyEmailResponse` 和 `CompleteRegistrationResponse` DTO

## 3. 定时清理任务

- [x] 3.1 创建 `RegistrationCleanupService` 类
- [x] 3.2 实现清理逻辑：查询超时用户并删除
- [x] 3.3 在 `ScheduledTasks` 中新增定时清理方法（每5分钟执行）
- [x] 3.4 添加清理日志记录
- [x] 3.5 添加清理事件记录到 `verification_events` 表

## 4. 配置管理

- [x] 4.1 在 `application.yml` 中新增 `registration.cleanup-timeout-minutes` 配置
- [x] 4.2 在 `application.yml` 中新增 `registration.cleanup-scheduled-enabled` 配置
- [x] 4.3 在 `application.yml` 中新增 `registration.cleanup-scheduled-interval-ms` 配置
- [x] 4.4 创建配置属性类 `RegistrationProperties`

## 5. 测试

- [x] 5.1 编写单元测试：验证邮箱后状态正确设置
- [x] 5.2 编写单元测试：完成注册后状态正确更新
- [x] 5.3 编写单元测试：超时场景正确抛出异常
- [x] 5.4 编写单元测试：定时清理任务正确删除超时用户
- [x] 5.5 编写集成测试：完整注册流程
- [x] 5.6 编写集成测试：超时注册流程
- [x] 5.7 编写集成测试：并发场景测试

## 6. 文档

- [x] 6.1 更新 API 文档，说明新的状态转换流程
- [x] 6.2 更新错误码文档
- [x] 6.3 更新配置说明文档
