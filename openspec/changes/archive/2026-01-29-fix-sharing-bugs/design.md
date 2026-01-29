# fix-sharing-bugs Design

## Architecture Overview

本次修复主要涉及以下模块：

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                             │
├─────────────────────────────────────────────────────────────┤
│  RegisterActivity │ MainActivity │ ShareActivity            │
│  ContactListActivity │ ContactSearchActivity               │
│  ScanShareActivity (新增)                                    │
├─────────────────────────────────────────────────────────────┤
│                      Manager Layer                           │
├─────────────────────────────────────────────────────────────┤
│  TokenManager │ ContactManager │ ContactSyncManager         │
├─────────────────────────────────────────────────────────────┤
│                      Network Layer                           │
├─────────────────────────────────────────────────────────────┤
│  FriendServiceApi │ ShareServiceApi                          │
└─────────────────────────────────────────────────────────────┘
```

## Component Changes

### 1. 邮箱保存修复 (RegisterActivity)

**问题**: 注册成功后没有保存邮箱到 TokenManager，导致 MyIdentityActivity 无法获取邮箱生成身份码

**解决方案**:
```java
// RegisterActivity.java - handleSetMasterPassword()
// 在注册成功后添加
TokenManager tokenManager = RetrofitClient.getInstance(getApplicationContext())
    .getTokenManager();
tokenManager.saveLastLoginEmail(registeredEmail);
```

**数据流**:
```
RegisterActivity.handleSetMasterPassword()
    ↓
BackendService.completeRegistration()
    ↓
TokenManager.saveLastLoginEmail(email) ← 新增
    ↓
MyIdentityActivity 从 TokenManager.getLastLoginEmail() 获取邮箱
```

### 2. 删除好友修复 (ContactListActivity)

**问题**: 只删除本地数据，未调用云端 API

**解决方案**:
```java
// ContactListActivity.java - showDeleteConfirmDialog()
// 修改删除逻辑
private void deleteContactAndFriend(Contact contact) {
    // 1. 先调用云端 API 删除好友关系
    friendServiceApi.deleteFriend(contact.cloudUserId)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            () -> {
                // 2. 云端删除成功后，删除本地数据
                contactManager.deleteContact(contact.contactId);
                loadContacts();
                Toast.makeText(this, "已删除好友", Toast.LENGTH_SHORT).show();
            },
            error -> {
                Toast.makeText(this, "删除失败: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        );
}
```

**数据流**:
```
用户长按联系人 → 选择删除
    ↓
调用 FriendServiceApi.deleteFriend(cloudUserId)
    ↓
云端删除成功 → ContactManager.deleteContact(contactId)
    ↓
本地数据库删除 → 刷新列表
```

### 3. 搜索过滤已添加好友 (ContactSearchActivity)

**问题**: 搜索结果显示所有用户，包括已添加的好友

**解决方案**:
```java
// ContactSearchActivity.java
private Set<String> friendCloudUserIds = new HashSet<>();

private void loadFriendFilter() {
    List<Contact> contacts = contactManager.getAllContacts();
    for (Contact contact : contacts) {
        if (contact.cloudUserId != null && !contact.cloudUserId.isEmpty()) {
            friendCloudUserIds.add(contact.cloudUserId);
        }
    }
}

private void performSearch(String query) {
    // ... 现有搜索逻辑
    friendServiceApi.searchUsers(query)
        .map(results -> {
            // 过滤掉已添加的好友
            List<UserSearchResult> filtered = new ArrayList<>();
            for (UserSearchResult result : results) {
                if (!friendCloudUserIds.contains(result.getUserId())) {
                    filtered.add(result);
                }
            }
            return filtered;
        })
        // ... 后续处理
}
```

### 4. 联系人选择修复 (ShareActivity)

**问题**: 使用已废弃的 `startActivityForResult` 但没有正确处理结果

**解决方案**:
```java
// ShareActivity.java
// 使用 ActivityResultLauncher 替代 startActivityForResult
private final ActivityResultLauncher<Intent> contactListLauncher =
    registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            String selectedContactId = result.getData().getStringExtra(ContactListActivity.EXTRA_CONTACT_ID);
            if (selectedContactId != null) {
                loadContact(selectedContactId);
            }
        }
    });

// 修改按钮点击事件
btnSelectContact.setOnClickListener(v -> {
    Intent intent = new Intent(this, ContactListActivity.class);
    contactListLauncher.launch(intent);
});
```

### 5. 扫码入口添加 (MainActivity + ScanShareActivity)

**问题**: 没有扫描分享二维码的入口

**解决方案**:

**a. 在 MainActivity 添加 FAB 按钮**:
```xml
<!-- activity_main.xml -->
<com.google.android.material.floatingactionbutton.FloatingActionButton
    android:id="@+id/fabScan"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom|end"
    android:layout_margin="16dp"
    android:contentDescription="扫一扫"
    app:srcCompat="@drawable/ic_qr_code_scan" />
```

**b. 创建 ScanShareActivity**:
```java
// 新建 ScanShareActivity.java
public class ScanShareActivity extends AppCompatActivity {
    // 类似 ScanContactActivity，但扫描分享码后跳转到 ReceiveShareActivity
}
```

**c. 处理扫描结果**:
```
用户点击 FAB → ScanShareActivity → 扫描 safevault://share/{shareId}
    ↓
解析 shareId → ReceiveShareActivity → 显示分享内容
```

### 6. 蓝牙设备搜索改进 (BluetoothDeviceListDialog)

**问题**: 无法区分已配对和新发现的设备

**解决方案**:
```java
// BluetoothDeviceListDialog.java
private static class DeviceItem {
    BluetoothDevice device;
    boolean isPaired;  // 新增字段标记是否已配对
}

private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            // 新发现的设备标记为未配对
            DeviceItem item = new DeviceItem(device, false);
            if (!devices.contains(item)) {
                devices.add(item);
                adapter.notifyDataSetChanged();
            }
        }
    }
};

private void loadPairedDevices() {
    Set<BluetoothDevice> pairedDevices = bluetoothManager.getPairedDevices();
    devices.clear();
    for (BluetoothDevice device : pairedDevices) {
        // 已配对设备标记为已配对
        devices.add(new DeviceItem(device, true));
    }
    adapter.notifyDataSetChanged();
}

// DeviceAdapter 中显示标识
void bind(DeviceItem item) {
    String name = device.getName();
    if (name == null || name.isEmpty()) {
        name = "未知设备";
    }
    textDeviceName.setText(name);
    textDeviceAddress.setText(device.getAddress());

    // 显示配对状态
    if (item.isPaired) {
        textStatus.setText("已配对");
        textStatus.setTextColor(getColor(R.color.success_green));
    } else {
        textStatus.setText("新设备");
        textStatus.setTextColor(getColor(R.color.primary_color));
    }
}
```

### 7. NFC 功能移除

**需要移除的文件**:
- `NFCTransferManager.java`
- `NFCSendActivity.java`
- `NFCReceiveActivity.java`

**需要修改的文件**:
- `ShareActivity.java` - 移除 NFC 相关按钮
- `AndroidManifest.xml` - 移除 NFC 权限
- 布局文件 - 移除 NFC 相关 UI

## Data Model Changes

不需要修改数据模型，只涉及行为变更。

## Security Considerations

1. **邮箱保存**: 使用 SharedPreferences，需要确保不会泄露敏感信息
2. **删除好友**: 需要验证操作权限，确保只能删除自己的好友
3. **扫码分享**: 需要验证分享码的格式和有效性

## Testing Strategy

1. **邮箱保存**: 注册后检查 TokenManager 中的邮箱是否正确保存
2. **删除好友**: 测试删除后本地和云端是否同步删除
3. **搜索过滤**: 添加好友后搜索，确认不会出现在结果中
4. **联系人选择**: 测试从 ShareActivity 打开联系人选择并返回
5. **扫码入口**: 测试 FAB 按钮能打开扫描界面并正确解析分享码
6. **蓝牙搜索**: 测试已配对设备显示"已配对"，新设备显示"新设备"
7. **NFC 移除**: 确认所有 NFC 相关代码和 UI 已移除

## Error Handling

1. **邮箱保存失败**: 记录日志，不影响注册流程
2. **删除好友失败**: 显示错误提示，保留本地数据
3. **搜索过滤失败**: 降级为显示所有结果
4. **联系人选择失败**: 显示错误提示，允许重试
5. **扫码失败**: 显示错误提示，引导用户手动输入分享码
6. **蓝牙搜索失败**: 显示错误提示，列出已配对设备
