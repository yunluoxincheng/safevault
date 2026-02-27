# Design: 重构生物识别认证架构

## Context

### 当前问题

SafeVault 的生物识别功能经历了多次迭代，导致架构不一致：

**历史演变**：
1. **第一版**：使用 `BiometricKeyManager` 将主密码加密存储在 AndroidKeyStore
2. **第二版**：改为会话内存中的 `sessionMasterPassword`，移除 BiometricKeyManager
3. **当前状态**：三层密钥存储架构（SecureKeyStorageManager）已实现，但生物识别认证逻辑仍分散在多个组件中

**架构不一致导致的隐患**：
```java
// ❌ 当前实现（AccountManager.java:138-173）
public boolean unlockWithBiometric() {
    if (sessionMasterPassword == null || sessionMasterPassword.isEmpty()) {
        return false;  // 直接使用内存中的主密码
    }
    boolean success = cryptoManager.unlock(sessionMasterPassword);
    return success;
}

// 问题：绕过了三层密钥存储架构的安全保护
// SecureKeyStorageManager.unlockWithBiometric() 使用 Keystore 授权
// 但 AccountManager 没有调用它
```

**生物识别无法启用**：
- `AccountSecurityFragment` 检查 `SecureKeyStorageManager.isMigrated()`
- 新用户可能还未完成迁移，导致无法启用生物识别

### 约束条件

1. **必须保持三层密钥存储架构** - 这是安全基础，不能改动
2. **必须向后兼容** - 老用户的数据不能丢失
3. **必须支持 Android 10+** - 最小 SDK 29，目标 SDK 36
4. **必须支持多种生物识别** - 指纹、面部、虹膜

### 利益相关者

- **终端用户** - 需要快速、安全的生物识别解锁
- **安全审计** - 需要明确的安全边界和职责划分
- **开发团队** - 需要清晰的架构和易于维护的代码

---

## Goals / Non-Goals

### Goals

1. **统一认证管理层** - 创建 BiometricAuthManager 作为单一入口
2. **信任硬件保护** - 让 Keystore 决定密钥访问权限，而不是应用层
3. **清晰职责划分** - Level 4 负责认证流程，Keystore 负责访问控制
4. **工程健壮性** - 防止静默失败、认证风暴等实际问题
5. **易于维护** - 代码集中，逻辑清晰

### Non-Goals

1. **不修改三层密钥存储架构** - SecureKeyStorageManager 保持不变
2. **不修改数据格式** - 无需数据迁移
3. **不改变用户体验** - 认证流程对用户保持一致

---

## Decisions

### Decision 1: 添加 Level 4 认证管理层

**What**:
在现有三层密钥存储架构之上，添加第四层认证管理层。

**Why**:
- 当前认证逻辑分散在多个组件中，难以维护
- 需要统一的认证路由和策略管理
- 需要清晰的职责划分

**Architecture**:
```
Level 4: BiometricAuthManager (新增)
  • 认证决策与路由
  • UI 管理
  • 业务级安全策略
  • 状态管理

Level 1-3: 密钥存储层 (现有，保持不变)
  • Level 3: RSA 私钥（DataKey 加密）
  • Level 2: DataKey（PasswordKey + DeviceKey 双重加密）
  • Level 1: DeviceKey（AndroidKeyStore 硬件保护）
```

**Alternatives Considered**:
1. **修改现有三层架构** - 风险高，可能破坏现有安全机制
2. **创建独立的认证服务** - 增加复杂度，难以维护
3. **添加第四层（已选择）** - 最小改动，职责清晰

---

### Decision 2: 信任硬件，而不是软件实现

**What**:
应用层不维护"认证成功状态"，完全交给 Keystore 验证。

**Why**:
- 应用层的 Token/时间戳可以被 Hook 攻击
- Keystore 提供硬件级保护，无法绕过
- 遵循 Android 安全最佳实践

**Code Pattern**:
```java
// ❌ 错误做法
if (state.isWithinAuthWindow()) {
    return;  // 绕过 UI 认证
}

// ✅ 正确做法
try {
    PrivateKey key = secureStorage.unlockWithBiometric();
    // Keystore 自己检查认证时效
} catch (KeyPermanentlyInvalidatedException e) {
    // 生物识别已变更
}
```

**Alternatives Considered**:
1. **应用层管理 Token** - 容易被绕过，不安全
2. **应用层管理时间戳** - 可以被 Hook 修改
3. **信任 Keystore（已选择）** - 硬件级保护，符合最佳实践

---

### Decision 3: 分离 UI 认证和密钥授权

**What**:
将回调分为两个阶段：`onUserVerified()` 和 `onKeyAccessGranted()`。

**Why**:
- `onSuccess()` 语义太强，容易被误解为"已授权"
- UI 认证成功 ≠ 密钥可用（Keystore 可能已过期）
- 需要清晰的语义区分

**API Design**:
```java
public interface AuthCallback {
    // 阶段1：用户通过 UI 认证（弱语义）
    void onUserVerified();

    // 阶段2：Keystore 授权成功（强语义）
    void onKeyAccessGranted();

    // 其他回调...
    void onFailure(AuthError error, String message, boolean canRetry);
    void onCancel();
    void onBiometricChanged();
}
```

**Usage Example**:
```java
@Override
public void onUserVerified() {
    // 此时不能直接访问敏感数据
    try {
        PrivateKey key = secureStorage.unlockWithBiometric();
        if (key != null) {
            onKeyAccessGranted();  // Keystore 授权成功
        } else {
            onFailure(AuthError.KEYSTORE_AUTH_EXPIRED, "认证已过期", true);
        }
    } catch (KeyPermanentlyInvalidatedException e) {
        onBiometricChanged();
    }
}

@Override
public void onKeyAccessGranted() {
    // 现在可以安全访问敏感数据
    showPasswords();
}
```

**Alternatives Considered**:
1. **保持 onSuccess()** - 语义不清，容易误用
2. **添加 isAuthorized 参数** - 仍然不清晰
3. **两阶段回调（已选择）** - 语义清晰，符合实际流程

---

### Decision 4: 只缓存失败状态，不缓存成功状态

**What**:
BiometricState 只追踪失败相关的状态，不追踪"认证成功"时间。

**Why**:
- 缓存"成功状态"会被误用成安全判断
- Hook 攻击可以修改时间，绕过认证
- 成功状态由 Keystore 管理，应用层不应重复

**State Design**:
```java
public class BiometricState {
    // ✅ 保留：失败追踪（业务级）
    private long lastFailureTime;
    private int consecutiveFailures;
    private boolean lockedOut;

    // ❌ 删除：成功状态（交给 Keystore）
    // private long lastAuthTime;  // 危险！
    // public boolean isWithinAuthWindow() { ... }  // 危险！
}
```

**Alternatives Considered**:
1. **同时缓存成功和失败** - 成功状态会带来安全风险
2. **只缓存成功** - 无法做防抖动等 UX 优化
3. **只缓存失败（已选择）** - 安全且满足需求

---

### Decision 5: 防抖动不应静默失败

**What**:
当防抖动触发时，返回 `DEBOUNCED` 错误，而不是静默返回。

**Why**:
- 静默失败会让用户卡住（既没弹认证，也没降级入口）
- 安全系统最怕"静默失败"
- 需要让 UI 层知道发生了什么

**Code Pattern**:
```java
// ❌ 错误做法
if (state.shouldDebouncePrompt()) {
    return;  // 静默失败，用户卡住
}

// ✅ 正确做法
if (state.shouldDebouncePrompt()) {
    callback.onFailure(AuthError.DEBOUNCED,
                      "认证处理中，请稍候",
                      false);
    return;
}
```

**UI Handling**:
```java
@Override
public void onFailure(AuthError error, String message, boolean canRetry) {
    if (error == AuthError.DEBOUNCED) {
        // 显示轻量级提示，不阻塞用户
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    } else {
        // 其他错误正常处理
        showErrorDialog(message, canRetry);
    }
}
```

**Alternatives Considered**:
1. **静默失败** - 用户体验差，容易卡住
2. **弹窗提示** - 打扰用户，过于严重
3. **Toast 提示（已选择）** - 轻量级，不打扰

---

### Decision 6: 防止认证风暴

**What**:
添加 `recentlyReauthenticated()` 检查，避免在某些 ROM 上重复弹窗。

**Why**:
- 部分国产 ROM（MIUI、ColorOS 等）认证窗口只有 0 秒
- 每次使用 DeviceKey 都要求重新认证
- 导致用户操作一次，弹两次指纹（循环弹窗）

**Code Pattern**:
```java
public void tryUnlockWithBiometric(@NonNull AuthCallback callback) {
    // 检查是否刚认证过（防止认证风暴）
    if (state.recentlyReauthenticated()) {
        // 刚认证过但仍然失败，说明 ROM 认证窗口过短
        // 此时应该降级到主密码，而不是再次弹窗
        callback.onFailure(AuthError.KEYSTORE_AUTH_EXPIRED,
                          "认证已过期，请使用主密码解锁",
                          true);
        return;
    }

    // 尝试使用 Keystore
    try {
        PrivateKey key = keyStorage.unlockWithBiometric();
        if (key != null) {
            state.updateLastAuthTime();
            callback.onKeyAccessGranted();
        } else {
            triggerReauthentication(callback);
        }
    } catch (KeyPermanentlyInvalidatedException e) {
        callback.onBiometricChanged();
    }
}
```

**Alternatives Considered**:
1. **无限重试** - 导致认证风暴，用户体验差
2. **限制重试次数** - 难以确定合适的阈值
3. **时间间隔保护（已选择）** - 简单有效

---

## Risks / Trade-offs

### Risk 1: 架构复杂度增加

**风险**: 添加第四层可能增加系统复杂度

**缓解措施**:
- 职责明确：Level 4 只负责认证流程，不负责密钥管理
- 文档完善：每个组件都有清晰的职责说明
- 测试覆盖：单元测试、集成测试、真机测试

### Risk 2: 迁移期间的功能回归

**风险**: 清理旧代码时可能破坏现有功能

**缓解措施**:
- 渐进式迁移：三阶段逐步推进
- 充分测试：每个阶段都进行完整测试
- 回滚计划：保留旧代码作为备份

### Risk 3: ROM 兼容性问题

**风险**: 不同厂商的 ROM 可能有不同的生物识别行为

**缓解措施**:
- 真机测试：覆盖主流厂商（Samsung, Xiaomi, Huawei, OPPO, vivo, OnePlus）
- 降级机制：任何失败都能降级到主密码
- 错误提示：清晰的错误信息和解决建议

### Trade-off: 性能 vs 安全

**选择**: 每次使用密钥都让 Keystore 验证认证时效

**权衡**:
- 优点：更安全，无法绕过
- 缺点：可能增加微小的性能开销

**结论**: 安全性优先，性能开销可接受

---

## Migration Plan

### Phase 1: 创建新组件（不影响现有功能）

**目标**: 创建 BiometricAuthManager 等新组件

**步骤**:
1. 创建 `security/biometric/` 包
2. 实现 BiometricAuthManager
3. 实现 BiometricState
4. 实现 AuthScenario、AuthCallback、AuthError
5. 编写单元测试

**验收标准**:
- 所有单元测试通过
- 代码审查通过

### Phase 2: 集成到登录流程

**目标**: 修改 LoginActivity 和 AccountSecurityFragment

**步骤**:
1. 修改 LoginActivity 使用 BiometricAuthManager
2. 修改 AccountSecurityFragment 启用流程
3. 测试登录场景
4. 测试启用/禁用场景

**验收标准**:
- 生物识别解锁功能正常
- 启用/禁用生物识别功能正常
- 所有真机测试通过

### Phase 3: 清理旧代码

**目标**: 移除 AccountManager 中的旧生物识别逻辑

**步骤**:
1. 确认新功能完全正常
2. 移除 AccountManager.unlockWithBiometric()
3. 移除 AccountManager.canUseBiometricAuthentication()
4. 移除 AccountManager.enableBiometricAuth()
5. 移除 AccountManager.disableBiometricAuth()
6. 最终验证

**验收标准**:
- 所有功能测试通过
- 代码审查通过
- 无编译警告

### Rollback Plan

**触发条件**: 发现严重问题无法快速修复

**回滚步骤**:
1. 恢复旧代码
2. 禁用新组件
3. 发布修复版本
4. 分析问题并重新规划

---

## Open Questions

1. **Q**: 是否需要支持多种生物识别优先级？
   **A**: 是的，BiometricState 已包含 preferredType 字段

2. **Q**: 是否需要支持 PIN 码作为第三种认证方式？
   **A**: 可以在后续版本中添加，当前版本专注于生物识别和主密码

3. **Q**: 防认证风暴的时间间隔是否需要可配置？
   **A**: 当前硬编码为 1 秒，如有问题可以在后续版本中调整

---

## References

- [Android Biometric API Guide](https://developer.android.com/training/sign-in/biometric-auth)
- [AndroidKeyStore Best Practices](https://developer.android.com/training/articles/keystore)
- [BiometricPrompt Documentation](https://developer.android.com/reference/android/hardware/biometric/BiometricPrompt)
- 现有实现：
  - `SecureKeyStorageManager.java` - 三层密钥存储架构
  - `BiometricAuthHelper.java` - UI 交互封装
  - `AccountManager.java` - 业务逻辑（待清理）
