# Change: SafeVault 安全加固 - 第一阶段（无风险修复）

## Why

经过全面的部署前安全审计，发现SafeVault项目存在24个严重安全问题、33个高危问题和26个中危问题。这些漏洞可能导致：
- JWT密钥泄露使攻击者可伪造任意Token
- SSL/TLS验证被禁用导致中间人攻击
- RSA私钥明文存储导致所有历史分享可被解密
- CORS配置不当导致CSRF攻击
- 敏感信息硬编码并提交到Git仓库

本次提案专注于**第一阶段修复**：不影响现有功能、不需要数据迁移的安全加固项目。

## What Changes

### 无风险修复（可直接部署）
- 修复Android客户端SSL证书验证（移除信任所有证书的代码）
- 限制后端CORS配置为特定域名（移除通配符`*`）
- 限制WebSocket连接来源为特定域名
- 禁用生产环境调试端点（添加Profile条件）
- 添加Token撤销检查到JWT认证过滤器
- 修复越权访问风险（从SecurityContext获取用户ID而非请求头）
- 修复生物识别密钥配置（启用用户认证要求）
- 启用ProGuard代码混淆和资源压缩

### 配置安全修复
- 移除所有硬编码的敏感信息（JWT密钥、邮件密码、数据库密码）
- 使用环境变量替代配置文件中的明文密码
- 添加必需环境变量的启动验证

### 代码质量改进
- 替换所有`printStackTrace()`为`Log.e()`
- 移除调试日志中的敏感信息
- 处理4个TODO注释

### 资源优化
- 移除未使用的资源文件
- 优化图片资源（WebP格式）
- 预计减少30-40%的APK大小

## Impact

### 受影响的规格
- `android-security` - Android前端安全规范
- `backend-security` - 后端安全规范
- `network-security` - 网络通信安全规范

### 受影响的代码

#### Android前端
- `app/src/main/java/com/ttt/safevault/network/RetrofitClient.java` - SSL证书验证
- `app/src/main/java/com/ttt/safevault/security/BiometricKeyManager.java` - 生物识别配置
- `app/src/main/java/com/ttt/safevault/security/KeyManager.java` - 密钥管理
- `app/src/main/java/com/ttt/safevault/crypto/CryptoManager.java` - 加密管理
- `app/build.gradle` - ProGuard配置

#### SpringBoot后端
- `safevault-backend/src/main/java/org/ttt/safevaultbackend/security/SecurityConfig.java` - CORS配置
- `safevault-backend/src/main/java/org/ttt/safevaultbackend/websocket/WebSocketConfig.java` - WebSocket配置
- `safevault-backend/src/main/java/org/ttt/safevaultbackend/security/JwtAuthenticationFilter.java` - JWT过滤器
- `safevault-backend/src/main/java/org/ttt/safevaultbackend/controller/AuthController.java` - 调试端点
- `safevault-backend/src/main/java/org/ttt/safevaultbackend/controller/VaultController.java` - 越权访问修复
- `safevault-backend/src/main/java/org/ttt/safevaultbackend/service/TokenRevokeService.java` - 移除硬编码密钥
- `safevault-backend/src/main/resources/application.yml` - 配置文件

### 部署影响
- **无功能影响** - 所有修复不改变现有业务逻辑
- **需配置环境变量** - 部署时需设置JWT_SECRET、DB_PASSWORD等环境变量
- **需更新前端域名白名单** - 确保safevaultapp.top在CORS白名单中

### 用户体验
- **无影响** - 用户无需任何操作
- **性能提升** - 启用ProGuard后APK大小减少30-40%

## Migration Plan

### 部署前准备
1. 生成新的JWT RSA密钥对（至少2048位）
2. 轮换所有暴露的密码（邮件、数据库、Redis）
3. 配置生产环境环境变量
4. 更新.gitignore排除敏感文件

### 部署步骤
1. 部署后端更新（包含环境变量配置）
2. 部署前端更新（包含ProGuard混淆）
3. 验证API连接正常
4. 验证WebSocket连接正常

### 回滚计划
- 保留原有配置文件备份
- 如遇问题可快速回滚到上一版本

## Non-Breaking Changes

所有修改均为**非破坏性变更**：
- 不改变API接口
- 不改变数据格式
- 不改变业务逻辑
- 向后兼容现有功能

## Next Phases

**第二阶段**（需数据迁移）：
- RSA私钥迁移到AndroidKeyStore
- 后端RSA填充从PKCS1改为OAEP
- 提高PBKDF2迭代次数

**第三阶段**（需用户操作）：
- JWT从HS256改为RS256（用户需重新登录）
- 自动填充密码加密存储
