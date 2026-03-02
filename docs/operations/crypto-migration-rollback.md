# 加密算法迁移回滚方案

## 概述

本文档描述了 SafeVault v3.6.0 加密算法迁移的回滚方案，确保在迁移失败或发现问题时可以安全回退。

**版本**: 1.0.0
**最后更新**: 2026-03-03

---

## 回滚原则

### 安全性优先
1. **不删除旧密钥**: RSA 密钥在迁移后保留
2. **不修改现有数据**: 密码库数据不受影响
3. **可逆操作**: 迁移是幂等的，失败可重试

### 用户体验
1. **无缝回退**: 用户无需任何操作即可继续使用
2. **透明切换**: 系统自动检测并使用可用密钥
3. **降级提示**: 可选择性地提示用户回退原因

---

## 回滚场景

### 场景 1: 迁移过程中网络失败

**触发条件**: 上传公钥到服务器时网络中断

**影响**:
- 本地已生成并保存新密钥
- 服务器未接收到新公钥

**回滚策略**:
1. 删除本地的新密钥（可选，保留也无害）
2. 标记迁移失败状态
3. 用户下次打开应用时重新提示迁移

**代码处理**:
```java
// 迁移失败时
prefs.edit()
    .putBoolean("has_migrated_to_v3", false)
    .remove("x25519_public_key")
    .remove("x25519_private_key_encrypted")
    .remove("ed25519_public_key")
    .remove("ed25519_private_key_encrypted")
    .apply();
```

---

### 场景 2: 服务器拒绝新公钥

**触发条件**: 服务器验证公钥格式失败或版本不兼容

**影响**:
- 本地已生成新密钥
- 服务器未接受新公钥

**回滚策略**:
1. 保留本地新密钥（不影响功能）
2. 使用旧密钥进行所有操作
3. 用户继续使用 v2.0 协议

**代码处理**:
```java
// 上传失败时，保留本地密钥但不标记为已迁移
// 系统会自动检测并使用 RSA 密钥
public boolean shouldUseV3Protocol(String receiverId) {
    UserKeyInfo receiverInfo = backendService.getUserKeyInfo(receiverId);
    boolean mySupportsV3 = hasV3Keys() && receiverInfo.supportsV3();
    return mySupportsV3 && prefs.getBoolean("has_migrated_to_v3", false);
}
```

---

### 场景 3: 发现安全漏洞

**触发条件**: 新算法发现严重安全漏洞

**影响**:
- 所有使用 v3.0 的分享可能受影响
- 需要紧急回退到 v2.0

**回滚策略**:
1. 紧急发布客户端更新
2. 强制所有操作使用 v2.0 协议
3. 清除本地 v3.0 密钥标记

**代码处理**:
```java
// 配置开关（通过远程配置控制）
public static final boolean FORCE_USE_V2_PROTOCOL = true;

public String detectProtocolVersion(UserKeyInfo sender, UserKeyInfo receiver) {
    if (FORCE_USE_V2_PROTOCOL) {
        return CryptoConstants.PROTOCOL_VERSION_V2;
    }
    // 正常版本协商逻辑...
}
```

---

### 场景 4: 兼容性问题

**触发条件**: 发现某些设备无法正确使用 v3.0

**影响**:
- 特定设备的用户迁移后出现问题
- 需要选择性回退

**回滚策略**:
1. 收集设备信息
2. 黑名单特定设备型号
3. 受影响设备强制使用 v2.0

**代码处理**:
```java
// 设备兼容性检查
public boolean isDeviceCompatibleWithV3() {
    // 检查 Android 版本
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return false;
    }

    // 检查特定已知问题设备
    String device = Build.MANUFACTURER + " " + Build.MODEL;
    return !BLACKLISTED_DEVICES.contains(device);
}
```

---

## 回滚步骤

### 前端回滚

#### 步骤 1: 检测回滚条件

```java
public class RollbackManager {
    private static final String TAG = "RollbackManager";

    /**
     * 检测是否需要回滚
     */
    public boolean shouldRollback(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
            SecureKeyStorageManager.PREFS_NAME, Context.MODE_PRIVATE);

        // 检查迁移状态
        boolean hasMigrated = prefs.getBoolean("has_migrated_to_v3", false);
        boolean hasV3Keys = hasValidV3Keys(prefs);

        // 如果标记已迁移但没有有效密钥，需要回滚
        if (hasMigrated && !hasV3Keys) {
            Log.w(TAG, "Migration flag set but no valid V3 keys, rolling back");
            return true;
        }

        // 检查远程配置
        boolean forceRollback = RemoteConfig.getBoolean("force_v2_protocol", false);
        if (forceRollback) {
            Log.w(TAG, "Remote config forces rollback to V2");
            return true;
        }

        return false;
    }

    private boolean hasValidV3Keys(SharedPreferences prefs) {
        String x25519Pub = prefs.getString(CryptoConstants.KEY_X25519_PUBLIC, null);
        String ed25519Pub = prefs.getString(CryptoConstants.KEY_ED25519_PUBLIC, null);
        return x25519Pub != null && ed25519Pub != null;
    }
}
```

#### 步骤 2: 执行回滚

```java
public class RollbackManager {
    /**
     * 执行回滚操作
     */
    public void executeRollback(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
            SecureKeyStorageManager.PREFS_NAME, Context.MODE_PRIVATE);

        Log.i(TAG, "Executing rollback to V2 protocol");

        SharedPreferences.Editor editor = prefs.edit();

        // 清除迁移标记
        editor.remove("has_migrated_to_v3");
        editor.remove(CryptoConstants.KEY_VERSION);

        // 可选：删除 v3.0 密钥（保留也无害）
        // editor.remove(CryptoConstants.KEY_X25519_PUBLIC);
        // editor.remove(CryptoConstants.KEY_X25519_PRIVATE_ENCRYPTED);
        // editor.remove(CryptoConstants.KEY_ED25519_PUBLIC);
        // editor.remove(CryptoConstants.KEY_ED25519_PRIVATE_ENCRYPTED);

        editor.apply();

        Log.i(TAG, "Rollback completed successfully");
    }
}
```

#### 步骤 3: 通知用户

```java
public class RollbackManager {
    /**
     * 通知用户回滚
     */
    public void notifyRollback(Context context, String reason) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
            CryptoConstants.NOTIFICATION_CHANNEL_ROLLBACK)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setContentTitle("加密算法已回退")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true);

        // 点击打开设置页面
        Intent intent = new Intent(context, SettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(pendingIntent);

        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID_ROLLBACK, builder.build());
    }
}
```

---

### 后端回滚

#### 步骤 1: 停止接受新公钥

```java
@RestController
@RequestMapping("/v1/users")
public class UserController {

    @Value("${safevault.crypto.enable_v3_protocol:true}")
    private boolean enableV3Protocol;

    @PostMapping("/me/ecc-public-keys")
    public ResponseEntity<UploadEccPublicKeyResponse> uploadEccPublicKey(
            @Valid @RequestBody UploadEccPublicKeyRequest request,
            Authentication auth) {

        // 检查是否允许 v3.0
        if (!enableV3Protocol) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(UploadEccPublicKeyResponse.builder()
                    .success(false)
                    .message("V3.0 协议当前已禁用，请使用 V2.0 协议")
                    .build());
        }

        // 正常处理...
    }
}
```

#### 步骤 2: 清除公钥信息

```sql
-- 清空所有用户的 v3.0 公钥（紧急情况）
UPDATE users
SET x25519_public_key = NULL,
    ed25519_public_key = NULL,
    key_version = 'v1'
WHERE key_version = 'v2';

-- 或者只清除特定问题用户的公钥
UPDATE users
SET x25519_public_key = NULL,
    ed25519_public_key = NULL,
    key_version = 'v1'
WHERE user_id = 'problematic_user_id';
```

#### 步骤 3: 更新版本协商逻辑

```java
public String detectProtocolVersion(UserKeyInfo sender, UserKeyInfo receiver) {
    // 检查远程配置
    boolean enableV3 = remoteConfigService.getBoolean("enable_v3_protocol", true);

    if (!enableV3) {
        return CryptoConstants.PROTOCOL_VERSION_V2;
    }

    // 正常版本协商...
}
```

---

## 紧急回滚流程

### 快速回滚（5分钟内）

**适用场景**: 发现严重安全漏洞

1. **服务端操作**（2分钟）
   ```bash
   # 更新远程配置，禁用 v3.0
   curl -X POST https://api.safevault.app/admin/config \
     -d '{"enable_v3_protocol": false}'
   ```

2. **客户端回滚**（3分钟）
   - 客户端检测到配置变更
   - 立即切换到 v2.0 协议
   - 通知用户（可选）

**回滚时间**: < 5 分钟
**数据损失**: 无

---

### 标准回滚（1小时内）

**适用场景**: 发现兼容性问题

1. **服务端操作**（30分钟）
   - 更新远程配置
   - 发布紧急客户端补丁

2. **客户端回滚**（30分钟）
   - 用户更新应用
   - 应用检测到回滚需求
   - 清除迁移标记

**回滚时间**: < 1 小时
**数据损失**: 无

---

### 计划回滚（未来版本）

**适用场景**: 逐步淘汰 v3.0（如发现更好的替代方案）

1. **通知期**（1-3 个月）
   - 通知用户即将弃用 v3.0
   - 建议迁移到新的算法

2. **过渡期**（3-6 个月）
   - 新用户使用新算法
   - 老用户逐步迁移
   - v3.0 仍可使用但提示升级

3. **停用期**（6-12 个月）
   - 停止接受新的 v3.0 密钥
   - 只允许解密现有 v3.0 分享

4. **完全停用**（12 个月后）
   - 移除 v3.0 支持

**回滚时间**: 12 个月
**数据损失**: 历史分享（需要提前备份）

---

## 回滚验证

### 验证清单

- [ ] RSA 密钥仍然可用
- [ ] 可以使用 v2.0 协议分享密码
- [ ] 可以接收 v2.0 协议的分享
- [ ] 密码库数据完整
- [ ] 新用户注册正常
- [ ] 版本协商正确

### 验证测试

```java
@Test
public void testRollbackToV2() {
    // 1. 确保有 RSA 密钥
    assertTrue(secureKeyStorage.hasRsaKeys());

    // 2. 执行回滚
    rollbackManager.executeRollback(context);

    // 3. 验证 v3.0 标记已清除
    assertFalse(prefs.getBoolean("has_migrated_to_v3", false));

    // 4. 验证可以创建 v2.0 分享
    EncryptedSharePacket packet = shareEncryptionManager.createEncryptedPacket(
        testData, receiverRsaPublicKey, senderRsaPrivateKey);
    assertNotNull(packet);
    assertEquals("2.0", packet.getVersion());
}
```

---

## 回滚监控

### 监控指标

1. **回滚事件数**
   - 记录所有回滚操作
   - 按原因分类（网络、服务器、安全、兼容性）

2. **协议使用比例**
   - v2.0 vs v3.0 分享数量
   - 迁移前后对比

3. **错误率**
   - v3.0 操作失败率
   - 回滚后的错误率

4. **用户反馈**
   - 迁移相关问题工单
   - 用户满意度调查

### 告警规则

```yaml
alerts:
  - name: 高回滚率
    condition: rollback_rate > 5%
    duration: 1h
    action: 立即通知开发团队

  - name: v3.0 协议错误率过高
    condition: v3_error_rate > 1%
    duration: 30min
    action: 考虑启用回滚

  - name: 用户反馈增加
    condition: migration_related_tickets > 10
    duration: 24h
    action: 评估回滚必要性
```

---

## 回滚后恢复

### 恢复到 v3.0

**适用场景**: 问题已修复，可以重新启用 v3.0

**步骤**:
1. 修复问题（代码或配置）
2. 更新远程配置，启用 v3.0
3. 发布客户端更新
4. 通知用户可重新迁移

**代码处理**:
```java
// 检查是否可以重新启用 v3.0
public boolean canReenableV3() {
    // 检查设备兼容性
    if (!isDeviceCompatibleWithV3()) {
        return false;
    }

    // 检查远程配置
    boolean enableV3 = RemoteConfig.getBoolean("enable_v3_protocol", false);
    if (!enableV3) {
        return false;
    }

    return true;
}

// 重新提示用户迁移
if (!prefs.getBoolean("has_migrated_to_v3", false) && canReenableV3()) {
    showMigrationNotification();
}
```

---

## 回滚方案文档维护

### 更新频率
- 每次迁移功能更新后
- 发现新的回滚场景后
- 回滚后总结经验教训

### 审查流程
1. 开发团队审核
2. 安全团队审核
3. 测试团队验证
4. 产品团队批准

---

## 相关文档

- [加密算法迁移指南](../guides/crypto-migration.md)
- [安全架构文档](../security-architecture.md)
- [API 文档 - 密钥管理](../api/key-management.md)

---

**版本历史**:
- v1.0.0 (2026-03-03): 初始版本