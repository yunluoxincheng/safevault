# Change: 移除旧安全架构，完全迁移到三层安全架构

## Why

当前项目存在新旧安全架构混用的情况：
- **旧架构**: CryptoManager (PBKDF2), KeyManager (明文存储), KeyDerivationManager (PBKDF2)
- **新架构**: CryptoSession, SecureKeyStorageManager, BiometricAuthManager, Argon2KeyDerivationManager

这种混用导致：
1. 代码维护复杂度高（需要同时维护两套架构）
2. 安全风险（旧架构使用弱密钥派生算法）
3. 性能损耗（多重密钥尝试降级逻辑）
4. 容易出现安全漏洞（旧架构已标记废弃但仍在使用）

开发测试环境无存量用户，可以激进地完全移除旧架构，无需向后兼容。

## What Changes

### 删除的组件（4个）
- **BREAKING**: 删除 `CryptoManager.java` - 已被 CryptoSession + SecureKeyStorageManager 替代
- **BREAKING**: 删除 `KeyManager.java` - 已被 SecureKeyStorageManager 完全替代
- **BREAKING**: 删除 `KeyDerivationManager.java` - 已被 Argon2KeyDerivationManager 替代
- **BREAKING**: 删除 `BackupCryptoUtil.java` - 将被新建的 BackupEncryptionManager 替代

### 重写的组件（5个）
- **BREAKING**: 重写 `ServiceLocator.java` - 移除 cryptoManager 和 keyManager 的 getter
- **BREAKING**: 重写 `BackendServiceImpl.java` - 移除对旧组件的引用
- **BREAKING**: 重写 `PasswordManager.java` - 移除向后兼容逻辑，只使用 CryptoSession.DataKey
- **BREAKING**: 重写 `EncryptionSyncManager.java` - 移除 KeyManager 依赖
- **BREAKING**: 重写 `DataImportExportManager.java` - 使用新架构组件

### 新建的组件（1个）
- 创建 `BackupEncryptionManager.java` - 使用 Argon2id + CryptoSession.DataKey

### 修改的组件（1个）
- 修改 `AuthViewModel.java` - 移除 KeyManager 依赖

### 数据清理
- 清空密码数据库表（旧架构加密的数据无法解密）
- 清理 SharedPreferences 中的旧数据（crypto_prefs 会话数据、key_prefs 整个文件）

## Impact

### 受影响的规格
- `crypto-security` - 移除 PBKDF2 相关要求，统一使用 Argon2id

### 受影响的代码
- **核心组件** (6个文件):
  - `ServiceLocator.java`
  - `BackendService.java` (接口保留)
  - `BackendServiceImpl.java`
  - `PasswordManager.java`
  - `AuthViewModel.java`
  - `BackupEncryptionManager.java` (新建)

- **删除组件** (4个文件):
  - `CryptoManager.java`
  - `KeyManager.java`
  - `KeyDerivationManager.java`
  - `BackupCryptoUtil.java`

- **同步和导出** (2个文件):
  - `EncryptionSyncManager.java`
  - `DataImportExportManager.java`

- **数据层** (1个数据库 + 2个SharedPreferences):
  - 密码数据库表（清空）
  - `crypto_prefs` SharedPreferences（清理会话数据）
  - `key_prefs` SharedPreferences（删除整个文件）

### Breaking Changes
1. **密码数据丢失**: 所有旧密码数据将被清空，用户需要重新添加
2. **接口变更**: BackendService 接口保持不变，但内部实现完全重写
3. **依赖变更**: 所有使用旧组件的代码必须更新

## Non-Breaking Changes
- 无

## Migration Plan
1. 新用户自动使用新架构
2. 旧用户（开发环境）数据将被清空，需要重新设置
3. 应用升级后首次启动时自动清理旧数据

## Open Questions
- 无
