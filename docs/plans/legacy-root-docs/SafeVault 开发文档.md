# SafeVault Android 密码管理器 - 开发文档

## 一、项目概述

SafeVault 是一款原生 Android 密码管理应用，采用前后端分离架构设计。项目支持密码生成、加密存储、自动填充、多设备同步、端到端加密密码分享等高级功能。应用采用零知识安全架构，确保用户敏感数据在任何时刻均以加密形式存储和传输。

### 当前版本

- **版本号**: v3.4.1
- **最低SDK**: 29 (Android 10)
- **目标SDK**: 36
- **语言**: Java 17

---

## 二、产品功能需求

### 2.1 核心功能（已实现）

| 功能模块 | 描述 | 状态 |
|---------|------|------|
| 主密码登录 | 使用主密码解锁应用 | ✅ |
| 生物识别解锁 | 指纹/面部识别快速解锁 | ✅ |
| PIN码解锁 | 4-20位数字PIN码 | ✅ |
| 密码生成器 | 可配置长度和字符集 | ✅ |
| 加密存储 | AES-256-GCM加密 | ✅ |
| 密码分类 | 标签和搜索功能 | ✅ |
| 自动锁定 | 后台超时自动锁定 | ✅ |
| 自动填充 | Android AutofillService | ✅ |

### 2.2 云端功能（已实现）

| 功能模块 | 描述 | 状态 |
|---------|------|------|
| 云端注册 | 邮箱验证注册 | ✅ |
| Challenge-Response登录 | 安全的挑战-响应认证 | ✅ |
| 设备私钥加密 | 主密码派生密钥加密私钥 | ✅ |
| 密码库同步 | 端到端加密同步 | ✅ |
| 设备管理 | 管理已登录设备 | ✅ |
| 账户注销 | 安全删除账户数据 | ✅ |

### 2.3 分享功能（已实现）

| 功能模块 | 描述 | 状态 |
|---------|------|------|
| 离线二维码分享 | 加密二维码分享 | ✅ |
| 蓝牙分享 | 近距离蓝牙传输 | ✅ |
| 联系人管理 | 添加/删除联系人 | ✅ |
| 端到端加密分享 | RSA-2048加密 | ✅ |
| 分享权限管理 | 查看/保存/撤销权限 | ✅ |
| 分享历史 | 创建/接收记录 | ✅ |

### 2.4 进阶功能（规划中）

| 功能模块 | 描述 | 状态 |
|---------|------|------|
| 双因素认证 | TOTP支持 | 🔄 |
| 安全审计 | 密码强度分析 | 🔄 |
| 导入导出 | 加密文件格式 | 🔄 |
| 浏览器扩展 | Chrome/Edge支持 | ⏳ |

---

## 三、系统架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────┐
│         Presentation Layer          │
│   (Activities / Fragments / UI)      │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│          ViewModel Layer            │
│     (业务逻辑、状态管理、导航)        │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│       BackendService Interface      │
│         (后端服务统一接口)           │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│         Manager Layer               │
│  ┌─────────────────────────────┐   │
│  │ SecureKeyStorageManager     │   │  密钥存储层
│  │ - Argon2密钥派生             │   │
│  │ - Android KeyStore管理       │   │
│  │ - 加密数据存储               │   │
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ PasswordManager             │   │  密码管理层
│  │ - 密码加密/解密              │   │
│  │ - 本地数据库操作             │   │
│  │ - 密码搜索                  │   │
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ EncryptionSyncManager       │   │  加密同步层
│  │ - 端到端加密同步             │   │
│  │ - 设备密钥管理               │   │
│  │ - 密码库导入导出             │   │
│  └─────────────────────────────┘   │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│        Platform Layer               │
│  - Android KeyStore                 │
│  - SQLite Database                  │
│  - Network (Retrofit + OkHttp)      │
│  - System Services (Biometric, etc) │
└─────────────────────────────────────┘
```

### 3.2 三层安全架构详解

#### 第一层：SecureKeyStorageManager（密钥存储层）

**职责**：
- 管理Android KeyStore中的密钥
- 处理Argon2密钥派生
- 加密存储敏感数据（如设备私钥、主密钥）

**核心功能**：
```java
public class SecureKeyStorageManager {
    // Argon2密钥派生
    public byte[] deriveKey(String password, byte[] salt);

    // 获取或生成主密钥
    public SecretKey getOrGenerateMasterKey();

    // 加密数据
    public EncryptedData encrypt(byte[] data, SecretKey key);

    // 解密数据
    public byte[] decrypt(EncryptedData encryptedData, SecretKey key);
}
```

#### 第二层：PasswordManager（密码管理层）

**职责**：
- 密码的加密和解密
- 密码数据的持久化
- 密码搜索和查询

**核心功能**：
```java
public class PasswordManager {
    // 添加密码
    public int addPassword(PasswordItem item);

    // 获取密码（解密）
    public PasswordItem getPassword(int id);

    // 搜索密码
    public List<PasswordItem> searchPasswords(String query);

    // 删除密码
    public boolean deletePassword(int id);

    // 更新密码
    public boolean updatePassword(PasswordItem item);
}
```

#### 第三层：EncryptionSyncManager（加密同步层）

**职责**：
- 端到端加密数据同步
- 设备密钥对管理
- 密码库加密导出/导入

**核心功能**：
```java
public class EncryptionSyncManager {
    // 生成设备密钥对
    public KeyPair generateDeviceKeyPair();

    // 获取设备私钥（解密后）
    public PrivateKey getDevicePrivateKey(String password);

    // 加密密码库
    public EncryptedVaultData encryptVault(List<PasswordItem> passwords);

    // 解密密码库
    public List<PasswordItem> decryptVault(EncryptedVaultData encrypted);
}
```

---

## 四、安全设计

### 4.1 安全架构原则

| 原则 | 描述 |
|------|------|
| 零知识 | 服务器无法访问任何明文数据 |
| 最小权限 | 只请求必要的系统权限 |
| 端到端加密 | 数据在传输和存储时始终加密 |
| 密钥分离 | 不同用途使用不同密钥 |

### 4.2 密钥与加密机制

| 用途 | 技术方案 | 描述 |
|------|---------|------|
| 主密码派生 | Argon2id | 从主密码派生主密钥（KEK） |
| 数据加密 | AES-256-GCM | 加密密码、用户名等敏感数据 |
| 密钥存储 | Android KeyStore | 安全存储密钥材料 |
| 非对称加密 | RSA-2048-OAEP | 端到端分享使用 |
| 密钥包装 | AES-KW | 主密钥加密其他密钥 |

### 4.3 加密流程图

#### 注册流程

```
用户输入邮箱和密码
        ↓
发送验证码到邮箱
        ↓
验证邮箱 → 设置主密码
        ↓
Argon2派生主密钥 (KEK)
        ↓
生成设备RSA密钥对
        ↓
使用KEK加密私钥 → 上传到云端
        ↓
完成注册
```

#### 登录流程

```
用户输入用户名和密码
        ↓
服务器返回挑战 (Challenge)
        ↓
客户端使用主密码派生密钥响应
        ↓
验证通过 → 获取访问令牌
        ↓
下载加密的设备私钥
        ↓
使用主密码解密私钥
        ↓
完成登录
```

#### 密码存储流程

```
用户输入密码信息
        ↓
生成随机AES密钥 (DEK)
        ↓
使用DEK加密密码数据
        ↓
使用KEK加密DEK
        ↓
存储加密数据和加密的DEK
```

### 4.4 安全策略

| 策略 | 实现 |
|------|------|
| 禁止截屏 | FLAG_SECURE |
| 自动锁定 | 后台超时检测 |
| 生物识别 | BiometricPrompt |
| 剪贴板保护 | 30秒自动清除 |
| Token刷新 | 自动刷新机制 |
| SSL Pinning | 调试SSL配置 |

---

## 五、主要模块设计

### 5.1 核心管理器

#### SecureKeyStorageManager

```java
public class SecureKeyStorageManager {
    // 密钥派生
    public byte[] deriveKey(String password, byte[] salt);

    // KeyStore操作
    public SecretKey getOrCreateKey(String alias);

    // 加密解密
    public EncryptedData encrypt(byte[] plaintext);
    public byte[] decrypt(EncryptedData encrypted);

    // 密钥包装
    public WrappedKey wrapKey(SecretKey keyToWrap);
    public SecretKey unwrapKey(WrappedKey wrappedKey);
}
```

#### PasswordManager

```java
public class PasswordManager {
    // CRUD操作
    public int addPassword(PasswordItem item);
    public boolean updatePassword(PasswordItem item);
    public boolean deletePassword(int id);
    public PasswordItem getPassword(int id);
    public List<PasswordItem> getAllPasswords();

    // 搜索
    public List<PasswordItem> search(String query);

    // 加密解密
    private EncryptedPassword encryptPassword(PasswordItem item);
    private PasswordItem decryptPassword(EncryptedPassword encrypted);
}
```

#### EncryptionSyncManager

```java
public class EncryptionSyncManager {
    // 设备密钥管理
    public KeyPair generateDeviceKeyPair();
    public void storeDevicePrivateKey(PrivateKey privateKey, String password);
    public PrivateKey getDevicePrivateKey(String password);
    public PublicKey getDevicePublicKey();

    // 密码库同步
    public EncryptedVaultData encryptVaultData(List<PasswordItem> passwords);
    public List<PasswordItem> decryptVaultData(EncryptedVaultData encrypted);

    // 云端同步
    public boolean uploadEncryptedVault();
    public boolean downloadEncryptedVault();
}
```

#### CloudAuthManager

```java
public class CloudAuthManager {
    // 认证
    public AuthResponse login(String username, String password);
    public AuthResponse register(String username, String password, String displayName);
    public void logout();

    // Token管理
    public void refreshToken();
    public boolean isTokenValid();

    // 邮箱验证
    public void sendEmailVerification(String email);
    public boolean verifyEmailCode(String email, String code);
}
```

#### ContactManager

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

#### ShareEncryptionManager

```java
public class ShareEncryptionManager {
    // 创建加密分享包
    public String createEncryptedSharePacket(PasswordItem password, PublicKey recipientKey);

    // 打开加密分享包
    public PasswordItem openEncryptedSharePacket(String encryptedPacket, PrivateKey myPrivateKey);

    // 离线分享
    public String createOfflineShare(PasswordItem password);
    public PasswordItem openOfflineShare(String encryptedData);
}
```

### 5.2 UI组件结构

```
ui/
├── auth/
│   ├── LoginActivity              # 登录页面
│   └── RegisterActivity           # 注册页面
├── main/
│   ├── MainActivity               # 主容器
│   ├── PasswordListFragment       # 密码列表
│   ├── PasswordDetailFragment     # 密码详情
│   ├── EditPasswordFragment       # 编辑密码
│   └── GeneratorFragment          # 密码生成器
├── settings/
│   ├── SettingsFragment           # 设置入口
│   ├── AccountSecurityFragment    # 账号安全
│   └── SyncSettingsFragment       # 同步设置
├── share/
│   ├── ShareActivity              # 分享配置
│   ├── ReceiveShareActivity       # 接收分享
│   ├── ContactListFragment        # 联系人列表
│   ├── QRShareActivity            # 二维码分享
│   ├── BluetoothShareActivity     # 蓝牙分享
│   └── MyIdentityActivity         # 我的身份
└── autofill/
    ├── AutofillCredentialSelectorActivity  # 凭据选择
    └── AutofillSaveActivity        # 保存密码
```

---

## 六、数据模型设计

### 6.1 数据库表结构

#### passwords (密码表)

| 字段 | 类型 | 描述 |
|------|------|------|
| id | INTEGER | 主键 |
| title | TEXT | 网站名称（加密或明文） |
| username_enc | BLOB | 加密的用户名 |
| password_enc | BLOB | 加密的密码 |
| url | TEXT | 网站URL |
| notes_enc | BLOB | 加密的备注 |
| iv | BLOB | AES-GCM初始化向量 |
| created_at | INTEGER | 创建时间戳 |
| updated_at | INTEGER | 更新时间戳 |

#### contacts (联系人表)

| 字段 | 类型 | 描述 |
|------|------|------|
| contact_id | TEXT | 联系人ID |
| username | TEXT | 用户名 |
| nickname | TEXT | 备注名称 |
| public_key | TEXT | 公钥（Base64） |
| added_at | INTEGER | 添加时间 |

#### shares (分享记录表)

| 字段 | 类型 | 描述 |
|------|------|------|
| share_id | TEXT | 分享ID |
| password_id | INTEGER | 密码ID |
| recipient_id | TEXT | 接收方ID |
| encrypted_data | TEXT | 加密的分享数据 |
| permission_json | TEXT | 权限配置（JSON） |
| expires_at | INTEGER | 过期时间 |
| is_active | INTEGER | 是否活跃（0/1） |
| created_at | INTEGER | 创建时间 |

### 6.2 数据模型类

```java
// 密码条目
public class PasswordItem {
    private int id;
    private String title;
    private String username;
    private String password;
    private String url;
    private String notes;
    private long updatedAt;
}

// 联系人
public class Contact {
    private String contactId;
    private String username;
    private String nickname;
    private String publicKey;
    private long addedAt;
}

// 分享记录
public class PasswordShare {
    private String shareId;
    private int passwordId;
    private String recipientId;
    private SharePermission permission;
    private long expiresAt;
    private boolean isActive;
}

// 分享权限
public class SharePermission {
    private boolean canView;
    private boolean canSave;
    private boolean revocable;
}
```

---

## 七、网络层设计

### 7.1 API接口

#### AuthApi (认证接口)

```java
public interface AuthApi {
    @POST("/v1/auth/register")
    Observable<AuthResponse> register(@Body RegisterRequest request);

    @POST("/v1/auth/login")
    Observable<AuthResponse> login(@Body LoginRequest request);

    @POST("/v1/auth/refresh")
    Observable<AuthResponse> refreshToken(@Body RefreshRequest request);

    @POST("/v1/auth/send-verification")
    Observable<BaseResponse> sendVerification(@Body EmailRequest request);

    @POST("/v1/auth/verify-email")
    Observable<BaseResponse> verifyEmail(@Body VerifyRequest request);
}
```

#### PasswordApi (密码接口)

```java
public interface PasswordApi {
    @GET("/v1/passwords")
    Observable<PasswordListResponse> getPasswords();

    @POST("/v1/passwords")
    Observable<PasswordResponse> createPassword(@Body PasswordRequest request);

    @PUT("/v1/passwords/{id}")
    Observable<PasswordResponse> updatePassword(@Path("id") String id,
                                                @Body PasswordRequest request);

    @DELETE("/v1/passwords/{id}")
    Observable<BaseResponse> deletePassword(@Path("id") String id);
}
```

#### ShareApi (分享接口)

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

#### ContactApi (联系人接口)

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

### 7.2 Token管理

```java
public class TokenManager {
    private static final String ACCESS_KEY = "access_token";
    private static final String REFRESH_KEY = "refresh_token";

    public void saveTokens(String accessToken, String refreshToken);
    public String getAccessToken();
    public String getRefreshToken();
    public void clearTokens();
    public boolean isAccessTokenExpired();
    public Observable<String> refreshAccessToken();
}
```

### 7.3 Token刷新拦截器

```java
public class TokenRefreshInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();

        // 检查Token是否过期
        if (tokenManager.isAccessTokenExpired()) {
            // 刷新Token
            String newToken = tokenManager.refreshAccessToken().blockingFirst();
            // 重新构建请求
            originalRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer " + newToken)
                .build();
        }

        return chain.proceed(originalRequest);
    }
}
```

---

## 八、UI设计规范

### 8.1 Material Design 3

SafeVault 使用 Material Design 3 设计规范：

| 组件 | 样式 |
|------|------|
| TextInputLayout | Outlined box |
| MaterialButton | Filled / Outlined / Text |
| MaterialCardView | 12dp corner radius |
| MaterialSwitch | - |
| LinearProgressIndicator | 用于加载和强度指示 |
| BottomNavigationView | 三个标签页 |

### 8.2 动态颜色

- Android 12+ (API 31+): 使用系统动态颜色
- Android 10-11 (API 29-30): 使用紫色主题

### 8.3 核心页面

| 页面 | 描述 |
|------|------|
| 登录页 | 主密码输入、生物识别、注册入口 |
| 主页 | 密码列表、搜索、悬浮添加按钮 |
| 详情页 | 密码信息、复制、编辑、分享 |
| 编辑页 | 表单输入、密码生成器 |
| 设置页 | 分组设置项 |
| 分享页 | 分享方式选择、权限配置 |

---

## 九、开发流程（里程碑）

### Sprint 1 - 认证框架 ✅

- [x] LoginActivity UI + 交互
- [x] RegisterActivity UI + 邮箱验证
- [x] MainActivity + Navigation框架
- [x] 后端API接口定义

### Sprint 2 - 密码管理 ✅

- [x] PasswordListFragment + 搜索
- [x] PasswordDetailFragment
- [x] EditPasswordFragment
- [x] GeneratorFragment + 密码生成器

### Sprint 3 - 分享功能 ✅

- [x] ShareActivity（分享配置）
- [x] ReceiveShareActivity（接收分享）
- [x] ContactListFragment（联系人列表）
- [x] MyIdentityActivity（我的身份）
- [x] 二维码扫描和生成

### Sprint 4 - 设置与完善 ✅

- [x] SettingsFragment
- [x] AccountSecurityFragment
- [x] SyncSettingsFragment
- [x] AutofillService UI
- [x] 动效和细节优化

---

## 十、测试与质量保证

### 10.1 单元测试

```java
// 密钥派生测试
@Test
public void testArgon2KeyDerivation() {
    byte[] salt = secureKeyStorageManager.generateSalt();
    byte[] key1 = secureKeyStorageManager.deriveKey("password123", salt);
    byte[] key2 = secureKeyStorageManager.deriveKey("password123", salt);
    assertArrayEquals(key1, key2);
}

// 密码加密解密测试
@Test
public void testPasswordEncryption() throws Exception {
    PasswordItem original = new PasswordItem("test", "user", "pass123");
    EncryptedPassword encrypted = passwordManager.encryptPassword(original);
    PasswordItem decrypted = passwordManager.decryptPassword(encrypted);
    assertEquals(original.getPassword(), decrypted.getPassword());
}
```

### 10.2 UI测试

```java
@Test
public void testLoginFlow() {
    // 输入用户名密码
    onView(withId(R.id.username_edit_text)).perform(typeText("testuser"));
    onView(withId(R.id.password_edit_text)).perform(typeText("password123"));

    // 点击登录
    onView(withId(R.id.login_button)).perform(click());

    // 验证跳转到主页面
    intended(hasComponent(MainActivity.class.getName()));
}
```

### 10.3 安全测试检查清单

- [ ] 所有敏感页面使用FLAG_SECURE
- [ ] 密码不以明文存储在数据库
- [ ] 主密码不存储在SharedPreferences
- [ ] 网络传输使用HTTPS
- [ ] Token过期自动刷新
- [ ] 生物识别正确集成
- [ ] 剪贴板自动清除

---

## 十一、发布与合规

### 11.1 Play Store 准备

- [ ] 应用图标（多尺寸）
- [ ] 功能截图（手机和平板）
- [ ] 应用描述
- [ ] 隐私政策
- [ ] 内容分级
- [ ] 签名配置

### 11.2 隐私政策要点

1. **数据收集**：仅收集必要的账户信息
2. **数据存储**：所有密码数据本地加密存储
3. **数据传输**：使用HTTPS端到端加密
4. **第三方服务**：不使用第三方分析工具
5. **用户权利**：用户可随时导出或删除数据

---

## 十二、版本历史

| 版本 | 日期 | 主要更新 |
|------|------|---------|
| v3.4.1 | 2026-02 | 动画优化、UI改进 |
| v3.4.0 | 2026-02 | 架构兼容性修复 |
| v3.3.3 | 2026-02 | Challenge-Response登录 |
| v3.3.0 | 2026-02 | 三层安全架构重构 |
| v3.2.0 | 2026-01 | 云端注册登录 |
| v3.1.0 | 2026-01 | 联系人管理、密码分享 |
| v3.0.0 | 2025-12 | 后端API集成、PIN码 |
| v2.1.0 | 2026-01 | 端到端加密分享 |
| v2.0.0 | 2025-12 | Material Design 3 |

---

**文档版本**: 3.0
**最后更新**: 2026-02-28
**维护者**: SafeVault 开发团队
