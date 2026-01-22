package com.ttt.safevault.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * 同步触发器（单例）
 * 负责在不同场景下触发同步
 */
public class SyncTrigger {

    private static final String TAG = "SyncTrigger";
    private static final long DEBOUNCE_INTERVAL_MS = 30_000; // 30秒防抖动

    private static volatile SyncTrigger instance;
    private final Context context;
    private final SyncScheduler syncScheduler;
    private final SyncStateManager syncStateManager;
    private final Handler mainHandler;
    private long lastTriggerTime = 0;

    private SyncTrigger(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.syncScheduler = SyncScheduler.getInstance(context);
        this.syncStateManager = SyncStateManager.getInstance();
        this.mainHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "SyncTrigger initialized");
    }

    /**
     * 获取单例实例
     */
    public static SyncTrigger getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SyncTrigger.class) {
                if (instance == null) {
                    instance = new SyncTrigger(context);
                }
            }
        }
        return instance;
    }

    /**
     * 在应用解锁时触发同步
     * 带防抖动检查，30秒内只同步一次
     * @return 是否成功触发同步
     */
    public boolean triggerSyncOnUnlock() {
        // 防抖动检查
        if (!shouldTriggerWithDebounce()) {
            Log.d(TAG, "Sync on unlock skipped: debounce");
            return false;
        }

        return performSync("解锁");
    }

    /**
     * 在下拉刷新时触发同步
     * 不受防抖限制，用户主动刷新
     * @return 是否成功触发同步
     */
    public boolean triggerSyncOnRefresh() {
        return performSync("刷新");
    }

    /**
     * 检查是否应该触发同步（防抖动）
     */
    private boolean shouldTriggerWithDebounce() {
        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < DEBOUNCE_INTERVAL_MS) {
            return false;
        }
        lastTriggerTime = now;
        return true;
    }

    /**
     * 执行同步
     * @param source 触发来源（用于日志）
     * @return 是否成功触发同步
     */
    private boolean performSync(@NonNull String source) {
        SyncConfig config = syncStateManager.getCurrentConfig();
        if (config == null || !config.isSyncEnabled()) {
            Log.d(TAG, "Sync on " + source + " skipped: sync disabled");
            return false;
        }

        Log.d(TAG, "Triggering sync on " + source);
        syncScheduler.syncNowIfAllowed();
        return true;
    }
}
