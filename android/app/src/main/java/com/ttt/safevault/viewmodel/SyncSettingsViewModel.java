package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.sync.SyncConfig;
import com.ttt.safevault.sync.SyncScheduler;
import com.ttt.safevault.sync.SyncState;
import com.ttt.safevault.sync.SyncStateManager;
import com.ttt.safevault.sync.VaultSyncManager;

public class SyncSettingsViewModel extends AndroidViewModel {

    private final SyncStateManager syncStateManager;
    private final VaultSyncManager vaultSyncManager;
    private final SyncScheduler syncScheduler;

    private final MutableLiveData<ConflictData> _conflict = new MutableLiveData<>(null);
    private final MutableLiveData<String> _message = new MutableLiveData<>(null);

    public LiveData<SyncState> syncState;
    public LiveData<SyncConfig> syncConfig;
    public LiveData<ConflictData> conflict = _conflict;
    public LiveData<String> message = _message;

    public SyncSettingsViewModel(@NonNull Application application) {
        super(application);
        Context ctx = application.getApplicationContext();
        this.syncStateManager = SyncStateManager.getInstance();
        this.vaultSyncManager = VaultSyncManager.getInstance(ctx);
        this.syncScheduler = SyncScheduler.getInstance(ctx);
        this.syncState = syncStateManager.getSyncState();
        this.syncConfig = syncStateManager.getSyncConfig();
    }

    public void updateSyncEnabled(boolean enabled) {
        SyncConfig config = syncStateManager.getCurrentConfig();
        if (config == null) return;
        config.setSyncEnabled(enabled);
        syncStateManager.updateConfig(config);
        if (enabled) {
            syncScheduler.scheduleSync();
            _message.postValue("自动同步已启用");
        } else {
            syncScheduler.cancelScheduledSync();
            _message.postValue("自动同步已禁用");
        }
    }

    public void updateWifiOnly(boolean wifiOnly) {
        SyncConfig config = syncStateManager.getCurrentConfig();
        if (config == null) return;
        config.setWifiOnly(wifiOnly);
        syncStateManager.updateConfig(config);
        syncScheduler.rescheduleSync();
        _message.postValue(wifiOnly ? "已启用仅 WiFi 同步" : "已允许使用移动数据同步");
    }

    public void updateInterval(int intervalMinutes) {
        SyncConfig config = syncStateManager.getCurrentConfig();
        if (config == null) return;
        config.setSyncIntervalMinutes(intervalMinutes);
        syncStateManager.updateConfig(config);
        syncScheduler.rescheduleSync();
    }

    public void performManualSync() {
        SyncConfig config = syncStateManager.getCurrentConfig();
        if (config != null && !config.isSyncEnabled()) {
            _message.postValue("请先启用同步功能");
            return;
        }

        _message.postValue("开始同步...");

        vaultSyncManager.syncNow(new VaultSyncManager.SyncCallback() {
            @Override
            public void onSyncSuccess(long newVersion) {
                _message.postValue("同步成功，版本: " + newVersion);
            }

            @Override
            public void onSyncConflict(long cloudVersion, long localVersion) {
                _conflict.postValue(new ConflictData(cloudVersion, localVersion));
            }

            @Override
            public void onSyncFailure(String errorMessage) {
                _message.postValue("同步失败: " + errorMessage);
            }
        });
    }

    private void resolveConflict(VaultSyncManager.SyncStrategy strategy) {
        _message.postValue("正在解决冲突...");

        vaultSyncManager.resolveConflict(strategy, new VaultSyncManager.SyncCallback() {
            @Override
            public void onSyncSuccess(long newVersion) {
                _message.postValue("冲突已解决，版本: " + newVersion);
            }

            @Override
            public void onSyncConflict(long cloudVersion, long localVersion) {
                _message.postValue("冲突解决失败");
            }

            @Override
            public void onSyncFailure(String errorMessage) {
                _message.postValue("冲突解决失败: " + errorMessage);
            }
        });
    }

    public void resolveWithCloud() {
        resolveConflict(VaultSyncManager.SyncStrategy.USE_CLOUD);
    }

    public void resolveWithLocal() {
        resolveConflict(VaultSyncManager.SyncStrategy.USE_LOCAL);
    }

    public void resolveCancel() {
        resolveConflict(VaultSyncManager.SyncStrategy.CANCEL);
    }

    public void clearMessage() {
        _message.setValue(null);
    }

    public void clearConflict() {
        _conflict.setValue(null);
    }

    public SyncConfig getCurrentConfig() {
        return syncStateManager.getCurrentConfig();
    }

    public static class ConflictData {
        public final long cloudVersion;
        public final long localVersion;

        public ConflictData(long cloudVersion, long localVersion) {
            this.cloudVersion = cloudVersion;
            this.localVersion = localVersion;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }
}
