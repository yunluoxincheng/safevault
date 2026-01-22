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
