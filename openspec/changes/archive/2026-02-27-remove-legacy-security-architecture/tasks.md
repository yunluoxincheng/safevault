# 移除旧安全架构任务清单

## 1. 准备工作
- [x] 1.1 验证新架构组件完整性（SecureKeyStorageManager, CryptoSession, BiometricAuthManager, Argon2KeyDerivationManager）
- [x] 1.2 备份当前代码状态（git commit）

## 2. 创建新组件
- [x] 2.1 创建 `BackupEncryptionManager.java`
  - [x] 2.1.1 实现 Argon2id 密钥派生
  - [x] 2.1.2 实现 AES-256-GCM 加密/解密
  - [x] 2.1.3 支持本地备份模式（使用 DataKey）
  - [x] 2.1.4 支持云端同步模式（使用固定 salt）

## 3. 重写核心组件
- [x] 3.1 重写 `PasswordManager.java`
  - [x] 3.1.1 移除 CryptoManager 依赖
  - [x] 3.1.2 移除向后兼容的多重密钥尝试逻辑
  - [x] 3.1.3 只使用 CryptoSession.DataKey 进行加密/解密
  - [x] 3.1.4 更新构造函数签名

- [x] 3.2 重写 `ServiceLocator.java`
  - [x] 3.2.1 移除 `getCryptoManager()` 方法
  - [x] 3.2.2 移除 `getKeyManager()` 方法
  - [x] 3.2.3 添加 `getCryptoSession()` 方法（如需要）
  - [x] 3.2.4 添加 `getSecureKeyStorageManager()` 方法（如需要）
  - [x] 3.2.5 添加 `getBiometricAuthManager()` 方法（如需要）

- [x] 3.3 重写 `BackendServiceImpl.java`
  - [x] 3.3.1 移除 CryptoManager 依赖
  - [x] 3.3.2 使用 CryptoSession 替代 CryptoManager 的会话管理
  - [x] 3.3.3 使用 SecureKeyStorageManager 替代 KeyManager
  - [x] 3.3.4 更新 unlock() 方法
  - [x] 3.3.5 更新 lock() 方法
  - [x] 3.3.6 更新 initialize() 方法
  - [x] 3.3.7 更新 isUnlocked() 方法

## 4. 重写同步和导出组件
- [x] 4.1 重写 `DataImportExportManager.java`
  - [x] 4.1.1 移除 CryptoManager 依赖
  - [x] 4.1.2 使用 BackupEncryptionManager 替代 BackupCryptoUtil
  - [x] 4.1.3 更新 exportData() 方法
  - [x] 4.1.4 更新 importData() 方法

- [x] 4.2 重写 `EncryptionSyncManager.java`
  - [x] 4.2.1 移除 KeyManager 依赖
  - [x] 4.2.2 使用 SecureKeyStorageManager 替代 KeyManager
  - [x] 4.2.3 更新 uploadEncryptedPrivateKey() 方法
  - [x] 4.2.4 更新 downloadEncryptedPrivateKey() 方法
  - [x] 4.2.5 更新 EncryptedPrivateKey 返回类型

## 5. 修改 ViewModel
- [x] 5.1 修改 `AuthViewModel.java`
  - [x] 5.1.1 移除 KeyManager 依赖
  - [x] 5.1.2 使用 BiometricAuthManager 替代 KeyManager
  - [x] 5.1.3 使用 SecureKeyStorageManager 进行密钥管理
  - [x] 5.1.4 更新所有相关方法

## 6. 删除旧组件
- [x] 6.1 删除 `CryptoManager.java`
- [x] 6.2 删除 `KeyManager.java`
- [x] 6.3 删除 `KeyDerivationManager.java`
- [x] 6.4 删除 `BackupCryptoUtil.java`

## 7. 清理遗留数据
- [x] 7.1 清空密码数据库表
  - [x] 7.1.1 创建数据库清理方法
  - [x] 7.1.2 在应用启动时检测并清理旧数据

- [x] 7.2 清理 SharedPreferences
  - [x] 7.2.1 清理 `crypto_prefs` 中的会话数据（session_master_key, session_master_iv, unlock_time, is_locked）
  - [x] 7.2.2 删除 `key_prefs` 整个文件

## 8. 更新规格文档
- [x] 8.1 更新 `crypto-security` 规格
  - [x] 8.1.1 移除 PBKDF2 相关要求
  - [x] 8.1.2 添加 Argon2id 作为唯一密钥派生算法

## 9. 验证和测试
- [x] 9.1 编译验证
- [x] 9.2 功能测试（密码增删改查）
- [x] 9.3 安全测试（生物识别、锁定/解锁）
- [x] 9.4 同步测试（导入/导出、云端同步）
- [x] 9.5 运行 openspec validate --strict

## 完成标准
- [x] 所有旧组件已删除
- [x] 所有新组件已创建并测试通过
- [x] 所有重写组件已更新并测试通过
- [x] 遗留数据已清理
- [x] 代码编译无错误
- [x] openspec 验证通过
- [ ] git commit 完成
