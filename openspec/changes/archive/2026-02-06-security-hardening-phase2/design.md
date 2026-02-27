# 安全加固第二阶段 - 技术设计文档

## Context

第二阶段专注于需要数据迁移的安全修复。这些修复虽然更加复杂，但能显著提升安全水平：

### 待解决的安全问题
1. **RSA私钥明文存储** - SharedPreferences在root设备上可被读取
2. **RSA/PKCS1Padding不安全** - 易受Bleichenbacher攻击
3. **密码哈希使用PBKDF2** - 易受GPU/ASIC攻击，需升级到Argon2id
4. **自动填充明文密码** - 违反零知识架构
5. **会话超时过长** - 增加攻击窗口

### 约束条件
- **必须向后兼容** - 历史数据不能丢失
- **用户无感知迁移** - 尽量自动化
- **支持回滚** - 迁移失败时的降级方案
- **Android 10+兼容** - 最低API级别29

---

## Goals / Non-Goals

### Goals（目标）
1. RSA私钥通过三层安全架构保护：
   - Level 1: PasswordKey(主密码派生) + DeviceKey(AndroidKeyStore保护)
   - Level 2: DataKey(随机AES-256)双重加密存储
   - Level 3: RSA私钥用DataKey加密存储
2. 新分享使用OAEP加密，旧分享可解密
3. 新用户使用Argon2id密码哈希（t=3, m=64MB, p=4）
4. 自动填充密码加密存储
5. 会话超时缩短到5分钟
6. 支持云端备份和跨设备恢复

### Non-Goals（非目标）
1. 不强制旧用户重新生成密钥对（仅迁移）
2. 不修改JWT算法（第三阶段）
3. 不添加速率限制（第三阶段）

---

## Decisions

### 决策1: 三层安全存储架构

**选择**: 采用三层密钥存储架构解决AndroidKeyStore密钥不可导出问题

**架构概述**:
```
┌────────────────────────┐
│      Level 3 (最终)     │
│   RSA 私钥              │
│   用 DataKey 加密       │
└──────────▲─────────────┘
                     │
                     │ DataKey (随机256bit)
                     │
┌──────────┴─────────────┐
│      Level 2 (可备份)   │
│ DataKey 被两把锁包裹：  │
│                        │
│ ① PasswordKey 加密     │ ← 可跨设备恢复
│ ② DeviceKey 加密       │ ← 本机快速解锁
└──────────▲─────────────┘
                     │
┌──────────┴─────────────┐
│      Level 1 (根)       │
│ PasswordKey  + DeviceKey│
└────────────────────────┘
```

**理由**:
- **安全性**: RSA私钥永远不会以明文形式存储
- **可恢复性**: 记住主密码即可在任何设备恢复（PasswordKey加密的DataKey）
- **性能**: 日常使用只需生物识别解锁DeviceKey，无需输入主密码
- **兼容性**: 解决AndroidKeyStore密钥不可导出与云端备份的矛盾

**组件定义**:

1. **PasswordKey** (Android前端):
   - 从用户主密码通过PBKDF2(600,000次迭代)派生（Android原生支持）
   - 用于加密DataKey以便云端备份
   - 用户必须记住主密码才能跨设备恢复

2. **DeviceKey**:
   - AndroidKeyStore生成的AES-256-GCM密钥
   - 需要生物识别认证才能使用
   - 设备绑定，不可导出
   - 用于日常快速解锁DataKey

3. **DataKey**:
   - 随机生成的256位AES密钥
   - 实际用于加密RSA私钥
   - 被双重加密存储（PasswordKey + DeviceKey）

4. **加密的RSA私钥**:
   - RSA私钥用DataKey加密存储
   - 公钥明文存储（用于分享）

5. **后端密码哈希** (服务器端):
   - 用户认证使用Argon2id（t=3, m=64MB, p=4）
   - 旧用户保持PBKDF2兼容
   - 新用户注册直接使用Argon2id

**迁移流程**:
```
1. 应用启动，检测迁移状态
2. 如果未迁移且用户已登录：
   a. 从旧存储获取明文RSA私钥
   b. 获取用户主密码和盐值
   c. 派PasswordKey（主密码 + 盐值）
   d. 创建DeviceKey（AndroidKeyStore）
   e. 生成随机DataKey
   f. 双重加密DataKey并保存
   g. 用DataKey加密RSA私钥并保存
   h. 创建云端备份包（可选）
   i. 标记迁移完成
3. 日常使用：
   a. 生物识别解锁DeviceKey
   b. DeviceKey解密DataKey
   c. DataKey解密RSA私钥（仅在内存中）
```

**代码结构**:
```java
// 新增核心类
SecureKeyStorageManager.java
├── derivePasswordKey()      // 派PasswordKey
├── getOrCreateDeviceKey()   // 获取/创建DeviceKey
├── generateDataKey()        // 生成随机DataKey
├── encryptAndSaveDataKey()  // 双重加密DataKey
├── encryptAndSaveRsaPrivateKey() // 加密RSA私钥
├── decryptRsaPrivateKey()   // 解密RSA私钥（需要DataKey）
├── migrateFromLegacy()      // 从旧存储迁移
└── createCloudBackup()      // 创建云端备份

// 修改现有类
KeyManager.java
├── 集成SecureKeyStorageManager
├── getPrivateKey()优先从安全存储获取
└── 保持向后兼容接口
```

**风险缓解**:
- **迁移失败**: 保持旧存储可用，下次重试
- **DeviceKey不可用**: 回退到PasswordKey恢复流程
- **生物识别失败**: 提示用户使用主密码验证
- **云端备份丢失**: 本地DeviceKey加密的DataKey仍可用

---

### 决策2: RSA填充双版本支持

**选择**: 新分享使用OAEP，旧分享保持PKCS1可解密

**理由**:
- 不破坏历史数据
- 平滑迁移路径
- 版本标识区分算法

**实现方案**:
```java
public class EncryptedSharePacket {
    private String version;  // "v1" = PKCS1, "v2" = OAEP
    private String encryptedData;
    private String encryptedAESKey;
    // ...
}

public ShareData decryptPacket(EncryptedSharePacket packet, PrivateKey privateKey) {
    if ("v1".equals(packet.getVersion())) {
        return decryptWithPKCS1(packet, privateKey);
    } else if ("v2".equals(packet.getVersion())) {
        return decryptWithOAEP(packet, privateKey);
    }
    throw new IllegalArgumentException("未知版本: " + packet.getVersion());
}
```

**数据库schema**:
```sql
-- contact_shares表添加version字段
ALTER TABLE contact_shares ADD COLUMN encryption_version VARCHAR(10) DEFAULT 'v1';

-- 新分享使用v2
UPDATE contact_shares SET encryption_version = 'v1'
WHERE created_at < '2024-02-01';
```

**性能影响**:
- 每次解密多一次版本检查（可忽略）
- v1数据保持可访问

---

### 决策3: 密码哈希从PBKDF2升级到Argon2id

**选择**: 新用户使用Argon2id，旧用户保持PBKDF2（可选迁移）

**理由**:
- Argon2id是2015年密码哈希竞赛获胜者
- 内存硬哈希，抗GPU/ASIC攻击能力远强于PBKDF2
- Argon2id混合模式：同时抵抗侧信道攻击和GPU攻击
- 向后兼容：旧用户保持PBKDF2

**Argon2id vs PBKDF2对比**:

| 特性 | PBKDF2 | Argon2id |
|------|--------|----------|
| 抗GPU攻击 | 弱 | 强（内存硬） |
| 抗ASIC攻击 | 弱 | 强 |
| 抗侧信道攻击 | 一般 | 优秀 |
| 推荐年份 | 2000年 | 2015年至今 |
| NIST推荐 | 是 | 是（首选） |
| Android支持 | 原生 | 需要库 |

**Argon2id参数配置**（平衡配置）:
- **时间成本 (t)**: 3次迭代
- **内存成本 (m)**: 64MB
- **并行度 (p)**: 4线程
- **输出长度**: 32字节（256位）
- **盐值长度**: 16字节

**实现方案**:
```java
// 添加依赖
// build.gradle (SpringBoot后端)
implementation 'com.lambdaworks:scrypt:1.4.0'
implementation 'de.mkammerer:argon2-jvm:2.11'

// 用户表添加password_hash_algorithm字段
ALTER TABLE users ADD COLUMN password_hash_algorithm VARCHAR(20) DEFAULT 'PBKDF2';

// 注册新用户
public User register(RegisterRequest request) {
    User user = new User();
    user.setPasswordHashAlgorithm("ARGON2ID");  // 新用户使用Argon2id

    Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    String hash = argon2.hash(
        3,              // 迭代次数
        64 * 1024,      // 内存成本 (64MB)
        4,              // 并行度
        request.getPassword().toCharArray()
    );

    user.setPasswordHash(hash);
    // ...
}

// 验证密码时自动检测算法
public boolean verifyPassword(User user, String password) {
    String algorithm = user.getPasswordHashAlgorithm();
    if ("ARGON2ID".equals(algorithm)) {
        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        return argon2.verify(user.getPasswordHash(), password.toCharArray());
    } else {
        // PBKDF2兼容
        int iterations = "v2".equals(user.getPbkdf2Version()) ? 600000 : 100000;
        String hash = hashWithPBKDF2(password, user.getSalt(), iterations);
        return hash.equals(user.getPasswordHash());
    }
}

// 旧用户迁移到Argon2id（可选）
public boolean migrateToArgon2id(User user, String currentPassword) {
    // 1. 验证当前密码
    if (!verifyPassword(user, currentPassword)) {
        return false;
    }

    // 2. 使用Argon2id重新哈希
    Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2ID);
    String newHash = argon2.hash(3, 64 * 1024, 4, currentPassword.toCharArray());

    // 3. 更新数据库
    user.setPasswordHash(newHash);
    user.setPasswordHashAlgorithm("ARGON2ID");
    userRepository.save(user);

    return true;
}
```

**性能对比**:
| 算法 | 耗时（现代设备） | 耗时（低端设备） | 内存占用 |
|------|-----------------|-----------------|---------|
| PBKDF2 (100k) | ~50ms | ~100ms | <1MB |
| PBKDF2 (600k) | ~300ms | ~600ms | <1MB |
| Argon2id (t=3, m=64MB) | ~400ms | ~800ms | 64MB |

**迁移策略**:
- **新用户**: 直接使用Argon2id
- **旧用户**:
  - 默认保持PBKDF2（向后兼容）
  - 可选迁移：用户登录时提示升级安全性
  - 迁移时需要重新输入密码（验证后重新哈希）

---

### 决策4: 自动填充密码加密存储

**选择**: 使用Android Keystore加密，需要时解密

**理由**:
- 符合零知识架构
- 密码不在SharedPreferences明文存储
- 使用硬件保护的密钥

**实现方案**:
```java
public class AutofillPasswordManager {
    private static final String MASTER_KEY_ALIAS = "autofill_master_key";

    public void savePassword(String password) {
        SecretKey masterKey = getOrCreateMasterKey();

        // 加密密码
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, masterKey);
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(128, iv));

        byte[] encrypted = cipher.doFinal(password.getBytes(UTF_8));

        // 存储 IV + 加密数据
        String combined = Base64.encodeToString(iv, NO_WRAP) + "." +
                         Base64.encodeToString(encrypted, NO_WRAP);

        prefs.edit().putString("encrypted_autofill_pw", combined).apply();
    }

    public String getPassword() {
        String combined = prefs.getString("encrypted_autofill_pw", null);
        if (combined == null) return null;

        String[] parts = combined.split("\\.");
        byte[] iv = Base64.decode(parts[0], NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], NO_WRAP);

        SecretKey masterKey = getMasterKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(128, iv));

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, UTF_8);
    }
}
```

**迁移现有用户**:
```java
// 检测明文密码
private boolean hasLegacyPassword() {
    return prefs.contains("master_password") &&
           !prefs.contains("encrypted_autofill_pw");
}

// 提示用户重新设置
private void migrateAutofillPassword() {
    if (hasLegacyPassword()) {
        // 提示用户重新启用生物识别
        showBiometricSetupPrompt();
        // 清除旧密码
        prefs.edit().remove("master_password").apply();
    }
}
```

---

### 决策5: 会话超时缩短

**选择**: 从30分钟缩短到5分钟

**理由**:
- 减少攻击窗口
- 符合NIST建议（5-15分钟）
- 平衡安全性和便利性

**实现**:
```java
// 修改超时常量
private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000;

// 更新相关注释和文档
/**
 * 会话超时时间（毫秒）
 * 超时后需要重新输入主密码
 * 推荐值：5分钟（平衡安全性和便利性）
 */
private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000;
```

**用户影响**:
- 用户可能需要更频繁地输入主密码
- 可在设置中配置（未来增强）

---

## Migration Plan

### 阶段1: 准备（1周）
1. 实现迁移代码
2. 编写单元测试
3. 准备回滚方案
4. 代码审查

### 阶段2: 测试（1周）
1. 迁移功能测试
2. 兼容性测试
3. 安全测试
4. 性能测试

### 阶段3: 灰度发布（2周）
1. 内部测试（10人）
2. 小范围灰度（5%用户）
3. 逐步扩大（20% → 50% → 100%）
4. 监控关键指标

### 阶段4: 全量发布
1. 发布到所有用户
2. 监控错误率
3. 收集用户反馈

---

## Rollback Plan

### 回滚触发条件
- 迁移失败率 > 10%
- 应用崩溃率显著上升
- 分享功能严重受影响
- 用户投诉激增

### 回滚步骤
1. 回滚到上一版本APK
2. 回滚后端代码（保留双版本支持）
3. 验证功能恢复
4. 分析失败原因
5. 修复问题后重新发布

### 预计回滚时间
- Android: <1小时（新版本审核）
- 后端: <30分钟

---

## Testing Strategy

### 单元测试
- RSA密钥迁移逻辑
- 双版本解密逻辑
- PBKDF2迭代次数选择
- 自动填充加密/解密

### 集成测试
- 完整的分享流程（v1和v2）
- 用户认证流程
- 自动填充流程

### 兼容性测试
- 旧用户迁移到新版本
- 新用户直接使用新版本
- v1数据解密
- v2数据解密

### 性能测试
- Argon2id哈希耗时（t=3, m=64MB, p=4）
- OAEP加密/解密性能
- 密钥迁移性能

---

## Monitoring

### 关键指标

#### 迁移成功率
```
目标: >95%
公式: 成功迁移用户数 / 尝试迁移用户数
告警: <90%
```

#### 密钥迁移失败率
```
目标: <5%
公式: 失败迁移次数 / 总迁移次数
告警: >10%
```

#### 分享功能成功率
```
目标: >99%
公式: 成功解密次数 / 总解密次数
告警: <95%
```

#### 应用崩溃率
```
目标: <0.1%
公式: 崩溃次数 / 活跃用户数
告警: >0.5%
```

### 日志记录
```java
// 迁移日志
Log.i(TAG, "RSA密钥迁移成功: userId=" + userId);
Log.w(TAG, "RSA密钥迁移失败: userId=" + userId + ", error=" + error);
Log.i(TAG, "使用旧密钥存储: userId=" + userId);

// 解密日志
Log.d(TAG, "解密v1分享: shareId=" + shareId);
Log.d(TAG, "解密v2分享: shareId=" + shareId);
```

---

## Open Questions

### Q1: 是否强制旧用户迁移密钥？

**建议**: 不强制，自动迁移但允许失败回退

### Q2: Argon2id参数是否让用户选择？

**建议**: 不选择，新用户固定使用平衡配置（t=3, m=64MB, p=4）

### Q3: 是否清除v1历史数据？

**建议**: 不清除，保持可访问性

---

## Success Criteria

- [x] 新用户RSA密钥在KeyStore中生成
- [ ] 旧用户迁移成功率 > 95%
- [x] v1和v2分享数据都可解密（Task 5: 后端RSA填充升级已完成）
- [x] 新用户使用Argon2id密码哈希（已在SecureKeyStorageManager中实现）
- [ ] 旧用户保持PBKDF2兼容
- [ ] 自动填充密码已加密存储
- [ ] 会话超时调整为5分钟
- [ ] 无重大功能回归
- [ ] 安全扫描评分提升到8.5/10

### Task 5: 后端RSA填充升级 ✅ 已完成
- [x] 数据库迁移：V19__add_encryption_version_to_contact_shares.sql
- [x] ContactShare 实体添加 encryptionVersion 字段
- [x] CryptoService 支持 OAEP 填充和双版本解密
- [x] CreateContactShareRequest 支持加密版本选择（默认v2）
- [x] 响应 DTO 包含 encryptionVersion 字段
- [x] ContactShareService 创建和查询支持加密版本

### Task 6: 密码学安全加固 ✅ 已完成
- [x] 6.1 私钥内存安全管理：SecureKeyStorageManager 添加 finally 块擦除私钥字节
- [x] 6.2 事务性密钥存储：所有关键操作使用 commit() 代替 apply()
- [x] 6.3 AEAD加密完整性验证：已使用 AES/GCM/NoPadding 并正确处理 AEADBadTagException

### 实现细节

**后端修改**:
1. `V19__add_encryption_version_to_contact_shares.sql` - 数据库迁移
2. `ContactShare.java` - 添加 encryptionVersion 字段
3. `CryptoService.java` - 添加 OAEP 支持和双版本方法
4. `CreateContactShareRequest.java` - 添加加密版本字段（默认v2）
5. `ContactShareResponse.java` - 添加加密版本字段
6. `SentContactShareResponse.java` - 添加加密版本字段
7. `ReceivedContactShareResponse.java` - 添加加密版本字段
8. `ContactShareService.java` - 创建和查询支持加密版本

**前端修改**:
1. `SecureKeyStorageManager.java` - 私钥解密后添加内存擦除（finally块）

### 待完成任务
- [ ] 旧用户密钥迁移测试
- [ ] 自动填充密码加密存储实现
- [ ] 会话超时调整
- [ ] 兼容性测试（v1/v2混合场景）
