# Android安全规格 - 第二阶段

## ADDED Requirements

### Requirement: RSA Key Storage Security
RSA密钥对SHALL存储在Android硬件支持的密钥库中，不得以明文形式存储在SharedPreferences中。

#### Scenario: 新用户RSA密钥直接存储在KeyStore
- **WHEN** 新用户注册或首次使用
- **THEN** SHALL在AndroidKeyStore中生成RSA密钥对
- **AND** SHALL使用`KeyGenParameterSpec`配置密钥属性
- **AND** SHALL设置密钥用途为加密、解密、签名、验证
- **AND** SHALL设置`setUserAuthenticationRequired(true)`

#### Scenario: 旧用户RSA密钥自动迁移
- **GIVEN** 用户的RSA私钥存储在SharedPreferences中
- **WHEN** 应用启动
- **THEN** SHALL自动检测到旧密钥存储
- **AND** SHALL将密钥迁移到AndroidKeyStore
- **AND** SHALL验证迁移成功
- **AND** 迁移成功后SHALL清除SharedPreferences中的私钥
- **AND** SHALL记录迁移状态日志

#### Scenario: 密钥迁移失败时回退
- **GIVEN** 密钥迁移过程中发生异常
- **WHEN** 迁移失败
- **THEN** SHALL保持旧存储（SharedPreferences）
- **AND** SHALL记录错误日志
- **AND** SHALL NOT删除旧密钥
- **AND** 应用应继续正常工作

#### Scenario: 密钥优先从KeyStore读取
- **WHEN** 需要使用RSA私钥
- **THEN** SHALL首先尝试从AndroidKeyStore读取
- **AND** 如果KeyStore不存在，回退到SharedPreferences
- **AND** 如果使用回退方案，应记录警告日志

#### Scenario: KeyStore密钥不可导出
- **GIVEN** RSA密钥存储在AndroidKeyStore中
- **WHEN** 尝试导出私钥材料
- **THEN** SHALL AndroidKeyStore拒绝导出
- **AND** 私钥材料保持受硬件保护

---

### Requirement: PBKDF2 Iteration Count
PBKDF2密钥派生函数SHALL使用足够高的迭代次数以抵抗暴力破解攻击。

#### Scenario: 新用户使用增强PBKDF2配置
- **WHEN** 新用户注册或设置主密码
- **THEN** SHALL使用600,000次PBKDF2迭代
- **AND** SHALL记录PBKDF2版本为"v2"
- **AND** 密钥派生耗时应在100-200ms

#### Scenario: 旧用户保持兼容PBKDF2配置
- **GIVEN** 用户使用旧的PBKDF2配置（100,000次迭代）
- **WHEN** 验证密码或派生密钥
- **THEN** SHALL继续使用100,000次迭代
- **AND** SHA支持向后兼容
- **AND** 用户密码验证应成功

#### Scenario: PBKDF2迭代次数检测
- **WHEN** 派生密钥
- **THEN** SHALL根据用户PBKDF2版本选择迭代次数
- **AND** "v2"用户使用600,000次
- **AND** "v1"用户或无版本标识使用100,000次

---

### Requirement: Session Timeout
应用会话超时SHALL限制在一定时间内，超时后需要重新认证。

#### Scenario: 会话超时为5分钟
- **WHEN** 应用锁定或后台运行
- **THEN** SHALL在5分钟无操作后要求重新认证
- **AND** SHALL清除内存中的主密码
- **AND** SHALL要求用户重新输入主密码或生物识别

---

### Requirement: Autofill Password Encryption
自动填充功能存储的主密码SHALL使用加密存储，不得明文存储在SharedPreferences中。

#### Scenario: 自动填充密码加密存储
- **WHEN** 保存密码用于自动填充
- **THEN** SHALL使用Android Keystore中的主密钥加密密码
- **AND** SHALL将加密后的密码存储在SharedPreferences
- **AND** SHALL NOT存储明文密码

#### Scenario: 自动填充密码解密使用
- **WHEN** 自动填充服务需要使用主密码
- **THEN** SHALL从SharedPreferences读取加密密码
- **AND** SHALL使用Android Keystore中的主密钥解密
- **AND** SHALL将明文密码用于自动填充
- **AND** SHALL不在内存中长时间保留明文

#### Scenario: 检测并迁移明文自动填充密码
- **GIVEN** 用户的自动填充密码明文存储
- **WHEN** 检测到明文存储
- **THEN** SHALL提示用户重新设置生物识别
- **AND** SHALL清除明文密码
- **AND** SHALL存储新的加密密码

---

### Requirement: Key Migration Status Tracking
应用SHALL跟踪密钥迁移状态，用于诊断和监控。

#### Scenario: 记录密钥迁移状态
- **WHEN** RSA密钥迁移完成
- **THEN** SHALL在SharedPreferences中记录迁移状态
- **AND** SHALL记录迁移时间戳
- **AND** SHALL记录迁移结果（成功/失败）

#### Scenario: 查询密钥迁移状态
- **WHEN** 诊断或监控需要查询迁移状态
- **THEN** SHALL提供查询接口返回迁移状态
- **AND** SHALL包含是否已迁移、迁移时间、迁移结果

---

### Requirement: Biometric Key Timeout
生物识别密钥的有效期SHALL限制在短时间内。

#### Scenario: 生物识别密钥30秒有效期
- **WHEN** 生成生物识别密钥
- **THEN** SHALL设置`setUserAuthenticationValidityDurationSeconds(30)`
- **AND** 30秒后使用密钥需要重新生物识别验证

#### Scenario: 生物识别认证后短暂免验证
- **GIVEN** 用户刚完成生物识别认证
- **WHEN** 在30秒内使用生物识别密钥
- **THEN** SHALL NOT要求重新验证
- **AND** 30秒后SHALL要求重新生物识别
