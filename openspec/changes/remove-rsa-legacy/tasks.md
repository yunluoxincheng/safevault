## 1. 准备工作
- [ ] 1.1 备份当前代码到 Git 分支
- [ ] 1.2 创建 `remove-rsa-legacy` Git 分支
- [ ] 1.3 确认所有 RSA 相关文件列表

## 2. 删除 RSA 相关组件
- [ ] 2.1 删除 `RSAKeyManager.java`
- [ ] 2.2 删除 `RSAEncryptionHelper.java`
- [ ] 2.3 删除 `LegacyShareProtocol.java`
- [ ] 2.4 标记 `EncryptedSharePacketV2.java` 为废弃

## 3. 修改核心组件
- [ ] 3.1 修改 `SecureKeyStorageManager.java` - 移除 RSA 密钥存储
- [ ] 3.2 修改 `ShareEncryptionManager.java` - 移除 RSA 加密支持
- [ ] 3.3 修改 `UserKeyInfo.java` - 移除 RSA 公钥字段
- [ ] 3.4 修改 `CryptoConstants.java` - 移除 RSA 相关常量
- [ ] 3.5 修改 `BackendServiceImpl.java` - 移除 RSA 相关方法

## 4. 更新加密协议
- [ ] 4.1 更新 `EncryptedSharePacketV3.java` 为标准协议
- [ ] 4.2 确保 `X25519KeyManager` 和 `Ed25519Signer` 正常工作
- [ ] 4.3 验证 Bouncy Castle 回退实现

## 5. 数据迁移
- [ ] 5.1 实现 RSA 密钥检测逻辑
- [ ] 5.2 实现迁移提示 UI
- [ ] 5.3 实现数据清理逻辑

## 6. 测试验证
- [ ] 6.1 测试 X25519 密钥生成和交换
- [ ] 6.2 测试 Ed25519 签名和验证
- [ ] 6.3 测试分享功能（仅 v3.0 协议）
- [ ] 6.4 测试回退机制（API 32- 和 API 33-）

## 7. 文档更新
- [ ] 7.1 更新 `openspec/specs/crypto-algorithms/spec.md`
- [ ] 7.2 更新 `openspec/specs/contact-sharing/spec.md`
- [ ] 7.3 更新 README.md 中的密码算法说明

## 8. 清理和归档
- [ ] 8.1 运行 `openspec validate remove-rsa-legacy --strict`
- [ ] 8.2 提交代码并创建 Pull Request
- [ ] 8.3 等待审批和合并