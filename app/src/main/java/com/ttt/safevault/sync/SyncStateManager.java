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
