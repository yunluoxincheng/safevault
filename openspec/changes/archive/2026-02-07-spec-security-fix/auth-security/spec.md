# auth-security Specification

## Purpose
定义SafeVault认证系统的安全要求，包括JWT算法、Token生命周期管理、多设备会话管理、速率限制和签名验证等。

## Requirements

### Requirement: JWT Algorithm Security
JWT Token签名算法SHALL使用非对称加密（RS256）而非对称加密（HS256）。

#### Scenario: RSA密钥对管理
- **WHEN** 初始化JWT服务
- **THEN** SHALL生成或加载RSA-2048密钥对
- **AND** 私钥SHALL安全存储（环境变量或密钥管理系统）
- **AND** 公钥SHALL用于Token验证
- **AND** 密钥SHALL至少2048位

#### Scenario: RS256 Token签名
- **WHEN** 生成JWT Token
- **THEN** SHALL使用RSA私钥和SHA-256算法签名
- **AND** SHALL在Token头指定算法为`RS256`
- **AND** SHALL包含标准声明（iss、sub、exp、iat）
- **AND** SHALL不包含敏感信息在Token中

#### Scenario: RS256 Token验证
- **WHEN** 验证JWT Token
- **THEN** SHALL使用RSA公钥验证签名
- **AND** SHALL验证算法为`RS256`
- **AND** SHALL拒绝算法为`none`的Token
- **AND** SHALL拒绝HS256算法的Token（迁移完成后）

#### Scenario: Token密钥轮换
- **WHEN** 需要轮换JWT密钥
- **THEN** SHALL生成新的RSA密钥对
- **AND** SHALL支持同时验证新旧公钥
- **AND** 新Token应使用新私钥签名
- **AND** 旧Token在过期前保持有效

---

### Requirement: Token Lifecycle Management
Token生命周期SHALL被严格管理以最小化安全风险。

#### Scenario: 访问Token短期有效
- **WHEN** 生成访问Token
- **THEN** SHALL设置过期时间为15分钟
- **AND** 过期后SHALL无效
- **AND** 无法用于刷新获取新Token

#### Scenario: 刷新Token中期有效
- **WHEN** 生成刷新Token
- **THEN** SHALL设置过期时间为7天
- **AND** 可用于获取新的访问Token
- **AND** 使用后应撤销旧刷新Token

#### Scenario: Token刷新机制
- **WHEN** 使用刷新Token
- **THEN** SHALL验证刷新Token有效性
- **AND** SHALL撤销旧的刷新Token
- **AND** SHALL生成新的访问Token和刷新Token
- **AND** 应返回新的Token对

---

### Requirement: Multi-Device Session Management
多设备会话管理SHALL限制同时活跃的会话数量。

#### Scenario: 设备会话记录
- **WHEN** 用户在新设备登录
- **THEN** SHALL创建新的设备会话记录
- **AND** SHALL记录设备ID、设备类型、登录时间
- **AND** SHALL分配唯一会话ID

#### Scenario: 活跃会话数量限制
- **GIVEN** 用户已达到最大活跃会话数（5个）
- **WHEN** 尝试在新设备登录
- **THEN** SHALL撤销最久未使用的会话
- **AND** SHALL创建新会话
- **AND** 应通知用户设备被移除

#### Scenario: 会话活跃更新
- **WHEN** 用户使用活跃会话
- **THEN** SHALL更新会话的最后活跃时间
- **AND** 应用于会话管理决策（如撤销顺序）

#### Scenario: 远程会话撤销
- **WHEN** 用户主动移除设备
- **THEN** SHALL立即撤销该设备的所有Token
- **AND** SHALL从活跃会话列表移除
- **AND** 后续API请求应被拒绝

---

### Requirement: Authentication Attempt Rate Limiting
认证尝试SHALL实施速率限制以防止暴力破解攻击。

#### Scenario: 登录速率限制
- **WHEN** 连续尝试登录
- **THEN** SHALL限制每IP每分钟最多5次尝试
- **AND** 超过限制时SHALL返回429状态码
- **AND** 应包含`Retry-After`响应头

#### Scenario: 密码错误累积限制
- **GIVEN** 同一账户多次密码错误
- **WHEN** 密码错误次数达到阈值（如5次）
- **THEN** SHALL临时锁定账户（如15分钟）
- **AND** 应通知用户账户被锁定
- **AND** 应提供解锁方式（如邮件链接）

#### Scenario: 注册速率限制
- **WHEN** 连续尝试注册
- **THEN** SHALL限制每IP每分钟最多3次尝试
- **AND** SHALL限制每邮箱每小时最多10次尝试
- **AND** 应防止邮箱枚举攻击

---

### Requirement: Signature-Based Request Authentication
基于签名的请求认证SHALL确保请求完整性。

#### Scenario: HMAC签名生成
- **WHEN** 客户端发送需要签名的请求
- **THEN** SHALL按规则拼接请求数据
- **AND** SHALL使用HMAC-SHA256计算签名
- **AND** SHALL将签名放在请求头或参数中
- **AND** 应包含时间戳防止重放

#### Scenario: HMAC签名验证
- **WHEN** 服务器接收签名请求
- **THEN** SHALL按相同规则重新计算签名
- **AND** SHALL使用时间安全比较防止时序攻击
- **AND** 签名不匹配时SHALL拒绝请求
- **AND** 应返回签名验证失败错误

#### Scenario: 时间戳验证
- **WHEN** 验证签名请求
- **THEN** SHALL验证请求时间戳
- **AND** 时间差超过5分钟SHALL拒绝请求
- **AND** 应防止重放攻击

---

### Requirement: Secure Credential Storage
凭据SHALL安全存储，不得以可逆形式存储。

#### Scenario: 密码哈希存储
- **WHEN** 存储用户密码
- **THEN** SHALL使用不可逆的哈希算法
- **AND** SHALL使用每个用户唯一的盐值
- **AND** SHALL使用足够的迭代次数（PBKDF2）
- **AND** SHALL不存储明文密码

#### Scenario: 敏感配置存储
- **WHEN** 存储JWT私钥等敏感配置
- **THEN** SHALL使用环境变量或密钥管理系统
- **AND** SHALL不提交到Git仓库
- **AND** SHALL限制访问权限

---

### Requirement: Authentication Event Logging
认证事件SHALL被记录用于安全审计。

#### Scenario: 成功登录记录
- **WHEN** 用户成功登录
- **THEN** SHALL记录用户ID、设备ID、IP地址、时间戳
- **AND** SHALL记录登录方式（密码、生物识别等）
- **AND** 应用于异常检测

#### Scenario: 失败登录记录
- **WHEN** 登录失败
- **THEN** SHALL记录用户ID（如存在）、失败原因、IP地址、时间戳
- **AND** SHALL连续失败应触发告警
- **AND** 应应用于检测暴力破解

#### Scenario: Token撤销记录
- **WHEN** Token被撤销
- **THEN** SHALL记录撤销原因、用户ID、时间戳
- **AND** 应包括登出、设备移除、超时等原因

---

### Requirement: Secure Token Transmission
Token在传输过程中SHALL被保护。

#### Scenario: Token通过HTTPS传输
- **WHEN** 传输Token
- **THEN** SHALL仅通过HTTPS连接
- **AND** SHALL不通过URL参数传输
- **AND** 应使用Authorization头传输

#### Scenario: Token不在日志中记录
- **WHEN** 记录请求日志
- **THEN** SHALL不记录完整Token
- **AND** 可记录Token前缀（如前10字符）
- **AND** 应避免泄露敏感信息

---

### Requirement: Token Revocation
Token撤销机制SHALL确保被撤销的Token立即失效。

#### Scenario: 登出撤销Token
- **WHEN** 用户主动登出
- **THEN** SHALL立即撤销当前Token
- **AND** SHALL将Token加入黑名单
- **AND** 后续验证应检查黑名单

#### Scenario: 设备移除撤销Token
- **WHEN** 用户移除设备
- **THEN** SHALL撤销该设备的所有Token
- **AND** SHALL从活跃设备列表移除
- **AND** 应通知用户移除成功

#### Scenario: 密码修改撤销所有Token
- **WHEN** 用户修改密码
- **THEN** SHALL撤销该用户的所有Token
- **AND** 应要求所有设备重新登录
- **AND** 应发送通知告知用户

---

### Requirement: Cross-Site Request Forgery Protection
系统SHALL防止CSRF攻击。

#### Scenario: CSRF Token验证
- **WHEN** 接收状态改变请求（POST、PUT、DELETE）
- **THEN** SHALL验证CSRF Token
- **AND** Token不匹配时SHALL拒绝请求
- **AND** 应为每个会话生成唯一Token

#### Scenario: SameSite Cookie属性
- **WHEN** 设置Cookie
- **THEN** SHALL设置`SameSite`属性为`Strict`或`Lax`
- **AND** 应防止跨站请求携带Cookie
