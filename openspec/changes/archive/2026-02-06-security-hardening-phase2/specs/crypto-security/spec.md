# 加密安全规格 - 第二阶段

## ADDED Requirements

### Requirement: RSA Padding Scheme
RSA加密SHALL使用OAEP（Optimal Asymmetric Encryption Padding）方案。

#### Scenario: OAEP加密参数
- **WHEN** 使用RSA加密
- **THEN** SHALL使用RSA/ECB/OAEPWithSHA-256AndMGF1Padding
- **AND** SHALL使用SHA-256作为摘要算法
- **AND** SHALL使用MGF1作为掩码生成函数

#### Scenario: OAEP与PKCS1共存
- **GIVEN** 系统包含使用PKCS1Padding的历史数据
- **WHEN** 加密新数据
- **THEN** SHALL使用OAEP填充
- **AND** SHALL标记为"v2"
- **AND** 历史数据保持"v1"标识

#### Scenario: OAEP解密验证
- **WHEN** 解密OAEP加密的数据
- **THEN** SHALL验证填充完整性
- **AND** 填充错误时SHALL抛出异常
- **AND** SHALL NOT返回部分解密数据

---

### Requirement: Password Hashing Algorithm
密码哈希SHALL使用Argon2id算法，抵抗GPU/ASIC暴力破解攻击。

#### Scenario: Argon2id参数配置
- **WHEN** 配置Argon2id参数
- **THEN** SHALL使用时间成本t=3（迭代次数）
- **AND** SHALL使用内存成本m=64MB
- **AND** SHALL使用并行度p=4
- **AND** 输出长度应至少32字节（256位）
- **AND** 应使单次哈希耗时300-500ms

#### Scenario: Argon2id向后兼容
- **GIVEN** 旧用户使用PBKDF2哈希
- **WHEN** 验证旧用户密码
- **THEN** SHALL继续支持PBKDF2验证
- **AND** 用户密码验证应成功
- **AND** 应提示用户可升级到Argon2id

#### Scenario: Argon2id盐值管理
- **WHEN** 生成Argon2id盐值
- **THEN** SHALL使用加密安全的随机数生成器
- **AND** 盐值长度应至少16字节
- **AND** SHALL为每次哈希操作生成唯一盐值
- **AND** 盐值应与哈希结果一起存储

---

### Requirement: Encryption Algorithm Versioning
加密数据SHALL包含算法版本标识，支持未来升级。

#### Scenario: 版本标识格式
- **WHEN** 创建加密数据包
- **THEN** SHALL包含版本字段
- **AND** 版本格式应为"vN"（N为数字）
- **AND** 当前版本应为"v2"

#### Scenario: 版本协商
- **WHEN** 两端协商加密算法
- **THEN** SHALL交换支持的版本列表
- **AND** SHALL选择双方支持的最新版本
- **AND** 如果无共同版本，应拒绝操作

#### Scenario: 版本升级策略
- **GIVEN** 新版本加密算法可用
- **WHEN** 创建新的加密数据
- **THEN** SHALL使用最新版本
- **AND** SHALL保持旧版本数据可解密
- **AND** 应考虑后台迁移策略

---

### Requirement: Key Migration Security
密钥迁移过程SHALL保护密钥材料不被泄露。

#### Scenario: 迁移过程内存保护
- **WHEN** 迁移密钥
- **THEN** SHALL尽量减少密钥在内存中的停留时间
- **AND** 迁移完成后应清除临时副本
- **AND** 应使用`char[]`而非String存储密码

#### Scenario: 迁移失败不泄露密钥
- **GIVEN** 密钥迁移失败
- **WHEN** 处理迁移失败
- **THEN** SHALL保护密钥材料不被泄露到日志
- **AND** 错误消息不应包含密钥信息
- **AND** 应记录失败原因但不记录敏感数据

#### Scenario: 迁移完整性验证
- **WHEN** 密钥迁移完成
- **THEN** SHALL验证迁移后的密钥可用性
- **AND** 应测试加密和解密操作
- **AND** 验证失败时SHALL回滚或保留旧密钥

---

### Requirement: Hardware-Backed Key Storage
当平台支持时，密钥SHALL存储在硬件支持的密钥库中。

#### Scenario: 检测硬件支持
- **WHEN** 初始化密钥存储
- **THEN** SHALL检测设备是否支持硬件支持的密钥库
- **AND** AndroidKeyStore优先于软件存储
- **AND** 应记录使用的存储类型

#### Scenario: 硬件密钥保护
- **GIVEN** 密钥存储在硬件支持的密钥库中
- **WHEN** 尝试导出密钥材料
- **THEN** SHALL硬件拒绝导出操作
- **AND** 密钥材料保持不可访问
- **AND** 加密操作应在硬件内部执行

#### Scenario: 硬件不可用降级
- **GIVEN** 设备不支持硬件密钥库
- **WHEN** 初始化密钥存储
- **THEN** SHALL使用软件密钥库作为降级方案
- **AND** 应记录降级事件
- **AND** 应通知用户安全级别降低

---

### Requirement: Private Key Memory Sanitization
RSA私钥在内存中的驻留时间SHALL被最小化，使用完毕后立即擦除。

#### Scenario: 私钥使用后立即擦除
- **WHEN** RSA私钥使用完毕（加密/解密/签名操作完成）
- **THEN** SHALL立即清除内存中的密钥材料
- **AND** SHALL使用`Arrays.fill()`将密钥字节填充为零
- **AND** SHALL将密钥引用设为null

#### Scenario: AutoCloseable私钥管理
- **WHEN** 解密或加载RSA私钥
- **THEN** SHALL返回实现AutoCloseable接口的包装类
- **AND** SHALL在try-with-resources块中使用私钥
- **AND** SHALL在close()方法中擦除内存

#### Scenario: 私钥驻留时间限制
- **GIVEN** RSA私钥已解密到内存
- **WHEN** 私钥在内存中驻留超过操作所需时间（如>5秒）
- **THEN** SHALL视为安全违规
- **AND** SHALL记录安全警告日志
- **AND** SHALL主动触发内存擦除

---

### Requirement: Transactional Key Storage
密钥对写入SHALL使用原子性事务，防止进程崩溃导致密钥对损坏。

#### Scenario: 密钥对原子性写入
- **WHEN** 保存RSA密钥对到SharedPreferences
- **THEN** SHALL使用commit()而非apply()
- **AND** SHALL在单次事务中写入公钥、私钥和版本标识
- **AND** SHALL确保全部写入成功或全部失败

#### Scenario: 密钥对完整性验证
- **WHEN** 从SharedPreferences读取密钥对
- **THEN** SHALL验证公钥、私钥和版本标识同时存在
- **AND** SHALL验证版本标识为期望值（如"v2"）
- **AND** 如果验证失败，SHALL视为密钥对损坏并触发迁移或恢复

#### Scenario: 临时文件事务模式（可选）
- **GIVEN** 需要更高级别的原子性保证
- **WHEN** 写入关键密钥数据
- **THEN** SHALL先写入临时SharedPreferences
- **AND** SHALL验证临时数据完整性
- **AND** SHALL验证成功后原子性复制到正式存储
- **AND** SHALL验证失败后回滚并保持旧数据可用

---

### Requirement: AEAD Encryption with Integrity Verification
所有加密操作SHALL使用AEAD模式（如AES-GCM），提供机密性和完整性保护。

#### Scenario: AES-GCM加密参数
- **WHEN** 执行AES加密操作
- **THEN** SHALL使用AES/GCM/NoPadding模式
- **AND** SHALL使用128位认证标签（GCM_TAG_LENGTH）
- **AND** SHALL使用96位（12字节）IV
- **AND** SHALL为每次加密生成新的随机IV

#### Scenario: GCM认证标签验证
- **WHEN** 解密AES-GCM加密的数据
- **THEN** SHALL自动验证认证标签
- **AND** 如果密文被篡改，SHALL抛出AEADBadTagException
- **AND** SHALL NOT返回部分解密数据
- **AND** SHALL记录完整性验证失败的安全事件

#### Scenario: HKDF密钥派生
- **WHEN** 从主密钥派生加密子密钥
- **THEN** SHALL使用HKDF（HMAC-based Key Derivation Function）
- **AND** SHALL为不同用途派生不同的子密钥（传入不同的info参数）
- **AND** SHALL使用SHA-256作为HKDF的哈希函数
- **AND** SHALL子密钥长度至少32字节（256位）

#### Scenario: 加密数据包格式
- **WHEN** 存储或传输加密数据
- **THEN** SHALL包含IV、密文和认证标签
- **AND** SHALL使用标准格式（如IV在前，密文在后）
- **AND** SHALL支持Base64编码以便存储
- **AND** SHALL包含版本标识以便未来升级
