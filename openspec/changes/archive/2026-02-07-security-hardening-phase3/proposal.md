# Change: SafeVault 安全加固 - 第三阶段

## Why

前两个阶段已解决大部分安全问题，但仍有以下关键安全加固需要实施：

1. **JWT使用HS256对称加密** - 密钥泄露可伪造任意Token，需改用RS256非对称加密
2. **Token过期时间过长** - 访问Token 24小时、刷新Token 30天，增加攻击窗口
3. **邮箱登录签名验证不完整** - 仅检查Base64格式，未验证HMAC
4. **缺少速率限制** - 登录/注册API无保护，易受暴力破解
5. **缺少并发登录控制** - 无法限制同时登录设备数量

## What Changes

### JWT算法升级（HS256 → RS256）
- **生成RSA-2048密钥对**
  - 私钥用于签名Token
  - 公钥用于验证Token
  - 私钥通过环境变量配置

### Token过期时间调整
- **访问Token**: 24小时 → 15分钟
- **刷新Token**: 30天 → 7天

### 完整HMAC签名验证
- 实现完整的HMAC-SHA256签名验证
- 移除简化的Base64格式检查
- 使用用户盐值作为HMAC密钥

### 速率限制
- 登录API: 5次/分钟
- 注册API: 3次/分钟
- 邮箱验证: 10次/小时

### 并发登录控制
- 限制最多5个设备同时登录
- 新设备登录时撤销最久未使用的Token

## Impact

### 受影响的规格
- `android-security` - Android Token管理
- `backend-security` - JWT和认证配置
- `auth-security` - 认证和授权安全

### 受影响的代码

#### Android前端
- `app/src/main/java/com/ttt/safevault/network/TokenManager.java` - Token存储
- `app/src/main/java/com/ttt/safevault/network/AuthInterceptor.java` - Token刷新

#### SpringBoot后端
- `safevault-backend/src/main/java/org/ttt/safevaultbackend/security/JwtTokenProvider.java` - JWT生成和验证
- `safevault-backend/src/main/java/org/ttt/safevaultbackend/service/AuthService.java` - 认证服务
- `safevault-backend/src/main/java/org/ttt/safevaultbackend/service/TokenRevokeService.java` - Token撤销

### 新增依赖
```xml
<!-- Bucket4j for rate limiting -->
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.1.0</version>
</dependency>
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.1.0</version>
</dependency>
```

### 环境变量配置
```bash
# RSA密钥对（Base64编码）
JWT_RSA_PRIVATE_KEY=...
JWT_RSA_PUBLIC_KEY=...
```

## Non-Breaking Changes

- API接口保持不变
- 数据格式保持不变
- 业务逻辑保持不变
- 仅认证机制升级
