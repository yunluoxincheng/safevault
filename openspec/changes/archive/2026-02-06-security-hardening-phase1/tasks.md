# 安全加固第一阶段 - 实施任务清单

## 1. Android前端安全修复

### 1.1 SSL/TLS证书验证修复
- [x] 1.1.1 移除`RetrofitClient.java`中的自定义`X509TrustManager`
- [x] 1.1.2 移除`sslSocketFactory`自定义配置
- [x] 1.1.3 移除`hostnameVerifier`信任所有配置
- [x] 1.1.4 使用系统默认SSL证书验证
- [x] 1.1.5 测试HTTPS连接正常

**文件**: `app/src/main/java/com/ttt/safevault/network/RetrofitClient.java:51-91`

### 1.2 生物识别密钥配置修复
- [x] 1.2.1 修改`BiometricKeyManager.java`中的`setUserAuthenticationRequired(false)`
- [x] 1.2.2 改为`setUserAuthenticationRequired(true)`
- [x] 1.2.3 设置认证有效期：`setUserAuthenticationValidityDurationSeconds(30)`
- [x] 1.2.4 测试生物识别认证流程

**文件**: `app/src/main/java/com/ttt/safevault/security/BiometricKeyManager.java:54-66`

### 1.3 密钥管理改进
- [x] 1.3.1 检查并标记需要迁移的旧密钥存储
- [x] 1.3.2 添加密钥迁移检测逻辑（为第二阶段准备）
- [x] 1.3.3 记录密钥迁移状态日志

**文件**: `app/src/main/java/com/ttt/safevault/security/KeyManager.java`

**实施内容**:
- 添加 `KeyStorageType` 枚举：LEGACY（SharedPreferences明文存储）和 ANDROID_KEYSTORE（硬件保护存储）
- 实现 `detectKeyStorageType()` 方法：检测当前密钥存储方式
- 实现 `recordKeyStorageStatus()` 方法：记录密钥存储状态，输出警告日志
- 实现 `needsMigration()` 方法：检查是否需要迁移
- 实现 `markKeysMigratedToKeyStore()` 方法：标记密钥已迁移（第二阶段使用）
- 在 `loadOrGenerateKeys()` 中自动检测并记录存储状态

### 1.4 日志安全改进
- [x] 1.4.1 搜索所有`Log.d()`调用中的敏感信息
- [x] 1.4.2 移除包含密码、Token、密钥的日志
- [x] 1.4.3 替换所有`printStackTrace()`为`Log.e()`（16处文件）
- [x] 1.4.4 添加ProGuard规则在Release版本移除日志

**影响文件**:
- `SafeVaultAutofillService.java` (3处)
- `AutofillSaveActivity.java` (3处)
- `SettingsFragment.java` (1处)
- 其他文件

### 1.5 启用代码混淆
- [x] 1.5.1 修改`app/build.gradle`设置`minifyEnabled true`
- [x] 1.5.2 设置`shrinkResources true`
- [x] 1.5.3 完善`proguard-rules.pro`规则
- [x] 1.5.4 配置R8完整模式到`gradle.properties`
- [x] 1.5.5 构建Release版本并测试

**文件**:
- `app/build.gradle`
- `app/proguard-rules.pro`
- `gradle.properties`

**完成结果**:
- Debug APK: 21MB
- Release APK (混淆+资源收缩): 8.0MB
- 减小比例: 62% (超出30%目标)
- 混淆映射文件已生成: `app/build/outputs/mapping/release/mapping.txt`
- 类名和方法名已成功混淆 (如: `KeyDerivationManager -> h5.a`, `TokenManager -> l5.k`)

### 1.6 资源优化
- [x] 1.6.1 运行`./gradlew lint`查找未使用资源
- [x] 1.6.2 移除未使用的drawable资源
- [x] 1.6.3 移除未使用的字符串资源
- [x] 1.6.4 移除未使用的颜色、布局、菜单、动画、raw资源
- [x] 1.6.5 测试APK功能正常

**已完成优化**:
- 移除4个未使用的布局文件
- 移除9个未使用的drawable资源
- 移除9个未使用的颜色资源
- 移除2个未使用的动画资源
- 移除4个未使用的菜单/导航资源
- 移除约90个未使用的字符串资源
- Release APK 大小保持 8.0MB (混淆+资源收缩已生效)

**注意**: `safevault_cert.pem` 是自签名证书文件，已被恢复保留，不应删除

---

## 2. 后端安全修复

### 2.1 CORS配置修复
- [x] 2.1.1 修改`SecurityConfig.java`中的`setAllowedOriginPatterns`
- [x] 2.1.2 改为只允许特定域名：
  ```java
  configuration.setAllowedOrigins(Arrays.asList(
      "https://safevaultapp.top",
      "safevault://"
  ));
  ```
- [ ] 2.1.3 测试跨域请求正常

**文件**: `safevault-backend/.../security/SecurityConfig.java:60`

**完成内容**:
- 添加 `ALLOWED_ORIGINS` 常量，包含白名单域名
- 使用 `setAllowedOrigins()` 替代 `setAllowedOriginPatterns()` 严格限制域名
- 仅允许 `https://safevaultapp.top` 和 `safevault://`（deep link scheme）

### 2.2 WebSocket配置修复
- [x] 2.2.1 修改`WebSocketConfig.java`中的`setAllowedOriginPatterns`
- [x] 2.2.2 改为只允许特定域名
- [ ] 2.2.3 测试WebSocket连接正常

**文件**: `safevault-backend/.../websocket/WebSocketConfig.java:29-30`

**完成内容**:
- 添加 `ALLOWED_ORIGINS` 常量数组，包含白名单域名
- 将两个端点的 `.setAllowedOriginPatterns("*")` 改为 `.setAllowedOriginPatterns(ALLOWED_ORIGINS)`
- 仅允许 `https://safevaultapp.top` 和 `safevault://`（deep link scheme）

### 2.3 禁用调试端点
- [x] 2.3.1 为`/debug/pending-user`添加`@Profile("dev")`注解
- [x] 2.3.2 为`/debug/redis-raw`添加`@Profile("dev")`注解
- [ ] 2.3.3 验证生产环境调试端点不可访问

**文件**: `safevault-backend/.../controller/AuthController.java:168-198`

**完成内容**:
- 导入 `org.springframework.context.annotation.Profile` 包
- 为 `/debug/pending-user` 端点添加 `@Profile("dev")` 注解
- 为 `/debug/redis-raw` 端点添加 `@Profile("dev")` 注解
- 更新API文档描述，标注"仅开发环境"
- 添加安全加固注释说明

### 2.4 Token撤销检查
- [x] 2.4.1 在`JwtAuthenticationFilter`中添加撤销检查
- [x] 2.4.2 调用`tokenRevokeService.isTokenRevoked()`
- [x] 2.4.3 Token已撤销时返回401状态码
- [ ] 2.4.4 测试登出后Token失效

**文件**: `safevault-backend/.../security/JwtAuthenticationFilter.java`

**完成内容**:
- 导入 `TokenRevokeService` 依赖
- 修改构造函数注入 `TokenRevokeService`
- 在 JWT 验证通过后，添加撤销检查
- 获取 `X-Device-ID` 请求头用于设备级撤销验证
- Token 已撤销时返回 `SC_UNAUTHORIZED` (401) 状态码
- 返回 JSON 格式错误响应：`{"error":"Token已撤销，请重新登录"}`
- 添加安全加固注释标识（2.4）

### 2.5 越权访问修复
- [x] 2.5.1 修改`VaultController`从SecurityContext获取用户ID
- [x] 2.5.2 修改`UserController`从SecurityContext获取用户ID
- [x] 2.5.3 修改其他Controller使用`@RequestHeader("X-User-Id")`的地方
- [x] 2.5.4 添加辅助方法`getCurrentUserId()`
- [ ] 2.5.5 测试用户只能访问自己的数据

**影响文件**:
- `BaseController.java` (新建)
- `VaultController.java` - 7个端点已修改
- `AuthController.java` - 3个端点已修改
- `UserController.java` - 已使用正确模式（/me端点），无需修改

**完成内容**:
- 创建 `BaseController` 抽象基类，包含 `getCurrentUserId()` 辅助方法
- `VaultController` 继承 `BaseController`，移除所有 `@RequestHeader("X-User-Id")` 参数
- `AuthController` 继承 `BaseController`，移除 `@RequestHeader("X-User-Id")` 参数（logout、getDevices、removeDevice）
- `UserController` 无需修改（已使用 `/me` 端点模式，Service层从SecurityContext获取用户）
- 添加安全加固注释标识（2.5）

### 2.6 移除硬编码密钥
- [x] 2.6.1 移除`TokenRevokeService.java`中的硬编码JWT密钥
- [x] 2.6.2 从`JwtTokenProvider`获取签名密钥
- [x] 2.6.3 确保TokenRevokeService和JwtTokenProvider使用相同密钥
- [ ] 2.6.4 测试Token撤销功能

**文件**: `safevault-backend/.../service/TokenRevokeService.java:175-176`

**完成内容**:
- 在 `JwtTokenProvider` 添加公共方法 `getSigningKeyPublic()` 返回签名密钥
- 修改 `TokenRevokeService.getSigningKey()` 调用 `tokenProvider.getSigningKeyPublic()`
- 移除硬编码的JWT密钥字符串
- 添加安全加固注释标识（2.6）

### 2.7 环境变量配置
- [x] 2.7.1 修改`application.yml`移除所有明文密码
- [x] 2.7.2 改用环境变量引用：
  ```yaml
  spring:
    mail:
      password: ${MAIL_PASSWORD}
    datasource:
      password: ${DB_PASSWORD}
    data:
      redis:
        password: ${REDIS_PASSWORD}
  jwt:
    secret: ${JWT_SECRET}
  ```
- [x] 2.7.3 添加环境变量启动验证
- [x] 2.7.4 创建`application-prod.yml`模板

**文件**: `safevault-backend/src/main/resources/application.yml`

**完成内容**:
- 修改 `application.yml`：移除邮件密码硬编码，改用 `${MAIL_PASSWORD}`
- 修改 `application.yml`：移除 Redis 密码默认值，改用 `${REDIS_PASSWORD}`
- 修改 `application.yml`：移除 keystore 密码硬编码，改用 `${KEYSTORE_PASSWORD}`
- 修改 `application-dev.yml`：移除邮件密码和数据库密码硬编码
- 修改 `application-prod.yml`：移除所有敏感字段默认值
- 创建 `.env.example`：环境变量配置模板
- 创建 `.env`：开发环境实际配置（已加入 .gitignore）
- 更新 `.gitignore`：添加 `.env`、`*.p12`、`*.jks`、`application-local.yml`
- 在 `JwtTokenProvider` 添加 `@PostConstruct` 验证密钥长度至少 32 字符
- 密钥不符合要求时启动失败并抛出 `IllegalStateException`

### 2.8 JWT密钥强度验证
- [x] 2.8.1 在`JwtTokenProvider`添加`@PostConstruct`验证
- [x] 2.8.2 检查密钥长度至少32字符
- [x] 2.8.3 密钥不符合要求时启动失败
- [x] 2.8.4 测试环境变量未设置时的行为

**文件**: `safevault-backend/.../security/JwtTokenProvider.java`

**完成内容**:
- 在 `JwtTokenProvider.java` 添加 `@PostConstruct` 方法 `validateJwtSecret()`
- 检查密钥是否为 null 或空，是则抛出 `IllegalStateException`
- 检查密钥长度至少 32 字符，否则抛出 `IllegalStateException`
- 检查是否使用默认/示例密钥（如包含 "change"、"test-only"），记录警告日志
- 验证通过时记录日志显示密钥长度

---

## 3. 配置和部署

### 3.1 生成新密钥
- [x] 3.1.1 生成JWT RSA密钥对（2048位）
- [x] 3.1.2 生成强随机数据库密码（跳过，用户确认不需要）
- [x] 3.1.3 生成强随机Redis密码（跳过，用户确认不需要）
- [x] 3.1.4 轮换邮件服务密码（跳过，用户确认不需要）

**完成内容**:
- 生成 2048 位 RSA 私钥：`safevault-backend/src/main/resources/keys/jwt-private.pem`
- 导出 RSA 公钥：`safevault-backend/src/main/resources/keys/jwt-public.pem`
- 更新 `.gitignore` 添加 `*.pem` 规则，防止私钥被提交到 Git
- 密钥为第三阶段 JWT 从 HS256 升级到 RS256 做准备，当前不实际使用

### 3.2 .gitignore更新
- [x] 3.2.1 添加`*.p12`到.gitignore
- [x] 3.2.2 添加`*.jks`到.gitignore
- [x] 3.2.3 添加`.env`到.gitignore
- [x] 3.2.4 添加`application-local.yml`到.gitignore
- [ ] 3.2.5 清理Git历史中的敏感数据（可选）

**完成内容**:
- 更新 `safevault-backend/.gitignore`，添加：
  - `.env`
  - `.env.local`
  - `*.p12`（密钥库文件）
  - `*.jks`（Java 密钥存储）
  - `application-local.yml`（本地配置）

### 3.3 环境变量文档
- [x] 3.3.1 创建部署环境变量清单
- [x] 3.3.2 创建生产环境配置模板
- [ ] 3.3.3 创建Docker Compose环境变量模板

**完成内容**:
- 创建 `.env.example`：包含所有环境变量的说明和示例
  - 数据库配置：DB_URL, DB_USER, DB_PASSWORD
  - Redis 配置：REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
  - JWT 配置：JWT_SECRET（至少 32 字符）
  - 邮件配置：MAIL_PASSWORD
  - SSL 配置：KEYSTORE_PASSWORD
- 创建 `.env`：开发环境实际配置（已加入 .gitignore）

---

## 4. 代码质量改进

### 4.1 处理TODO注释
- [x] 4.1.1 处理`ShareNotificationService.java:344` - 实现本地数据库保存（转为Issue跟踪）
- [x] 4.1.2 处理`PasswordListViewModel.java:269` - 使用剪贴板管理器（已实现）
- [x] 4.1.3 处理`ShareHistoryActivity.java:202` - 实现保存功能（转为Issue跟踪）
- [x] 4.1.4 删除`BaseActivity.java:34`中的无用TODO（已删除）

**完成内容**:
- `ShareNotificationService.java:344` - 将TODO转为 `@issue` 注释，说明需要实现本地联系人持久化功能
- `PasswordListViewModel.java:269` - 实现 `copyPassword()` 方法，添加系统 ClipboardManager 支持
- `ShareHistoryActivity.java:202` - 将TODO转为 `@issue` 注释，说明需要实现保存到密码库功能
- `BaseActivity.java:34` - 删除无用的BackendService获取TODO注释
- `KeyManager.java:35` - 保留作为第二阶段迁移文档参考

### 4.2 异常处理改进
- [x] 4.2.1 替换`SafeVaultAutofillService.java`中的`printStackTrace()`
- [x] 4.2.2 替换`AutofillSaveActivity.java`中的`printStackTrace()`
- [x] 4.2.3 替换`SettingsFragment.java`中的`printStackTrace()`
- [x] 4.2.4 替换其他文件中的`printStackTrace()`

**完成内容**: 所有 `printStackTrace()` 已在 1.4.4 节中完成替换为 `Log.e()`

### 4.3 空Catch块修复
- [x] 4.3.1 修复`AuthInterceptor.java`中的空catch块
- [x] 4.3.2 修复其他文件中的空catch块
- [x] 4.3.3 为所有空catch块添加日志记录

**完成内容**:
- `AuthInterceptor.java:285` - 添加中断处理和恢复中断状态的代码
- `FillResponseBuilder.java:334` - 添加日志记录
- `AutofillMatcher.java:280` - 添加日志记录
- `AutofillParser.java:914` - 添加日志记录
- `AutofillParser.java:921` - 添加日志记录
- `ClipboardManager.java:113` - 添加日志记录

---

## 5. 测试

### 5.1 单元测试
- [ ] 5.1.1 测试SSL证书验证
- [ ] 5.1.2 测试生物识别认证
- [ ] 5.1.3 测试Token撤销
- [ ] 5.1.4 测试越权访问防护

### 5.2 集成测试
- [ ] 5.2.1 测试完整的认证流程
- [ ] 5.2.2 测试API跨域请求
- [ ] 5.2.3 测试WebSocket连接
- [ ] 5.2.4 测试密码库CRUD操作

### 5.3 安全测试
- [ ] 5.3.1 验证调试端点不可访问
- [ ] 5.3.2 验证CORS限制生效
- [ ] 5.3.3 验证Token撤销生效
- [ ] 5.3.4 尝试越权访问（应失败）

---

## 6. 部署

### 6.1 部署前检查
- [ ] 6.1.1 所有测试通过
- [ ] 6.1.2 ProGuard混淆构建成功
- [ ] 6.1.3 APK大小减少验证
- [ ] 6.1.4 环境变量配置完成

### 6.2 部署执行
- [ ] 6.2.1 部署后端服务（包含环境变量）
- [ ] 6.2.2 部署前端APK
- [ ] 6.2.3 验证核心功能正常
- [ ] 6.2.4 监控错误日志

### 6.3 部署后验证
- [ ] 6.3.1 用户登录测试
- [ ] 6.3.2 密码库同步测试
- [ ] 6.3.3 分享功能测试
- [ ] 6.3.4 生物识别测试

---

## 完成标准

- [ ] 所有任务标记为完成
- [ ] 所有测试通过
- [ ] APK大小减少至少30%
- [ ] 无安全扫描严重漏洞
- [ ] 生产环境部署成功
- [ ] 用户反馈无异常
