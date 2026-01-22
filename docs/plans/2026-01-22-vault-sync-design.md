# 密码库云端同步功能设计

## 概述

为 SafeVault 添加密码库云端同步功能，支持多设备间密码数据的自动同步，采用零知识架构确保数据安全。

## 功能需求

### 同步模式
- **混合模式**：自动同步 + 手动触发
- **实时同步**：添加/修改/删除密码后立即同步
- **定时同步**：用户可配置同步间隔（30分钟/1小时/2小时/4小时）

### 冲突处理
- 版本冲突时弹出对话框让用户选择保留本地或云端数据

### 状态展示
- 主界面顶部显示同步状态指示器
- 设置页面提供详细的同步信息和配置

### 离线支持
- 网络不可用时操作保存到队列
- 网络恢复后自动同步

### 同步策略
- **智能混合**：首次同步/冲突时全量，平时增量同步

## 架构设计

### 核心组件

| 组件 | 职责 |
|------|------|
| `VaultSyncManager` | 管理同步状态和同步任务 |
| `SyncScheduler` | 根据用户配置执行定时同步 |
| `SyncConflictResolver` | 检测和解决同步冲突 |
| `SyncStateManager` | 管理同步状态供 UI 订阅 |
| `SyncOfflineQueue` | 管理离线操作队列 |

### 架构层次

```
UI Layer (MainActivity, SettingsFragment, etc.)
    │
ViewModel Layer
    │
Sync Manager Layer (VaultSyncManager, SyncScheduler, etc.)
    │
EncryptionSyncManager (现有，保持不变)
    │
Network Layer (VaultServiceApi)
```

## UI 组件设计

### 1. 同步状态指示器 (SyncStatusIndicator)

**位置**：MainActivity 顶部，Toolbar 下方

**状态**：
- 空闲：灰色云图标 + "已同步" + 最后同步时间
- 同步中：蓝色旋转动画 + "同步中..."
- 成功：绿色对勾（2秒后消失）
- 失败：红色警告 + "同步失败"（点击重试）
- 冲突：橙色冲突图标 + "有冲突"（点击打开对话框）
- 离线：灰色断网图标 + "离线模式"

### 2. 同步设置页面 (SyncSettingsFragment)

**入口**：设置页面添加"云端同步"选项

**内容**：
- 同步开关
- 自动同步间隔下拉菜单
- "仅 WiFi 下同步"开关
- 同步状态信息（最后同步时间、版本号等）
- 手动同步按钮

### 3. 冲突解决对话框 (SyncConflictDialog)

**样式**：Material3 AlertDialog

**内容**：
- 标题："检测到冲突"
- 显示本地和云端版本信息（修改时间、设备名、修改数量）
- 三个按钮："保留本地" / "保留云端" / "取消"

## 数据流程设计

### 实时同步流程

```
用户操作（添加/修改/删除）
    │
    ▼
本地数据库更新
    │
    ├─ 网络可用 ─► VaultSyncManager.sync()
    │                  │
    │                  ├─ 无冲突 ─► 上传成功
    │                  └─ 有冲突 ─► 显示冲突对话框
    │
    └─ 网络不可用 ─► 添加到离线队列
```

### 定时同步流程

```
WorkManager 触发
    │
    ▼
检查网络
    │
    ├─ 不可用 ─► 跳过
    │
    └─ 可用 ─► 比较版本
                  │
                  ├─ 版本一致 ─► 无需同步
                  ├─ 云端较新 ─► 下载合并
                  └─ 本地较新 ─► 上传
```

### 网络恢复流程

```
网络状态广播
    │
    ▼
检查离线队列
    │
    ├─ 有待处理 ─► 逐个执行
    │                ├─ 成功 ─► 移除
    │                └─ 失败 ─► 保留/重试
    │
    └─ 无待处理 ─► 正常同步
```

## 离线队列设计

### 数据结构

```java
public class SyncOperation {
    private String operationId;
    private OperationType type;    // CREATE, UPDATE, DELETE
    private String passwordId;
    private String encryptedData;
    private String iv;
    private long timestamp;
    private int retryCount;
    private SyncStatus status;     // PENDING, IN_PROGRESS, SUCCESS, FAILED
}
```

### 重试机制
- 最多重试 3 次
- 每次间隔 5 秒
- 3 次失败后标记为 FAILED

### 操作合并优化
- 同一密码的连续 UPDATE 只保留最后一个
- DELETE 操作优先

## 文件结构

```
app/src/main/java/com/ttt/safevault/
├── sync/
│   ├── VaultSyncManager.java
│   ├── SyncScheduler.java
│   ├── SyncConflictResolver.java
│   ├── SyncStateManager.java
│   └── SyncOfflineQueue.java
├── ui/
│   ├── SyncStatusIndicator.java
│   ├── SyncSettingsFragment.java
│   └── SyncConflictDialog.java
└── data/
    └── SyncOperationEntity.java

res/layout/
├── sync_status_indicator.xml
├── fragment_sync_settings.xml
└── dialog_sync_conflict.xml
```

## 安全考虑

1. **零知识架构**：云端只存储加密数据，后端无法解密
2. **版本控制**：使用版本号检测和解决冲突
3. **HTTPS**：所有网络通信使用 HTTPS
4. **令牌管理**：使用现有的 TokenManager 自动刷新令牌

## 后续实现计划

1. 创建核心同步组件
2. 实现状态指示器和设置页面
3. 实现冲突解决对话框
4. 集成离线队列
5. 测试同步流程

---

**文档版本**: 1.0
**创建日期**: 2026-01-22
