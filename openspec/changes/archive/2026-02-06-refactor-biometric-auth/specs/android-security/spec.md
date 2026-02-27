# Android安全规格变更 - 生物识别重构

## ADDED Requirements

### Requirement: Biometric Authentication Manager
系统SHALL提供统一的生物识别认证管理器（BiometricAuthManager）作为Level 4认证管理层。

**设计原则**:
- 应用层只触发认证，不做授权判断
- 真正的访问控制由AndroidKeyStore硬件决定
- 信任硬件保护，而不是软件实现

#### Scenario: 认证管理器初始化
- **WHEN** 应用启动或需要生物识别认证
- **THEN** SHALL创建BiometricAuthManager单例
- **AND** SHALL初始化BiometricState状态管理
- **AND** SHALL准备好BiometricPrompt UI封装

#### Scenario: 认证决策
- **GIVEN** 用户请求生物识别认证
- **WHEN** BiometricAuthManager.authenticate()被调用
- **THEN** SHALL检查是否需要认证（场景判断）
- **AND** SHALL检查失败次数是否超限
- **AND** SHALL检查是否应该防抖动
- **AND** SHALL根据决策结果触发认证或返回错误

#### Scenario: UI认证成功回调
- **GIVEN** 用户通过了生物识别UI认证
- **WHEN** BiometricPrompt认证成功
- **THEN** SHALL调用callback.onUserVerified()
- **AND** SHALL不授权任何密钥访问
- **AND** SHALL尝试使用Keystore解锁密钥

#### Scenario: Keystore授权成功回调
- **GIVEN** 用户通过UI认证且Keystore允许访问
- **WHEN** SecureKeyStorageManager.unlockWithBiometric()成功返回
- **THEN** SHALL调用callback.onKeyAccessGranted()
- **AND** 此时可以安全访问敏感数据

#### Scenario: Keystore认证过期
- **GIVEN** 用户通过UI认证但Keystore认证窗口已过期
- **WHEN** SecureKeyStorageManager.unlockWithBiometric()抛出异常
- **THEN** SHALL自动触发重新认证
- **AND** SHALL防止认证风暴（某些ROM认证窗口过短）

---

### Requirement: Biometric State Management
系统SHALL提供BiometricState类管理认证状态，只追踪失败状态，不追踪成功状态。

**安全原则**:
- 成功状态由Keystore管理，应用层不应重复
- 只缓存失败状态用于防抖动和业务级锁定
- 防止状态被误用成安全判断

#### Scenario: 防抖动检查
- **GIVEN** 用户最近认证失败（2秒内）
- **WHEN** 再次请求生物识别认证
- **THEN** SHALL返回AuthError.DEBOUNCED错误
- **AND** 不应静默失败（避免用户卡住）
- **AND** 应显示"认证处理中，请稍候"提示

#### Scenario: 业务级锁定
- **GIVEN** 用户连续认证失败5次
- **WHEN** 再次请求生物识别认证
- **THEN** SHALL返回AuthError.LOCKED_OUT错误
- **AND** SHALL要求使用主密码解锁
- **AND** 主密码解锁成功后重置失败计数

#### Scenario: 防认证风暴
- **GIVEN** 用户刚完成生物识别认证（1秒内）
- **WHEN** Keystore仍然拒绝访问（某些ROM认证窗口过短）
- **THEN** SHALL不自动触发重新认证
- **AND** SHALL返回AuthError.KEYSTORE_AUTH_EXPIRED错误
- **AND** SHALL要求使用主密码解锁

---

### Requirement: Two-Phase Callback Semantics
系统SHALL使用两阶段回调语义，明确区分UI认证成功和密钥授权成功。

**回调定义**:
- `onUserVerified()` - 用户通过UI认证（弱语义）
- `onKeyAccessGranted()` - Keystore授权成功（强语义）

#### Scenario: UI认证但Keystore过期
- **GIVEN** 用户通过了生物识别UI认证
- **WHEN** Keystore认证窗口已过期
- **THEN** SHALL调用callback.onUserVerified()
- **AND** SHALL不调用callback.onKeyAccessGranted()
- **AND** 应自动触发重新认证或降级到主密码

#### Scenario: UI认证且Keystore有效
- **GIVEN** 用户通过了生物识别UI认证
- **WHEN** Keystore认证窗口仍然有效
- **THEN** SHALL调用callback.onUserVerified()
- **AND** SHALL调用callback.onKeyAccessGranted()
- **AND** 可以安全访问敏感数据

#### Scenario: 回调误用防护
- **GIVEN** 开发者在onUserVerified()中直接访问敏感数据
- **WHEN** 密钥访问实际需要Keystore授权
- **THEN** SHALL抛出异常或返回错误
- **AND** 应引导开发者使用正确的回调（onKeyAccessGranted()）

---

### Requirement: Biometric Fallback Strategy
系统SHALL提供完善的降级策略，处理生物识别失败或不可用的情况。

#### Scenario: 设备不支持生物识别
- **GIVEN** 设备不支持生物识别或未设置生物识别信息
- **WHEN** 用户尝试使用生物识别
- **THEN** SHALL隐藏生物识别按钮
- **AND** SHALL只显示主密码输入

#### Scenario: 生物识别连续失败
- **GIVEN** 用户连续生物识别认证失败（<5次）
- **WHEN** 每次失败
- **THEN** SHALL显示错误提示
- **AND** SHALL允许重试
- **AND** 应累计失败次数

#### Scenario: 生物识别业务级锁定
- **GIVEN** 用户连续生物识别认证失败（≥5次）
- **WHEN** 再次请求生物识别
- **THEN** SHALL显示"生物识别已锁定，请使用主密码"提示
- **AND** SHALL自动显示主密码输入框
- **AND** 主密码解锁成功后重置失败计数

#### Scenario: 生物识别信息变更
- **GIVEN** 检测到KeyPermanentlyInvalidatedException
- **WHEN** 用户添加/删除了指纹或面部数据
- **THEN** SHALL调用callback.onBiometricChanged()
- **AND** SHALL清除加密数据
- **AND** SHALL要求重新设置生物识别

#### Scenario: Keystore认证过期
- **GIVEN** Keystore认证窗口已过期（30秒）
- **WHEN** 尝试使用生物识别解锁
- **THEN** SHALL自动触发重新认证
- **AND** 如果刚认证过仍失败，降级到主密码
- **AND** 应避免认证风暴（某些ROM）

---

### Requirement: Unified Biometric Authentication
生物识别认证SHALL通过BiometricAuthManager统一管理。

**设计原则**:
- 认证逻辑集中在BiometricAuthManager作为Level 4统一入口
- 架构清晰，职责明确
- 不再依赖AccountManager中的生物识别方法

#### Scenario: 统一认证入口
- **GIVEN** 应用需要生物识别认证
- **WHEN** 调用生物识别认证
- **THEN** SHALL使用BiometricAuthManager.authenticate()
- **AND** SHALL不再直接使用AccountManager.unlockWithBiometric()

#### Scenario: 场景化认证
- **GIVEN** 不同的认证场景（登录、敏感操作、自动锁定恢复）
- **WHEN** 调用生物识别认证
- **THEN** SHALL传递AuthScenario参数
- **AND** SHALL根据场景应用不同的策略

---
