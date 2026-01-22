# 任务清单

## 后端任务

### 1. 数据库迁移
- [x] 创建 `contact_shares` 表的迁移脚本
- [x] 创建删除 `password_shares` 表的迁移脚本
- [x] 创建删除 `online_users` 表的迁移脚本
- [x] 创建删除 `share_audit_logs` 表的迁移脚本
- [x] 编写数据迁移脚本（从旧表迁移活跃的 USER_TO_USER 分享）
- [ ] 测试迁移脚本在开发环境

### 2. 实体和 Repository
- [x] 创建 `ContactShare` 实体类
- [x] 创建 `ContactShareRepository` 接口
- [x] 实现 `ContactShareRepositoryImpl`
- [x] 添加好友关系验证方法

### 3. Service 层
- [x] 创建 `ContactShareService` 类
- [x] 实现创建联系人分享方法（包含好友验证）
- [x] 实现获取发送的分享列表方法
- [x] 实现获取接收的分享列表方法
- [x] 实现接受分享方法（状态转换）
- [x] 实现撤销分享方法
- [x] 实现分享过期检查定时任务
- [x] 删除 `DiscoveryService` 类

### 4. Controller 层
- [x] 创建 `ContactShareController` 类
- [x] 实现 POST `/api/v1/shares/contact` 创建分享端点
- [x] 实现 GET `/api/v1/shares/sent` 获取发送列表端点
- [x] 实现 GET `/api/v1/shares/received` 获取接收列表端点
- [x] 实现 POST `/api/v1/shares/{shareId}/accept` 接受分享端点
- [x] 实现 DELETE `/api/v1/shares/{shareId}` 撤销分享端点
- [x] 删除 `DiscoveryController` 类
- [x] 修改 `ShareController`，移除 DIRECT 和 NEARBY 相关端点

### 5. DTO 类
- [x] 创建 `CreateContactShareRequest` DTO
- [x] 创建 `ContactShareResponse` DTO
- [x] 创建 `ReceivedContactShareResponse` DTO
- [x] 创建 `AcceptShareResponse` DTO
- [x] 删除 `CreateShareRequest` 中的 shareType 字段
- [x] 简化 `ShareResponse` DTO

### 6. WebSocket 通知
- [x] 简化 `ShareNotificationService`
- [x] 移除附近用户相关通知
- [x] 更新通知格式适配新结构

### 7. 单元测试
- [ ] `ContactShareServiceTest` - 创建分享测试
- [ ] `ContactShareServiceTest` - 好友验证测试
- [ ] `ContactShareServiceTest` - 状态转换测试
- [ ] `ContactShareServiceTest` - 权限测试
- [ ] `ContactShareRepositoryTest` - 数据访问测试
- [ ] `ContactShareControllerTest` - API 端点测试

### 8. 集成测试
- [ ] 联系人分享端到端测试
- [ ] WebSocket 通知集成测试
- [ ] 数据库约束验证测试

---

## 前端任务 (SafeVault Android)

### 1. UI 修改
- [x] 修改 `ShareActivity` - 移除分享方式选择（已实现，仅支持联系人分享）
- [x] 修改 `ShareActivity` - 仅显示联系人选择按钮（已实现）
- [x] 删除 `NearbyUsersActivity`（不存在，已清理相关布局）
- [x] 删除 `NearbyUsersAdapter`（不存在）
- [x] 修改 `ShareListFragment` - 移除分享类型筛选（已移除离线/云端标签）

### 2. ViewModel 简化
- [x] 修改 `ShareViewModel` - 移除 `ShareType` 相关逻辑
- [x] 修改 `ShareViewModel` - 移除离线分享方法
- [x] 修改 `ShareViewModel` - 简化分享创建逻辑

### 3. DTO 类更新
- [x] 修改 `CreateShareRequest` - 移除 shareType 字段（当前实现无此字段）
- [x] 修改 `ShareType` 枚举 - 删除 DIRECT 和 NEARBY（前端未使用）
- [x] 修改 `ReceivedShareResponse` - 移除 shareType 字段（无需修改）

### 4. 网络 API
- [x] 修改 `ShareApiService` - 更新 API 端点（现有实现已是联系人分享）
- [x] 删除 `DiscoveryApiService`（不存在）
- [x] 更新 WebSocket 消息处理（无需修改）

### 5. 导航和路由
- [x] 从导航图中移除 `NearbyUsersActivity`（不存在）
- [x] 更新分享流程跳转逻辑（已是联系人分享流程）

### 6. 测试
- [ ] `ShareViewModelTest` - 单元测试更新
- [ ] `ShareActivityTest` - UI 测试更新
- [ ] 手动测试完整分享流程

---

## 文档任务

- [ ] 更新 API 文档
- [ ] 更新数据库架构文档
- [ ] 编写迁移指南（如有需要）
- [ ] 更新用户使用文档

---

## 部署任务

### 后端部署
- [ ] 在测试环境部署并验证
- [ ] 执行数据库迁移
- [ ] 验证 API 功能
- [ ] 监控错误日志

### 前端部署
- [ ] 构建 APK 并在测试设备验证
- [ ] 测试完整分享流程
- [ ] 验证通知功能

### 生产部署
- [ ] 备份生产数据库
- [ ] 执行生产环境数据库迁移
- [ ] 部署新版本后端
- [ ] 发布新版本前端
- [ ] 监控生产环境指标

---

## 验证任务

- [ ] 所有单元测试通过
- [ ] 所有集成测试通过
- [ ] 手动测试完成
- [ ] 代码审查完成
- [ ] 性能测试通过
- [ ] 安全审查通过

---

## 回滚计划（如有需要）

- [ ] 准备回滚脚本
- [ ] 恢复数据库备份
- [ ] 重新部署旧版本
