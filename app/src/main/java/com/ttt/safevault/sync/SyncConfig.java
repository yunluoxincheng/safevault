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
