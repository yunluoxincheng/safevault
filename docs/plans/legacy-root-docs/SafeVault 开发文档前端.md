# SafeVault Android 前端开发文档

## 1. 概述

本文件为 SafeVault Android 密码管理应用的 **前端开发文档**，基于 Java 17 + Android 原生技术栈。文档内容覆盖：UI设计、交互流程、模块结构、数据模型、与后端协作方式、自动填充服务设计等。

---

## 2. 技术栈

### 2.1 核心技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Android SDK | Min 29, Target 36 | Android 10+ 支持 |
| Java | 17 | 编程语言 |
| Material Design | 3 | UI设计规范 |
| ViewBinding | - | 视图绑定 |
| Navigation Component | 2.6.0 | 页面导航 |
| RecyclerView | 1.3.0 | 列表展示 |
| ViewPager2 | 1.0.0 | 页面滑动 |
| SwipeRefreshLayout | 1.1.0 | 下拉刷新 |

### 2.2 架构组件

| 组件 | 版本 | 说明 |
|------|------|------|
| ViewModel | 2.6.2 | 视图模型 |
| LiveData | 2.6.2 | 数据观察 |
| Room | 2.5.0 | 本地数据访问（仅访问Repository） |
| WorkManager | 2.9.0 | 后台任务调度 |

### 2.3 网络与异步

| 技术 | 版本 | 说明 |
|------|------|------|
| Retrofit | 2.9.0 | REST API |
| OkHttp | 4.11.0 | HTTP客户端 |
| RxJava | 3.1.5 | 响应式编程 |
| Gson | 2.10.1 | JSON解析 |

### 2.4 功能组件

| 技术 | 版本 | 说明 |
|------|------|------|
| Biometric | 1.1.0 | 生物识别 |
| ZXing | 3.5.2 | 二维码 |
| Glide | 4.16.0 | 图片加载 |
| Play Services Location | 21.0.1 | 位置服务（可选） |
| Argon2Kt | 1.6.0 | 密钥派生 |

---

## 3. 前端整体架构

```
┌─────────────────────────────┐
│   Presentation Layer        │
│   (Activities / Fragments)  │
└─────────────┬───────────────┘
              │
┌─────────────▼───────────────┐
│      ViewModel Layer        │
│   (业务逻辑、状态管理)        │
└─────────────┬───────────────┘
              │
┌─────────────▼───────────────┐
│   BackendService Interface  │
│      (后端服务接口)          │
└─────────────────────────────┘
```

### 前端职责范围

前端只处理：
- UI展示和用户交互
- 状态管理和业务逻辑协调
- 发起请求到后端服务
- 渲染后端返回的数据

**前端不处理**：
- 加密/解密操作
- 数据库直接访问
- 明文密码持久化

---

## 4. UI页面设计

### 4.1 认证相关页面

#### LoginActivity (登录页面)

**功能**：
- 用户名/密码登录
- Challenge-Response安全认证
- 生物识别快速解锁
- 跳转注册页面

**关键交互**：
```java
// 调用后端登录
backend.login(username, password)
    .subscribe(response -> {
        // 登录成功
        navigateToMain();
    }, error -> {
        // 显示错误信息
        showError(error.getMessage());
    });
```

#### RegisterActivity (注册页面)

**功能**：
- 用户名/邮箱/密码输入
- 邮箱验证码验证
- 主密码设置
- 生成设备密钥对

**关键交互**：
```java
// 1. 发送验证码
backend.sendEmailVerification(email);

// 2. 完成注册（设置主密码）
backend.completeRegistration(email, username, masterPassword);
```

### 4.2 主界面页面

#### MainActivity (主容器)

**功能**：
- BottomNavigationView导航
- 三个顶部标签页：密码库、生成器、设置
- 处理返回键逻辑

#### PasswordListFragment (密码列表)

**功能**：
- RecyclerView显示所有密码
- 搜索框实时搜索
- 点击查看详情
- 长按显示操作菜单

**关键交互**：
```java
// 搜索密码
searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
    @Override
    public boolean onQueryTextSubmit(String query) {
        viewModel.searchPasswords(query);
        return true;
    }
});

// 点击条目
adapter.setOnItemClickListener(item -> {
    navController.navigate(
        R.id.action_list_to_detail,
        PasswordDetailFragment.createArgs(item.getId())
    );
});
```

#### PasswordDetailFragment (密码详情)

**功能**：
- 显示密码详细信息
- 复制用户名/密码（30秒自动清除）
- 编辑/删除密码
- 分享密码入口

#### EditPasswordFragment (编辑密码)

**功能**：
- 添加/编辑密码表单
- 密码生成器对话框
- 保存验证

### 4.3 生成器页面

#### GeneratorFragment (密码生成器)

**功能**：
- 长度滑块（8-32位）
- 字符类型选择
- 密码强度指示器
- 生成历史记录
- 预设配置（PIN码、强密码、易记密码）

### 4.4 设置页面

#### SettingsFragment (设置入口)

**功能**：
- 账号与安全
- 同步设置
- 外观设置
- 关于

#### AccountSecurityFragment (账号安全)

**功能**：
- 修改主密码
- 设置PIN码
- 生物识别设置
- 自动锁定时间
- 数据导出/导入
- 注销账号

#### SyncSettingsFragment (同步设置)

**功能**：
- 云端同步开关
- 同步状态显示
- 手动同步
- 同步冲突处理

### 4.5 分享相关页面

#### ShareActivity (分享配置)

**功能**：
- 选择分享方式（离线/联系人）
- 设置权限（查看/保存/撤销）
- 设置过期时间
- 生物识别验证

#### ReceiveShareActivity (接收分享)

**功能**：
- 显示分享详情
- 查看密码信息
- 保存到本地

#### ContactListFragment (联系人列表)

**功能**：
- 显示所有联系人
- 添加联系人（扫码/输入身份码）
- 查看联系人详情

#### MyIdentityActivity (我的身份)

**功能**：
- 显示个人身份二维码
- 显示身份码
- 复制身份信息

### 4.6 自动填充页面

#### AutofillCredentialSelectorActivity (凭据选择)

**功能**：
- 显示匹配的密码列表
- 选择填充凭据
- 生物识别验证

#### AutofillSaveActivity (保存密码)

**功能**：
- 保存新密码提示
- 编辑密码信息
- 确认保存

---

## 5. 数据模型

### 5.1 PasswordItem (密码条目)

```java
public class PasswordItem {
    private int id;                    // 条目ID
    private String title;              // 网站名称
    private String username;           // 用户名
    private String password;           // 密码（明文，仅在内存中）
    private String url;                // 网站URL
    private String notes;              // 备注
    private long updatedAt;            // 更新时间

    // Getters and Setters
}
```

### 5.2 Contact (联系人)

```java
public class Contact {
    private String contactId;          // 联系人ID
    private String username;           // 用户名
    private String nickname;           // 备注名称
    private String publicKey;          // 公钥（Base64）
    private long addedAt;              // 添加时间

    // Getters and Setters
}
```

### 5.3 PasswordShare (分享记录)

```java
public class PasswordShare {
    private String shareId;            // 分享ID
    private int passwordId;            // 密码ID
    private String recipientId;        // 接收方ID
    private SharePermission permission; // 分享权限
    private long expiresAt;            // 过期时间
    private boolean isActive;          // 是否活跃

    // Getters and Setters
}
```

### 5.4 SharePermission (分享权限)

```java
public class SharePermission {
    private boolean canView;           // 可查看
    private boolean canSave;           // 可保存
    private boolean revocable;         // 可撤销
}
```

---

## 6. 与后端的接口

### 6.1 BackendService接口（核心方法）

```java
public interface BackendService {
    // ========== 认证相关 ==========
    AuthResponse register(String username, String password, String displayName);
    AuthResponse login(String username, String password);
    void logout();
    boolean deleteAccount();

    // ========== 密码管理 ==========
    PasswordItem decryptItem(int id);
    List<PasswordItem> search(String query);
    int saveItem(PasswordItem item);
    boolean deleteItem(int id);
    List<PasswordItem> getAllItems();

    // ========== 密码生成 ==========
    String generatePassword(int length, boolean useUppercase,
                           boolean useLowercase, boolean useNumbers,
                           boolean useSymbols);

    // ========== 分享功能 ==========
    String createOfflineShare(int passwordId, int expireInMinutes, SharePermission permission);
    PasswordItem receiveOfflineShare(String encryptedData);
    ShareResponse createCloudShare(int passwordId, String toUserId,
                                   int expireInMinutes, SharePermission permission);
    ReceivedShareResponse receiveCloudShare(String shareId);

    // ========== 联系人管理 ==========
    List<Contact> getContacts();
    boolean addContact(String publicKey, String nickname);
    boolean deleteContact(String contactId);
    String getMyIdentityKey();

    // ========== 本地安全 ==========
    boolean setPinCode(String pinCode);
    boolean verifyPinCode(String pinCode);
    void lock();
    boolean isUnlocked();
}
```

### 6.2 调用示例

#### 登录示例

```java
private void performLogin(String username, String password) {
    Disposable disposable = backend.login(username, password)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            response -> {
                // 保存Token
                tokenManager.saveToken(response.getAccessToken());
                // 跳转主界面
                navigateToMain();
            },
            error -> {
                // 显示错误
                showError("登录失败: " + error.getMessage());
            }
        );
    compositeDisposable.add(disposable);
}
```

#### 保存密码示例

```java
private void savePassword(PasswordItem item) {
    Disposable disposable = Single.fromCallable(() -> backend.saveItem(item))
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            id -> {
                // 保存成功
                showSuccess("密码已保存");
                navigateBack();
            },
            error -> {
                // 显示错误
                showError("保存失败: " + error.getMessage());
            }
        );
    compositeDisposable.add(disposable);
}
```

---

## 7. 前端的安全措施

### 7.1 FLAG_SECURE

所有敏感页面禁止截屏录屏：

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
    );
}
```

### 7.2 剪贴板保护

复制后30秒自动清除：

```java
clipboardManager.copyToClipboard(text, 30000); // 30秒后清除
```

### 7.3 自动锁定

应用进入后台后自动锁定：

```java
@Override
protected void onPause() {
    super.onPause();
    backend.recordBackgroundTime();
}

@Override
protected void onResume() {
    super.onResume();
    long backgroundTime = backend.getBackgroundTime();
    int timeout = backend.getAutoLockTimeout();
    if (System.currentTimeMillis() - backgroundTime > timeout * 60 * 1000) {
        backend.lock();
        navigateToLogin();
    }
}
```

### 7.4 生物识别验证

敏感操作前进行生物识别验证：

```java
private void authenticate(BiometricAuthHelper.Callback callback) {
    biometricAuthHelper.authenticate(this, new BiometricAuthHelper.Callback() {
        @Override
        public void onAuthenticationSucceeded() {
            callback.onAuthenticationSucceeded();
        }

        @Override
        public void onAuthenticationFailed(int error, String errorDesc) {
            showError("生物识别验证失败");
        }
    });
}
```

---

## 8. 前端模块结构

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
│   ├── SettingsFragment.java        # 设置入口
│   ├── AccountSecurityFragment.java # 账号安全
│   ├── SyncSettingsFragment.java    # 同步设置
│   ├── share/                       # 分享UI
│   │   ├── ShareActivity.java
│   │   ├── ReceiveShareActivity.java
│   │   ├── ContactListFragment.java
│   │   └── MyIdentityActivity.java
│   └── autofill/                    # 自动填充UI
├── viewmodel/                       # ViewModels
│   ├── AuthViewModel.java
│   ├── PasswordViewModel.java
│   └── ShareViewModel.java
├── model/                           # 数据模型
│   ├── BackendService.java
│   ├── PasswordItem.java
│   └── Contact.java
├── adapter/                         # 适配器
│   ├── PasswordListAdapter.java
│   └── ContactListAdapter.java
└── utils/                           # 工具类
    ├── ClipboardManager.java
    └── QRCodeGenerator.java
```

---

## 9. 开发流程与Sprint分工

### Sprint 1 - 认证框架

**目标**：搭建登录注册框架

**任务**：
- [ ] LoginActivity UI + 基础交互
- [ ] RegisterActivity UI + 邮箱验证
- [ ] MainActivity + Navigation框架
- [ ] 后端API接口定义

### Sprint 2 - 密码管理

**目标**：实现核心密码管理功能

**任务**：
- [ ] PasswordListFragment + 搜索
- [ ] PasswordDetailFragment
- [ ] EditPasswordFragment
- [ ] GeneratorFragment + 密码生成器

### Sprint 3 - 分享功能

**目标**：实现密码分享功能

**任务**：
- [ ] ShareActivity（分享配置）
- [ ] ReceiveShareActivity（接收分享）
- [ ] ContactListFragment（联系人列表）
- [ ] MyIdentityActivity（我的身份）
- [ ] 二维码扫描和生成

### Sprint 4 - 设置与完善

**目标**：完善设置和自动填充

**任务**：
- [ ] SettingsFragment
- [ ] AccountSecurityFragment
- [ ] SyncSettingsFragment
- [ ] AutofillService UI
- [ ] 动效和细节优化

---

## 10. 开发注意事项

### 10.1 内存泄漏

- 使用`CompositeDisposable`管理RxJava订阅
- Activity/Fragment销毁时取消订阅
- 避免持有Activity/Fragment的长生命周期引用

### 10.2 性能优化

- RecyclerView使用ViewHolder模式
- 图片加载使用Glide缓存
- 网络请求使用异步处理

### 10.3 用户体验

- 加载状态显示进度条
- 错误信息友好提示
- 操作反馈及时（如复制成功提示）

### 10.4 代码规范

- 遵循Java代码规范
- 使用有意义的变量和方法名
- 添加必要的注释
- 提取公共方法到工具类

---

**文档版本**: 3.0
**最后更新**: 2026-02-28
**维护者**: SafeVault 开发团队
