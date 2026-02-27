# 后端安全规格 - 第一阶段

## ADDED Requirements

### Requirement: CORS Configuration
跨域资源共享（CORS）配置SHALL仅允许特定域名，不得使用通配符允许所有来源。

#### Scenario: CORS仅允许指定域名
- **WHEN** 配置CORS策略
- **THEN** SHALL仅允许以下来源:
  - `https://safevaultapp.top`
  - `safevault://` (Deep link scheme)
- **AND** SHALL NOT使用`setAllowedOriginPatterns("*")`
- **AND** SHALL使用`setAllowedOrigins()`而非`setAllowedOriginPatterns()`

#### Scenario: 未允许的域名请求被拒绝
- **WHEN** 来自未配置域名的跨域请求
- **THEN** 浏览器SHALL阻止该请求
- **AND** 服务器不应返回CORS头

---

### Requirement: WebSocket Security
WebSocket端点SHALL仅允许特定来源的连接。

#### Scenario: WebSocket仅允许指定域名
- **WHEN** 配置WebSocket端点
- **THEN** SHALL仅允许`https://safevaultapp.top`的连接
- **AND** SHALL使用`setAllowedOrigins()`
- **AND** SHALL NOT使用`setAllowedOriginPatterns("*")`

#### Scenario: 未授权的WebSocket连接被拒绝
- **WHEN** 来自未允许域的WebSocket连接尝试
- **THEN** 服务器SHALL拒绝握手
- **AND** 连接失败

---

### Requirement: Debug Endpoints Protection
调试端点SHALL仅在开发环境启用，生产环境不可访问。

#### Scenario: 调试端点仅在开发环境启用
- **WHEN** 配置调试端点
- **THEN** SHALL使用`@Profile("dev")`注解
- **AND** SHALL包括`/debug/pending-user`和`/debug/redis-raw`
- **AND** 生产环境（`spring.profiles.active=prod`）不可访问

#### Scenario: 生产环境访问调试端点
- **GIVEN** 生产环境（Profile=prod）
- **WHEN** 访问`/debug/pending-user`或`/debug/redis-raw`
- **THEN** SHALL返回404 Not Found
- **AND** SHALL NOT泄露任何调试信息

---

### Requirement: Token Revocation Validation
JWT认证过滤器SHALL验证Token是否已被撤销。

#### Scenario: 每次请求检查Token撤销状态
- **WHEN** 处理JWT认证请求
- **THEN** SHALL调用`tokenRevokeService.isTokenRevoked(jwt, userId, deviceId)`
- **AND** 如Token已撤销，返回401 Unauthorized
- **AND** SHALL记录警告日志

#### Scenario: 登出后Token失效
- **GIVEN** 用户已登出（Token被撤销）
- **WHEN** 使用该Token访问受保护API
- **THEN** SHALL返回401 Unauthorized
- **AND** 响应消息包含"Token已撤销"

#### Scenario: 设备被移除后Token失效
- **GIVEN** 用户设备已被移除
- **WHEN** 使用该设备的Token访问API
- **THEN** SHALL返回401 Unauthorized
- **AND** Token被识别为已撤销

---

### Requirement: Authorization Source
用户身份标识SHALL从JWT Token（SecurityContext）获取，不得从请求头读取。

#### Scenario: 从SecurityContext获取用户ID
- **WHEN** Controller需要获取当前用户ID
- **THEN** SHALL从`SecurityContextHolder.getContext().getAuthentication()`获取
- **AND** SHALL NOT从`@RequestHeader("X-User-Id")`读取
- **AND** SHALL使用辅助方法`getCurrentUserId()`

#### Scenario: 伪造X-User-Id被忽略
- **GIVEN** 请求头包含伪造的`X-User-Id: other-user-id`
- **WHEN** 处理该请求
- **THEN** SHALL使用JWT中的用户ID
- **AND** SHALL忽略请求头中的伪造值
- **AND** 用户只能访问自己的数据

---

### Requirement: Secrets Management
敏感配置信息（密钥、密码）SHALL通过环境变量提供，不得硬编码或使用弱默认值。

#### Scenario: JWT密钥从环境变量读取
- **WHEN** 配置JWT密钥
- **THEN** SHALL从环境变量`JWT_SECRET`读取
- **AND** SHALL NOT提供默认值
- **AND** 密钥长度必须至少32字符
- **AND** 启动时验证密钥存在和强度

#### Scenario: 数据库密码从环境变量读取
- **WHEN** 配置数据库连接
- **THEN** SHALL从环境变量`DB_PASSWORD`读取
- **AND** SHALL NOT提供默认值
- **AND** SHALL NOT在配置文件中包含明文密码

#### Scenario: 邮件服务密码从环境变量读取
- **WHEN** 配置邮件服务
- **THEN** SHALL从环境变量`MAIL_PASSWORD`读取
- **AND** SHALL NOT提供默认值
- **AND** SHALL轮换已暴露的密码

#### Scenario: Redis密码从环境变量读取
- **WHEN** 配置Redis连接
- **THEN** SHALL从环境变量`REDIS_PASSWORD`读取
- **AND** SHALL NOT使用弱默认密码（如`123456`）
- **AND** 生产环境必须设置强密码

#### Scenario: 环境变量未设置时启动失败
- **GIVEN** 必需的环境变量未设置
- **WHEN** 应用启动
- **THEN** SHALL抛出IllegalStateException
- **AND** 错误消息明确指出缺失的环境变量
- **AND** 应用拒绝启动

---

### Requirement: Hardcoded Secrets Elimination
代码库 SHALL 不包含任何硬编码的敏感信息。

#### Scenario: TokenRevokeService无硬编码密钥
- **GIVEN** `TokenRevokeService.java`
- **WHEN** 审查代码
- **THEN** SHALL NOT包含硬编码的JWT密钥
- **AND** SHALL从`JwtTokenProvider`获取签名密钥
- **AND** SHALL使用与Token验证相同的密钥

#### Scenario: 配置文件无明文密码
- **GIVEN** `application.yml`和`application-prod.yml`
- **WHEN** 审查配置文件
- **THEN** SHALL NOT包含任何明文密码
- **AND** SHALL使用环境变量引用（`${VAR_NAME}`）
- **AND** 敏感字段不应有默认值

---

### Requirement: API Error Message Security
错误响应SHALL不泄露敏感的内部实现细节。

#### Scenario: 全局异常处理不泄露堆栈
- **WHEN** 捕获未处理的异常
- **THEN** SHALL返回通用错误消息
- **AND** SHALL NOT将异常堆栈返回给客户端
- **AND** SHALL将详细错误记录到服务器日志

#### Scenario: 数据库错误不泄露结构
- **WHEN** 发生数据库异常
- **THEN** 错误消息SHALL NOT包含表结构
- **AND** 错误消息SHALL NOT包含SQL语句
- **AND** 返回"数据库操作失败"等通用消息

---

### Requirement: Configuration File Security
配置文件SHALL不包含敏感信息，敏感信息通过环境变量注入。

#### Scenario: .gitignore排除敏感文件
- **GIVEN** `.gitignore`文件
- **WHEN** 检查排除规则
- **THEN** SHALL排除`*.p12`和`*.jks`（密钥库文件）
- **AND** SHALL排除`.env`文件
- **AND** SHALL排除`application-local.yml`
- **AND** SHALL排除`application-prod.yml`

#### Scenario: 生产配置使用环境变量
- **GIVEN** `application-prod.yml`
- **WHEN** 检查配置
- **THEN** 所有敏感字段SHALL使用环境变量
- **AND** SHALL不包含明文密钥或密码
- **AND** SHALL不包含硬编码的凭据
