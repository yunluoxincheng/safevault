# SafeVault v3.2.0 - 安全加固第二阶段 (Argon2id 升级)

## 发布日期
2026-02-06

## 版本信息
- 版本号: 3.2.0
- 版本代码: 3

---

## 更新概述

本版本主要完成安全加固第二阶段的 **Argon2id 密码哈希算法升级**，实现前后端统一的密钥派生算法，显著提升抗 GPU/ASIC 攻击能力。

---

## 主要变更

### 1. Android 前端 Argon2id 支持

#### 新增依赖
- 添加 `de.mkammerer:argon2-jvm:2.11` 依赖（与后端一致）

#### 新增类
- `Argon2KeyDerivationManager.java`: Argon2id 密钥派生管理器
  - 参数配置与后端一致：t=3, m=64MB, p=4
  - 支持用户算法版本检测
  - 提供密码哈希和验证功能

#### 修改类
- `SecureKeyStorageManager.java`:
  - `derivePasswordKey()` 方法改用 Argon2id
  - 添加 `derivePasswordKeyWithPBKDF2()` 作为废弃方法保留向后兼容
  - 更新类文档说明 Argon2id 参数配置

#### 废弃类
- `KeyDerivationManager.java`: 标记为 `@Deprecated`
  - 使用 PBKDF2 进行密钥派生
  - 推荐迁移到 `Argon2KeyDerivationManager`

#### 修改方法
- `KeyManager.deriveKeyFromMasterPassword()`: 标记为 `@Deprecated`

---

### 2. 前后端算法统一

#### Android 前端
```java
// 新版 Argon2id 配置
Argon2KeyDerivationManager.getInstance(context)
    .deriveKeyWithArgon2id(masterPassword, salt);

// 参数：timeCost=3, memoryCost=64MB, parallelism=4, outputLength=32bytes
```

#### 后端 (Spring Boot)
```java
// Argon2PasswordHasher.java（已存在）
// Argon2Config.java（已存在）
// 参数：timeCost=3, memoryCost=65536KB, parallelism=4
```

---

### 3. 安全性提升

| 对比项 | PBKDF2 (旧) | Argon2id (新) |
|--------|------------|---------------|
| 抗 GPU 攻击 | 弱 | 强（内存硬） |
| 抗 ASIC 攻击 | 弱 | 强 |
| 抗侧信道攻击 | 一般 | 优秀 |
| 推荐年份 | 2000 年 | 2015 年至今 |
| 迭代次数 | 600,000 | t=3 |
| 内存占用 | <1MB | 64MB |
| 并行度 | - | 4 线程 |

---

## 技术细节

### Argon2id 参数配置

```yaml
# application.yml (后端)
security:
  password-hash:
    algorithm: argon2id
    argon2:
      time-cost: 3          # 迭代次数
      memory-cost: 65536    # 64MB (KB)
      parallelism: 4        # 并行度
      output-length: 32     # 输出长度（字节）
      salt-length: 16       # 盐值长度（字节）
```

```java
// Argon2KeyDerivationManager.java (前端)
private static final int ARGON2_TIME_COST = 3;
private static final int ARGON2_MEMORY_COST = 65536; // 64MB
private static final int ARGON2_PARALLELISM = 4;
private static final int ARGON2_OUTPUT_LENGTH = 32;
private static final int ARGON2_SALT_LENGTH = 16;
```

### 密钥派生性能对比

| 算法 | 耗时（现代设备） | 耗时（低端设备） | 内存占用 |
|------|-----------------|-----------------|---------|
| PBKDF2 (600k) | ~300ms | ~600ms | <1MB |
| Argon2id (t=3, m=64MB) | ~400ms | ~800ms | 64MB |

---

## 兼容性说明

### 开发环境
- **直接删除 PBKDF2 支持**，不做向后兼容迁移
- 所有新用户和测试用户直接使用 Argon2id

### 生产环境（待后续实现）
- 支持旧用户 PBKDF2 兼容
- 提供迁移流程引导用户升级

---

## 构建命令

```bash
# 清理并构建 Debug 版本
./gradlew clean assembleDebug

# 构建 Release 版本
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

---

## 已知问题

1. **无迁移机制**: 开发环境直接删除 PBKDF2，旧数据无法解密
2. **性能**: Argon2id 比 PBKDF2 慢约 30-100%，但安全性显著提升

---

## 下一步计划

### 安全加固第三阶段 (Phase 3)
1. 添加速率限制
2. JWT 算法升级
3. 会话管理优化
4. 审计日志增强

---

## 相关文档

- [安全加固第二阶段设计文档](./design.md)
- [Android 安全规格](./specs/android-security/spec.md)
- [后端安全规格](./specs/backend-security/spec.md)
- [加密安全规格](./specs/crypto-security/spec.md)
