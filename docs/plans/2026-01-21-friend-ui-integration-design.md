# 好友系统UI集成设计

**日期**: 2026-01-21
**状态**: 已确认

## 概述

将已实现的好友系统功能集成到联系人界面，提供统一的联系人管理体验。

## 背景

现有状态：
- `ContactListActivity` - 仅支持扫码添加联系人
- `FriendRequestListActivity` - 代码完整，无UI入口
- `ContactSearchActivity` - 代码完整，无UI入口

设计目标：
1. 添加"搜索好友"功能入口
2. 添加"好友请求"通知入口
3. 统一联系人列表，无需区分来源

---

## UI设计

### 界面布局

```
┌─────────────────────────────────────────┐
│ [←] 我的联系人              [🔔请求图标]  │  ← Toolbar
├─────────────────────────────────────────┤
│                                         │
│   [联系人列表]                          │
│   - 用户A                               │
│   - 用户B                               │
│   - 用户C                               │
│                                         │
│   所有联系人统一显示，无来源区分          │
└─────────────────────────────────────────┘
[我的身份码]                    [+ 添加]    ← 底部按钮
```

### 具体变更点

1. **Toolbar 右侧**：添加好友请求图标
   - 使用 `MaterialToolbar` 的 `menu` 属性
   - 图标：`ic_friend_request`（铃铛图标）
   - 有未读请求时显示 Badge（红点+数字）

2. **FAB 点击行为**：显示 BottomSheet 选项菜单
   - 选项1：`扫码添加联系人` → 跳转 `ScanContactActivity`
   - 选项2：`搜索添加好友` → 跳转 `ContactSearchActivity`

3. **联系人列表**：保持现有逻辑不变
   - 好友通过 `ContactSyncManager` 自动同步
   - 用户无需区分来源

---

## 交互逻辑

### FAB BottomSheet 选择菜单

```
用户点击 [+]
    ↓
显示 BottomSheetDialog
┌─────────────────────┐
│ 添加联系人           │
├─────────────────────┤
│ 📷 扫码添加          │
│ 🔍 搜索添加好友      │
│ ✕ 取消              │
└─────────────────────┘
    ↓
用户选择 → 跳转对应Activity
```

### 好友自动同步流程

```
用户接受好友请求
    ↓
FriendRequestListActivity 调用 API
    ↓
服务器返回成功
    ↓
ContactSyncManager.syncContacts()
    ↓
查询云端好友列表 → 插入到 Contact 表
    ↓
ContactListActivity.onResume() → 刷新列表
```

---

## 通知系统

### Toolbar 图标通知逻辑

```
┌─────────────────────────────────────────┐
│ [←] 我的联系人              🔔 (3)      │  ← 显示未读数量
└─────────────────────────────────────────┘
```

### 通知数量获取流程

```
ContactListActivity.onStart()
    ↓
查询本地 FriendRequestDao.getPendingRequestCount()
    ↓
更新 Toolbar Badge（数量 > 0 时显示）
    ↓
用户点击图标 → 跳转 FriendRequestListActivity
```

### Badge 显示规则

| 条件 | 显示效果 |
|------|----------|
| 0 条 | 无红点 |
| 1-9 条 | 红点 + 数字 |
| 10+ 条 | 红点 + "9+" |

### 自动刷新时机

- `onResume()` - 页面返回时刷新
- `ShareNotificationService` 收到好友请求推送 → 刷新
- 用户从好友请求页面返回后 → 清除 Badge

---

## 错误处理

### 网络错误处理

| 场景 | 处理方式 |
|------|----------|
| 搜索用户失败 | 显示 Toast "搜索失败，请检查网络" |
| 发送好友请求失败 | 显示 Toast "发送失败，请重试" |
| 加载好友请求失败 | 显示本地缓存，Toast 提示网络错误 |

### 边界情况

1. **未登录状态**
   - 点击"搜索添加好友" → 检查登录状态
   - 未登录 → 提示"请先登录" → 跳转 LoginActivity

2. **空状态优化**
   - 联系人列表为空 → 显示友好提示
   - 文案："点击右下角按钮添加联系人（扫码或搜索好友）"

3. **权限检查**
   - 扫码需要相机权限 → 运行时请求
   - 拒绝权限 → 提示"需要相机权限才能扫码"

4. **重复添加**
   - 尝试添加已是联系人/好友的用户 → API 返回错误
   - 显示 Toast "该用户已是你的联系人"

### 数据一致性

- 接受好友请求后，延迟 500ms 刷新联系人列表（确保同步完成）
- 使用 `LiveData` 或 `runOnUiThread()` 避免 UI 线程问题

---

## 需要修改的文件

### 新建文件
1. `res/menu/contact_list_menu.xml` - Toolbar 菜单
2. `res/layout/bottom_sheet_add_contact.xml` - FAB 选项菜单
3. `res/drawable/ic_friend_request.xml` - 铃铛图标

### 修改文件
1. `ui/share/ContactListActivity.java` - 添加菜单和 FAB 逻辑
2. `activity_contact_list.xml` - 可能需要调整布局
3. `AndroidManifest.xml` - 注册新 Activity（如需要）

---

## 技术要点

1. **BottomSheet** 使用 `com.google.android.material.bottomsheet.BottomSheetDialog`
2. **Badge** 使用 Material Components 的 `BadgeDrawable`
3. **未读数量** 异步查询 `FriendRequestDao`
4. **登录检查** 使用 `TokenManager` 验证 token 状态
