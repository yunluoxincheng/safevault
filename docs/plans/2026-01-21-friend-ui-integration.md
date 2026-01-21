# 好友系统UI集成实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**目标:** 将已实现的好友系统功能集成到联系人界面，提供统一的联系人管理体验。

**架构:** 修改 ContactListActivity，添加 Toolbar 菜单和 FAB BottomSheet 选择器，保持现有数据流不变。

**技术栈:** Material Design 3, Room, RxJava, BottomSheetDialog, BadgeDrawable

---

## Task 1: 创建好友请求图标

**文件:**
- Create: `app/src/main/res/drawable/ic_friend_request.xml`

**Step 1: 创建铃铛图标 drawable**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorOnSurface">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"/>
</vector>
```

**Step 2: 验证文件创建**

Run: `ls -la app/src/main/res/drawable/ic_friend_request.xml`
Expected: 文件存在

**Step 3: 提交**

```bash
git add app/src/main/res/drawable/ic_friend_request.xml
git commit -m "feat: add friend request bell icon"
```

---

## Task 2: 创建联系人列表 Toolbar 菜单

**文件:**
- Create: `app/src/main/res/menu/contact_list_menu.xml`

**Step 1: 创建菜单文件**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <item
        android:id="@+id/action_friend_requests"
        android:title="好友请求"
        android:icon="@drawable/ic_friend_request"
        app:showAsAction="ifRoom" />

</menu>
```

**Step 2: 验证文件创建**

Run: `ls -la app/src/main/res/menu/contact_list_menu.xml`
Expected: 文件存在

**Step 3: 提交**

```bash
git add app/src/main/res/menu/contact_list_menu.xml
git commit -m "feat: add contact list toolbar menu"
```

---

## Task 3: 创建 BottomSheet 布局

**文件:**
- Create: `app/src/main/res/layout/bottom_sheet_add_contact.xml`

**Step 1: 创建 BottomSheet 布局**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp"
    android:background="?attr/colorSurface">

    <!-- 标题 -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="添加联系人"
        android:textAppearance="?attr/textAppearanceHeadline6"
        android:textColor="?attr/colorOnSurface"
        android:gravity="center"
        android:paddingBottom="16dp" />

    <!-- 扫描QR码按钮 -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_scan_qr"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="8dp"
        android:text="扫码添加联系人"
        app:icon="@drawable/ic_qr_code_scanner"
        app:iconGravity="start"
        style="@style/Widget.Material3.Button.OutlinedButton" />

    <!-- 搜索好友按钮 -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_search_friend"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="8dp"
        android:text="搜索添加好友"
        app:icon="@drawable/ic_search"
        app:iconGravity="start"
        style="@style/Widget.Material3.Button.OutlinedButton" />

    <!-- 取消按钮 -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_cancel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="取消"
        style="@style/Widget.Material3.Button.TextButton" />

</LinearLayout>
```

**Step 2: 验证文件创建**

Run: `ls -la app/src/main/res/layout/bottom_sheet_add_contact.xml`
Expected: 文件存在

**Step 3: 提交**

```bash
git add app/src/main/res/layout/bottom_sheet_add_contact.xml
git commit -m "feat: add bottom sheet layout for add contact options"
```

---

## Task 4: 修改 ContactListActivity 添加菜单

**文件:**
- Modify: `app/src/main/java/com/ttt/safevault/ui/share/ContactListActivity.java:47-74`

**Step 1: 在 initViews() 方法中添加菜单 inflation**

在第 49 行 `toolbar.setNavigationOnClickListener(v -> finish());` 之后添加：

```java
// Inflate toolbar menu
toolbar.inflateMenu(R.menu.contact_list_menu);
toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
```

**Step 2: 添加菜单点击处理方法**

在类的末尾（第 169 行之后）添加：

```java
private boolean onMenuItemClick(android.view.MenuItem item) {
    if (item.getItemId() == R.id.action_friend_requests) {
        // 打开好友请求列表
        Intent intent = new Intent(this, com.ttt.safevault.ui.friend.FriendRequestListActivity.class);
        startActivity(intent);
        return true;
    }
    return false;
}
```

**Step 3: 验证编译**

Run: `cd .worktrees/friend-ui-integration && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: 提交**

```bash
git add app/src/main/java/com/ttt/safevault/ui/share/ContactListActivity.java
git commit -m "feat: add toolbar menu to ContactListActivity"
```

---

## Task 5: 修改 FAB 点击事件显示 BottomSheet

**文件:**
- Modify: `app/src/main/java/com/ttt/safevault/ui/share/ContactListActivity.java:62-67`

**Step 1: 替换 FAB 点击事件**

将第 62-67 行的代码：

```java
FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
fabAdd.setOnClickListener(v -> {
    // 打开扫描添加联系人界面
    Intent intent = new Intent(this, ScanContactActivity.class);
    startActivity(intent);
});
```

替换为：

```java
FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
fabAdd.setOnClickListener(v -> showAddContactBottomSheet());
```

**Step 2: 添加 BottomSheet 显示方法**

在类的末尾添加：

```java
private void showAddContactBottomSheet() {
    com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet =
        new com.google.android.material.bottomsheet.BottomSheetDialog(this);

    View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_add_contact, null);

    // 扫码添加
    sheetView.findViewById(R.id.btn_scan_qr).setOnClickListener(v -> {
        bottomSheet.dismiss();
        Intent intent = new Intent(this, ScanContactActivity.class);
        startActivity(intent);
    });

    // 搜索添加好友
    sheetView.findViewById(R.id.btn_search_friend).setOnClickListener(v -> {
        bottomSheet.dismiss();
        checkLoginAndNavigateToSearch();
    });

    // 取消
    sheetView.findViewById(R.id.btn_cancel).setOnClickListener(v -> bottomSheet.dismiss());

    bottomSheet.setContentView(sheetView);
    bottomSheet.show();
}
```

**Step 3: 添加登录检查方法**

在类的末尾添加：

```java
private void checkLoginAndNavigateToSearch() {
    com.ttt.safevault.network.TokenManager tokenManager =
        new com.ttt.safevault.network.TokenManager(this);

    if (!tokenManager.isLoggedIn()) {
        new androidx.appcomcat.app.AlertDialog.Builder(this)
            .setTitle("需要登录")
            .setMessage("搜索添加好友需要先登录云端账号")
            .setPositiveButton("去登录", (dialog, which) -> {
                Intent intent = new Intent(this, com.ttt.safevault.ui.LoginActivity.class);
                startActivity(intent);
            })
            .setNegativeButton("取消", null)
            .show();
        return;
    }

    // 已登录，跳转到搜索页面
    Intent intent = new Intent(this, com.ttt.safevault.ui.friend.ContactSearchActivity.class);
    startActivity(intent);
}
```

**Step 4: 修复导入**

在文件顶部的 import 区域添加：

```java
import androidx.appcompat.app.AlertDialog;
```

**Step 5: 验证编译**

Run: `cd .worktrees/friend-ui-integration && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 6: 提交**

```bash
git add app/src/main/java/com/ttt/safevault/ui/share/ContactListActivity.java
git commit -m "feat: add bottom sheet for FAB add contact options"
```

---

## Task 6: 添加好友请求 Badge 通知

**文件:**
- Modify: `app/src/main/java/com/ttt/safevault/ui/share/ContactListActivity.java`

**Step 1: 添加成员变量**

在第 34 行 `private ContactManager contactManager;` 之后添加：

```java
private com.google.android.material.badge.BadgeDrawable friendRequestBadge;
```

**Step 2: 修改菜单 inflation 代码初始化 Badge**

修改 Step 4 中添加的菜单代码为：

```java
// Inflate toolbar menu
toolbar.inflateMenu(R.menu.contact_list_menu);
toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

// Setup badge for friend requests
android.view.MenuItem friendRequestItem = toolbar.getMenu().findItem(R.id.action_friend_requests);
friendRequestBadge = com.google.android.material.badge.BadgeDrawable.create(this);
friendRequestBadge.setHorizontalOffset(8);
friendRequestBadge.setVerticalOffset(8);
com.google.android.material.internal.TooltipCompat.setTooltipText(friendRequestItem, "好友请求");
androidx.appcompat.widget.TooltipCompat.setTooltipText(friendRequestItem, "好友请求");
friendRequestItem.setIcon(friendRequestBadge);
```

**修正 - 使用正确的 Badge 绑定方式：**

```java
// Inflate toolbar menu
toolbar.inflateMenu(R.menu.contact_list_menu);
toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

// Setup badge for friend requests
android.view.MenuItem friendRequestItem = toolbar.getMenu().findItem(R.id.action_friend_requests);
friendRequestBadge = com.google.android.material.badge.BadgeUtils.attachBadgeDrawable(
    friendRequestBadge,
    friendRequestItem,
    toolbar
);
```

**Step 3: 添加更新 Badge 方法**

在类的末尾添加：

```java
private void updateFriendRequestBadge() {
    new Thread(() -> {
        int count = com.ttt.safevault.data.AppDatabase.getInstance(this)
            .friendRequestDao()
            .getPendingCount();

        runOnUiThread(() -> {
            if (count > 0) {
                friendRequestBadge.setVisible(true);
                friendRequestBadge.setNumber(Math.min(count, 9));
                if (count > 9) {
                    friendRequestBadge.setBadgeTextColor(android.content.res.ColorStateList.valueOf(
                        getResources().getColor(android.R.color.white, getTheme())
                    ));
                }
            } else {
                friendRequestBadge.setVisible(false);
            }
        });
    }).start();
}
```

**Step 4: 在生命周期方法中调用 Badge 更新**

修改 `onResume()` 方法（第 77-80 行）：

```java
@Override
protected void onResume() {
    super.onResume();
    loadContacts();
    updateFriendRequestBadge();
}
```

添加 `onStart()` 方法：

```java
@Override
protected void onStart() {
    super.onStart();
    updateFriendRequestBadge();
}
```

**Step 5: 验证编译**

Run: `cd .worktrees/friend-ui-integration && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 6: 提交**

```bash
git add app/src/main/java/com/ttt/safevault/ui/share/ContactListActivity.java
git commit -m "feat: add friend request badge notification"
```

---

## Task 7: 更新空状态文案

**文件:**
- Modify: `app/src/main/res/layout/activity_contact_list.xml:65-71`

**Step 1: 修改空状态提示文案**

将第 69 行的文案从：

```xml
android:text="点击右下角按钮扫描添加"
```

改为：

```xml
android:text="点击右下角按钮添加联系人（扫码或搜索好友）"
```

**Step 2: 验证文件修改**

Run: `grep -A2 "暂无联系人" app/src/main/res/layout/activity_contact_list.xml`
Expected: 显示新的文案

**Step 3: 提交**

```bash
git add app/src/main/res/layout/activity_contact_list.xml
git commit -m "feat: update empty state hint for friend search"
```

---

## Task 8: 注册 FriendRequestListActivity 到 Manifest

**文件:**
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: 检查 Activity 是否已注册**

Run: `grep -n "FriendRequestListActivity" app/src/main/AndroidManifest.xml`
Expected: 如果未找到，需要添加

**Step 2: 如果未注册，添加 Activity 声明**

在 `</application>` 标签前添加：

```xml
<!-- 好友请求列表 -->
<activity
    android:name=".ui.friend.FriendRequestListActivity"
    android:exported="false"
    android:screenOrientation="portrait"
    android:theme="@style/Theme.SafeVault.NoActionBar" />
```

**Step 3: 如果已注册，跳过此任务**

**Step 4: 验证编译**

Run: `cd .worktrees/friend-ui-integration && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 5: 提交**（如果做了修改）

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: register FriendRequestListActivity in manifest"
```

---

## Task 9: 注册 ContactSearchActivity 到 Manifest

**文件:**
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: 检查 Activity 是否已注册**

Run: `grep -n "ContactSearchActivity" app/src/main/AndroidManifest.xml`
Expected: 如果未找到，需要添加

**Step 2: 如果未注册，添加 Activity 声明**

在 `</application>` 标签前添加：

```xml
<!-- 云端用户搜索 -->
<activity
    android:name=".ui.friend.ContactSearchActivity"
    android:exported="false"
    android:screenOrientation="portrait"
    android:windowSoftInputMode="adjustResize"
    android:theme="@style/Theme.SafeVault.NoActionBar" />
```

**Step 3: 如果已注册，跳过此任务**

**Step 4: 验证编译**

Run: `cd .worktrees/friend-ui-integration && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 5: 提交**（如果做了修改）

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: register ContactSearchActivity in manifest"
```

---

## Task 10: 最终构建验证

**Step 1: 清理构建**

```bash
cd .worktrees/friend-ui-integration
./gradlew clean
```

**Step 2: 完整构建**

```bash
./gradlew assembleDebug
```

**Expected:** BUILD SUCCESSFUL

**Step 3: 检查 APK 生成**

Run: `ls -lh app/build/outputs/apk/debug/app-debug.apk`
Expected: APK 文件存在，大小合理

**Step 4: 代码审查清单**

- [ ] Toolbar 有好友请求图标
- [ ] FAB 点击显示 BottomSheet 选项
- [ ] 未登录时点击搜索提示登录
- [ ] Badge 显示未读请求数量
- [ ] 空状态文案已更新
- [ ] 所有 Activity 已注册到 Manifest

**Step 5: 最终提交**

```bash
git add -A
git commit -m "feat: complete friend system UI integration

- Add friend request icon to toolbar
- Add BottomSheet for FAB add options
- Add badge notification for pending requests
- Update empty state hint text
- Register new Activities in manifest"
```

---

## 测试指南

### 手动测试步骤

1. **Toolbar 菜单测试**
   - 打开联系人列表
   - 验证右上角有铃铛图标
   - 点击图标应跳转到好友请求列表

2. **FAB BottomSheet 测试**
   - 点击右下角 + 按钮
   - 验证显示 BottomSheet 有三个选项
   - 点击"扫码添加"跳转到 ScanContactActivity
   - 点击"搜索添加好友"检查登录状态

3. **登录状态检查**
   - 未登录状态点击搜索
   - 应显示登录提示对话框
   - 点击"去登录"跳转到 LoginActivity

4. **Badge 通知测试**
   - 添加待处理的好友请求到数据库
   - 返回联系人列表
   - 验证 Badge 显示数量
   - 处理所有请求后 Badge 消失

5. **空状态测试**
   - 删除所有联系人
   - 验证显示新的提示文案

---

## 参考

- **Material Badge**: https://material.io/components/badges/overview
- **BottomSheet**: https://material.io/components/sheets-bottom
- **设计文档**: `docs/plans/2026-01-21-friend-ui-integration-design.md`
