# 移除旧安全架构设计文档

## Context

SafeVault 项目当前处于新旧安全架构混用状态：

### 旧架构（待移除）
- **CryptoManager**: 使用 PBKDF2 (100,000次迭代) 派生密钥
- **KeyManager**: RSA 私钥以 Base64 明文存储在 SharedPreferences
- **KeyDerivationManager**: 使用 PBKDF2WithHmacSHA256
- **BackupCryptoUtil**: 使用 PBKDF2 派生备份密钥

### 新架构（已实现）
- **SecureKeyStorageManager**: 三层安全存储架构
  - Level 0: DataKey (内存会话，5分钟超时)
  - Level 1: PasswordKey (Argon2id) + DeviceKey (AndroidKeyStore)
  - Level 2: VaultKey (双重加密)
  - Level 3: RSA 私钥（DataKey 加密）
- **CryptoSession**: 会话管理，5分钟超时，zeroize() 安全清除
- **BiometricAuthManager**: 统一认证管理（Level 4）
- **Argon2KeyDerivationManager**: Argon2id 密钥派生 (timeCost=3, 64MB, parallelism=4)

### 当前问题
1. **架构混用**: PasswordManager 同时支持 DataKey 和 masterKey
2. **安全风险**: PBKDF2 迭代次数较低（100,000 vs Argon2id 的内存成本）
3. **性能损耗**: 多重密钥尝试降级逻辑
4. **维护成本**: 需要同时维护两套架构

## Goals / Non-Goals

### Goals
- 完全移除旧架构组件（CryptoManager, KeyManager, KeyDerivationManager, BackupCryptoUtil）
- 所有组件统一使用新架构
- 清理所有遗留数据
- 简化代码，移除向后兼容逻辑

### Non-Goals
- 保持向后兼容性（开发环境无存量用户）
- 迁移旧数据（旧数据将被清空）
- 保留 PBKDF2 支持

## Decisions

### 决策1: 完全删除旧组件
**选择**: 删除 CryptoManager, KeyManager, KeyDerivationManager, BackupCryptoUtil

**理由**:
- 这些组件的功能已被新架构完全覆盖
- 保留会增加维护负担和安全风险
- 开发环境无存量用户，无需向后兼容

**替代方案**:
- 保留但标记 @Deprecated → 已拒绝（增加复杂度）
- 保留作为兼容层 → 已拒绝（安全风险）

### 决策2: 重写而非修改
**选择**: 完全重写 PasswordManager, ServiceLocator, BackendServiceImpl 等

**理由**:
- 旧代码包含大量向后兼容逻辑，需要完全重写
- 重写可以确保新架构的一致性
- 避免遗留旧架构的代码片段

**替代方案**:
- 增量修改 → 已拒绝（容易遗漏旧代码）

### 决策3: 数据清空策略
**选择**: 清空所有旧数据，让用户重新添加

**理由**:
- 旧数据使用 PBKDF2 加密，新架构使用 Argon2id，无法直接解密
- 迁移复杂度高，风险大
- 开发环境无存量用户，影响小

**替代方案**:
- 保留数据并迁移 → 已拒绝（复杂且风险高）
- 提供导出工具 → 已拒绝（用户未要求）

### 决策4: BackupEncryptionManager 设计
**选择**: 创建新的 BackupEncryptionManager，使用 Argon2id + CryptoSession.DataKey

**理由**:
- 本地备份：直接使用 DataKey（会话中已有）
- 云端同步：使用 Argon2id 派生密钥 + 固定 salt（基于邮箱）

**设计**:
```java
public class BackupEncryptionManager {
    private final CryptoSession cryptoSession;
    private final Argon2KeyDerivationManager argon2Manager;

    // 本地备份：使用 DataKey
    public LocalBackupResult encryptForLocalBackup(String jsonData);

    // 云端同步：使用 Argon2id + 固定 salt
    public CloudBackupResult encryptForCloudSync(String jsonData, String userEmail);
}
```

## Risks / Trade-offs

### 风险1: 密钥获取问题
**风险**:云端同步需要主密码派生密钥，但主密码可能不在会话中

**缓解**:
- 使用 CryptoSession.DataKey 进行本地备份（会话中已有）
- 云端同步在用户已解锁状态下进行（会话有效）
- 如果会话超时，提示用户重新解锁

### 风险2: 破坏性变更
**风险**: 大量代码需要修改，可能引入新的 bug

**缓解**:
- 按顺序执行：先创建新组件，再重写核心组件，最后删除旧组件
- 每个组件修改后立即测试
- 使用 git 进行版本控制，随时可以回滚

### 风险3: 遗漏引用
**风险**: 可能遗漏某些对旧组件的引用

**缓解**:
- 使用 Grep 搜索所有引用
- 编译器会报错未找到的类
- 逐个修复所有编译错误

## Migration Plan

### 阶段1: 准备工作（0.5天）
1. 验证新架构组件完整性
2. 备份当前代码状态

### 阶段2: 创建新组件（1天）
1. 创建 BackupEncryptionManager
2. 单元测试 BackupEncryptionManager

### 阶段3: 重写核心组件（2天）
1. 重写 PasswordManager
2. 重写 ServiceLocator
3. 重写 BackendServiceImpl
4. 修改 AuthViewModel

### 阶段4: 重写同步和导出（1天）
1. 重写 DataImportExportManager
2. 重写 EncryptionSyncManager

### 阶段5: 删除旧组件（0.5天）
1. 删除 CryptoManager
2. 删除 KeyManager
3. 删除 KeyDerivationManager
4. 删除 BackupCryptoUtil

### 阶段6: 清理数据（0.5天）
1. 清空密码数据库
2. 清理 SharedPreferences

### 阶段7: 验证和测试（0.5天）
1. 编译验证
2. 功能测试
3. 安全测试

**总计**: 约 6 天

### 回滚计划
如果迁移失败，可以：
1. 使用 git 回滚到迁移前的 commit
2. 删除新创建的文件
3. 恢复旧组件

## Open Questions

### Q1: EncryptedPrivateKey 返回类型如何处理？
**问题**: EncryptionSyncManager.downloadEncryptedPrivateKey() 返回 `KeyManager.EncryptedPrivateKey`，KeyManager 删除后如何处理？

**选项**:
- A. 创建新的 EncryptedPrivateKey 类在 security 包下
- B. 使用 Map<String, String> 返回
- C. 创建专门的 DTO 类

**推荐**: 选项 A - 在 SecureKeyStorageManager 中添加 EncryptedPrivateKey 内部类

### Q2: ServiceLocator 是否需要保留？
**问题**: ServiceLocator 是全局服务定位器，是否需要保留？

**选项**:
- A. 完全移除，使用依赖注入
- B. 保留但简化，只提供新架构组件

**推荐**: 选项 B - 保留但简化，移除旧组件的 getter

### Q3: 如何处理应用启动时的数据清理？
**问题**: 旧数据何时清理？如何触发？

**选项**:
- A. 应用启动时检测并自动清理
- B. 在设置中提供"重置"按钮
- C. 在首次启动新版本时清理

**推荐**: 选项 A - 应用启动时检测旧数据并自动清理（开发环境）
