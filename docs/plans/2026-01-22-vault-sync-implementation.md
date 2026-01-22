# SafeVault 密码库云端同步功能实现计划

**文档版本**: 1.0
**创建日期**: 2026-01-22
**预计总时间**: 约 60-90 分钟（约 15-20 个任务，每个 2-5 分钟）

---

## 目录

1. [项目概述](#项目概述)
2. [实现步骤总览](#实现步骤总览)
3. [第一阶段：创建核心同步组件](#第一阶段创建核心同步组件)
4. [第二阶段：创建UI组件](#第二阶段创建ui组件)
5. [第三阶段：集成到现有系统](#第三阶段集成到现有系统)
6. [测试步骤](#测试步骤)
7. [依赖清单](#依赖清单)

---

## 项目概述

### 目标

为 SafeVault 添加密码库云端同步功能，实现：
- 实时同步（添加/修改/删除密码后自动同步）
- 定时同步（用户可配置间隔）
- 离线队列（网络恢复后自动同步）
- 冲突解决（版本冲突时用户选择）

### 技术栈

- **网络层**: Retrofit + RxJava3
- **数据库**: Room (版本 4)
- **架构**: MVVM
- **UI**: Material Design 3

---

## 实现步骤总览

| 阶段 | 任务数 | 预计时间 |
|------|--------|----------|
| 第一阶段 | 7 | ~20分钟 |
| 第二阶段 | 6 | ~20分钟 |
| 第三阶段 | 2 | ~10分钟 |
| 测试 | - | ~10分钟 |

---

## 第一阶段：创建核心同步组件

### 任务 1.1：创建同步状态枚举类

**文件路径**: `app/src/main/java/com/ttt/safevault/sync/SyncStatus.java`

**完整代码**:

```java
package com.ttt.safevault.sync;

/**
 * 同步状态枚举
 */
public enum SyncStatus {
    /**
     * 空闲状态（已同步）
     */
    IDLE,

    /**
     * 同步中
     */
    SYNCING,

    /**
     * 同步成功
     */
    SUCCESS,

    /**
     * 同步失败
     */
    FAILED,

    /**
     * 有冲突
     */
    CONFLICT,

    /**
     * 离线模式
     */
    OFFLINE
}
```

**时间**: 2 分钟

---

### 任务 1.2：创建同步配置类

**文件路径**: `app/src/main/java/com/ttt/safevault/sync/SyncConfig.java`

**完整代码**:

```java
package com.ttt.safevault.sync;

/**
 * 同步配置类
 */
public class SyncConfig {

    /**
     * 同步间隔选项（分钟）
     */
    public static final int INTERVAL_30_MINUTES = 30;
    public static final int INTERVAL_1_HOUR = 60;
    public static final int INTERVAL_2_HOURS = 120;
    public static final int INTERVAL_4_HOURS = 240;
    public static final int INTERVAL_MANUAL_ONLY = 0;  // 仅手动同步

    private boolean syncEnabled = true;
    private int syncIntervalMinutes = INTERVAL_30_MINUTES;
    private boolean wifiOnly = false;

    public SyncConfig() {
    }

    public SyncConfig(boolean syncEnabled, int syncIntervalMinutes, boolean wifiOnly) {
        this.syncEnabled = syncEnabled;
        this.syncIntervalMinutes = syncIntervalMinutes;
        this.wifiOnly = wifiOnly;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public int getSyncIntervalMinutes() {
        return syncIntervalMinutes;
    }

    public void setSyncIntervalMinutes(int syncIntervalMinutes) {
        this.syncIntervalMinutes = syncIntervalMinutes;
    }

    public boolean isWifiOnly() {
        return wifiOnly;
    }

    public void setWifiOnly(boolean wifiOnly) {
        this.wifiOnly = wifiOnly;
    }
}
```

**时间**: 2 分钟

---

### 任务 1.3：创建同步状态数据实体

**文件路径**: `app/src/main/java/com/ttt/safevault/sync/SyncState.java`

**完整代码**:

```java
package com.ttt.safevault.sync;

/**
 * 同步状态数据
 */
public class SyncState {

    private SyncStatus status;
    private String lastSyncTime;
    private Long clientVersion;
    private Long serverVersion;
    private String errorMessage;
    private boolean hasPendingOperations;

    public SyncState() {
        this.status = SyncStatus.IDLE;
    }

    public SyncState(SyncStatus status) {
        this.status = status;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public void setStatus(SyncStatus status) {
        this.status = status;
    }

    public String getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(String lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public Long getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(Long clientVersion) {
        this.clientVersion = clientVersion;
    }

    public Long getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(Long serverVersion) {
        this.serverVersion = serverVersion;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isHasPendingOperations() {
        return hasPendingOperations;
    }

    public void setHasPendingOperations(boolean hasPendingOperations) {
        this.hasPendingOperations = hasPendingOperations;
    }
}
```

**时间**: 2 分钟

---

### 任务 1.4：创建同步状态管理器

**文件路径**: `app/src/main/java/com/ttt/safevault/sync/SyncStateManager.java`

**完整代码**:

```java
package com.ttt.safevault.sync;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * 同步状态管理器（单例模式）
 * 管理同步状态供 UI 订阅
 */
public class SyncStateManager extends ViewModel {

    private static volatile SyncStateManager INSTANCE;

    private final MutableLiveData<SyncState> syncState = new MutableLiveData<>(new SyncState());
    private final MutableLiveData<SyncConfig> syncConfig = new MutableLiveData<>(new SyncConfig());

    private SyncStateManager() {
    }

    public static SyncStateManager getInstance() {
        if (INSTANCE == null) {
            synchronized (SyncStateManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SyncStateManager();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 获取同步状态的 LiveData
     */
    public MutableLiveData<SyncState> getSyncState() {
        return syncState;
    }

    /**
     * 获取同步配置的 LiveData
     */
    public MutableLiveData<SyncConfig> getSyncConfig() {
        return syncConfig;
    }

    /**
     * 更新同步状态
     */
    public void updateStatus(SyncStatus status) {
        SyncState current = syncState.getValue();
        if (current != null) {
            current.setStatus(status);
            syncState.postValue(current);
        }
    }

    /**
     * 更新完整同步状态
     */
    public void updateSyncState(SyncState newState) {
        syncState.postValue(newState);
    }

    /**
     * 更新同步配置
     */
    public void updateConfig(SyncConfig config) {
        syncConfig.postValue(config);
    }

    /**
     * 获取当前同步状态（同步获取）
     */
    public SyncState getCurrentState() {
        return syncState.getValue();
    }

    /**
     * 获取当前同步配置（同步获取）
     */
    public SyncConfig getCurrentConfig() {
        return syncConfig.getValue();
    }
}
```

**时间**: 3 分钟

---

### 任务 1.5：创建离线操作实体

**文件路径**: `app/src/main/java/com/ttt/safevault/data/SyncOperationEntity.java`

**完整代码**:

```java
package com.ttt.safevault.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 离线同步操作实体
 * 用于在网络不可用时缓存操作
 */
@Entity(tableName = "sync_operations")
public class SyncOperationEntity {

    @PrimaryKey
    private String operationId;

    /**
     * 操作类型：CREATE, UPDATE, DELETE
     */
    private String operationType;

    /**
     * 密码条目ID（如果适用）
     */
    private Integer passwordId;

    /**
     * 操作时间戳
     */
    private long timestamp;

    /**
     * 重试次数
     */
    private int retryCount;

    /**
     * 操作状态：PENDING, IN_PROGRESS, SUCCESS, FAILED
     */
    private String status;

    public SyncOperationEntity() {
        this.timestamp = System.currentTimeMillis();
        this.retryCount = 0;
        this.status = "PENDING";
    }

    public SyncOperationEntity(String operationId, String operationType, Integer passwordId) {
        this();
        this.operationId = operationId;
        this.operationType = operationType;
        this.passwordId = passwordId;
    }

    // Getters and Setters
    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public Integer getPasswordId() {
        return passwordId;
    }

    public void setPasswordId(Integer passwordId) {
        this.passwordId = passwordId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
```

**时间**: 2 分钟

---

### 任务 1.6：创建离线操作 DAO

**文件路径**: `app/src/main/java/com/ttt/safevault/data/SyncOperationDao.java`

**完整代码**:

```java
package com.ttt.safevault.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;

import java.util.List;

/**
 * 离线同步操作 DAO
 */
@Dao
public interface SyncOperationDao {

    /**
     * 插入操作记录
     */
    @Insert
    void insert(SyncOperationEntity operation);

    /**
     * 删除操作记录
     */
    @Delete
    void delete(SyncOperationEntity operation);

    /**
     * 获取所有待处理操作
     */
    @Query("SELECT * FROM sync_operations WHERE status = 'PENDING' ORDER BY timestamp ASC")
    List<SyncOperationEntity> getPendingOperations();

    /**
     * 获取所有操作
     */
    @Query("SELECT * FROM sync_operations ORDER BY timestamp DESC")
    List<SyncOperationEntity> getAllOperations();

    /**
     * 清空所有操作
     */
    @Query("DELETE FROM sync_operations")
    void clearAll();

    /**
     * 更新操作状态
     */
    @Query("UPDATE sync_operations SET status = :status WHERE operationId = :operationId")
    void updateStatus(String operationId, String status);

    /**
     * 增加重试次数
     */
    @Query("UPDATE sync_operations SET retryCount = retryCount + 1 WHERE operationId = :operationId")
    void incrementRetryCount(String operationId);

    /**
     * 获取待处理操作数量
     */
    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = 'PENDING'")
    int getPendingCount();
}
```

**时间**: 2 分钟

---

### 任务 1.7：更新数据库版本

**文件路径**: `app/src/main/java/com/ttt/safevault/data/AppDatabase.java`

**修改内容**:

1. 更新 `@Database` 注解，添加 `SyncOperationEntity.class` 到 entities 数组
2. 将版本号从 4 升级到 5
3. 添加迁移逻辑 `MIGRATION_4_5`

**完整修改后的类声明**:

```java
@Database(entities = {EncryptedPasswordEntity.class, Contact.class, ShareRecord.class, FriendRequest.class, SyncOperationEntity.class}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "safevault_db";
    private static volatile AppDatabase INSTANCE;

    public abstract PasswordDao passwordDao();
    public abstract ContactDao contactDao();
    public abstract ShareRecordDao shareRecordDao();
    public abstract FriendRequestDao friendRequestDao();
    public abstract SyncOperationDao syncOperationDao();

    // ... 保留现有的迁移 ...

    // 数据库版本4到版本5的迁移：添加同步操作表
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 创建同步操作表
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_operations (" +
                "operationId TEXT PRIMARY KEY NOT NULL, " +
                "operationType TEXT NOT NULL, " +
                "passwordId INTEGER, " +
                "timestamp INTEGER NOT NULL, " +
                "retryCount INTEGER NOT NULL, " +
                "status TEXT NOT NULL)"
            );

            // 创建索引
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_status ON sync_operations(status)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_timestamp ON sync_operations(timestamp)");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                     .build();
                }
            }
        }
        return INSTANCE;
    }

    // ... 保留其他方法 ...
}
```

**时间**: 5 分钟

---

## 第二阶段：创建UI组件

### 任务 2.1：创建同步状态指示器布局

**文件路径**: `app/src/main/res/layout/sync_status_indicator.xml`

**完整代码**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/sync_status_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="12dp"
    android:gravity="center_vertical"
    android:background="?attr/colorSurfaceVariant">

    <!-- 状态图标 -->
    <ImageView
        android:id="@+id/sync_status_icon"
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:src="@drawable/ic_cloud_done"
        android:contentDescription="@null"
        app:tint="?attr/colorOnSurfaceVariant" />

    <!-- 状态文本 -->
    <TextView
        android:id="@+id/sync_status_text"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="8dp"
        android:text="已同步"
        android:textAppearance="?attr/textAppearanceBodySmall"
        android:textColor="?attr/colorOnSurfaceVariant" />

    <!-- 最后同步时间 -->
    <TextView
        android:id="@+id/sync_last_sync_time"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="刚刚"
        android:textAppearance="?attr/textAppearanceBodySmall"
        android:textColor="?attr/colorOnSurfaceVariant" />

</LinearLayout>
```

**时间**: 3 分钟

---

### 任务 2.2：创建同步设置页面布局

**文件路径**: `app/src/main/res/layout/fragment_sync_settings.xml`

**完整代码**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface"
    android:fillViewport="true"
    tools:context=".ui.SyncSettingsFragment">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- 云端同步开关卡片 -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:padding="16dp"
                android:gravity="center_vertical">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_cloud_done"
                    android:contentDescription="@null"
                    app:tint="?attr/colorPrimary"
                    android:layout_marginEnd="16dp" />

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="云端同步"
                        android:textAppearance="?attr/textAppearanceTitleMedium"
                        android:textColor="?attr/colorOnSurface" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="自动同步密码到云端"
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurfaceVariant"
                        android:layout_marginTop="2dp" />
                </LinearLayout>

                <com.google.android.material.materialswitch.MaterialSwitch
                    android:id="@+id/switch_sync_enabled"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- 同步间隔设置卡片 -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/card_sync_interval"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            android:clickable="true"
            android:focusable="true"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:padding="16dp"
                android:gravity="center_vertical">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_autorenew"
                    android:contentDescription="@null"
                    app:tint="?attr/colorOnSurface"
                    android:layout_marginEnd="16dp" />

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="同步间隔"
                        android:textAppearance="?attr/textAppearanceTitleMedium"
                        android:textColor="?attr/colorOnSurface" />

                    <TextView
                        android:id="@+id/tv_sync_interval_value"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="30 分钟"
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurfaceVariant"
                        android:layout_marginTop="2dp" />
                </LinearLayout>

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_navigate_next"
                    android:contentDescription="@null"
                    app:tint="?attr/colorOnSurfaceVariant" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- 仅 WiFi 同步开关 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:padding="16dp"
            android:gravity="center_vertical"
            android:background="@drawable/settings_item_background"
            android:layout_marginBottom="16dp">

            <ImageView
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:src="@drawable/ic_public"
                android:contentDescription="@null"
                app:tint="?attr/colorOnSurface"
                android:layout_marginEnd="16dp" />

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="仅 WiFi 下同步"
                    android:textAppearance="?attr/textAppearanceTitleMedium"
                    android:textColor="?attr/colorOnSurface" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="节省移动数据流量"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    android:textColor="?attr/colorOnSurfaceVariant"
                    android:layout_marginTop="2dp" />
            </LinearLayout>

            <com.google.android.material.materialswitch.MaterialSwitch
                android:id="@+id/switch_wifi_only"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
        </LinearLayout>

        <!-- 同步状态信息卡片 -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="同步状态"
                    android:textAppearance="?attr/textAppearanceTitleMedium"
                    android:textColor="?attr/colorOnSurface"
                    android:layout_marginBottom="12dp" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:layout_marginBottom="8dp">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="最后同步"
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurfaceVariant" />

                    <TextView
                        android:id="@+id/tv_last_sync_time"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="从未同步"
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurface" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:layout_marginBottom="8dp">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="本地版本"
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurfaceVariant" />

                    <TextView
                        android:id="@+id/tv_client_version"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="-"
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurface" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="云端版本"
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurfaceVariant" />

                    <TextView
                        android:id="@+id/tv_server_version"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="-"
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurface" />
                </LinearLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- 手动同步按钮 -->
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_manual_sync"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="立即同步"
            android:layout_marginBottom="16dp"
            app:icon="@drawable/ic_autorenew"
            style="@style/Widget.Material3.Button.OutlinedButton" />

    </LinearLayout>

</androidx.core.widget.NestedScrollView>
```

**时间**: 5 分钟

---

### 任务 2.3：创建同步设置 Fragment

**文件路径**: `app/src/main/java/com/ttt/safevault/ui/SyncSettingsFragment.java`

**完整代码**:

```java
package com.ttt.safevault.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;
import com.ttt.safevault.databinding.FragmentSyncSettingsBinding;
import com.ttt.safevault.sync.SyncConfig;
import com.ttt.safevault.sync.SyncStateManager;
import com.ttt.safevault.sync.SyncStatus;
import com.ttt.safevault.sync.SyncState;
import com.ttt.safevault.viewmodel.ViewModelFactory;

/**
 * 云端同步设置 Fragment
 */
public class SyncSettingsFragment extends BaseFragment {

    private FragmentSyncSettingsBinding binding;
    private SyncStateManager syncStateManager;
    private ViewModelFactory viewModelFactory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSyncSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        syncStateManager = SyncStateManager.getInstance();
        viewModelFactory = new ViewModelFactory(requireActivity().getApplication());

        setupClickListeners();
        observeSyncState();
        observeSyncConfig();
    }

    private void setupClickListeners() {
        // 同步开关
        binding.switchSyncEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SyncConfig config = syncStateManager.getCurrentConfig();
            if (config != null) {
                config.setSyncEnabled(isChecked);
                syncStateManager.updateConfig(config);
            }
        });

        // WiFi 限制开关
        binding.switchWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SyncConfig config = syncStateManager.getCurrentConfig();
            if (config != null) {
                config.setWifiOnly(isChecked);
                syncStateManager.updateConfig(config);
            }
        });

        // 同步间隔选择
        binding.cardSyncInterval.setOnClickListener(v -> showSyncIntervalDialog());

        // 手动同步
        binding.btnManualSync.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "开始同步...", Toast.LENGTH_SHORT).show();
            // TODO: 调用同步管理器执行同步
        });
    }

    private void showSyncIntervalDialog() {
        SyncConfig config = syncStateManager.getCurrentConfig();
        int currentInterval = config != null ? config.getSyncIntervalMinutes() : SyncConfig.INTERVAL_30_MINUTES;

        String[] intervals = {"30 分钟", "1 小时", "2 小时", "4 小时", "仅手动同步"};
        int[] values = {
            SyncConfig.INTERVAL_30_MINUTES,
            SyncConfig.INTERVAL_1_HOUR,
            SyncConfig.INTERVAL_2_HOURS,
            SyncConfig.INTERVAL_4_HOURS,
            SyncConfig.INTERVAL_MANUAL_ONLY
        };

        int checkedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentInterval) {
                checkedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择同步间隔")
            .setSingleChoiceItems(intervals, checkedIndex, (dialog, which) -> {
                if (config != null) {
                    config.setSyncIntervalMinutes(values[which]);
                    syncStateManager.updateConfig(config);
                    updateIntervalText(values[which]);
                }
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void updateIntervalText(int intervalMinutes) {
        String text;
        if (intervalMinutes == SyncConfig.INTERVAL_MANUAL_ONLY) {
            text = "仅手动同步";
        } else if (intervalMinutes < 60) {
            text = intervalMinutes + " 分钟";
        } else {
            text = (intervalMinutes / 60) + " 小时";
        }
        binding.tvSyncIntervalValue.setText(text);
    }

    private void observeSyncState() {
        syncStateManager.getSyncState().observe(getViewLifecycleOwner(), this::updateSyncStateUI);
    }

    private void observeSyncConfig() {
        syncStateManager.getSyncConfig().observe(getViewLifecycleOwner(), this::updateSyncConfigUI);
    }

    private void updateSyncStateUI(SyncState state) {
        if (state == null) return;

        // 更新状态信息
        if (state.getLastSyncTime() != null) {
            binding.tvLastSyncTime.setText(formatSyncTime(state.getLastSyncTime()));
        } else {
            binding.tvLastSyncTime.setText("从未同步");
        }

        if (state.getClientVersion() != null) {
            binding.tvClientVersion.setText(String.valueOf(state.getClientVersion()));
        }

        if (state.getServerVersion() != null) {
            binding.tvServerVersion.setText(String.valueOf(state.getServerVersion()));
        }
    }

    private void updateSyncConfigUI(SyncConfig config) {
        if (config == null) return;

        binding.switchSyncEnabled.setChecked(config.isSyncEnabled());
        binding.switchWifiOnly.setChecked(config.isWifiOnly());
        updateIntervalText(config.getSyncIntervalMinutes());
    }

    private String formatSyncTime(String timeString) {
        // 简单格式化时间
        try {
            long time = Long.parseLong(timeString);
            long now = System.currentTimeMillis();
            long diff = now - time;

            if (diff < 60000) {
                return "刚刚";
            } else if (diff < 3600000) {
                return (diff / 60000) + " 分钟前";
            } else if (diff < 86400000) {
                return (diff / 3600000) + " 小时前";
            } else {
                return (diff / 86400000) + " 天前";
            }
        } catch (Exception e) {
            return timeString;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
```

**时间**: 5 分钟

---

### 任务 2.4：创建同步图标drawable

**文件路径**: `app/src/main/res/drawable/ic_cloud_done.xml`

**完整代码**:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM10,17l-3.5,-3.5 1.41,-1.41L10,14.17l4.59,-4.59L16,11l-6,6z"/>
</vector>
```

**文件路径**: `app/src/main/res/drawable/ic_sync.xml`

**完整代码**:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,5l-5,5 5,5V5z"/>
</vector>
```

**文件路径**: `app/src/main/res/drawable/ic_cloud_off.xml`

**完整代码**:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM10,17l-3.5,-3.5 1.41,-1.41L10,14.17l4.59,-4.59L16,11l-6,6z"/>
    <path
        android:strokeColor="@android:color/white"
        android:strokeWidth="2"
        android:pathData="M2,2 L22,22"/>
</vector>
```

**时间**: 3 分钟

---

### 任务 2.5：添加字符串资源

**文件路径**: `app/src/main/res/values/strings.xml`

**添加以下字符串**:

```xml
<!-- 云端同步 -->
<string name="cloud_sync">云端同步</string>
<string name="sync_enabled">自动同步密码到云端</string>
<string name="sync_interval">同步间隔</string>
<string name="wifi_only_sync">仅 WiFi 下同步</string>
<string name="wifi_only_sync_summary">节省移动数据流量</string>
<string name="manual_sync">立即同步</string>
<string name="sync_status">同步状态</string>
<string name="last_sync">最后同步</string>
<string name="client_version">本地版本</string>
<string name="server_version">云端版本</string>
<string name="never_synced">从未同步</string>
<string name="syncing">同步中...</string>
<string name="sync_success">同步成功</string>
<string name="sync_failed">同步失败</string>
<string name="sync_conflict">检测到冲突</string>
<string name="sync_offline">离线模式</string>
<string name="sync_just_now">刚刚</string>
<string name="sync_minutes_ago">%d 分钟前</string>
<string name="sync_hours_ago">%d 小时前</string>
<string name="sync_days_ago">%d 天前</string>
<string name="sync_interval_30_minutes">30 分钟</string>
<string name="sync_interval_1_hour">1 小时</string>
<string name="sync_interval_2_hours">2 小时</string>
<string name="sync_interval_4_hours">4 小时</string>
<string name="sync_interval_manual_only">仅手动同步</string>
<string name="select_sync_interval">选择同步间隔</string>
<string name="keep_local">保留本地版本</string>
<string name="keep_server">保留云端版本</string>
<string name="conflict_detected">检测到冲突</string>
<string name="conflict_message">本地和云端都有修改，请选择保留哪个版本</string>
```

**时间**: 2 分钟

---

### 任务 2.6：添加导航配置

**文件路径**: `app/src/main/res/navigation/main_nav_graph.xml`

**在设置 fragment 中添加 action**（在现有的 actions 后面）:

```xml
<action
    android:id="@+id/action_settings_to_syncSettings"
    app:destination="@id/syncSettingsFragment"
    app:enterAnim="@anim/slide_in_right"
    app:exitAnim="@anim/slide_out_left"
    app:popEnterAnim="@anim/slide_in_left"
    app:popExitAnim="@anim/slide_out_right" />
```

**添加新的 fragment**（在 aboutFragment 后面）:

```xml
<!-- 云端同步设置页面 -->
<fragment
    android:id="@+id/syncSettingsFragment"
    android:name="com.ttt.safevault.ui.SyncSettingsFragment"
    android:label="云端同步"
    tools:layout="@layout/fragment_sync_settings" />
```

**时间**: 2 分钟

---

## 第三阶段：集成到现有系统

### 任务 3.1：在设置页面添加同步选项入口

**文件路径**: `app/src/main/res/layout/fragment_settings.xml`

**修改内容**: 在"分享历史"卡片后添加"云端同步"卡片

**添加以下代码**（在 card_share_history 后面）:

```xml
<!-- 云端同步卡片 -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/card_cloud_sync"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="16dp"
    android:clickable="true"
    android:focusable="true"
    app:cardCornerRadius="12dp"
    app:cardElevation="2dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:gravity="center_vertical">

        <!-- 图标 -->
        <ImageView
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@drawable/ic_cloud_done"
            android:contentDescription="@null"
            app:tint="?attr/colorPrimary"
            android:layout_marginEnd="16dp" />

        <!-- 文本区域 -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/cloud_sync"
                android:textAppearance="?attr/textAppearanceTitleMedium"
                android:textColor="?attr/colorOnSurface" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/sync_enabled"
                android:textAppearance="?attr/textAppearanceBodyMedium"
                android:textColor="?attr/colorOnSurfaceVariant"
                android:layout_marginTop="2dp" />
        </LinearLayout>

        <!-- 导航箭头 -->
        <ImageView
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@drawable/ic_navigate_next"
            android:contentDescription="@null"
            app:tint="?attr/colorOnSurfaceVariant" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

**时间**: 3 分钟

---

### 任务 3.2：在 SettingsFragment 中添加点击处理

**文件路径**: `app/src/main/java/com/ttt/safevault/ui/SettingsFragment.java`

**修改内容**: 在 `setupClickListeners()` 方法中添加同步卡片点击处理

**在方法中添加**:

```java
// 云端同步
binding.cardCloudSync.setOnClickListener(v ->
        Navigation.findNavController(v).navigate(R.id.action_settings_to_syncSettings));
```

**时间**: 1 分钟

---

## 测试步骤

### 1. 编译测试

```bash
cd E:\Android\SafeVault
./gradlew assembleDebug
```

**预期结果**: 编译成功，无错误

---

### 2. UI 测试

1. **启动应用**
   - 打开 SafeVault 应用
   - 导航到"设置"页面

2. **验证同步选项入口**
   - 确认"云端同步"卡片显示在设置页面
   - 点击卡片，确认能打开同步设置页面

3. **验证同步设置页面**
   - 确认同步开关可以切换
   - 确认 WiFi 限制开关可以切换
   - 点击同步间隔，确认弹出选择对话框
   - 确认手动同步按钮可点击

---

### 3. 功能测试（基础）

1. **状态管理测试**
   - 切换同步开关，观察状态变化
   - 修改同步间隔，确认值正确保存

2. **UI 响应测试**
   - 观察状态指示器显示正确
   - 确认最后同步时间正确显示

---

### 4. 数据库迁移测试

1. **数据库版本升级**
   - 卸载旧版本应用
   - 安装新版本应用
   - 确认数据库从版本 4 成功迁移到版本 5
   - 确认 sync_operations 表已创建

---

## 依赖清单

### 新增文件列表

| 文件路径 | 说明 |
|----------|------|
| `app/src/main/java/com/ttt/safevault/sync/SyncStatus.java` | 同步状态枚举 |
| `app/src/main/java/com/ttt/safevault/sync/SyncConfig.java` | 同步配置类 |
| `app/src/main/java/com/ttt/safevault/sync/SyncState.java` | 同步状态数据 |
| `app/src/main/java/com/ttt/safevault/sync/SyncStateManager.java` | 同步状态管理器 |
| `app/src/main/java/com/ttt/safevault/data/SyncOperationEntity.java` | 离线操作实体 |
| `app/src/main/java/com/ttt/safevault/data/SyncOperationDao.java` | 离线操作 DAO |
| `app/src/main/java/com/ttt/safevault/ui/SyncSettingsFragment.java` | 同步设置 Fragment |
| `app/src/main/res/layout/sync_status_indicator.xml` | 状态指示器布局 |
| `app/src/main/res/layout/fragment_sync_settings.xml` | 同步设置页面布局 |
| `app/src/main/res/drawable/ic_cloud_done.xml` | 云端完成图标 |
| `app/src/main/res/drawable/ic_sync.xml` | 同步图标 |
| `app/src/main/res/drawable/ic_cloud_off.xml` | 云端离线图标 |

### 修改文件列表

| 文件路径 | 修改内容 |
|----------|----------|
| `app/src/main/java/com/ttt/safevault/data/AppDatabase.java` | 添加 SyncOperationEntity，版本升级到 5，添加 MIGRATION_4_5 |
| `app/src/main/res/layout/fragment_settings.xml` | 添加云端同步卡片 |
| `app/src/main/java/com/ttt/safevault/ui/SettingsFragment.java` | 添加同步卡片点击处理 |
| `app/src/main/res/navigation/main_nav_graph.xml` | 添加 syncSettingsFragment 和 action |
| `app/src/main/res/values/strings.xml` | 添加同步相关字符串 |

---

## 后续工作（不在本次计划内）

以下功能需要在后续版本中实现：

1. **VaultSyncManager** - 核心同步管理器
2. **SyncScheduler** - 定时同步调度器
3. **SyncConflictResolver** - 冲突解决器
4. **SyncOfflineQueue** - 离线队列管理
5. **SyncConflictDialog** - 冲突解决对话框
6. **MainActivity 集成** - 在主页面添加同步状态指示器
7. **网络状态监听** - 网络恢复时自动同步
8. **WorkManager 集成** - 后台定时同步任务

---

## 注意事项

1. **数据库迁移**: 确保现有用户数据在升级后不受影响
2. **线程安全**: SyncStateManager 使用单例模式，注意多线程访问
3. **网络状态**: 后续需要添加网络状态监听
4. **错误处理**: 网络请求需要添加完善的错误处理和重试机制
5. **版本控制**: 客户端版本号需要在首次同步时初始化

---

**文档结束**
