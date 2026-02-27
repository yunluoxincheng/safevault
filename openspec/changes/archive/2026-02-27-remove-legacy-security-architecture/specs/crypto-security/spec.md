# crypto-security Delta Changes

## REMOVED Requirements

### Requirement: Argon2id向后兼容
**Reason**: 旧架构已完全移除，不再支持 PBKDF2 向后兼容。开发环境无存量用户，所有用户将使用 Argon2id。

**Migration**: 旧数据将被清空，用户需要重新添加密码数据。

---

## MODIFIED Requirements

### Requirement: Password Hashing Algorithm
密码哈希SHALL使用Argon2id算法，抵抗GPU/ASIC暴力破解攻击。系统SHALL NOT支持PBKDF2或其他弱密钥派生算法。

#### Scenario: Argon2id参数配置
- **WHEN** 配置Argon2id参数
- **THEN** SHALL使用时间成本t=3（迭代次数）
- **AND** SHALL使用内存成本m=64MB
- **AND** SHALL使用并行度p=4
- **AND** 输出长度应至少32字节（256位）
- **AND** 应使单次哈希耗时300-500ms

#### Scenario: Argon2id作为唯一算法
- **GIVEN** 系统初始化
- **WHEN** 执行任何密钥派生操作
- **THEN** SHALL仅使用Argon2id算法
- **AND** SHALL NOT使用PBKDF2
- **AND** SHALL NOT使用其他弱密钥派生算法
- **AND** 所有密钥派生SHALL通过Argon2KeyDerivationManager执行

#### Scenario: Argon2id盐值管理
- **WHEN** 生成Argon2id盐值
- **THEN** SHALL使用加密安全的随机数生成器
- **AND** 盐值长度应至少16字节
- **AND** SHALL为每次哈希操作生成唯一盐值
- **AND** 盐值应与哈希结果一起存储

---

## ADDED Requirements

### Requirement: Key Derivation Centralization
所有密钥派生操作SHALL通过Argon2KeyDerivationManager统一执行，系统SHALL NOT存在其他密钥派生实现。

#### Scenario: 唯一密钥派生入口
- **GIVEN** 应用需要派生密钥
- **WHEN** 执行密钥派生操作
- **THEN** SHALL通过Argon2KeyDerivationManager.deriveKeyWithArgon2id()执行
- **AND** SHALL NOT使用CryptoManager（已删除）
- **AND** SHALL NOT使用KeyDerivationManager（已删除）
- **AND** SHALL NOT使用KeyManager（已删除）

#### Scenario: 旧密钥派生组件已移除
- **GIVEN** 代码引用已删除的密钥派生组件
- **WHEN** 尝试编译代码
- **THEN** SHALL出现编译错误
- **AND** 开发者SHALL更新代码使用Argon2KeyDerivationManager

---

### Requirement: Session Management via CryptoSession
应用会话状态SHALL通过CryptoSession管理，提供DataKey内存缓存和5分钟超时机制。

#### Scenario: DataKey会话管理
- **WHEN** 用户成功解锁应用
- **THEN** SHALL将DataKey缓存到CryptoSession
- **AND** SHALL设置5分钟超时
- **AND** SHALL在超时后自动清除DataKey

#### Scenario: 会话锁定清除敏感数据
- **WHEN** 应用锁定或会话超时
- **THEN** SHALL调用CryptoSession.clear()
- **AND** SHALL清除内存中的DataKey
- **AND** SHALL使用zeroize()安全清除敏感数据

#### Scenario: 密码加密使用DataKey
- **GIVEN** 用户已解锁应用（CryptoSession.isUnlocked() == true）
- **WHEN** 加密或解密密码数据
- **THEN** SHALL从CryptoSession.getDataKey()获取DataKey
- **AND** SHALL使用DataKey进行AES-256-GCM操作
- **AND** SHALL NOT尝试使用其他密钥（如masterKey）

---

### Requirement: Three-Layer Security Architecture
系统SHALL使用三层安全架构存储密钥，确保密钥材料永不以明文形式存储。

#### Scenario: 三层架构层级
- **GIVEN** 系统初始化
- **WHEN** 查看密钥存储架构
- **THEN** SHALL包含以下层级：
  - Level 0: DataKey（内存会话，5分钟超时）
  - Level 1: PasswordKey（Argon2id派生）+ DeviceKey（AndroidKeyStore）
  - Level 2: VaultKey（双重加密：PasswordKey版本 + DeviceKey版本）
  - Level 3: RSA私钥（DataKey加密）

#### Scenario: 密钥层级关系
- **GIVEN** 用户使用主密码解锁
- **WHEN** 追踪密钥派生链
- **THEN** SHALL遵循：主密码 → (Argon2id) → PasswordKey → 解密DataKey → 解密RSA私钥
- **AND** DeviceKey路径：生物识别 → DeviceKey → 解密DataKey → 解密RSA私钥

#### Scenario: SecureKeyStorageManager作为唯一入口
- **WHEN** 执行任何密钥存储操作
- **THEN** SHALL通过SecureKeyStorageManager执行
- **AND** SHALL NOT使用KeyManager（已删除）
- **AND** SHALL NOT直接访问SharedPreferences存储密钥
