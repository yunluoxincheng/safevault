# Tasks: 重构生物识别认证架构

## 1. 实现阶段 1：创建新组件

### 1.1 创建包结构和基础类
- [x] 1.1.1 创建 `security/biometric/` 包
- [x] 1.1.2 创建 `BiometricState.java` 框架
- [x] 1.1.3 创建 `AuthScenario.java` 枚举
- [x] 1.1.4 创建 `AuthError.java` 枚举
- [x] 1.1.5 创建 `AuthCallback.java` 接口

### 1.2 实现 BiometricState
- [x] 1.2.1 实现失败追踪功能（防抖动）
  - [x] `shouldDebouncePrompt()` 方法
  - [x] `recordFailure()` 方法
- [x] 1.2.2 实现业务级锁定功能
  - [x] `recordFailure()` 累计次数
  - [x] `isLockedOut()` 检查
  - [x] `resetFailures()` 重置
- [x] 1.2.3 实现防认证风暴功能
  - [x] `recentlyReauthenticated()` 检查
  - [x] `updateLastAuthTime()` 更新
  - [x] `resetLastAuthTime()` 重置

### 1.3 实现 BiometricAuthManager
- [x] 1.3.1 实现核心认证流程
  - [x] `authenticate(AuthScenario, AuthCallback)` 主入口
  - [x] `shouldAuthenticate(AuthScenario)` 决策
  - [x] `triggerBiometricPrompt()` UI 调用
- [x] 1.3.2 实现密钥访问流程
  - [x] `tryUnlockWithKeystore()` 尝试解锁
  - [x] `triggerReauthentication()` 重新认证
  - [x] Keystore 异常处理集成
- [x] 1.3.3 实现降级流程
  - [x] `fallbackToPassword()` 主密码降级
  - [x] `shouldFallbackToPassword()` 决策
- [x] 1.3.4 实现工具方法
  - [x] `canUseBiometric()` 检查可用性
  - [x] `disableBiometric()` 禁用

### 1.4 编写单元测试
- [ ] 1.4.1 BiometricState 测试
  - [ ] 防抖动测试
  - [ ] 失败计数测试
  - [ ] 锁定测试
  - [ ] 防认证风暴测试
- [ ] 1.4.2 BiometricAuthManager 测试
  - [ ] 认证路由测试
  - [ ] 防抖动测试
  - [ ] 业务级锁定测试
  - [ ] 降级逻辑测试

---

## 2. 实现阶段 2：集成到登录流程

### 2.1 修改 LoginActivity
- [x] 2.1.1 更新 `performBiometricAuthentication()` 方法
  - [x] 使用 BiometricAuthManager 替代直接调用
  - [x] 实现 `onUserVerified()` 回调
  - [x] 实现 `onKeyAccessGranted()` 回调
  - [x] 实现 `onFailure()` 回调（处理 DEBOUNCED 错误）
  - [x] 实现 `onBiometricChanged()` 回调
- [x] 2.1.2 添加 `tryUnlockWithKeystore()` 辅助方法（由 BiometricAuthManager 内部处理）
- [x] 2.1.3 添加 `showBiometricChangedDialog()` 方法

### 2.2 修改 AccountSecurityFragment
- [x] 2.2.1 更新启用生物识别逻辑
  - [x] 使用 BiometricAuthManager 替代 AccountManager
  - [x] 实现新的回调处理
- [x] 2.2.2 更新禁用生物识别逻辑
- [x] 2.2.3 更新生物识别按钮可见性检查

### 2.3 集成测试
- [ ] 2.3.1 测试登录流程
  - [ ] 指纹解锁成功
  - [ ] 面部识别解锁成功
  - [ ] 生物识别失败降级到主密码
  - [ ] 生物识别信息变更处理
- [ ] 2.3.2 测试启用/禁用流程
  - [ ] 启用生物识别成功
  - [ ] 禁用生物识别成功
  - [ ] 设备不支持时的处理

---

## 3. 实现阶段 3：清理旧代码

### 3.1 清理 AccountManager
- [x] 3.1.1 移除 `unlockWithBiometric()` 方法
- [x] 3.1.2 移除 `canUseBiometricAuthentication()` 方法
- [x] 3.1.3 移除 `enableBiometricAuth()` 方法
- [x] 3.1.4 移除 `disableBiometricAuth()` 方法
- [x] 3.1.5 清理相关的导入和注释

### 3.2 移除 BackendService 接口中的旧方法
- [x] 3.2.1 从 `BackendService.java` 移除接口方法
- [x] 3.2.2 从 `BackendServiceImpl.java` 移除实现

### 3.3 修复所有依赖文件
- [x] 3.3.1 修复 `LoginViewModel.java` - 使用 BiometricAuthManager，移除 loginWithBiometric()
- [x] 3.3.2 修复 `ShareActivity.java` - 使用 BiometricAuthManager.canUseBiometric()
- [x] 3.3.3 修复 `QRShareActivity.java` - 使用 BiometricAuthManager.canUseBiometric()
- [x] 3.3.4 修复 `BluetoothShareActivity.java` - 使用 BiometricAuthManager.canUseBiometric()
- [x] 3.3.5 修复 `AutofillCredentialSelectorActivity.java` - 使用 BiometricAuthManager.authenticate()
- [x] 3.3.6 编译构建验证成功

### 3.4 验证和文档
- [ ] 3.4.1 运行所有单元测试
- [ ] 3.4.2 运行所有集成测试
- [ ] 3.4.3 真机测试（主流厂商）
- [ ] 3.4.4 更新代码注释和文档

---

## 4. 测试和验证

### 4.1 单元测试
- [ ] 4.1.1 BiometricState 测试覆盖率 > 90%
- [ ] 4.1.2 BiometricAuthManager 测试覆盖率 > 80%

### 4.2 集成测试
- [ ] 4.2.1 登录流程测试
- [ ] 4.2.2 启用/禁用测试
- [ ] 4.2.3 降级流程测试

### 4.3 真机测试
- [ ] 4.3.1 Samsung 设备测试
- [ ] 4.3.2 Xiaomi 设备测试
- [ ] 4.3.3 Huawei 设备测试
- [ ] 4.3.4 OPPO/vivo 设备测试
- [ ] 4.3.5 OnePlus 设备测试

### 4.4 性能测试
- [ ] 4.4.1 认证响应时间 < 200ms
- [ ] 4.4.2 内存占用 < 50KB
- [ ] 4.4.3 无内存泄漏

---

## 5. 文档和发布

### 5.1 代码文档
- [ ] 5.1.1 添加类注释
- [ ] 5.1.2 添加方法注释
- [ ] 5.1.3 添加关键代码段注释

### 5.2 用户文档
- [ ] 5.2.1 更新用户手册（如需要）
- [ ] 5.2.2 准备发布说明

### 5.3 发布准备
- [ ] 5.3.1 版本号更新
- [ ] 5.3.2 变更日志更新
- [ ] 5.3.3 发布到测试环境
- [ ] 5.3.4 收集反馈并修复问题
