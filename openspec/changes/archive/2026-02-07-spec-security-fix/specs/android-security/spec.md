# Android安全规格变更 - 添加第三阶段要求

## ADDED Requirements

### Requirement: JWT Token Algorithm
JWT Token SHALL使用RS256非对称加密算法进行签名。

#### Scenario: RS256 Token验证
- **WHEN** 接收JWT Token
- **THEN** SHALL使用RSA公钥验证签名
- **AND** SHALL接受RS256算法的Token
- **AND** SHALL拒绝HS256算法的Token

#### Scenario: Token自动刷新
- **GIVEN** 访问Token即将过期（15分钟有效期）
- **WHEN** Token过期
- **THEN** SHALL自动使用刷新Token获取新的访问Token
- **AND** SHALL在后台静默刷新
- **AND** 刷新失败时应提示用户重新登录

#### Scenario: 401响应处理
- **GIVEN** API返回401 Unauthorized
- **WHEN** 接收到401响应
- **THEN** SHALL尝试刷新Token
- **AND** 如果刷新失败，跳转到登录页面
- **AND** 应显示"会话已过期，请重新登录"提示

---

### Requirement: Session Timeout (Updated)
会话超时时间SHALL与Token过期时间保持一致。

#### Scenario: 会话超时为15分钟
- **WHEN** 用户15分钟无操作
- **THEN** SHALL清除会话状态
- **AND** 下次操作时应重新认证
- **AND** 生物识别认证可快速恢复

---

### Requirement: Device Management
用户SHALL能够查看和管理登录的设备。

#### Scenario: 获取设备列表
- **WHEN** 用户请求设备列表
- **THEN** SHALL显示所有登录的设备
- **AND** 应包含设备名称、登录时间、最后活跃时间
- **AND** 应标识当前设备

#### Scenario: 移除远程设备
- **WHEN** 用户选择移除设备
- **THEN** SHALL调用API撤销该设备的Token
- **AND** 应显示移除成功确认
- **AND** 被移除的设备应无法访问账户

#### Scenario: 设备数量限制提示
- **GIVEN** 用户已登录5台设备
- **WHEN** 尝试在第6台设备登录
- **THEN** SHALL显示"设备数量已达上限"提示
- **AND** 应引导用户移除旧设备
- **AND** 最久未使用的设备应被自动撤销

---

### Requirement: Auto Token Refresh
应用SHALL自动刷新过期的访问Token。

#### Scenario: 透明Token刷新
- **GIVEN** 用户有有效的刷新Token
- **WHEN** 访问Token过期（15分钟后）
- **THEN** SHALL自动调用刷新API
- **AND** SHOULD在后台执行，用户无感知
- **AND** 刷新成功后重试原请求

#### Scenario: 刷新Token过期
- **GIVEN** 刷新Token已过期（7天后）
- **WHEN** 尝试刷新访问Token
- **THEN** 刷新SHALL失败
- **AND** SHALL清除本地Token
- **AND** SHALL跳转到登录页面
- **AND** 应显示"会话已过期，请重新登录"

---

### Requirement: Biometric Re-authentication
生物识别SHALL用于快速重新认证。

#### Scenario: 生物识别快速恢复会话
- **GIVEN** 用户已启用生物识别
- **WHEN** 会话超时或Token过期
- **THEN** SHALL提示使用生物识别
- **AND** 生物识别成功后恢复会话
- **AND** 应自动获取新Token

#### Scenario: 生物识别不可用降级
- **GIVEN** 用户已启用生物识别但设备不可用
- **WHEN** 需要重新认证
- **THEN** SHALL降级到主密码输入
- **AND** 应提示输入主密码
