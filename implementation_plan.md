# Implementation Plan

[Overview]
完善 SafeVault 密码分享功能的完整实现，包括云端分享和离线分享的端到端加密集成。

本计划旨在解决项目审查中发现的核心问题：分享功能实现不完整。当前 `BackendServiceImpl.java` 中的云端分享和离线分享方法多为简化实现（返回模拟数据），未与 `ShareEncryptionManager` 和 `ShareServiceApi` 完整集成。实施本计划后，应用将支持完整的端到端加密分享功能，包括版本 2.0（RSA+AES）和版本 3.0（X25519/Ed25519）协议。

[Types]
定义分享功能所需的数据传输对象和加密协议类型。

### 现有类型（已定义，需审查）
| 类型名称 | 文件路径 | 说明 |
|----------|----------|------|
| `ShareDataPacket` | `app/src/main/java/com/ttt/safevault/model/ShareDataPacket.java` | 分享数据包（明文） |
| `EncryptedSharePacket` | `app/src/main/java/com/ttt/safevault/model/EncryptedSharePacket.java` | 加密分享包（v2.0） |
| `EncryptedSharePacketV3` | `app/src/main/java/com/ttt/safevault/model/EncryptedSharePacketV3.java` | 加密分享包（v3.0） |
| `SharePermission` | `app/src/main/java/com/ttt/safevault/model/SharePermission.java` | 分享权限配置 |
| `UserKeyInfo` | `app/src/main/java/com/ttt/safevault/model/UserKeyInfo.java` | 用户密钥信息（支持 v2/v3） |
| `CreateShareRequest` | `app/src/main/java/com/ttt/safevault/dto/request/CreateShareRequest.java` | 创建分享请求 DTO |
| `ShareResponse` | `app/src/main/java/com/ttt/safevault/dto/response/ShareResponse.java` | 创建分享响应 DTO |
| `ReceivedShareResponse` | `app/src/main/java/com/ttt/safevault/dto/response/ReceivedShareResponse.java` | 接收分享响应 DTO |
| `PasswordData` | `app/src/main/java/com/ttt/safevault/dto/PasswordData.java` | 密码数据 DTO |

### 需新增的类型
| 类型名称 | 文件路径 | 说明 |
|----------|----------|------|
| `AcceptShareRequest` | `app/src/main/java/com/ttt/safevault/dto/request/AcceptShareRequest.java` | 接受分享请求 DTO |
| `RejectShareRequest` | `app/src/main/java/com/ttt/safevault/dto/request/RejectShareRequest.java` | 拒绝分享请求 DTO |

[Files]
创建新文件并修改现有文件以完成分享功能集成。

### 新文件创建
| 文件路径 | 目的 |
|----------|------|
| `app/src/main/java/com/ttt/safevault/dto/request/AcceptShareRequest.java` | 接受分享请求 DTO |
| `app/src/main/java/com/ttt/safevault/dto/request/RejectShareRequest.java` | 拒绝分享请求 DTO |
| `app/src/main/java/com/ttt/safevault/service/manager/ShareManager.java` | 分享功能统一管理器（封装云端/离线分享逻辑） |

### 现有文件修改
| 文件路径 | 修改内容 |
|----------|----------|
| `app/src/main/java/com/ttt/safevault/service/BackendServiceImpl.java` | 将简化实现的分享方法委托给 `ShareManager` |
| `app/src/main/java/com/ttt/safevault/network/api/ShareServiceApi.java` | 添加接受/拒绝分享 API 方法 |
| `app/src/main/java/com/ttt/safevault/crypto/ShareEncryptionManager.java` | 添加协议版本自动检测方法 |
| `app/src/main/java/com/ttt/safevault/model/UserKeyInfo.java` | 添加 `supportsV3()` 方法（如缺失） |

### 配置文件更新
| 文件路径 | 修改内容 |
|----------|----------|
| `openspec/project.md` | 更新语言版本从 "Java 8" 到 "Java 17" |

[Functions]
新增和修改函数以支持完整的分享功能。

### 新增函数（ShareManager.java）
| 函数签名 | 目的 |
|----------|------|
| `createCloudShare(int passwordId, String toUserId, int expireInMinutes, SharePermission permission): ShareResponse` | 创建云端加密分享 |
| `receiveCloudShare(String shareId): ReceivedShareResponse` | 接收云端分享并解密 |
| `acceptCloudShare(String shareId): boolean` | 接受云端分享 |
| `rejectCloudShare(String shareId): boolean` | 拒绝云端分享 |
| `revokeCloudShare(String shareId): boolean` | 撤销云端分享 |
| `saveCloudShare(String shareId): boolean` | 保存云端分享到本地 |
| `getMyCloudShares(): List<ReceivedShareResponse>` | 获取我创建的分享列表 |
| `getReceivedCloudShares(): List<ReceivedShareResponse>` | 获取我接收的分享列表 |
| `createOfflineShare(int passwordId, int expireInMinutes, SharePermission permission): String` | 创建离线分享（QR 码内容） |
| `receiveOfflineShare(String encryptedData): PasswordItem` | 接收离线分享并解密 |

### 修改函数（BackendServiceImpl.java）
| 函数名 | 当前行为 | 修改后行为 |
|--------|----------|------------|
| `createCloudShare(...)` | 返回模拟数据 | 委托给 `ShareManager.createCloudShare()` |
| `receiveCloudShare(...)` | 返回模拟数据 | 委托给 `ShareManager.receiveCloudShare()` |
| `revokeCloudShare(...)` | 空方法 | 委托给 `ShareManager.revokeCloudShare()` |
| `saveCloudShare(...)` | 空方法 | 委托给 `ShareManager.saveCloudShare()` |
| `getMyCloudShares()` | 返回空列表 | 委托给 `ShareManager.getMyCloudShares()` |
| `getReceivedCloudShares()` | 返回空列表 | 委托给 `ShareManager.getReceivedCloudShares()` |
| `createOfflineShare(...)` | 返回简单字符串 | 委托给 `ShareManager.createOfflineShare()` |
| `receiveOfflineShare(...)` | 返回模拟数据 | 委托给 `ShareManager.receiveOfflineShare()` |

### 新增 API 方法（ShareServiceApi.java）
| 方法签名 | 目的 |
|----------|------|
| `@POST("v1/shares/{shareId}/accept") Observable<Void> acceptShare(@Path("shareId") String shareId, @Body AcceptShareRequest request)` | 接受分享 |
| `@POST("v1/shares/{shareId}/reject") Observable<Void> rejectShare(@Path("shareId") String shareId, @Body RejectShareRequest request)` | 拒绝分享 |

[Classes]
新增和修改类以支持分享功能。

### 新增类
```java
// app/src/main/java/com/ttt/safevault/service/manager/ShareManager.java
public class ShareManager {
    private final Context context;
    private final RetrofitClient retrofitClient;
    private final ShareEncryptionManager encryptionManager;
    private final PasswordManager passwordManager;
    private final SessionGuard sessionGuard;
    private final SecureKeyStorageManager secureKeyStorage;

    // 构造函数
    public ShareManager(Context context, RetrofitClient retrofitClient, 
                       PasswordManager passwordManager) { ... }

    // 云端分享方法（见 Functions 节）
    // 离线分享方法（见 Functions 节）
}
```

### 修改类
| 类名 | 修改内容 |
|------|----------|
| `BackendServiceImpl` | 添加 `ShareManager` 字段，将所有分享方法委托给 `ShareManager` |
| `ShareServiceApi` | 添加 `acceptShare()` 和 `rejectShare()` 方法 |

[Dependencies]
无需新增外部依赖，使用项目现有库。

### 现有依赖利用
| 依赖 | 用途 |
|------|------|
| `retrofit2` | 云端分享 API 调用 |
| `RxJava3` | 异步网络请求 |
| `Gson` | JSON 序列化/反序列化 |
| `javax.crypto` | AES-GCM 加密 |
| `java.security` | RSA/X25519/Ed25519 密钥操作 |

[Testing]
为分享功能添加单元测试和集成测试。

### 新增测试文件
| 文件路径 | 测试内容 |
|----------|----------|
| `app/src/test/java/com/ttt/safevault/service/manager/ShareManagerTest.java` | `ShareManager` 单元测试 |
| `app/src/test/java/com/ttt/safevault/crypto/ShareEncryptionManagerIntegrationTest.java` | 分享加密集成测试 |

### 测试覆盖要求
- [ ] 云端分享创建流程（v2.0 和 v3.0 协议）
- [ ] 云端分享接收流程
- [ ] 离线分享 QR 码生成和解析
- [ ] 签名验证成功/失败场景
- [ ] 过期分享处理
- [ ] 权限检查（canView/canSave）

[Implementation Order]
按以下顺序实施以最小化冲突并确保成功集成。

1. **更新项目文档** - 修改 `openspec/project.md` 将语言版本从 "Java 8" 更新为 "Java 17"

2. **创建数据传输对象** - 创建 `AcceptShareRequest.java` 和 `RejectShareRequest.java`

3. **扩展 API 接口** - 在 `ShareServiceApi.java` 中添加 `acceptShare()` 和 `rejectShare()` 方法

4. **实现 ShareManager** - 创建 `ShareManager.java` 实现完整的云端/离线分享逻辑

5. **集成到 BackendService** - 修改 `BackendServiceImpl.java` 将分享方法委托给 `ShareManager`

6. **添加测试** - 创建 `ShareManagerTest.java` 和集成测试

7. **验证和修复** - 运行现有测试确保无回归，修复编译错误