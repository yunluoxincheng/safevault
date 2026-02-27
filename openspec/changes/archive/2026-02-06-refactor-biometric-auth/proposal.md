# Change: 重构生物识别认证架构

## Why

当前生物识别功能存在以下关键问题：

1. **架构不一致** - 存在两套系统：
   - 旧流程：`BiometricKeyManager` 加密存储主密码（已移除）
   - 新流程：会话内存中的 `sessionMasterPassword`（当前实现）
   - 导致代码混乱，职责不清

2. **安全隐患** - `AccountManager.unlockWithBiometric()` 直接使用内存中的主密码，绕过了三层密钥存储架构的安全保护

3. **生物识别无法启用** - 检查 `SecureKeyStorageManager.isMigrated()` 时失败，因为新用户可能还未完成迁移

4. **缺少统一的认证管理层** - 认证逻辑分散在多个组件中：
   - `BiometricAuthHelper` - UI 交互
   - `SecureKeyStorageManager` - 密钥存储
   - `AccountManager` - 业务逻辑

## What Changes

### 核心变更：添加 Level 4 认证管理层

```
现有三层密钥存储架构（保持不变）：
┌────────────────────────┐
│   Level 3: RSA 私钥     │
└──────────▲─────────────┘
           │ DataKey
┌──────────┴─────────────┐
│   Level 2: DataKey      │
└──────────▲─────────────┘
           │
┌──────────┴─────────────┐
│   Level 1: DeviceKey   │ ← 生物识别认证授权
└────────────────────────┘

新增 Level 4（认证管理层）：
┌────────────────────────────────┐
│   BiometricAuthManager         │
│  • 认证决策与路由              │
│  • UI 管理                     │
│  • 业务级安全策略              │
│  • 状态管理                    │
└────────────┬───────────────────┘
             │ 仅触发认证，不做授权判断
             ↓
      AndroidKeyStore（硬件级访问控制）
```

### 设计原则

**信任硬件，而不是软件实现**：
- ❌ 应用层不自作聪明生成 SessionToken
- ❌ 应用层不做 Nonce 防重放（Keystore 已做）
- ❌ 应用层不做时间戳防篡改（系统已做）
- ✅ 应用层只触发认证，让 Keystore 决定是否允许访问

### 职责划分

**Level 4 (BiometricAuthManager) 负责**：
- ✅ 认证方式路由（生物识别/主密码/PIN）
- ✅ UI 管理（对话框、提示、降级）
- ✅ 业务级安全策略（失败次数、用户偏好）
- ✅ 是否需要认证的决策
- ✅ 触发 BiometricPrompt
- ❌ 真正授权密钥使用（交给 Keystore）

**AndroidKeyStore 负责**：
- ✅ 硬件级访问控制（认证窗口 30 秒）
- ✅ 防重放（每次使用密钥都检查时效）
- ✅ 防篡改（硬件保护，无法绕过）
- ✅ 生物识别变更检测（KeyPermanentlyInvalidatedException）

### 新增组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `BiometricAuthManager` | `security/biometric/BiometricAuthManager.java` | 核心管理器 |
| `BiometricState` | `security/biometric/BiometricState.java` | 状态管理 |
| `AuthScenario` | `security/biometric/AuthScenario.java` | 场景枚举 |
| `AuthCallback` | `security/biometric/AuthCallback.java` | 回调接口 |
| `AuthError` | `security/biometric/AuthError.java` | 错误枚举 |

### 关键安全改进

1. **移除危险的状态缓存**：
   - ❌ 不缓存"成功状态"（lastAuthTime）
   - ✅ 只缓存"失败状态"（用于防抖动）
   - ✅ 成功是否有效 → 完全交给 Keystore 验证

2. **分离 UI 认证和密钥授权**：
   - ❌ `onSuccess()` - 语义太强，容易被误解
   - ✅ `onUserVerified()` - UI 通过（弱语义）
   - ✅ `onKeyAccessGranted()` - Keystore 授权成功（强语义）

3. **防止工程问题**：
   - 防抖动不应静默失败（返回 DEBOUNCED 错误）
   - 防止认证风暴（某些 ROM 认证窗口过短）

### 修改的组件

**移除的功能**：
- `AccountManager.unlockWithBiometric()` - 迁移到 BiometricAuthManager
- `AccountManager.canUseBiometricAuthentication()` - 迁移到 BiometricAuthManager
- `AccountManager.enableBiometricAuth()` - 迁移到 BiometricAuthManager
- `AccountManager.disableBiometricAuth()` - 迁移到 BiometricAuthManager

**保留的功能**：
- `BiometricAuthHelper` - 作为 UI 交互的底层封装
- `SecureKeyStorageManager` - 保持不变（三层密钥存储）

## Impact

### 受影响的规格
- `android-security` - Android 生物识别认证

### 受影响的代码

#### 新增文件
```
app/src/main/java/com/ttt/safevault/security/biometric/
├── BiometricAuthManager.java
├── BiometricState.java
├── AuthScenario.java
├── AuthCallback.java
└── AuthError.java
```

#### 修改文件
- `app/src/main/java/com/ttt/safevault/service/manager/AccountManager.java` - 移除生物识别相关方法
- `app/src/main/java/com/ttt/safevault/ui/LoginActivity.java` - 使用 BiometricAuthManager
- `app/src/main/java/com/ttt/safevault/ui/AccountSecurityFragment.java` - 使用 BiometricAuthManager

### 兼容性

#### 数据兼容性
- 无需数据迁移
- `SecurityConfig` 中的 `biometric_enabled` 设置保持不变
- `SecureKeyStorageManager` 中的密钥保持不变
- 只是改变了认证流程，不改变存储格式

#### 向后兼容
- 老用户继续使用旧代码（阶段 1-2）
- 阶段 3 清除旧代码后，自动迁移到新架构
- 迁移过程透明，无需用户操作

## Migration Plan

### 阶段 1：创建新组件（2-3 天）
- 创建 BiometricAuthManager
- 创建 BiometricState
- 创建 AuthScenario、AuthCallback、AuthError
- 编写单元测试

### 阶段 2：集成到登录流程（2-3 天）
- 修改 LoginActivity 使用 BiometricAuthManager
- 修改 AccountSecurityFragment 启用流程
- 测试登录和启用场景

### 阶段 3：清理旧代码（1 天）
- 移除 AccountManager 中的旧生物识别逻辑
- 保留 BiometricAuthHelper（作为 UI 封装）
- 最终验证

## Testing Requirements

### 单元测试
- BiometricState 测试（防抖动、失败计数、防认证风暴）
- BiometricAuthManager 测试（认证路由、降级逻辑）

### 集成测试
- 登录流程集成测试
- 启用/禁用生物识别测试

### 真机测试
- 各厂商 ROM 兼容性测试（Samsung, Xiaomi, Huawei, OPPO, vivo, OnePlus）
- 生物识别类型测试（指纹、面部、虹膜）
- 认证风暴测试（国产 ROM）
- 边界测试（Activity 重建、App 切换）

### 性能测试
- 认证响应时间 < 200ms
- 内存占用 < 50KB
- 无内存泄漏
