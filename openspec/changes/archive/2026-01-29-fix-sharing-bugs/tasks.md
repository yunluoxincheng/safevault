# fix-sharing-bugs Tasks

## 顺序任务列表

### Phase 1: 核心功能修复 (问题 1-4)

#### Task 1.1: 修复注册后邮箱未保存
**File**: `app/src/main/java/com/ttt/safevault/ui/RegisterActivity.java`
**Description**: 在 `handleSetMasterPassword()` 方法中，注册成功后调用 `TokenManager.saveLastLoginEmail()` 保存邮箱
**Acceptance**:
- 用户注册成功后，TokenManager 中保存了邮箱
- MyIdentityActivity 能正确获取邮箱并生成身份码

#### Task 1.2: 修复删除好友功能
**File**: `app/src/main/java/com/ttt/safevault/ui/share/ContactListActivity.java`
**Description**:
1. 修改 `showDeleteConfirmDialog()` 方法，添加 FriendServiceApi 依赖
2. 创建新方法 `deleteContactAndFriend(Contact)`，先调用云端 API，成功后再删除本地
**Acceptance**:
- 删除好友时，先调用 `FriendServiceApi.deleteFriend()`
- 云端删除成功后才删除本地数据
- 删除失败时显示错误提示，本地数据保持不变

#### Task 1.3: 添加搜索过滤已添加好友
**File**: `app/src/main/java/com/ttt/safevault/ui/friend/ContactSearchActivity.java`
**Description**:
1. 添加 `Set<String> friendCloudUserIds` 字段
2. 添加 `loadFriendFilter()` 方法，加载本地好友的 cloudUserId
3. 修改 `performSearch()` 方法，过滤结果中的已添加好友
**Acceptance**:
- 搜索结果中不显示已添加的好友
- 尝试添加已存在的好友时显示提示

#### Task 1.4: 修复联系人选择界面启动
**File**: `app/src/main/java/com/ttt/safevault/ui/share/ShareActivity.java`
**Description**:
1. 使用 `ActivityResultLauncher` 替代 `startActivityForResult`
2. 修改 `btnSelectContact` 点击事件
3. 处理返回结果
**Acceptance**:
- 点击"选择联系人"能打开 ContactListActivity
- 选择联系人后能正确返回并显示

---

### Phase 2: 扫码入口添加 (问题 5)

#### Task 2.1: 在 MainActivity 添加扫码 FAB
**Files**:
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/java/com/ttt/safevault/MainActivity.java`
**Description**:
1. 在布局中添加 FAB 按钮
2. 设置点击事件打开 ScanShareActivity
**Acceptance**:
- 主界面右下角显示"扫一扫"按钮
- 点击按钮能打开扫描界面

#### Task 2.2: 创建 ScanShareActivity
**Files**:
- `app/src/main/java/com/ttt/safevault/ui/share/ScanShareActivity.java`
- `app/src/main/res/layout/activity_scan_share.xml`
**Description**:
1. 创建扫描分享二维码的 Activity
2. 使用 ZXing 扫描 `safevault://share/{shareId}` 格式的码
3. 扫描成功后解析 shareId 并跳转到 ReceiveShareActivity
**Acceptance**:
- 能正确扫描分享二维码
- 解析 shareId 并跳转到接收界面

---

### Phase 3: 蓝牙搜索改进 (问题 6)

#### Task 3.1: 修改蓝牙设备列表显示
**File**: `app/src/main/java/com/ttt/safevault/ui/share/BluetoothDeviceListDialog.java`
**Description**:
1. 创建 `DeviceItem` 类，包含 `isPaired` 字段
2. 修改 `loadPairedDevices()`，标记已配对设备
3. 修改 `discoveryReceiver`，标记新发现的设备
4. 更新 `DeviceAdapter.ViewHolder.bind()` 显示配对状态
**Acceptance**:
- 已配对设备显示"已配对"标识（绿色）
- 新发现的设备显示"新设备"标识（主题色）

---

### Phase 4: NFC 功能移除 (问题 7)

#### Task 4.1: 移除 NFC 相关代码
**Files**:
- `app/src/main/java/com/ttt/safevault/utils/NFCTransferManager.java` (删除)
- `app/src/main/java/com/ttt/safevault/ui/share/NFCSendActivity.java` (删除)
- `app/src/main/java/com/ttt/safevault/ui/share/NFCReceiveActivity.java` (删除)
**Description**: 删除所有 NFC 相关的 Java 文件
**Acceptance**: 文件已删除

#### Task 4.2: 移除 NFC UI 入口
**Files**:
- `app/src/main/java/com/ttt/safevault/ui/share/ShareActivity.java`
- 相关布局文件
**Description**: 移除所有 NFC 相关的按钮和菜单
**Acceptance**: 分享界面中没有 NFC 相关选项

#### Task 4.3: 移除 NFC 权限
**File**: `app/src/main/AndroidManifest.xml`
**Description**: 移除 NFC 相关权限
**Acceptance**: AndroidManifest.xml 中没有 NFC 权限

---

### Phase 5: 测试与验证

#### Task 5.1: 单元测试
**Description**: 为修改的关键方法添加单元测试
**Acceptance**: 核心修改有对应的测试覆盖

#### Task 5.2: 集成测试
**Description**: 端到端测试所有修复的功能
**Acceptance**:
- 注册流程测试通过
- 添加/删除好友测试通过
- 搜索和分享测试通过
- 扫码功能测试通过
- 蓝牙功能测试通过

#### Task 5.3: 清理与文档
**Description**:
1. 清理未使用的导入
2. 更新相关注释
3. 提交代码
**Acceptance**: 代码已清理并提交
