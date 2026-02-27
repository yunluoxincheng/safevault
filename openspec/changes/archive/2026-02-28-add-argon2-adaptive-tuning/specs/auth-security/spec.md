# auth-security Spec Delta (Argon2 自适应调优)

## ADDED Requirements

### Requirement: Argon2 自适应参数配置
系统 SHALL 根据设备能力自动调优 Argon2id 参数，并设定最低安全下限。

#### Scenario: 高端设备使用标准参数
- **GIVEN** 设备可用内存 >= 512MB
- **AND** CPU 核心数 >= 4
- **WHEN** 初始化 Argon2 配置
- **THEN** 系统 SHALL 使用内存成本 = 128MB
- **AND** 系统 SHALL 使用迭代次数 = 3
- **AND** 系统 SHALL 使用并行度 = 4

#### Scenario: 低端设备使用降级参数
- **GIVEN** 设备可用内存 < 192MB
- **OR** CPU 核心数 < 4
- **WHEN** 初始化 Argon2 配置
- **THEN** 系统 SHALL 使用内存成本 = 64MB（最低下限）
- **AND** 系统 SHALL 使用迭代次数 = 2（最低下限）
- **AND** 系统 SHALL 使用并行度 = 2（最低下限）

#### Scenario: 中端设备使用平衡参数
- **GIVEN** 设备可用内存 >= 192MB 但 < 512MB
- **WHEN** 初始化 Argon2 配置
- **THEN** 系统 SHALL 计算内存成本 = min(可用内存 / 4, 128MB)
- **AND** 系统 SHALL 使用迭代次数 = 2 或 3（根据内存）
- **AND** 系统 SHALL 使用并行度 = min(CPU 核心数, 4)

---

### Requirement: Argon2 参数最低安全下限
系统 SHALL 确保 Argon2id 参数不低于最低安全下限。

#### Scenario: 最低内存成本限制
- **WHEN** 计算最优内存成本
- **THEN** 系统 SHALL 确保内存成本 >= 64MB (65536 KB)
- **AND** 如果计算值低于下限，系统 SHALL 使用下限值
- **AND** 系统 SHALL 记录"使用最低下限"日志

#### Scenario: 最低迭代次数限制
- **WHEN** 计算最优迭代次数
- **THEN** 系统 SHALL 确保迭代次数 >= 2
- **AND** 如果计算值低于下限，系统 SHALL 使用下限值

#### Scenario: 最低并行度限制
- **WHEN** 计算最优并行度
- **THEN** 系统 SHALL 确保并行度 >= 2
- **AND** 系统 SHALL 不超过 CPU 核心数

---

### Requirement: Argon2 参数持久化
系统 SHALL 在首次启动时计算并存储参数，避免重复计算。

#### Scenario: 首次启动计算参数
- **GIVEN** SharedPreferences 中不存在 Argon2 配置
- **WHEN** 应用首次启动
- **THEN** 系统 SHALL 检测设备能力
- **AND** 系统 SHALL 计算最优参数
- **AND** 系统 SHALL 存储到 SharedPreferences

#### Scenario: 后续启动读取参数
- **GIVEN** SharedPreferences 中已存在 Argon2 配置
- **WHEN** 应用启动
- **THEN** 系统 SHALL 直接从 SharedPreferences 读取参数
- **AND** 系统 SHALL NOT 重新计算

#### Scenario: 参数格式
- **WHEN** 存储参数到 SharedPreferences
- **THEN** 系统 SHALL 使用键名 "memory_cost", "time_cost", "parallelism"
- **AND** 系统 SHALL 使用 int 类型存储
- **AND** 系统 SHALL 使用文件名 "argon2_config"

---

### Requirement: 用户无感知的参数调优
系统 SHALL 不提供任何用户可选择的性能模式选项。

#### Scenario: 无用户选项
- **WHEN** 用户浏览设置界面
- **THEN** 系统 SHALL NOT 显示"性能模式"选项
- **AND** 系统 SHALL NOT 显示"安全级别"选项
- **AND** 系统 SHALL 自动调优参数

#### Scenario: 用户无法修改参数
- **WHEN** 用户尝试修改 Argon2 参数
- **THEN** 系统 SHALL 拒绝修改
- **AND** 系统 SHALL 不提供任何手动修改接口

---

### Requirement: 参数变化向后兼容
系统 SHALL 确保参数变化不影响已有用户数据。

#### Scenario: 参数变化不影响验证
- **GIVEN** 用户使用旧参数生成的密码哈希
- **WHEN** 用户更新应用，参数发生变化
- **THEN** 系统 SHALL 仍能使用新参数验证旧密码
- **AND** 系统 SHALL 使用存储的盐值（不变）
- **AND** 验证 SHALL 成功

#### Scenario: 密钥派生使用新参数
- **GIVEN** 用户更新应用，参数已更新
- **WHEN** 用户执行密钥派生操作
- **THEN** 系统 SHALL 使用新参数
- **AND** 系统 SHALL 记录实际使用的参数

---

## MODIFIED Requirements

### Requirement: 密码哈希存储
凭据 SHALL 使用不可逆的哈希算法存储，并使用自适应 Argon2id 参数。

#### Scenario: 密码哈希存储
- **WHEN** 存储用户密码
- **THEN** 系统 SHALL 使用不可逆的哈希算法
- **AND** 系统 SHALL 使用每个用户唯一的盐值
- **AND** 系统 SHALL 使用自适应 Argon2id 参数
- **AND** 系统 SHALL 不存储明文密码

#### Scenario: 密码哈希使用自适应参数
- **WHEN** 哈希用户密码
- **THEN** 系统 SHALL 从 SharedPreferences 读取 Argon2 参数
- **AND** 系统 SHALL 使用读取的参数进行哈希
- **AND** 系统 SHALL 在日志中记录实际参数值
