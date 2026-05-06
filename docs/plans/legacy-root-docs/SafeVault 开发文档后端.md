# SafeVault Android 后端开发文档

## 1. 概述

本文件为 SafeVault Android 密码管理应用的 **后端开发文档**。后端职责包括：加密核心、密钥管理、数据持久化、云端同步API、密码分享加密、以及为前端提供明确的安全接口。

### 技术栈

| 技术 | 版本/说明 |
|------|----------|
| 语言 | Java 17 |
| 加密 | JCA + BouncyCastle |
| 密钥派生 | Argon2Kt (Android) / Argon2-JVM (后端) |
| 本地存储 | SQLite + SQLCipher |
| 网络通信 | Retrofit 2 + OkHttp 4 |
| 数据解析 | Gson |
| 异步处理 | RxJava 3 |

---

## 2. 后端职责边界

### 2.1 核心职责

- **加密逻辑**：主密钥派生、密码加密/解密、数据完整性校验
- **密钥管理**：Android KeyStore集成、密钥包装/解包
- **数据持久化**：数据库CRUD、加密数据存储
- **云端同步**：端到端加密数据同步
- **密码分享**：离线和云端分享的加密/解密
- **认证管理**：Challenge-Response登录、Token管理

### 2.2 前后端边界

```
前端 (UI/ViewModel)
    ↓ 调用
BackendService (接口)
    ↓ 实现
后端实现层
    ├── SecureKeyStorageManager (密钥)
    ├── PasswordManager (密码)
    ├── EncryptionSyncManager (同步)
    ├── CloudAuthManager (认证)
    ├── ContactManager (联系人)
    └── ShareEncryptionManager (分享)
```

---

## 3. 三层安全架构

### 3.1 架构设计

```
┌─────────────────────────────────────┐
│      BackendService Interface       │  统一接口
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│         Manager Layer               │
│  ┌─────────────────────────────┐   │
│  │ SecureKeyStorageManager     │   │  密钥存储层
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ PasswordManager             │   │  密码管理层
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ EncryptionSyncManager       │   │  加密同步层
│  └─────────────────────────────┘   │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│         Storage & Network Layer     │
│  - SQLite Database                  │
│  - Android KeyStore                 │
│  - Retrofit Client                  │
└─────────────────────────────────────┘
```

### 3.2 核心管理器

#### SecureKeyStorageManager (密钥存储层)

**职责**：
- 管理Android KeyStore中的密钥
- Argon2密钥派生
- 加密存储敏感数据（设备私钥、主密钥）

**核心方法**：
```java
public class SecureKeyStorageManager {
    // Argon2密钥派生
    public byte[] deriveKey(String password, byte[] salt);

    // KeyStore操作
    public SecretKey getOrCreateKey(String alias);
    public void deleteKey(String alias);

    // 加密解密
    public EncryptedData encrypt(byte[] plaintext, SecretKey key);
    public byte[] decrypt(EncryptedData encrypted, SecretKey key);

    // 密钥包装
    public WrappedKey wrapKey(SecretKey keyToWrap, SecretKey wrappingKey);
    public SecretKey unwrapKey(WrappedKey wrappedKey, SecretKey wrappingKey);

    // 设备密钥管理
    public KeyPair generateDeviceKeyPair();
    public void storeEncryptedPrivateKey(PrivateKey privateKey, String password);
    public PrivateKey getDevicePrivateKey(String password);
}
```

#### PasswordManager (密码管理层)

**职责**：
- 密码的加密和解密
- 密码数据的持久化
- 密码搜索和查询

**核心方法**：
```java
public class PasswordManager {
    // CRUD操作
    public int addPassword(PasswordItem item);
    public boolean updatePassword(PasswordItem item);
    public boolean deletePassword(int id);
    public PasswordItem getPassword(int id);
    public List<PasswordItem> getAllPasswords();

    // 搜索
    public List<PasswordItem> searchPasswords(String query);

    // 统计
    public AppStats getStats();

    // 内部加密方法
    private EncryptedPassword encryptPassword(PasswordItem item);
    private PasswordItem decryptPassword(EncryptedPassword encrypted);
}
```

#### EncryptionSyncManager (加密同步层)

**职责**：
- 端到端加密数据同步
- 设备密钥对管理
- 密码库加密导出/导入

**核心方法**：
```java
public class EncryptionSyncManager {
    // 密码库同步
    public EncryptedVaultData encryptVaultData(List<PasswordItem> passwords);
    public List<PasswordItem> decryptVaultData(EncryptedVaultData encrypted);

    // 云端同步
    public boolean uploadEncryptedVault();
    public boolean downloadEncryptedVault();

    // 导入导出
    public boolean exportData(String exportPath);
    public boolean importData(String importPath);
}
```

#### CloudAuthManager (认证管理)

**职责**：
- 用户注册和登录
- Challenge-Response认证
- Token管理和刷新
- 邮箱验证

**核心方法**：
```java
public class CloudAuthManager {
    // 认证
    public AuthResponse login(String username, String password);
    public AuthResponse register(String username, String password, String displayName);
    public CompleteRegistrationResponse completeRegistration(
        String email, String username, String masterPassword);
    public void logout();

    // Token管理
    public void refreshToken();
    public boolean isTokenValid();

    // 邮箱验证
    public void sendEmailVerification(String email);
    public boolean verifyEmailCode(String email, String code);
}
```

#### ContactManager (联系人管理)

**职责**：
- 联系人CRUD操作
- 身份码管理
- 公钥存储

**核心方法**：
```java
public class ContactManager {
    // 联系人操作
    public List<Contact> getContacts();
    public boolean addContact(String publicKey, String nickname);
    public boolean deleteContact(String contactId);
    public Contact getContact(String contactId);

    // 身份管理
    public String getMyIdentityKey();
    public String getMyIdentityCode();
}
```

#### ShareEncryptionManager (分享加密)

**职责**：
- 端到端加密分享
- 离线分享
- 分享权限管理

**核心方法**：
```java
public class ShareEncryptionManager {
    // 端到端加密分享
    public String createEncryptedSharePacket(PasswordItem password, PublicKey recipientKey);
    public PasswordItem openEncryptedSharePacket(String encryptedPacket, PrivateKey myPrivateKey);

    // 离线分享
    public String createOfflineShare(PasswordItem password);
    public PasswordItem openOfflineShare(String encryptedData);
}
```

---

## 4. 数据库设计

### 4.1 表结构

#### passwords (密码表)

```sql
CREATE TABLE passwords (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    username_enc BLOB NOT NULL,
    password_enc BLOB NOT NULL,
    url TEXT,
    notes_enc BLOB,
    iv BLOB NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 索引
CREATE INDEX idx_passwords_updated ON passwords(updated_at);
```

#### contacts (联系人表)

```sql
CREATE TABLE contacts (
    contact_id TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    nickname TEXT,
    public_key TEXT NOT NULL,
    added_at INTEGER NOT NULL
);
```

#### shares (分享记录表)

```sql
CREATE TABLE shares (
    share_id TEXT PRIMARY KEY,
    password_id INTEGER NOT NULL,
    recipient_id TEXT,
    encrypted_data TEXT NOT NULL,
    permission_json TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    is_active INTEGER DEFAULT 1,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (password_id) REFERENCES passwords(id) ON DELETE CASCADE
);
```

#### user_settings (用户设置表)

```sql
CREATE TABLE user_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- 预设设置项
INSERT INTO user_settings (key, value) VALUES
    ('pin_code_enabled', '0'),
    ('biometric_enabled', '1'),
    ('auto_lock_timeout', '5'),
    ('master_password_hash', '');
```

### 4.2 加密数据格式

#### EncryptedPassword (加密密码)

```java
public class EncryptedPassword {
    private int id;
    private String title;
    private byte[] usernameEnc;  // AES-GCM加密
    private byte[] passwordEnc;  // AES-GCM加密
    private String url;
    private byte[] notesEnc;     // AES-GCM加密
    private byte[] iv;           // 12字节IV
    private long createdAt;
    private long updatedAt;
}
```

---

## 5. 加密模块设计

### 5.1 Argon2密钥派生

```java
public class Argon2KeyDerivationManager {
    private static final int ARGON2_ITERATIONS = 3;
    private static final int ARGON2_MEMORY = 65536;  // 64 MB
    private static final int ARGON2_PARALLELISM = 1;
    private static final int ARGON2_OUTPUT_LENGTH = 32;  // 256 bits

    public byte[] deriveKey(String password, byte[] salt) {
        // 使用Argon2id算法
        Argon2Result result = Argon2Factory.create()
            .hash(
                ARGON2_ITERATIONS,
                ARGON2_MEMORY,
                ARGON2_PARALLELISM,
                password.toCharArray(),
                salt
            );
        return result.getHash();
    }
}
```

### 5.2 AES-GCM加密

```java
public class AesGcmEncryption {
    private static final String GCM_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    public EncryptedData encrypt(byte[] plaintext, SecretKey key)
        throws Exception {
        // 生成随机IV
        byte[] iv = generateRandomBytes(GCM_IV_LENGTH);

        // 加密
        Cipher cipher = Cipher.getInstance(GCM_ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] ciphertext = cipher.doFinal(plaintext);

        return new EncryptedData(iv, ciphertext);
    }

    public byte[] decrypt(EncryptedData encrypted, SecretKey key)
        throws Exception {
        Cipher cipher = Cipher.getInstance(GCM_ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, encrypted.iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(encrypted.ciphertext);
    }
}
```

### 5.3 RSA密钥对管理

```java
public class RsaKeyManager {
    private static final String RSA_ALGO = "RSA";
    private static final int RSA_KEY_SIZE = 2048;
    private static final String RSA_PADDING = "OAEPWITHSHA-256ANDMGF1PADDING";

    public KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA_ALGO);
        keyGen.initialize(RSA_KEY_SIZE);
        return keyGen.generateKeyPair();
    }

    public byte[] encryptWithPublicKey(byte[] data, PublicKey publicKey)
        throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGO + "/" + RSA_PADDING);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    public byte[] decryptWithPrivateKey(byte[] encrypted, PrivateKey privateKey)
        throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGO + "/" + RSA_PADDING);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encrypted);
    }
}
```

---

## 6. 网络层设计

### 6.1 API接口定义

#### AuthApi

```java
public interface AuthApi {
    @POST("/v1/auth/login")
    Observable<AuthResponse> login(@Body LoginRequest request);

    @POST("/v1/auth/register")
    Observable<AuthResponse> register(@Body RegisterRequest request);

    @POST("/v1/auth/refresh")
    Observable<AuthResponse> refreshToken(@Body RefreshRequest request);

    @POST("/v1/auth/send-verification")
    Observable<BaseResponse> sendVerification(@Body EmailRequest request);

    @POST("/v1/auth/verify-email")
    Observable<BaseResponse> verifyEmail(@Body VerifyRequest request);

    @POST("/v1/auth/complete-registration")
    Observable<CompleteRegistrationResponse> completeRegistration(
        @Body CompleteRegistrationRequest request);
}
```

#### VaultApi

```java
public interface VaultApi {
    @GET("/v1/vault")
    Observable<VaultResponse> getVault();

    @POST("/v1/vault")
    Observable<BaseResponse> uploadVault(@Body VaultRequest request);

    @POST("/v1/vault/sync")
    Observable<SyncResponse> syncVault(@Body SyncRequest request);
}
```

#### ShareApi

```java
public interface ShareApi {
    @POST("/v1/shares")
    Observable<ShareResponse> createShare(@Body ShareRequest request);

    @GET("/v1/shares/{shareId}")
    Observable<ReceivedShareResponse> getShare(@Path("shareId") String shareId);

    @DELETE("/v1/shares/{shareId}")
    Observable<BaseResponse> revokeShare(@Path("shareId") String shareId);

    @GET("/v1/shares/my")
    Observable<ShareListResponse> getMyShares();

    @GET("/v1/shares/received")
    Observable<ShareListResponse> getReceivedShares();
}
```

#### ContactApi

```java
public interface ContactApi {
    @GET("/v1/contacts")
    Observable<ContactListResponse> getContacts();

    @POST("/v1/contacts")
    Observable<ContactResponse> addContact(@Body ContactRequest request);

    @DELETE("/v1/contacts/{contactId}")
    Observable<BaseResponse> deleteContact(@Path("contactId") String contactId);
}
```

### 6.2 Token管理

```java
public class TokenManager {
    private static final String ACCESS_TOKEN_KEY = "access_token";
    private static final String REFRESH_TOKEN_KEY = "refresh_token";
    private static final String EXPIRES_AT_KEY = "expires_at";

    private SharedPreferences prefs;

    public void saveTokens(String accessToken, String refreshToken, long expiresIn) {
        prefs.edit()
            .putString(ACCESS_TOKEN_KEY, accessToken)
            .putString(REFRESH_TOKEN_KEY, refreshToken)
            .putLong(EXPIRES_AT_KEY, System.currentTimeMillis() + expiresIn * 1000)
            .apply();
    }

    public String getAccessToken() {
        return prefs.getString(ACCESS_TOKEN_KEY, null);
    }

    public String getRefreshToken() {
        return prefs.getString(REFRESH_TOKEN_KEY, null);
    }

    public boolean isAccessTokenExpired() {
        long expiresAt = prefs.getLong(EXPIRES_AT_KEY, 0);
        return System.currentTimeMillis() >= expiresAt;
    }

    public void clearTokens() {
        prefs.edit()
            .remove(ACCESS_TOKEN_KEY)
            .remove(REFRESH_TOKEN_KEY)
            .remove(EXPIRES_AT_KEY)
            .apply();
    }
}
```

### 6.3 Token刷新拦截器

```java
public class TokenRefreshInterceptor implements Interceptor {
    private TokenManager tokenManager;
    private AuthApi authApi;

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();

        // 检查Token是否即将过期
        if (tokenManager.isAccessTokenExpired()) {
            try {
                // 刷新Token
                String refreshToken = tokenManager.getRefreshToken();
                AuthResponse response = authApi.refreshToken(
                    new RefreshRequest(refreshToken)).blockingGet();

                // 保存新Token
                tokenManager.saveTokens(
                    response.getAccessToken(),
                    response.getRefreshToken(),
                    response.getExpiresIn()
                );

                // 重新构建请求
                originalRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer " + response.getAccessToken())
                    .build();
            } catch (Exception e) {
                // 刷新失败，清除Token
                tokenManager.clearTokens();
            }
        }

        return chain.proceed(originalRequest);
    }
}
```

---

## 7. BackendService接口

```java
public interface BackendService {
    // ========== 认证相关 ==========
    AuthResponse register(String username, String password, String displayName);
    AuthResponse login(String username, String password);
    AuthResponse refreshToken(String refreshToken);
    void logout();
    boolean deleteAccount();
    CompleteRegistrationResponse completeRegistration(
        String email, String username, String masterPassword);

    // ========== 密码管理 ==========
    PasswordItem decryptItem(int id);
    List<PasswordItem> search(String query);
    int saveItem(PasswordItem item);
    boolean deleteItem(int id);
    List<PasswordItem> getAllItems();

    // ========== 密码生成 ==========
    String generatePassword(int length, boolean symbols);
    String generatePassword(int length, boolean useUppercase, boolean useLowercase,
                           boolean useNumbers, boolean useSymbols);

    // ========== 本地安全 ==========
    void lock();
    boolean isUnlocked();
    String getMasterPassword();
    void setSessionMasterPassword(String masterPassword);
    boolean isInitialized();
    boolean initialize(String masterPassword);
    boolean changeMasterPassword(String oldPassword, String newPassword);

    // ========== 分享功能 ==========
    String createOfflineShare(int passwordId, int expireInMinutes, SharePermission permission);
    PasswordItem receiveOfflineShare(String encryptedData);
    ShareResponse createCloudShare(int passwordId, String toUserId,
                                   int expireInMinutes, SharePermission permission);
    ReceivedShareResponse receiveCloudShare(String shareId);
    void revokeCloudShare(String shareId);

    // ========== 联系人管理 ==========
    List<Contact> getContacts();
    boolean addContact(String publicKey, String nickname);
    boolean deleteContact(String contactId);

    // ========== 加密同步 ==========
    boolean uploadEncryptedPrivateKey(String encryptedPrivateKey, String iv, String salt);
    EncryptedPrivateKey downloadEncryptedPrivateKey();
    boolean uploadEncryptedVaultData(String encryptedVaultData, String iv, String authTag);
    EncryptedVaultData downloadEncryptedVaultData();
}
```

---

## 8. 开发流程（Sprint）

### Sprint 1 - 密钥与加密 ✅

- [x] Argon2密钥派生实现
- [x] AES-GCM加密解密
- [x] RSA密钥对生成
- [x] Android KeyStore集成

### Sprint 2 - 密码管理 ✅

- [x] PasswordManager实现
- [x] 数据库CRUD操作
- [x] 密码搜索功能
- [x] 统计功能

### Sprint 3 - 认证管理 ✅

- [x] Challenge-Response登录
- [x] Token管理
- [x] 邮箱验证
- [x] 生物识别集成

### Sprint 4 - 分享功能 ✅

- [x] ShareEncryptionManager实现
- [x] 离线二维码分享
- [x] ContactManager实现
- [x] 端到端加密分享

### Sprint 5 - 同步功能 ✅

- [x] EncryptionSyncManager实现
- [x] 密码库加密导出
- [x] 密码库加密导入
- [x] 云端同步API

---

## 9. 测试与安全

### 9.1 单元测试

```java
// 密钥派生测试
@Test
public void testArgon2KeyDerivation() {
    byte[] salt = secureKeyStorageManager.generateSalt();
    byte[] key1 = argon2Manager.deriveKey("password123", salt);
    byte[] key2 = argon2Manager.deriveKey("password123", salt);
    assertArrayEquals(key1, key2);
}

// 加密解密测试
@Test
public void testAesGcmEncryption() throws Exception {
    byte[] plaintext = "Hello, World!".getBytes();
    SecretKey key = secureKeyStorageManager.generateKey();
    EncryptedData encrypted = aesGcmEncryption.encrypt(plaintext, key);
    byte[] decrypted = aesGcmEncryption.decrypt(encrypted, key);
    assertArrayEquals(plaintext, decrypted);
}
```

### 9.2 安全检查清单

- [ ] 密码不以明文存储
- [ ] 主密码不存储在SharedPreferences
- [ ] 敏感数据使用FLAG_SECURE
- [ ] 网络传输使用HTTPS
- [ ] Token过期自动刷新
- [ ] 密钥使用后及时清零

---

## 10. 性能优化

### 10.1 数据库优化

- 使用索引加速查询
- 批量操作使用事务
- 加密数据使用BLOB类型

### 10.2 加密优化

- 密钥派生使用异步执行
- 复用Cipher实例
- 预生成随机IV池

### 10.3 网络优化

- 使用连接池
- 启用GZIP压缩
- 实现请求缓存

---

**文档版本**: 3.0
**最后更新**: 2026-02-28
**维护者**: SafeVault 开发团队
