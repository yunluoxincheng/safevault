package com.ttt.safevault.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.service.manager.EncryptionSyncManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 密码库同步管理器（单例模式）
 * 负责协调 EncryptionSyncManager 和 SyncStateManager，提供同步功能的统一接口
 */
public class VaultSyncManager {

    private static final String TAG = "VaultSyncManager";

    /**
     * 同步策略枚举
     */
    public enum SyncStrategy {
        USE_CLOUD,   // 使用云端数据（覆盖本地）
        USE_LOCAL,   // 保留本地数据（覆盖云端）
        CANCEL       // 取消同步
    }

    /**
     * 同步回调接口
     */
    public interface SyncCallback {
        /**
         * 同步成功
         * @param newVersion 新版本号
         */
        void onSyncSuccess(long newVersion);

        /**
         * 发生冲突
         * @param cloudVersion 云端版本号
         * @param localVersion 本地版本号
         */
        void onSyncConflict(long cloudVersion, long localVersion);

        /**
         * 同步失败
         * @param errorMessage 错误消息
         */
        void onSyncFailure(String errorMessage);
    }

    private static volatile VaultSyncManager instance;

    private final Context context;
    private final EncryptionSyncManager encryptionSyncManager;
    private final SyncStateManager syncStateManager;
    private final RetrofitClient retrofitClient;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    /**
     * 私有构造函数
     */
    private VaultSyncManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.retrofitClient = RetrofitClient.getInstance(this.context);
        this.encryptionSyncManager = new EncryptionSyncManager(this.context, this.retrofitClient);
        this.syncStateManager = SyncStateManager.getInstance();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "VaultSyncManager initialized");
    }

    /**
     * 获取单例实例
     */
    public static VaultSyncManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (VaultSyncManager.class) {
                if (instance == null) {
                    instance = new VaultSyncManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * 手动触发同步
     *
     * @param callback 同步回调
     */
    public void syncNow(@Nullable final SyncCallback callback) {
        Log.d(TAG, "Manual sync triggered");

        // 更新同步状态为同步中
        syncStateManager.updateStatus(SyncStatus.SYNCING);

        // 在后台线程执行同步
        executorService.execute(() -> {
            try {
                // 调用 EncryptionSyncManager 执行同步
                EncryptionSyncManager.SyncResult result = encryptionSyncManager.syncVaultData();

                // 在主线程处理结果
                mainHandler.post(() -> handleSyncResult(result, callback));

            } catch (Exception e) {
                Log.e(TAG, "Sync failed with exception", e);
                mainHandler.post(() -> {
                    SyncState currentState = syncStateManager.getCurrentState();
                    if (currentState != null) {
                        currentState.setStatus(SyncStatus.FAILED);
                        currentState.setErrorMessage("同步异常：" + e.getMessage());
                        syncStateManager.updateSyncState(currentState);
                    }
                    if (callback != null) {
                        callback.onSyncFailure("同步异常：" + e.getMessage());
                    }
                });
            }
        });
    }

    /**
     * 解决同步冲突
     *
     * @param strategy  同步策略
     * @param callback  同步回调
     */
    public void resolveConflict(@NonNull SyncStrategy strategy, @Nullable final SyncCallback callback) {
        Log.d(TAG, "Resolving conflict with strategy: " + strategy);

        // 更新同步状态为同步中
        syncStateManager.updateStatus(SyncStatus.SYNCING);

        // 在后台线程执行冲突解决
        executorService.execute(() -> {
            EncryptionSyncManager.SyncResult result;

            try {
                switch (strategy) {
                    case USE_CLOUD:
                        // 使用云端数据覆盖本地
                        result = encryptionSyncManager.useCloudData();
                        break;

                    case USE_LOCAL:
                        // 上传本地数据覆盖云端
                        result = encryptionSyncManager.uploadLocalVaultData();
                        break;

                    case CANCEL:
                        // 取消同步
                        Log.d(TAG, "Sync cancelled by user");
                        mainHandler.post(() -> {
                            syncStateManager.updateStatus(SyncStatus.IDLE);
                            if (callback != null) {
                                callback.onSyncFailure("用户取消同步");
                            }
                        });
                        return;

                    default:
                        result = EncryptionSyncManager.SyncResult.failure("未知的同步策略");
                        break;
                }

                // 在主线程处理结果
                mainHandler.post(() -> handleSyncResult(result, callback));

            } catch (Exception e) {
                Log.e(TAG, "Conflict resolution failed", e);
                mainHandler.post(() -> {
                    SyncState currentState = syncStateManager.getCurrentState();
                    if (currentState != null) {
                        currentState.setStatus(SyncStatus.FAILED);
                        currentState.setErrorMessage("冲突解决失败：" + e.getMessage());
                        syncStateManager.updateSyncState(currentState);
                    }
                    if (callback != null) {
                        callback.onSyncFailure("冲突解决失败：" + e.getMessage());
                    }
                });
            }
        });
    }

    /**
     * 获取当前同步状态
     *
     * @return 当前同步状态
     */
    @Nullable
    public SyncState getCurrentSyncState() {
        return syncStateManager.getCurrentState();
    }

    /**
     * 更新同步配置
     *
     * @param config 新的同步配置
     */
    public void updateSyncConfig(@NonNull SyncConfig config) {
        Log.d(TAG, "Updating sync config: enabled=" + config.isSyncEnabled() +
                   ", interval=" + config.getSyncIntervalMinutes() +
                   ", wifiOnly=" + config.isWifiOnly());
        syncStateManager.updateConfig(config);
    }

    /**
     * 获取当前同步配置
     *
     * @return 当前同步配置
     */
    @Nullable
    public SyncConfig getCurrentSyncConfig() {
        return syncStateManager.getCurrentConfig();
    }

    /**
     * 处理同步结果
     *
     * @param result   同步结果
     * @param callback 回调接口
     */
    private void handleSyncResult(@NonNull EncryptionSyncManager.SyncResult result,
                                  @Nullable SyncCallback callback) {
        SyncState currentState = syncStateManager.getCurrentState();

        if (currentState == null) {
            currentState = new SyncState();
        }

        if (result.isSuccess()) {
            // 同步成功
            Log.d(TAG, "Sync successful, new version: " + result.getNewVersion());
            currentState.setStatus(SyncStatus.SUCCESS);
            currentState.setClientVersion(result.getNewVersion());
            currentState.setServerVersion(result.getNewVersion());
            currentState.setErrorMessage(null);
            currentState.setLastSyncTime(String.valueOf(System.currentTimeMillis()));

            syncStateManager.updateSyncState(currentState);

            if (callback != null) {
                callback.onSyncSuccess(result.getNewVersion());
            }

            // 延迟后恢复到 IDLE 状态
            mainHandler.postDelayed(() -> {
                syncStateManager.updateStatus(SyncStatus.IDLE);
            }, 2000);

        } else {
            // 检查是否是冲突
            String message = result.getMessage();
            if (message != null && message.contains("数据冲突")) {
                // 数据冲突
                Log.w(TAG, "Sync conflict detected: " + message);
                currentState.setStatus(SyncStatus.CONFLICT);
                currentState.setErrorMessage(message);

                syncStateManager.updateSyncState(currentState);

                if (callback != null) {
                    // 尝试从消息中提取版本号
                    long cloudVersion = 0;
                    long localVersion = 0;

                    try {
                        // 消息格式: "数据冲突：云端版本 X，本地版本 Y"
                        String[] parts = message.split("，本地版本 ");
                        if (parts.length == 2) {
                            String cloudPart = parts[0].replace("数据冲突：云端版本 ", "");
                            String localPart = parts[1].trim();
                            cloudVersion = Long.parseLong(cloudPart);
                            localVersion = Long.parseLong(localPart);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse version numbers from message", e);
                    }

                    callback.onSyncConflict(cloudVersion, localVersion);
                }

            } else {
                // 同步失败
                Log.e(TAG, "Sync failed: " + message);
                currentState.setStatus(SyncStatus.FAILED);
                currentState.setErrorMessage(message);

                syncStateManager.updateSyncState(currentState);

                if (callback != null) {
                    callback.onSyncFailure(message);
                }

                // 延迟后恢复到 IDLE 状态
                mainHandler.postDelayed(() -> {
                    syncStateManager.updateStatus(SyncStatus.IDLE);
                }, 3000);
            }
        }
    }

    /**
     * 关闭执行器（应用退出时调用）
     */
    public void shutdown() {
        Log.d(TAG, "Shutting down VaultSyncManager");
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
}
