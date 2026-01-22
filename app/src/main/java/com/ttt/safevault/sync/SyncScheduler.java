package com.ttt.safevault.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * 密码库同步调度器（单例模式）
 * 负责根据用户配置调度自动同步任务
 */
public class SyncScheduler {

    private static final String TAG = "SyncScheduler";
    private static final String WORK_NAME = "vault_sync_work";

    /**
     * WorkManager 最小间隔时间限制（15分钟）
     */
    private static final long MIN_INTERVAL_MINUTES = 15;

    private static volatile SyncScheduler instance;

    private final Context context;
    private final VaultSyncManager vaultSyncManager;
    private final SyncStateManager syncStateManager;
    private final WorkManager workManager;
    private final ConnectivityManager connectivityManager;

    /**
     * 私有构造函数
     */
    private SyncScheduler(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.vaultSyncManager = VaultSyncManager.getInstance(context);
        this.syncStateManager = SyncStateManager.getInstance();
        this.workManager = WorkManager.getInstance(context);
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        Log.d(TAG, "SyncScheduler initialized");
    }

    /**
     * 获取单例实例
     */
    public static SyncScheduler getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SyncScheduler.class) {
                if (instance == null) {
                    instance = new SyncScheduler(context);
                }
            }
        }
        return instance;
    }

    /**
     * 根据当前配置调度同步
     */
    public void scheduleSync() {
        SyncConfig config = syncStateManager.getCurrentConfig();
        if (config == null) {
            Log.w(TAG, "SyncConfig is null, using default config");
            config = new SyncConfig();
        }

        if (!config.isSyncEnabled()) {
            Log.d(TAG, "Sync is disabled in config, skipping schedule");
            return;
        }

        int intervalMinutes = config.getSyncIntervalMinutes();

        // 检查是否为手动同步模式
        if (intervalMinutes == SyncConfig.INTERVAL_MANUAL_ONLY) {
            Log.d(TAG, "Manual sync mode, no automatic scheduling");
            return;
        }

        // WorkManager 最小间隔为 15 分钟
        if (intervalMinutes < MIN_INTERVAL_MINUTES) {
            Log.w(TAG, "Interval " + intervalMinutes + " minutes is below minimum, using " + MIN_INTERVAL_MINUTES + " minutes");
            intervalMinutes = (int) MIN_INTERVAL_MINUTES;
        }

        Log.d(TAG, "Scheduling sync every " + intervalMinutes + " minutes, WiFi only: " + config.isWifiOnly());

        // 构建约束条件
        Constraints.Builder constraintsBuilder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);

        // 如果配置要求仅 WiFi，设置网络类型为未计费（WiFi 或 以太网）
        if (config.isWifiOnly()) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED);
            Log.d(TAG, "WiFi-only mode enabled");
        }

        Constraints constraints = constraintsBuilder.build();

        // 创建周期性工作请求
        PeriodicWorkRequest syncWorkRequest = new PeriodicWorkRequest.Builder(
                SyncWorker.class,
                intervalMinutes,
                TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .build();

        // 使用 REPLACE 策略确保使用最新配置
        workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                syncWorkRequest
        );

        Log.d(TAG, "Sync scheduled successfully");
    }

    /**
     * 取消所有已调度的同步
     */
    public void cancelScheduledSync() {
        Log.d(TAG, "Canceling scheduled sync");
        workManager.cancelUniqueWork(WORK_NAME);
        Log.d(TAG, "Scheduled sync canceled");
    }

    /**
     * 重新调度同步（先取消再调度）
     */
    public void rescheduleSync() {
        Log.d(TAG, "Rescheduling sync");
        cancelScheduledSync();

        // 短暂延迟以确保取消完成
        workManager.pruneWork();
        scheduleSync();
    }

    /**
     * 手动触发同步（带条件检查）
     * 检查同步是否启用、网络是否可用、WiFi 限制等
     */
    public void syncNowIfAllowed() {
        SyncConfig config = syncStateManager.getCurrentConfig();
        if (config == null) {
            Log.w(TAG, "SyncConfig is null, cannot check sync permissions");
            return;
        }

        // 检查同步是否启用
        if (!config.isSyncEnabled()) {
            Log.d(TAG, "Sync is disabled in config");
            return;
        }

        // 检查网络连接
        if (!isNetworkAvailable()) {
            Log.d(TAG, "Network not available, skipping sync");
            return;
        }

        // 检查 WiFi 限制
        if (config.isWifiOnly() && !isWifiConnected()) {
            Log.d(TAG, "WiFi-only mode enabled but not on WiFi, skipping sync");
            return;
        }

        // 所有条件满足，执行同步
        Log.d(TAG, "All sync conditions met, triggering sync now");
        vaultSyncManager.syncNow(new VaultSyncManager.SyncCallback() {
            @Override
            public void onSyncSuccess(long newVersion) {
                Log.d(TAG, "Manual sync successful, new version: " + newVersion);
            }

            @Override
            public void onSyncConflict(long cloudVersion, long localVersion) {
                Log.w(TAG, "Manual sync conflict: cloud=" + cloudVersion + ", local=" + localVersion);
            }

            @Override
            public void onSyncFailure(String errorMessage) {
                Log.e(TAG, "Manual sync failed: " + errorMessage);
            }
        });
    }

    /**
     * 检查网络是否可用
     */
    private boolean isNetworkAvailable() {
        if (connectivityManager == null) {
            Log.e(TAG, "ConnectivityManager is null");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } else {
            // Android 5.0 - 5.1 (API 21-22) 兼容处理
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }

    /**
     * 检查是否连接到 WiFi
     */
    private boolean isWifiConnected() {
        if (connectivityManager == null) {
            Log.e(TAG, "ConnectivityManager is null");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            // Android 5.0 - 5.1 (API 21-22) 兼容处理
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null
                    && networkInfo.isConnected()
                    && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        }
    }

    /**
     * 同步 Worker 类
     * 由 WorkManager 在后台执行同步任务
     */
    public static class SyncWorker extends Worker {

        private static final String TAG = "SyncWorker";

        public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            Log.d(TAG, "SyncWorker started");

            // 获取 SyncScheduler 实例（通过 Context）
            SyncScheduler syncScheduler = SyncScheduler.getInstance(getApplicationContext());
            SyncConfig config = syncScheduler.syncStateManager.getCurrentConfig();

            if (config == null) {
                Log.w(TAG, "SyncConfig is null, skipping sync");
                return Result.success();
            }

            // 再次检查同步是否启用（配置可能在调度后被修改）
            if (!config.isSyncEnabled()) {
                Log.d(TAG, "Sync disabled in config, worker will not run again");
                return Result.success();
            }

            // 检查网络（WorkManager 的约束已确保网络可用，但再次检查以防万一）
            if (!syncScheduler.isNetworkAvailable()) {
                Log.d(TAG, "Network not available, retrying later");
                return Result.retry();
            }

            // 执行同步
            final boolean[] syncSuccess = new boolean[1];
            final String[] syncError = new String[1];
            final boolean[] waitingForResult = new boolean[]{true};

            // 创建一个简单的同步等待机制
            VaultSyncManager vaultSyncManager = VaultSyncManager.getInstance(getApplicationContext());

            vaultSyncManager.syncNow(new VaultSyncManager.SyncCallback() {
                @Override
                public void onSyncSuccess(long newVersion) {
                    Log.d(TAG, "Scheduled sync successful, new version: " + newVersion);
                    syncSuccess[0] = true;
                    waitingForResult[0] = false;
                }

                @Override
                public void onSyncConflict(long cloudVersion, long localVersion) {
                    Log.w(TAG, "Scheduled sync conflict: cloud=" + cloudVersion + ", local=" + localVersion);
                    // 后台同步遇到冲突时，自动解决：
                    // - 如果本地较新（localVersion > cloudVersion），使用本地数据
                    // - 如果云端较新（cloudVersion > localVersion），使用云端数据
                    VaultSyncManager.SyncStrategy strategy;
                    if (localVersion > cloudVersion) {
                        Log.d(TAG, "Local is newer, using USE_LOCAL strategy");
                        strategy = VaultSyncManager.SyncStrategy.USE_LOCAL;
                    } else {
                        Log.d(TAG, "Cloud is newer, using USE_CLOUD strategy");
                        strategy = VaultSyncManager.SyncStrategy.USE_CLOUD;
                    }

                    // 注意：不要在这里设置 waitingForResult[0] = false
                    // 让它保持 true，等待 resolveConflict 的回调来设置
                    // 自动解决冲突
                    vaultSyncManager.resolveConflict(strategy, new VaultSyncManager.SyncCallback() {
                        @Override
                        public void onSyncSuccess(long newVersion) {
                            Log.d(TAG, "Auto-resolved conflict successful, new version: " + newVersion);
                            syncSuccess[0] = true;
                            waitingForResult[0] = false;  // 现在才设置 false
                        }

                        @Override
                        public void onSyncConflict(long cloudV, long localV) {
                            // 理论上不应该再次冲突，但以防万一
                            Log.e(TAG, "Unexpected conflict after auto-resolve");
                            syncSuccess[0] = false;
                            waitingForResult[0] = false;
                        }

                        @Override
                        public void onSyncFailure(String errorMessage) {
                            Log.e(TAG, "Auto-resolve conflict failed: " + errorMessage);
                            syncSuccess[0] = false;
                            waitingForResult[0] = false;
                        }
                    });
                }

                @Override
                public void onSyncFailure(String errorMessage) {
                    Log.e(TAG, "Scheduled sync failed: " + errorMessage);
                    syncError[0] = errorMessage;
                    syncSuccess[0] = false;
                    waitingForResult[0] = false;
                }
            });

            // 等待同步完成（最多等待 30 秒）
            int maxWaitSeconds = 30;
            int waitedSeconds = 0;
            try {
                while (waitingForResult[0] && waitedSeconds < maxWaitSeconds) {
                    Thread.sleep(1000);
                    waitedSeconds++;
                }

                if (waitingForResult[0]) {
                    Log.w(TAG, "Sync timeout after " + maxWaitSeconds + " seconds");
                    return Result.retry();
                }

                if (syncSuccess[0]) {
                    Log.d(TAG, "SyncWorker completed successfully");
                    return Result.success();
                } else {
                    Log.e(TAG, "SyncWorker failed: " + syncError[0]);
                    // 失败后重试
                    return Result.retry();
                }

            } catch (InterruptedException e) {
                Log.e(TAG, "SyncWorker interrupted", e);
                Thread.currentThread().interrupt();
                return Result.failure();
            }
        }
    }

    /**
     * 销毁调度器（应用退出时调用）
     */
    public void destroy() {
        Log.d(TAG, "Destroying SyncScheduler");
        // WorkManager 会自动管理，不需要手动清理
        instance = null;
    }
}
