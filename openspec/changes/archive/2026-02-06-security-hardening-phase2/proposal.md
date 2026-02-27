# Change: SafeVault 安全加固 - 第二阶段（数据迁移）

## Why

第一阶段修复了无风险的安全问题，但仍有以下关键安全漏洞需要解决：

1. **RSA私钥明文存储** - 当前存储在SharedPreferences中，可被root设备提取
2. **后端使用不安全的RSA/PKCS1Padding** - 易受Bleichenbacher攻击
3. **密码哈希使用PBKDF2** - 易受GPU/ASIC暴力破解，需升级到Argon2id
4. **自动填充服务明文存储密码** - 破坏零知识架构
5. **会话密钥超时过长** - 30分钟增加攻击窗口
6. **私钥在内存中长期驻留** - RSA私钥解密后未及时擦除，可被内存转储提取
7. **SharedPreferences非事务写入** - apply()异步写入可能导致密钥对损坏
8. **加密操作缺少完整性验证** - 未明确使用AEAD模式，无法检测密文篡改

这些修复需要**数据迁移**和**向后兼容处理**，因此单独作为第二阶段。

## What Changes

### 需要数据迁移的修复

#### Android前端迁移
- **RSA私钥迁移到安全三层存储架构**
  - **Level 1 (根层)**: PasswordKey(主密码派生) + DeviceKey(AndroidKeyStore保护)
  - **Level 2 (中间层)**: DataKey(随机AES-256)被双重加密：PasswordKey加密(用于云端备份) + DeviceKey加密(用于本地快速解锁)
  - **Level 3 (数据层)**: RSA私钥用DataKey加密存储
  - 检测旧存储（SharedPreferences中的明文私钥）
  - 迁移到三层安全存储
  - 保持向后兼容（旧用户可迁移，新用户直接使用新架构）

- **自动填充密码加密存储**
  - 使用Android Keystore加密存储
  - 修改BackendServiceImpl实现
  - 迁移现有用户（如需要）

#### 后端加密升级
- **RSA填充从PKCS1改为OAEP**
  - 保留旧版本解密支持（v1格式）
  - 新分享使用OAEP（v2格式）
  - 添加版本标识

- **密码哈希从PBKDF2升级到Argon2id**
  - Argon2id是2015年密码哈希竞赛获胜者，抗GPU/ASIC攻击更强
  - 配置参数：时间成本t=3，内存成本m=64MB，并行度p=4
  - 新注册用户使用Argon2id
  - 旧用户可选择迁移或保持PBKDF2兼容

### 配置调整
- **会话密钥超时调整**
  - 从30分钟改为5分钟
  - 生物识别密钥有效期30秒

### 密码学安全加固（新增）
- **私钥内存安全管理**
  - RSA私钥解密后使用AutoCloseable模式
  - 操作完成后立即擦除内存（Arrays.fill）
  - 限制私钥在内存中的驻留时间

- **事务性密钥存储**
  - 密钥对存储使用commit()而非apply()
  - 确保公钥和私钥原子性写入
  - 防止进程崩溃导致的密钥对损坏

- **AEAD加密完整性验证**
  - 所有加密操作使用AES-GCM模式
  - 明确验证GCM认证标签
  - 密钥派生使用HKDF
  - 检测并拒绝被篡改的密文

## Impact

### 受影响的规格
- `android-security` - Android密钥管理
- `backend-security` - 后端加密配置
- `crypto-security` - 加密算法和参数（PBKDF2→Argon2id）

### 受影响的代码

#### Android前端
- `app/src/main/java/com/ttt/safevault/security/SecureKeyStorageManager.java` - 新增：三层安全存储架构实现
- `app/src/main/java/com/ttt/safevault/security/KeyManager.java` - 修改：集成SecureKeyStorageManager，提供向后兼容接口
- `app/src/main/java/com/ttt/safevault/crypto/CryptoManager.java` - 修改：密码哈希从PBKDF2升级到Argon2id
- `app/src/main/java/com/ttt/safevault/model/BackendServiceImpl.java` - 修改：自动填充密码加密存储

#### SpringBoot后端
- `safevault-backend/src/main/java/org/ttt/safevaultbackend/service/CryptoService.java` - RSA填充
- `safevault-backend/src/main/java/org/ttt/safevaultbackend/service/AuthService.java` - 密码哈希从PBKDF2升级到Argon2id
- 添加Argon2-jvm依赖库

### 数据迁移需求

| 迁移项 | 现有用户影响 | 迁移策略 |
|--------|-------------|----------|
| RSA私钥 | 需要迁移 | 检测旧存储→迁移到三层安全存储架构 |
| RSA填充 | 历史数据需兼容 | 双版本支持（v1/v2） |
| 密码哈希算法 | 可选迁移 | 新用户Argon2id，旧用户保持PBKDF2或迁移 |
| 自动填充密码 | 可能需要重设 | 首次使用生物识别时重新设置 |
| 云端备份 | 可选迁移 | 创建PasswordKey加密的DataKey备份，支持跨设备恢复 |

### 用户体验影响
- **RSA私钥迁移** - 自动进行，用户无感知（应用启动时自动迁移）
- **日常使用** - 生物识别解锁DeviceKey，快速访问RSA私钥
- **跨设备恢复** - 使用主密码可从云端备份恢复密钥（PasswordKey加密的DataKey）
- **历史分享数据** - 保持可访问（双版本解密）
- **自动填充** - 首次使用需重新认证
- **安全性提升** - RSA私钥不再明文存储，即使设备被root也无法提取

## Migration Plan

### 迁移前准备
1. 备份生产数据库
2. 准备回滚方案
3. 通知用户（如需要）

### 迁移步骤

#### Android迁移（应用启动时）
```java
// SecureKeyStorageManager.java - 三层架构迁移
public MigrationResult migrateFromLegacy(KeyManager oldKeyManager,
                                       String masterPassword,
                                       String saltBase64) {
    MigrationResult result = new MigrationResult();

    try {
        // 1. 从旧存储获取RSA私钥
        PrivateKey oldPrivateKey = oldKeyManager.getPrivateKey();
        if (oldPrivateKey == null) {
            return result.failure("No RSA private key found in legacy storage");
        }

        // 2. 派PasswordKey（从主密码+盐值）
        SecretKey passwordKey = derivePasswordKey(masterPassword, saltBase64);

        // 3. 获取或创建DeviceKey（AndroidKeyStore保护）
        SecretKey deviceKey = getOrCreateDeviceKey();
        if (deviceKey == null) {
            return result.failure("Failed to create DeviceKey in AndroidKeyStore");
        }

        // 4. 生成随机DataKey
        SecretKey dataKey = generateDataKey();

        // 5. 双重加密DataKey并保存
        if (!encryptAndSaveDataKey(dataKey, passwordKey, deviceKey)) {
            return result.failure("Failed to encrypt and save DataKey");
        }

        // 6. 用DataKey加密RSA私钥并保存
        String publicKeyBase64 = oldKeyManager.getPublicKey();
        // ... 解析公钥
        if (!encryptAndSaveRsaPrivateKey(oldPrivateKey, dataKey, publicKey)) {
            return result.failure("Failed to encrypt and save RSA private key");
        }

        // 7. 标记迁移完成
        setMigrationStatus(Status.COMPLETED);

        // 8. 创建云端备份包（可选）
        CloudBackup backup = createCloudBackup(masterPassword, saltBase64);

        return result.success(backup);

    } catch (Exception e) {
        setMigrationStatus(Status.FAILED);
        return result.failure("Migration failed: " + e.getMessage());
    }
}
```

#### 后端双版本支持
```java
// CryptoService.java
public String decryptShareData(String encryptedData, String version) {
    if ("v1".equals(version)) {
        return decryptWithPKCS1(encryptedData); // 旧格式
    } else {
        return decryptWithOAEP(encryptedData);  // 新格式
    }
}
```

### 回滚计划
- Android: 迁移失败时保持旧存储可用，新存储问题不影响旧功能
- Android: 提供手动回滚工具，从云端备份或旧存储恢复
- 后端: 保留PKCS1解密代码（双版本支持）

## Backward Compatibility

### RSA私钥迁移
- **新用户**: 直接使用三层安全存储架构（SecureKeyStorageManager）
- **旧用户**: 首次启动时自动迁移到三层架构，保持旧存储作为回退选项
- **迁移失败**: 回退到旧存储（记录日志），下次启动时重试
- **云端备份**: 支持使用主密码从任何设备恢复（PasswordKey加密的DataKey）

### RSA填充双版本
- **新分享**: 标记为v2（OAEP）
- **旧分享**: 保持v1（PKCS1），可正常解密
- **解密**: 根据版本标识选择算法

### 密码哈希算法
- **新注册**: 使用Argon2id（t=3, m=64MB, p=4）
- **现有用户**: 保持PBKDF2（向后兼容），可选迁移到Argon2id
- **验证**: 支持检测算法版本

## Non-Breaking Changes

所有修改均为**非破坏性变更**：
- 保持向后兼容
- 用户数据不丢失
- 历史数据可访问
- API接口不变

## Testing Requirements

### 迁移测试
- 测试旧用户迁移成功
- 测试迁移失败回退
- 测试新用户直接使用新方案

### 兼容性测试
- 测试v1格式数据解密
- 测试v2格式数据解密
- 测试混合场景

### 安全测试
- 验证三层架构安全性：
  - DeviceKey在AndroidKeyStore中且不可导出
  - DataKey被双重加密存储（PasswordKey + DeviceKey）
  - RSA私钥始终以DataKey加密形式存储
- 验证迁移过程安全性：
  - 迁移失败时旧存储保持可用
  - 迁移成功后可选择性清除旧存储
- 验证云端备份安全性：
  - 只有PasswordKey加密的DataKey上传到云端
  - 无明文私钥或DeviceKey泄露风险
- 验证OAEP加密正确性
- 验证PBKDF2强度提升（600,000次迭代）

## Next Phases

**第三阶段**（需用户操作）：
- JWT从HS256改为RS256（用户需重新登录）
- Token过期时间调整
- 实现完整HMAC签名验证
- 添加速率限制
- 添加并发登录控制
