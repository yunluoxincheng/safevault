# SafeVault - Android密码管理器

<div align="center">

![SafeVault](https://img.shields.io/badge/SafeVault-v3.6.0-brightgreen)
![Platform](https://img.shields.io/badge/platform-Android%2010%2B-blue)
![Language](https://img.shields.io/badge/language-Java%2017-orange)
![License](https://img.shields.io/badge/license-MIT-green)

**安全的密码管理器，支持云端同步和端到端加密密码分享**

</div>

## 目录

- [项目简介](#项目简介)
- [主要功能](#主要功能)
- [技术栈](#技术栈)
- [系统要求](#系统要求)
- [快速开始](#快速开始)
- [功能详解](#功能详解)
- [项目结构](#项目结构)
- [开发指南](#开发指南)
- [版本历史](#版本历史)

---

## 项目简介

SafeVault是一款原生Android密码管理器应用，采用前后端分离架构设计。前端负责UI展示和用户交互，所有加密存储和数据持久化操作由后端服务处理。项目采用三层安全架构，确保用户数据的端到端加密保护。

### 设计理念

- **安全第一**：采用Argon2密钥派生、AES-256-GCM加密、X25519/Ed25519现代密码算法
- **隐私保护**：零知识架构，服务器无法访问明文密码
- **便捷分享**：支持端到端加密的密码分享，包括离线和云端方式
- **无缝体验**：集成Android自动填充服务，提供流畅的密码管理体验
- **好友协作**：支持联系人管理，方便与好友安全分享密码

---

## 主要功能

### 核心功能

| 功能 | 说明 |
|------|------|
| 密码管理 | 创建、编辑、删除、搜索密码条目 |
| 密码生成器 | 自定义长度和字符类型的强密码生成 |
| 生物识别 | 支持指纹和面部识别快速解锁 |
| PIN码解锁 | 4-20位数字PIN码快速解锁 |
| 自动锁定 | 应用进入后台后自动锁定，保护数据安全 |
| 自动填充 | Android系统级自动填充服务集成 |
| 安全随机填充 | 防止元数据泄露的安全填充机制 |

### 账号与安全

| 功能 | 说明 |
|------|------|
| 云端注册 | 邮箱验证注册，支持设置主密码 |
| Challenge-Response登录 | 安全的挑战-响应登录机制 |
| 设备私钥加密 | 使用主密码派生的密钥加密设备私钥 |
| 密码库同步 | 端到端加密的密码库同步 |
| 账户注销 | 安全删除账户和所有数据 |
| 密钥迁移 | 支持从旧版加密算法迁移到新算法 |

### 分享功能

| 方式 | 说明 |
|------|------|
| 离线二维码 | 生成包含加密密码的二维码，扫码即可接收 |
| 蓝牙分享 | 近距离蓝牙传输，无需网络 |
| 联系人分享 | 添加联系人后，端到端加密分享密码 |
| 分享历史 | 查看所有分享记录和接收记录 |
| 权限管理 | 设置查看、保存、撤销等权限 |
| 安全码验证 | 分享时安全码验证防止 MITM 攻击 |

### 联系人管理

| 功能 | 说明 |
|------|------|
| 扫码添加 | 扫描好友二维码快速添加联系人 |
| 身份码添加 | 手动输入好友身份码添加 |
| 好友请求 | 发送和接收好友请求 |
| 备注管理 | 为联系人添加自定义备注 |

---

## 技术栈

### 前端技术

- **语言**: Java 17
- **架构**: MVVM (Model-View-ViewModel)
- **UI框架**: Material Design 3 + ConstraintLayout
- **导航**: Android Navigation Component
- **数据绑定**: ViewBinding
- **异步处理**: RxJava 3
- **图片加载**: Glide
- **二维码**: ZXing

### 安全架构

- **密钥派生**: Argon2Kt (Android端) / Argon2-JVM (后端)
- **数据加密**: AES-256-GCM
- **非对称加密**: X25519 (ECDH) + Ed25519 (EdDSA) - v3.6.0 新增
- **兼容性**: RSA-2048 (OAEP填充) - 保留用于向后兼容
- **密钥存储**: Android KeyStore
- **三层架构**: SecureKeyStorageManager (密钥) + PasswordManager (密码) + EncryptionSyncManager (同步)

### v3.6.0 密码算法升级

| 组件 | 实现方式 |
|------|----------|
| **X25519 ECDH** | API 33+ 使用系统实现，API 32- 回退 Bouncy Castle |
| **Ed25519 EdDSA** | API 34+ 使用系统实现，API 33- 回退 Bouncy Castle |
| **HKDF 密钥派生** | 支持 SHA-256，支持身份绑定防混淆攻击 |
| **版本协商** | v2.0 (RSA) 与 v3.0 (X25519/Ed25519) 互操作 |

### 网络层

- **REST API**: Retrofit 2 + OkHttp 4
- **数据解析**: Gson
- **Token管理**: 自动刷新机制
- **SSL配置**: 支持调试SSL和正式SSL

### 系统集成

- **生物识别**: AndroidX Biometric
- **自动填充**: Android AutofillService
- **蓝牙**: Android Bluetooth API
- **通知**: Android Notification Channel

---

## 系统要求

| 项目 | 要求 |
|------|------|
| 最低版本 | Android 10 (API 29) |
| 目标版本 | Android 15 (API 36) |
| 存储空间 | 约 80MB |
| 权限 | 生物识别、相机、蓝牙（可选） |

---

## 快速开始

### 前置要求

- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 17
- Android SDK 36

### 克隆项目

```bash
git clone https://github.com/yunluoxincheng/SafeVault.git
cd SafeVault
```

### 构建项目

```bash
# 清理构建
./gradlew clean

# 构建Debug版本
./gradlew assembleDebug

# 构建Release版本
./gradlew assembleRelease

# 运行测试
./gradlew test
```

### 安装到设备

```bash
# 通过USB安装到连接的设备
./gradlew installDebug
```

### 首次运行

1. 启动应用后，选择「注册账号」或「登录」
2. 注册需要邮箱验证，验证后设置主密码
3. 开启生物识别解锁（可选）
4. 开始使用SafeVault管理密码

---

## 功能详解

### 账号注册与登录

#### 注册流程

1. 点击「注册账号」
2. 输入用户名、邮箱和密码
3. 接收并输入邮箱验证码
4. 验证成功后设置主密码
5. 系统生成设备密钥对（X25519 + Ed25519），私钥加密上传
6. 完成注册

#### 登录流程

1. 输入用户名和密码
2. 服务器返回挑战（Challenge）
3. 客户端使用主密码派生密钥响应挑战
4. 验证成功后获取访问令牌
5. 下载加密的设备私钥并解密
6. 完成登录

### 密码管理

#### 添加密码

1. 点击主界面的「+」按钮
2. 填写网站名称、用户名、密码
3. 可选填写网站URL和备注
4. 点击保存

#### 搜索密码

- 在密码列表页面的搜索框中输入关键词
- 支持搜索标题、用户名、URL等字段
- 实时显示匹配结果

#### 编辑/删除密码

1. 点击密码条目查看详情
2. 点击编辑按钮修改信息
3. 点击删除按钮删除密码

### 密码生成器

#### 使用方法

1. 进入「生成器」标签页
2. 调整密码长度（8-32位）
3. 选择字符类型：大写、小写、数字、符号
4. 查看密码强度指示器
5. 点击生成并复制

#### 预设配置

- **PIN码**: 4位数字
- **强密码**: 16位混合字符
- **易记密码**: 12位可读字符

### 联系人管理

#### 添加联系人

**扫码添加**
1. 点击「添加联系人」
2. 选择「扫描二维码」
3. 扫描好友的身份二维码
4. 添加备注并确认

**身份码添加**
1. 点击「添加联系人」
2. 选择「输入身份码」
3. 输入好友的身份码
4. 添加备注并确认

#### 查看我的身份

1. 进入「我的身份」页面
2. 显示个人身份二维码
3. 好友可扫描此二维码添加你

### 密码分享

#### 离线二维码分享

1. 打开密码详情
2. 点击「分享」按钮
3. 选择「离线二维码」方式
4. 生成包含加密密码的二维码
5. 对方扫码即可接收

#### 蓝牙分享

1. 选择「蓝牙」分享方式
2. 选择附近的蓝牙设备
3. 配对后传输数据
4. 对方接收后保存

#### 联系人分享

1. 选择「联系人」分享方式
2. 从联系人列表选择接收方
3. 设置分享权限和过期时间
4. 生物识别验证后发送
5. 对方在分享历史中查看

#### 安全码验证（防 MITM 攻击）

分享密码时，系统会生成并验证安全码：
- 发送方和接收方各自生成安全码
- 双方通过其他渠道（如口头、短信）确认安全码
- 只有安全码匹配才会完成分享
- 有效防止中间人攻击

### 密钥迁移（v3.6.0）

#### 迁移原因

X25519/Ed25519 相比 RSA-2048 的优势：
- **性能**: 密钥生成快约 100 倍
- **密钥尺寸**: 公钥/私钥仅 32 字节（RSA 约 300 字节）
- **签名尺寸**: 64 字节（RSA 约 256 字节）
- **前向保密**: 支持前向保密协议

#### 迁移流程

1. 进入「设置」→「账号安全」
2. 点击「密钥迁移」
3. 阅读迁移说明并确认
4. 系统生成新的 X25519/Ed25519 密钥对
5. 使用主密码加密保存新密钥
6. 上传新公钥到服务器
7. 迁移完成

#### 回滚支持

- 支持从 v3.0 回滚到 v2.0
- 生物识别功能不受影响
- 数据完整性保证

### 自动填充服务

#### 启用自动填充

1. 进入手机「设置」→「系统」→「语言和输入法」→「高级」→「自动填充服务」
2. 选择「SafeVault」
3. 确认启用

#### 使用自动填充

1. 在其他应用中登录时，点击用户名或密码输入框
2. 系统弹出SafeVault自动填充界面
3. 选择对应的密码条目
4. 自动填充用户名和密码

---

## 项目结构

```
com.ttt.safevault/
├── ui/                              # UI组件
│   ├── LoginActivity.java           # 登录页面
│   ├── RegisterActivity.java        # 注册页面
│   ├── MainActivity.java            # 主容器
│   ├── PasswordListFragment.java    # 密码列表
│   ├── PasswordDetailFragment.java  # 密码详情
│   ├── EditPasswordFragment.java    # 编辑密码
│   ├── GeneratorFragment.java       # 密码生成器
│   ├── SettingsFragment.java        # 设置页面
│   ├── AccountSecurityFragment.java # 账号安全设置
│   ├── SyncSettingsFragment.java    # 同步设置
│   ├── KeyMigrationActivity.java    # 密钥迁移向导
│   ├── share/                       # 分享相关UI
│   │   ├── ShareActivity.java       # 分予权限配置
│   │   ├── ReceiveShareActivity.java # 接收分享
│   │   ├── QRShareActivity.java     # 二维码分享
│   │   ├── BluetoothShareActivity.java # 蓝牙分享
│   │   ├── ContactListFragment.java # 联系人列表
│   │   ├── ShareHistoryActivity.java # 分享历史
│   │   └── MyIdentityActivity.java  # 我的身份
│   ├── friend/                      # 好友相关UI
│   │   ├── FriendDetailActivity.java # 好友详情
│   │   ├── FriendRequestListActivity.java # 好友请求
│   │   └── ContactSearchActivity.java # 联系人搜索
│   └── autofill/                    # 自动填充UI
│       ├── AutofillSaveActivity.java        # 保存密码
│       └── AutofillCredentialSelectorActivity.java # 凭据选择
├── viewmodel/                       # MVVM ViewModels
│   ├── AuthViewModel.java           # 认证视图模型
│   ├── PasswordViewModel.java       # 密码视图模型
│   └── ShareViewModel.java          # 分享视图模型
├── model/                           # 数据模型
│   ├── BackendService.java          # 后端服务接口
│   ├── PasswordItem.java            # 密码条目模型
│   ├── PasswordShare.java           # 分享记录模型
│   ├── EncryptedSharePacketV3.java  # v3.0 加密分享包
│   └── UserKeyInfo.java             # 用户密钥信息
├── service/                         # 后端服务实现
│   ├── BackendServiceImpl.java      # 后端服务实现
│   ├── KeyMigrationService.java     # 密钥迁移服务
│   └── manager/                     # 管理器
│       ├── SecureKeyStorageManager.java  # 密钥存储管理
│       ├── PasswordManager.java     # 密码管理
│       ├── EncryptionSyncManager.java  # 加密同步管理
│       ├── CloudAuthManager.java    # 云端认证管理
│       ├── AccountManager.java      # 账户管理
│       ├── ContactManager.java      # 联系人管理
│       └── ShareRecordManager.java  # 分享记录管理
├── crypto/                          # 加密管理
│   ├── Argon2KeyDerivationManager.java # Argon2密钥派生
│   ├── ShareEncryptionManager.java  # 分享加密管理
│   ├── X25519KeyManager.java        # X25519 ECDH 接口
│   ├── X25519KeyManagerFactory.java # X25519 工厂类
│   ├── SystemX25519KeyManager.java  # 系统 API 实现 (API 33+)
│   ├── BouncyCastleX25519KeyManager.java # Bouncy Castle 实现
│   ├── Ed25519Signer.java           # Ed25519 EdDSA 接口
│   ├── Ed25519SignerFactory.java    # Ed25519 工厂类
│   ├── SystemEd25519Signer.java     # 系统 API 实现 (API 34+)
│   ├── BouncyCastleEd25519Signer.java # Bouncy Castle 实现
│   ├── HKDFManager.java             # HKDF 密钥派生
│   └── CryptoConstants.java         # 安全常量配置
├── security/                        # 安全组件
│   ├── SecureKeyStorageManager.java # 三层安全架构核心
│   ├── BiometricAuthHelper.java     # 生物识别帮助类
│   ├── CryptoSession.java           # 加密会话
│   ├── SessionGuard.java            # 会话守卫
│   ├── BackupEncryptionManager.java # 备份加密管理
│   └── biometric/                   # 生物识别组件
├── network/                         # 网络层
│   ├── RetrofitClient.java          # Retrofit客户端
│   ├── TokenManager.java            # Token管理
│   ├── TokenRefreshInterceptor.java # Token刷新拦截器
│   ├── WebSocketManager.java        # WebSocket管理
│   ├── DebugSslProvider.java        # 调试SSL提供者
│   └── api/                         # API接口
│       ├── AuthApi.java             # 认证API
│       ├── AuthServiceApi.java      # 认证服务API
│       ├── PasswordApi.java         # 密码API
│       ├── ShareApi.java            # 分享API
│       └── ContactApi.java          # 联系人API
├── dto/                             # 数据传输对象
│   ├── request/                     # 请求对象
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── ShareRequest.java
│   │   └── UploadEccPublicKeyRequest.java # ECC公钥上传
│   └── response/                    # 响应对象
│       ├── AuthResponse.java
│       ├── ShareResponse.java
│       ├── PasswordListResponse.java
│       └── UploadEccPublicKeyResponse.java # ECC公钥响应
├── autofill/                        # 自动填充服务
│   └── SafeVaultAutofillService.java # 自动填充服务
├── adapter/                         # 适配器
│   ├── PasswordListAdapter.java     # 密码列表适配器
│   ├── ContactListAdapter.java      # 联系人列表适配器
│   └── ShareHistoryAdapter.java     # 分享历史适配器
└── utils/                           # 工具类
    ├── PasswordStrengthCalculator.java # 密码强度计算
    ├── ClipboardManager.java        # 剪贴板管理
    └── QRCodeGenerator.java         # 二维码生成
```

---

## 开发指南

### 架构设计

SafeVault采用MVVM架构模式结合三层安全架构：

```
┌─────────────────┐
│     UI Layer    │  Activities / Fragments
└────────┬────────┘
         │
┌────────▼────────┐
│  ViewModel      │  管理UI状态和业务逻辑
└────────┬────────┘
         │
┌────────▼────────┐
│  BackendService │  后端服务接口
└────────┬────────┘
         │
┌────────▼────────┐
│   Manager Layer │  业务逻辑层
└────────┬────────┘
         │
┌────────▼────────┐
│  Security Layer │  三层安全架构
│  - KeyStorage   │  密钥存储 (RSA + X25519 + Ed25519)
│  - Password     │  密码管理
│  - Sync         │  加密同步
└─────────────────┘
```

### 三层安全架构

SafeVault采用模块化的三层安全架构设计：

1. **SecureKeyStorageManager（密钥存储层）**
   - 管理Android KeyStore中的密钥
   - 支持多版本密钥：RSA-2048 + X25519 + Ed25519
   - 处理主密码派生（Argon2）
   - 加密存储敏感数据

2. **PasswordManager（密码管理层）**
   - 密码的加密和解密
   - 密码数据的持久化
   - 密码搜索和查询

3. **EncryptionSyncManager（加密同步层）**
   - 端到端加密数据同步
   - 设备私钥管理
   - 密码库加密导出/导入

### 核心接口

#### BackendService接口（部分）

```java
// 认证相关
AuthResponse register(String username, String password, String displayName);
AuthResponse login(String username, String password);
void logout();
boolean deleteAccount();

// ECC 密钥管理（v3.6.0 新增）
UploadEccPublicKeyResponse uploadEccPublicKey(String x25519PublicKey, String ed25519PublicKey);

// 密码管理
PasswordItem decryptItem(int id);
List<PasswordItem> search(String query);
int saveItem(PasswordItem item);
boolean deleteItem(int id);

// 分享功能
String createOfflineShare(int passwordId, int expireInMinutes, SharePermission permission);
PasswordItem receiveOfflineShare(String encryptedData);
ShareResponse createCloudShare(int passwordId, String toUserId, ...);

// 联系人管理
List<Contact> getContacts();
boolean addContact(String identityKey, String nickname);
boolean deleteContact(String contactId);

// 密钥迁移
KeyMigrationResult migrateToX25519(String password, String salt, BackendService backendService);
```

### 安全特性

- **Argon2密钥派生**: 使用Argon2id算法从主密码派生加密密钥，自适应性能调优
- **AES-256-GCM**: 所有数据使用AES-256-GCM加密
- **X25519 ECDH**: v3.0+ 使用 X25519 进行密钥交换，支持前向保密
- **Ed25519 EdDSA**: v3.0+ 使用 Ed25519 进行数字签名
- **RSA-2048**: 保留用于向后兼容
- **HKDF**: 密钥派生函数，支持身份绑定防混淆攻击
- **FLAG_SECURE**: 所有页面禁止截屏录屏
- **自动锁定**: 后台超时自动锁定
- **剪贴板保护**: 复制后30秒自动清除
- **生物识别**: 支持指纹/面部识别
- **零知识**: 服务器无法访问明文密码
- **安全随机填充**: 防止元数据泄露

### 调试模式

启用调试日志：

```java
// 在Application中设置
SafeVaultApplication.setDebugMode(true);
```

查看自动填充日志：

```bash
adb shell
run-as com.ttt.safevault
cat files/autofill_debug.log
```

### 构建变体

```bash
# Debug构建
./gradlew assembleDebug

# Release构建
./gradlew assembleRelease

# 查看构建报告
./gradlew assembleDebug --info
```

---

## 版本历史

### v3.6.0 (最新)

**密码算法升级**

- 新增 X25519 ECDH 密钥交换（API 33+ 优先系统 API，自动回退 Bouncy Castle）
- 新增 Ed25519 EdDSA 数字签名（API 34+ 优先系统 API，自动回退 Bouncy Castle）
- 新增 HKDF 密钥派生（支持身份绑定，防密钥混淆攻击）
- RSA-2048 保留用于向后兼容

**数据传输协议 v3.0**

- EncryptedSharePacketV3：使用 ephemeral X25519 前向保密
- 支持版本协商：v2.0（RSA）与 v3.0（X25519/Ed25519）互操作
- UserKeyInfo：支持查询用户的所有公钥信息

**密钥管理**

- SecureKeyStorageManager 扩展：同时存储 RSA + X25519 + Ed25519 密钥
- KeyMigrationService：密钥迁移服务（支持老用户迁移到 v3.0）
- 支持 v2 ↔ v3 版本回滚

**性能提升**

- X25519 密钥生成比 RSA-2048 快约 100 倍
- 密钥尺寸从 300 字节减少到 32 字节
- 签名尺寸从 256 字节减少到 64 字节

### v3.5.0

**内存安全强化**

- 内存安全强化（敏感数据自动清除）
- 添加 authTag 支持和本地保险库重置功能
- 修复卸载重装后无法同步云端数据的问题

### v3.4.1

- 动画优化
- UI体验改进

### v3.4.0

- 架构兼容性修复
- 完善错误处理机制

### v3.3.4

- 文档更新
- 代码注释完善

### v3.3.3

- 新增Challenge-Response登录机制
- 提升登录安全性

### v3.3.0 - v3.3.2

- 三层安全架构重构
- 密钥管理模块化
- 加密同步优化

### v3.2.0

- 云端注册登录功能
- 邮箱验证机制
- 设备私钥加密存储
- 密码库端到端加密同步

### v3.1.0

- 联系人管理功能
- 端到端加密密码分享
- 离线二维码分享
- 蓝牙分享

### v3.0.0

- 完整的后端API集成
- Token自动刷新机制
- PIN码解锁功能
- 账户安全设置

### v2.x 系列

- 自动填充服务实现
- 密码生成器
- 生物识别解锁
- Material Design 3 UI

---

## 贡献指南

欢迎贡献代码、报告问题或提出建议！

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 联系方式

- 作者: yunluoxincheng
- 邮箱: yunluoxincheng@outlook.com
- 项目链接: [https://github.com/yunluoxincheng/SafeVault](https://github.com/yunluoxincheng/SafeVault)

---

<div align="center">

**Made with ❤️ for Android Security**

</div>