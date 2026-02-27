# 安全随机填充 - 实施清单

## 1. 核心工具类实现

- [x] 1.1 创建 `SecurePaddingUtil` 类
  - [x] 定义块大小常量（BLOCK_SIZE = 256 字节）
  - [x] 实现 `pad(byte[] plaintext)` 方法
  - [x] 实现 `unpad(byte[] padded)` 方法
  - [x] 实现 `padString(String plaintext)` 方法
  - [x] 实现 `unpadToString(byte[] padded)` 方法

- [x] 1.2 随机填充实现
  - [x] 使用 `SecureRandom` 生成随机填充字节
  - [x] 在最后一个字节记录填充长度
  - [x] 验证填充长度合理性

## 2. PasswordManager 集成

- [x] 2.1 修改 `encryptField()` 方法
  - [x] 将明文转为 UTF-8 字节
  - [x] 调用 `SecurePaddingUtil.pad()` 填充
  - [x] 执行 AES-256-GCM 加密
  - [x] 返回 IV + 密文

- [x] 2.2 修改 `decryptField()` 方法
  - [x] 执行 AES-256-GCM 解密
  - [x] 调用 `SecurePaddingUtil.unpad()` 移除填充
  - [x] 转换为 String 返回

## 3. 数据格式兼容性

- [x] 3.1 版本标识
  - [x] 在加密数据中包含版本标识（v2 = 带填充）
  - [x] 旧数据标识为 v1（不带填充）

- [x] 3.2 解密时自动识别版本
  - [x] v1 数据：直接解密
  - [x] v2 数据：解密后 unpad

## 4. 数据迁移

- [x] 4.1 创建迁移工具
  - [x] `DataMigrationService.migrateToPaddedFormat()`
  - [x] 遍历所有密码项
  - [x] 解密旧格式，加密新格式
  - [x] 批量处理，显示进度

- [x] 4.2 触发迁移
  - [x] 应用更新后首次解锁时执行
  - [x] 后台静默迁移
  - [x] 完成后通知用户

## 5. 测试和验证

- [x] 5.1 单元测试
  - [x] 测试 pad/unpad 对称性
  - [x] 测试边界条件（空数据、正好块大小）
  - [x] 测试填充长度验证

- [x] 5.2 安全测试
  - [x] 验证密文长度固定（同一块大小）
  - [x] 验证随机填充不可预测
  - [x] 验证无法从密文推断明文长度

- [x] 5.3 集成测试
  - [x] 测试旧数据解密兼容性
  - [x] 测试迁移完整性
  - [x] 测试迁移失败回滚

## 6. 性能测试

- [x] 6.1 性能基准测试
  - [x] 测量填充操作耗时（目标 <1ms）
  - [x] 测量加密后数据大小增长（最多 256 字节）
  - [x] 验证数据库大小增长可接受

