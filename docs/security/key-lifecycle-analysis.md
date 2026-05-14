# SafeVault 密钥生命周期分析

> 基于 stabilize-auth-key-lifecycle OpenSpec 变更的盘点结果。

## 1. 注册流程时序

```
RegisterActivity.completeRegistration()
  -> BackendService.completeRegistration(email, username, password)
    -> AuthViewModel.completeRegistration()
      -> [Cloud] POST /v1/auth/register (创建云端账号)
      -> [Cloud] POST /v1/auth/complete-registration (验证邮箱, 获取 JWT)
      -> [Local] BackendService.initialize(masterPassword)
        -> 1. Argon2KeyDerivationManager.generateSalt()       // 16 字节随机盐
        -> 2. Argon2KeyDerivationManager.deriveKeyWithArgon2id(password, salt)  // PasswordKey
        -> 3. SecureKeyStorageManager.generateDataKey()       // 256-bit AES DataKey
        -> 4. SecureKeyStorageManager.generateRSAKeyPair()    // RSA keypair (密码分享用)
        -> 5. SecureKeyStorageManager.getOrCreateDeviceKey()  // AndroidKeyStore AES-256
        -> 6. SecureKeyStorageManager.encryptAndSaveDataKey(dataKey, passwordKey, deviceKey)
        -> 7. SecureKeyStorageManager.encryptAndSaveRsaPrivateKey(rsaPrivate, dataKey)
        -> 8. SessionGuard.unlockWithDataKey(dataKey)         // UNLOCKED
        -> 9. MemorySanitizer.secureWipe(passwordKey)         // 清除 PasswordKey
  -> navigateToLogin()  // 跳转 LoginActivity
  -> SessionGuard 仍持有 DataKey (单例)
```

**注意**: 注册后跳转到 LoginActivity，用户需再次登录。此时 SessionGuard 已持有 DataKey (UNLOCKED)，
LoginActivity 中的 unlockVault() 会再次解密并加载同一个 DataKey。

## 2. 登录流程时序

```
LoginActivity.handleCloudLoginSuccess()
  -> [Cloud] POST /v1/auth/login (获取 JWT + refresh token)
  -> loginViewModel.saveEmailLoginInfo(email, tokens)
  -> loginViewModel.isVaultInitialized()
    -> 检查 salt + initializedFlag + DataKey 是否完整

  -> [已初始化] loginViewModel.unlockVault(password)
    -> BackendService.unlock(masterPassword)
      -> 1. Argon2KeyDerivationManager.deriveKeyWithArgon2id(password, salt)  // PasswordKey
      -> 2. SecureKeyStorageManager.decryptDataKeyWithPassword(passwordKey, salt)
      -> 3. SessionGuard.unlockWithDataKey(dataKey)         // UNLOCKED
      -> 4. MemorySanitizer.secureWipe(passwordKey)         // 清除 PasswordKey

  -> [未初始化] loginViewModel.initializeVault(password)
    -> BackendService.initialize(masterPassword)  // 同注册流程步骤 1-9

  -> handleCloudDataSync()  // 可选: 云端数据同步
```

## 3. 生物识别解锁时序

```
LoginActivity.performBiometricAuthentication()
  -> BiometricAuthManager.authenticate(activity, AuthScenario.LOGIN, callback)
    -> canAuthenticateForUnlock()  // 检查 hasDeviceKey() && hasDeviceEncryptedDataKey()
    -> triggerBiometricPrompt()    // BiometricPrompt UI
    -> [用户通过认证] tryUnlockWithKeystore()
      -> SecureKeyStorageManager.unlockWithBiometric()
        -> getOrCreateDeviceKey()          // AndroidKeyStore 认证
        -> decryptDataKeyWithDevice(deviceKey)  // AES-GCM 解密
        -> decryptRsaPrivateKey(dataKey)   // RSA 私钥解密
      -> callback.onKeyAccessGranted()
  -> loginViewModel.completeBiometricUnlock()
    -> BackendService.unlockSessionWithBiometric()
      -> SecureKeyStorageManager.unlockDataKeyWithBiometric()  // 仅解密 DataKey
      -> SessionGuard.unlockWithDataKey(dataKey)               // UNLOCKED
```

## 4. 锁定时序

```
[后台超时锁定]
MainActivity.onResume()
  -> checkAutoLock()
    -> backendService.getBackgroundTime()
    -> backendService.shouldLockByBackgroundTimeout(ctx)
      -> SessionGuard.shouldLockBySessionTimeout(bgTime)
        -> SecurityConfig.getAutoLockTimeoutMillisForMode()
    -> [超时] lockApp()
      -> BackendService.lock()
        -> SessionGuard.clear()
          -> SensitiveData.close()  // 安全清零 DataKey

[内存压力锁定]
ApplicationLifecycleWatcher.onTrimMemory(RUNNING_LOW/MODERATE)
  -> clearSensitiveData("onTrimMemory()")
    -> SessionGuard.clear()

ApplicationLifecycleWatcher.onLowMemory()
  -> clearSensitiveData("onLowMemory()")
    -> SessionGuard.clear()
```

## 5. 登出时序

```
AccountSecurityFragment -> AccountSecurityViewModel
  -> BackendService.logout()
    -> AccountManager.logout()
      -> 清除云端 token、会话密码、SharedPreferences 中的 auth 数据
    -> TokenRevokeService.revokeToken(token, userId, deviceId, "LOGOUT")
      -> SHA-256(token) 存入 revoked_tokens 表
```

**注意**: BackendService.logout() 委托给 AccountManager.logout()，需要确认是否调用了 SessionGuard.clear()。

## 6. 实际行为与 design.md 状态机的偏差

### 偏差 1: 注册流程的 UI 跳转

| 项目 | design.md 定义 | 实际代码行为 |
|------|---------------|-------------|
| 注册后状态 | UNINITIALIZED → UNLOCKED | initialize() 确实调用了 unlockWithDataKey()，但 UI 跳转到 LoginActivity |
| 用户感知 | 注册后直接进入解锁态 | 注册后跳转到登录页，用户需再次登录 |

**评估**: 技术上 SessionGuard 在注册后确实处于 UNLOCKED 状态（DataKey 在内存中），
但 UI 流程让用户回到 LoginActivity。这不影响安全性，但与 design.md 描述有差异。
可在 tasks 2.2 中对齐。

### 偏差 2: 会话状态无显式枚举

| 项目 | design.md 定义 | 实际代码行为 |
|------|---------------|-------------|
| 状态表示 | 6 个显式状态枚举 | SessionGuard 仅用 `boolean unlocked` + DataKey 是否存在 |
| 状态转换校验 | 拒绝非法转换 | 无转换校验逻辑 |

**评估**: 需要在 task 3.1 中实现 SessionState 枚举，task 3.2 中添加转换校验。

### 偏差 3: 登出流程清理完整性

| 项目 | design.md 定义 | 实际代码行为 |
|------|---------------|-------------|
| DataKey 清除 | 登出时调用 SessionGuard.lock() | BackendService.logout() -> AccountManager.logout()，需验证是否调用 SessionGuard.clear() |
| 后端 refresh token 撤销 | 撤销 refresh token | TokenRevokeService.revokeToken() 存在，但需验证是否在登出时被调用 |
| PasswordKeyEncryptedDataKey | 保留（允许重新登录） | 不在登出时清除（正确） |

**评估**: 需要在 task 3.6 中验证并修复登出清理完整性。

### 偏差 4: 后端 Refresh Token 无轮换

| 项目 | design.md 定义 | 实际代码行为 |
|------|---------------|-------------|
| Token 轮换 | 旧 token 标记 rotated，签发新 token | 无轮换机制，旧 token 7 天内可无限使用 |
| Token Family 追踪 | jti + family 模型 | JWT 无 jti claim，无 RefreshTokenRecord 实体 |
| 重用检测 | rotated token 重用时撤销 family | 不存在 |

**评估**: 需要在 Section 4 全部任务中实现。

### 已对齐项

以下行为已与 design.md 对齐：
- PasswordKey→DataKey→DeviceKey 生成顺序正确（task 1.1 验证）
- 生物识别 ENROLLMENT/UNLOCK 前置条件已区分（BiometricAuthManager）
- ApplicationLifecycleWatcher 仅在内存压力时清除 DataKey（不处理业务超时）
- MainActivity.onResume() 通过 SessionGuard.shouldLockBySessionTimeout() 检查超时
- PasswordKey 明文在 encrypt/decrypt 后通过 MemorySanitizer.secureWipe() 清除
