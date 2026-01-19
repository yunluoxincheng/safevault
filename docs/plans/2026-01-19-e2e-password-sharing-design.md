# SafeVault 端到端加密密码分享功能设计文档

**日期**: 2026-01-19
**版本**: 1.0
**状态**: 设计阶段

---

## 一、概述

### 1.1 设计目标

实现符合零知识架构的端到端加密密码分享功能，确保：
- 服务器永远无法访问明文密码
- 所有设备共享同一对 RSA 密钥对
- 支持在线和离线分享场景
- 为未来团队功能预留扩展点

### 1.2 核心原则

| 原则 | 说明 |
|------|------|
| **零知识** | 服务器无法读取任何明文信息 |
| **端到端加密** | 使用接收方公钥加密，只有接收方私钥可解密 |
| **密钥一致性** | 密钥对由主密码派生，所有设备共享 |
| **用户友好** | 联系人系统 + 单次扫码 |

### 1.3 分享方式

支持4种传输方式：
- **QR码分享**：面对面快速分享
- **分享链接**：远程分享（safevault://share/...）
- **联系人系统**：预先保存联系人，直接选择分享
- **离线文件**：生成 .svsf 文件，无网络场景

---

## 二、密钥架构设计

### 2.1 现有加密架构

```
SafeVault 现有加密架构
├── CryptoManager (本地数据加密)
│   ├── PBKDF2-HMAC-SHA256 (100,000迭代)
│   ├── 主密码 + 盐 → AES-256 密钥
│   └── 用于加密本地数据库中的密码条目
│
└── KeyManager (RSA密钥 + 云端同步)
    ├── RSA-2048 密钥对 (每设备独立生成) ← 需要修改
    ├── 私钥用主密码派生的密钥加密
    └── 支持私钥导入/导出
```

### 2.2 新的密钥派生架构

**核心改动**：将 KeyManager 的 RSA 密钥生成从随机改为确定性派生

```
主密码
    │
    ├─→ PBKDF2 (设备盐) → AES-256 本地加密密钥 (CryptoManager)
    │
    └─→ PBKDF2 (用户邮箱盐) → 种子 → RSA-2048 密钥对 (KeyManager)
                                    │
                                    ├─→ 公钥：用于分享加密
                                    └─→ 私钥：用于分享解密
```

### 2.3 KeyDerivationManager 设计

```java
public class KeyDerivationManager {
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int DERIVED_KEY_LENGTH = 256;

    /**
     * 从主密码确定性派生RSA密钥对
     */
    public KeyPair deriveKeyPairFromMasterPassword(
            String masterPassword,
            String userEmail
    ) {
        // 1. 获取或生成用户盐值
        String salt = getOrGenerateUserSalt(userEmail);

        // 2. 使用 PBKDF2 从主密码派生种子
        SecretKey seedKey = deriveSeedFromMasterPassword(masterPassword, salt);

        // 3. 使用种子作为随机数生成器的种子
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        secureRandom.setSeed(seedKey.getEncoded());

        // 4. 确定性生成RSA密钥对
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, secureRandom);
        return keyGen.generateKeyPair();
    }

    /**
     * 获取公钥（用于分享加密）
     */
    public PublicKey getPublicKey(String masterPassword, String userEmail) {
        KeyPair keyPair = deriveKeyPairFromMasterPassword(masterPassword, userEmail);
        return keyPair.getPublic();
    }

    /**
     * 获取私钥（用于分享解密）
     */
    public PrivateKey getPrivateKey(String masterPassword, String userEmail) {
        KeyPair keyPair = deriveKeyPairFromMasterPassword(masterPassword, userEmail);
        return keyPair.getPrivate();
    }
}
```

### 2.4 KeyManager 修改

```java
// 在 KeyManager 中新增方法
public class KeyManager {
    // 现有方法保持不变...

    /**
     * 获取或派生用户密钥对（确定性派生）
     */
    public KeyPair getOrDeriveUserKeyPair(String masterPassword, String userEmail) {
        // 检查是否已有密钥对
        if (keyPair != null) {
            return keyPair;
        }

        // 从主密码派生密钥对
        KeyPair derivedKeyPair = deriveKeyPairFromMasterPassword(masterPassword, userEmail);

        // 保存到内存
        this.keyPair = derivedKeyPair;

        return derivedKeyPair;
    }
}
```

---

## 三、分享流程设计

### 3.1 面对面分享（QR码）

#### 发送方流程

```
1. 用户选择密码 → 点击"分享"
2. 系统验证身份（生物识别/主密码）
3. 从联系人列表选择接收方
4. 系统执行：
   a. 获取接收方的公钥
   b. 用接收方公钥加密密码数据（RSA-OAEP）
   c. 用发送方私钥签名（可选）
   d. 设置过期时间、权限
5. 生成QR码 → 显示
6. 保存分享记录到本地数据库
```

#### 接收方流程

```
1. 扫描QR码 → 解析分享数据包
2. 用自己的私钥解密（RSA-OAEP）
3. 验证发送方签名（可选）
4. 检查过期时间、权限
5. 显示密码 → 选择"保存到我的密码库"
```

### 3.2 远程分享（分享链接）

```
发送方 → 生成加密数据 → Base64编码 → safevault://share/{data}
                                      ↓
                              通过微信/邮件发送
                                      ↓
接收方点击链接 → SafeVault打开 → 解密流程同QR码
```

### 3.3 联系人系统

#### 添加联系人（首次）

```
1. 让接收方打开 SafeVault → "我的联系人" → "我的身份码"
2. 接收方显示QR码（包含：用户ID + 公钥 + 昵称）
3. 发送方扫描QR码 → 输入备注（如"妈妈"）→ 保存
```

#### 后续分享

```
1. 发送方选择密码 → 点击"分享"
2. 从联系人列表选择"妈妈"
3. 系统用妈妈的公钥加密 → 生成QR码
4. 妈妈扫描QR码 → 用私钥解密 → 完成
```

### 3.4 离线文件分享

```
1. 选择联系人 → 生成加密数据
2. 保存为 safevault_share_{timestamp}.svsf 文件
3. 通过U盘/文件传输应用发送
4. 接收方打开文件 → SafeVault自动解密
```

---

## 四、数据结构设计

### 4.1 身份QR码数据

```java
/**
 * 我的身份QR码内容（用于添加联系人）
 * 格式：safevault://identity/{base64(json)}
 */
class IdentityQRData {
    String version = "1.0";      // 协议版本
    String userId;               // 我的用户ID
    String username;             // 我的邮箱
    String displayName;          // 我的显示名称
    String publicKey;            // 我的公钥（Base64）
    long generatedAt;            // 生成时间
}
```

### 4.2 分享数据包（加密前）

```java
/**
 * 分享数据包（加密前的明文结构）
 */
class ShareDataPacket {
    String version;              // 协议版本
    String senderId;             // 发送方用户ID
    String senderPublicKey;      // 发送方公钥（用于验证签名）
    long createdAt;              // 创建时间
    long expireAt;               // 过期时间
    SharePermission permission;  // 权限
    PasswordItem password;       // 密码数据
    byte[] signature;            // 发送方签名（可选）
}
```

### 4.3 加密分享包

```java
/**
 * 加密后的分享包（用于QR码/链接）
 * 格式：safevault://share/{base64(encryptedData)}
 */
class EncryptedSharePacket {
    String encryptedData;        // RSA-OAEP加密的数据
    String senderId;             // 明文，用于识别发送方
}
```

### 4.4 联系人实体

```java
/**
 * 联系人实体
 */
@Entity(tableName = "contacts")
class Contact {
    @PrimaryKey String contactId;    // 联系人唯一ID
    String userId;                   // 对方的用户ID
    String username;                 // 对方的用户名（邮箱）
    String displayName;              // 对方的显示名称
    String publicKey;                // 对方的RSA公钥（Base64）
    String myNote;                   // 我的备注（如"妈妈"）
    long addedAt;                    // 添加时间
    long lastUsedAt;                 // 最后使用时间
}
```

### 4.5 分享记录实体

```java
/**
 * 分享记录实体
 */
@Entity(tableName = "share_records")
class ShareRecord {
    @PrimaryKey String shareId;      // 分享唯一ID
    int passwordId;                  // 密码ID
    String type;                     // 'sent' 或 'received'
    String contactId;                // 联系人ID
    String remoteUserId;             // 远程用户ID
    String encryptedData;            // 加密的分享数据
    SharePermission permission;      // 权限
    long expireAt;                   // 过期时间戳
    String status;                   // 'active', 'expired', 'revoked', 'accepted'
    long createdAt;                  // 创建时间
    long accessedAt;                 // 最后访问时间
}
```

---

## 五、核心组件设计

### 5.1 KeyDerivationManager

**职责**：从主密码确定性派生 RSA 密钥对

**主要方法**：
```java
public class KeyDerivationManager {
    // 派生RSA密钥对
    KeyPair deriveKeyPairFromMasterPassword(String masterPassword, String userEmail);

    // 获取公钥
    PublicKey getPublicKey(String masterPassword, String userEmail);

    // 获取私钥
    PrivateKey getPrivateKey(String masterPassword, String userEmail);

    // 获取或生成用户盐值
    String getOrGenerateUserSalt(String userEmail);
}
```

### 5.2 ShareEncryptionManager

**职责**：处理分享数据的加密/解密和签名

**主要方法**：
```java
public class ShareEncryptionManager {
    // 加密分享数据（用接收方公钥）
    String encryptShare(ShareDataPacket data, PublicKey receiverPublicKey);

    // 解密分享数据（用自己的私钥）
    ShareDataPacket decryptShare(String encryptedData, PrivateKey privateKey);

    // 签名分享数据（用自己的私钥）
    byte[] signShare(ShareDataPacket data, PrivateKey privateKey);

    // 验证签名（用发送方公钥）
    boolean verifySignature(ShareDataPacket data, PublicKey senderPublicKey);
}
```

**加密参数**：
```java
String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
int KEY_SIZE = 2048;
String HASH_ALGORITHM = "SHA-256";
```

### 5.3 ContactManager

**职责**：管理联系人（公钥+用户信息）

**主要方法**：
```java
public class ContactManager {
    // 生成我的身份QR码
    String generateMyIdentityQR(String userEmail, String masterPassword);

    // 扫描并添加联系人
    boolean addContactFromQR(String qrContent, String note);

    // 获取所有联系人
    List<Contact> getAllContacts();

    // 搜索联系人
    List<Contact> searchContacts(String query);

    // 获取联系人的公钥
    String getContactPublicKey(String contactId);

    // 删除联系人
    boolean deleteContact(String contactId);

    // 更新最后使用时间
    void updateLastUsed(String contactId);
}
```

### 5.4 ShareRecordManager

**职责**：管理分享历史记录

**主要方法**：
```java
public class ShareRecordManager {
    // 保存分享记录
    boolean saveShareRecord(ShareRecord record);

    // 获取我发送的分享
    List<ShareRecord> getMySentShares();

    // 获取我接收的分享
    List<ShareRecord> getMyReceivedShares();

    // 撤销分享
    boolean revokeShare(String shareId);

    // 检查分享是否过期
    boolean isShareExpired(String shareId);
}
```

---

## 六、数据库设计

### 6.1 Schema

```sql
-- 联系人表
CREATE TABLE contacts (
    contact_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    username TEXT NOT NULL,
    display_name TEXT,
    public_key TEXT NOT NULL,
    my_note TEXT,
    added_at INTEGER NOT NULL,
    last_used_at INTEGER
);

-- 分享记录表
CREATE TABLE share_records (
    share_id TEXT PRIMARY KEY,
    password_id INTEGER NOT NULL,
    type TEXT NOT NULL,              -- 'sent' 或 'received'
    contact_id TEXT,                 -- 接收方/发送方的联系人ID
    remote_user_id TEXT,             -- 远程用户ID（非联系人分享时）
    encrypted_data TEXT NOT NULL,    -- 加密的分享数据
    permission TEXT NOT NULL,        -- JSON格式的权限
    expire_at INTEGER,               -- 过期时间戳
    status TEXT NOT NULL,            -- 'active', 'expired', 'revoked', 'accepted'
    created_at INTEGER NOT NULL,
    accessed_at INTEGER,             -- 最后访问时间
    FOREIGN KEY (contact_id) REFERENCES contacts(contact_id)
);

-- 索引
CREATE INDEX idx_share_records_type ON share_records(type);
CREATE INDEX idx_share_records_status ON share_records(status);
CREATE INDEX idx_contacts_last_used ON contacts(last_used_at);
```

### 6.2 DAO 接口

```java
@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY last_used_at DESC")
    List<Contact> getAllContacts();

    @Query("SELECT * FROM contacts WHERE contact_id = :contactId")
    Contact getContact(String contactId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertContact(Contact contact);

    @Update
    int updateContact(Contact contact);

    @Delete
    int deleteContact(Contact contact);

    @Query("SELECT * FROM contacts WHERE display_name LIKE '%' || :query || '%' OR my_note LIKE '%' || :query || '%'")
    List<Contact> searchContacts(String query);
}

@Dao
interface ShareRecordDao {
    @Query("SELECT * FROM share_records WHERE type = 'sent' ORDER BY created_at DESC")
    List<ShareRecord> getMySentShares();

    @Query("SELECT * FROM share_records WHERE type = 'received' ORDER BY created_at DESC")
    List<ShareRecord> getMyReceivedShares();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertShareRecord(ShareRecord record);

    @Update
    int updateShareRecord(ShareRecord record);

    @Query("UPDATE share_records SET status = 'revoked' WHERE share_id = :shareId")
    int revokeShare(String shareId);

    @Query("SELECT * FROM share_records WHERE share_id = :shareId")
    ShareRecord getShareRecord(String shareId);
}
```

---

## 七、安全考虑

### 7.1 加密算法

| 用途 | 算法 | 参数 |
|------|------|------|
| 分享数据加密 | RSA-OAEP | 2048位密钥，SHA-256 |
| 签名 | SHA256withRSA | 2048位密钥 |
| 本地数据加密 | AES-GCM | 256位密钥 |
| 密钥派生 | PBKDF2 | 100,000迭代，HMAC-SHA256 |

### 7.2 关键安全措施

| 安全点 | 实现方式 |
|--------|----------|
| **私钥保护** | 私钥永不出设备，用主密码派生的密钥加密存储 |
| **端到端加密** | 使用接收方公钥加密，只有接收方私钥可解密 |
| **签名验证** | 发送方用私钥签名，接收方验证来源 |
| **防重放攻击** | 分享数据包包含时间戳和唯一ID |
| **过期机制** | 接收方验证过期时间，过期后拒绝解密 |
| **权限控制** | 解密后根据权限标志控制UI操作 |
| **内存安全** | 敏感数据使用后及时清除 |

### 7.3 RSA 加密实现

```java
public class ShareEncryptionManager {
    private static final String RSA_TRANSFORMATION =
        "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /**
     * 用接收方公钥加密分享数据
     */
    public String encryptShare(ShareDataPacket data, PublicKey receiverPublicKey) {
        try {
            // 1. 将数据包序列化为JSON
            String json = new Gson().toJson(data);
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            // 2. 使用 RSA-OAEP 加密
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, receiverPublicKey);

            byte[] encrypted = cipher.doFinal(dataBytes);

            // 3. Base64 编码
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt share data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * 用自己的私钥解密分享数据
     */
    public ShareDataPacket decryptShare(String encryptedData, PrivateKey privateKey) {
        try {
            // 1. Base64 解码
            byte[] encryptedBytes = Base64.decode(encryptedData, Base64.NO_WRAP);

            // 2. 使用 RSA-OAEP 解密
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] decrypted = cipher.doFinal(encryptedBytes);

            // 3. 反序列化
            String json = new String(decrypted, StandardCharsets.UTF_8);
            return new Gson().fromJson(json, ShareDataPacket.class);

        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt share data", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
```

---

## 八、需要删除/重构的旧代码

### 8.1 需要完全删除的文件

| 文件 | 删除原因 |
|------|----------|
| `ShareManager.java` | 明文JSON传输，完全违背零知识架构 |
| `OfflineShareUtils.java` | 密钥嵌入QR码，任何人扫描即可解密 |
| `NearbyUsersActivity.java` | 位置隐私风险，不需要此功能 |
| `CloudShareManager.java` | 本次不实现云端分享 |
| `ShareHistoryFragment.java` | 需要重新实现 |

### 8.2 需要重构的文件

| 文件 | 重构内容 |
|------|----------|
| `ShareActivity.java` | 改用新的端到端加密流程 |
| `ReceiveShareActivity.java` | 改用新的解密流程 |
| `ScanQRCodeActivity.java` | 支持扫描身份QR码和分享QR码 |
| `BluetoothReceiveActivity.java` | 可选保留，但需使用加密数据 |
| `ShareResultActivity.java` | 简化，合并到其他Activity |

### 8.3 BackendService 接口更新

```java
// 需要移除的旧接口
- String createPasswordShare(...)           // 旧实现
- String createDirectPasswordShare(...)     // 旧实现
- PasswordItem receivePasswordShare(...)    // 旧实现
- String createOfflineShare(...)            // 旧实现
- PasswordItem receiveOfflineShare(...)     // 旧实现

// 需要添加的新接口
+ List<Contact> getAllContacts()
+ boolean addContact(...)
+ String generateMyIdentityQR()
+ String createE2EShare(...)
+ PasswordItem receiveE2EShare(...)
```

---

## 九、新增文件清单

### 9.1 核心组件

```
app/src/main/java/com/ttt/safevault/
├── crypto/
│   ├── KeyDerivationManager.java          # 新增：密钥派生
│   └── ShareEncryptionManager.java        # 新增：分享加密
├── service/manager/
│   ├── ContactManager.java                # 新增：联系人管理
│   └── ShareRecordManager.java            # 新增：分享记录管理
└── utils/
    └── ShareQRGenerator.java              # 新增：QR码生成工具
```

### 9.2 数据层

```
app/src/main/java/com/ttt/safevault/
├── data/
│   ├── Contact.java                       # 新增：联系人实体
│   ├── ContactDao.java                    # 新增：联系人DAO
│   ├── ShareRecord.java                   # 新增：分享记录实体
│   └── ShareRecordDao.java                # 新增：分享记录DAO
└── database/
    └── AppDatabase.java                   # 更新：添加新表
```

### 9.3 UI组件

```
app/src/main/java/com/ttt/safevault/ui/
├── share/
│   ├── ContactListActivity.java           # 新增：联系人列表
│   ├── MyIdentityActivity.java            # 新增：我的身份码
│   ├── ScanContactActivity.java           # 新增：扫描添加联系人
│   ├── ShareActivity.java                 # 重构：分享界面
│   └── ReceiveShareActivity.java          # 重构：接收分享
├── adapter/
│   └── ContactAdapter.java                # 新增：联系人列表适配器
└── viewmodel/
    └── ShareViewModel.java                # 重构：分享ViewModel
```

### 9.4 Model类

```
app/src/main/java/com/ttt/safevault/model/
├── Contact.java                           # 新增
├── IdentityQRData.java                    # 新增
├── ShareDataPacket.java                   # 新增
├── EncryptedSharePacket.java              # 新增
└── SharePermission.java                   # 已存在，可能需要更新
```

---

## 十、实施步骤

### 阶段1：基础设施（第1-2周）

**目标**：建立新的密钥派生和加密基础

1. **KeyDerivationManager 实现**
   - [ ] 从主密码派生 RSA 密钥对
   - [ ] 单元测试：验证相同密码派生相同密钥对
   - [ ] 集成到 KeyManager

2. **ShareEncryptionManager 实现**
   - [ ] RSA-OAEP 加密/解密
   - [ ] 签名/验证功能
   - [ ] 单元测试

3. **数据库更新**
   - [ ] 创建 contacts 表
   - [ ] 创建 share_records 表
   - [ ] 实现 DAO 接口

### 阶段2：联系人系统（第3周）

**目标**：实现联系人管理功能

1. **ContactManager 实现**
   - [ ] 生成身份QR码
   - [ ] 扫描添加联系人
   - [ ] CRUD 操作

2. **UI组件**
   - [ ] ContactListActivity
   - [ ] MyIdentityActivity
   - [ ] ScanContactActivity

### 阶段3：分享功能（第4-5周）

**目标**：实现端到端加密分享

1. **核心分享流程**
   - [ ] 创建分享（用接收方公钥加密）
   - [ ] 接收分享（用私钥解密）
   - [ ] ShareRecordManager 实现

2. **UI组件**
   - [ ] 重构 ShareActivity
   - [ ] 重构 ReceiveShareActivity
   - [ ] 分享历史查看

### 阶段4：清理与测试（第6周）

**目标**：删除旧代码，完成测试

1. **代码清理**
   - [ ] 删除 ShareManager.java
   - [ ] 删除 OfflineShareUtils.java
   - [ ] 删除不需要的Activity

2. **测试**
   - [ ] 单元测试覆盖率 > 80%
   - [ ] 集成测试
   - [ ] UI测试

3. **文档**
   - [ ] 更新 API 文档
   - [ ] 更新用户指南

---

## 十一、风险与缓解措施

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 主密码更改导致密钥对变化 | 旧分享失效 | 提示用户备份旧分享，重新分享 |
| RSA密钥派生性能问题 | 分享创建慢 | 缓存派生的密钥对 |
| QR码数据过大 | 扫描困难 | 使用数据压缩，分级QR码 |
| 旧用户迁移困难 | 体验差 | 提供迁移工具或重新初始化选项 |

---

## 十二、附录

### A. QR码数据格式

**身份QR码**：
```
safevault://identity/{base64(json)}
{
  "version": "1.0",
  "userId": "user_123",
  "username": "user@example.com",
  "displayName": "张三",
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "generatedAt": 1705689600000
}
```

**分享QR码**：
```
safevault://share/{base64(encrypted_data)}
加密数据包含：
- 版本
- 发送方ID
- 过期时间
- 权限
- 加密的密码数据
- 签名（可选）
```

### B. 错误代码

| 代码 | 含义 |
|------|------|
| E001 | 无法获取私钥，请先解锁 |
| E002 | 分享数据已过期 |
| E003 | 无法验证签名 |
| E004 | QR码格式无效 |
| E005 | 联系人不存在 |

### C. 参考资料

- [RFC 8017: PKCS #1: RSA Cryptography Specifications](https://tools.ietf.org/html/rfc8017)
- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [ZXing QR Code Generator](https://github.com/zxing/zxing)

---

**文档状态**: ✅ 设计完成，等待实施
**下一步**: 开始阶段1 - 基础设施实现
