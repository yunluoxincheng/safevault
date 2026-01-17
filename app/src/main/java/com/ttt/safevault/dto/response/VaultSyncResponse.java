package com.ttt.safevault.dto.response;

/**
 * 密码库同步响应
 */
public class VaultSyncResponse {

    /**
     * 同步是否成功
     */
    private boolean success;

    /**
     * 是否有冲突
     */
    private boolean hasConflict;

    /**
     * 冲突描述（当 hasConflict 为 true 时提供）
     */
    private String conflictMessage;

    /**
     * 服务器版本号（同步前）
     */
    private Long serverVersion;

    /**
     * 客户端版本号（同步前）
     */
    private Long clientVersion;

    /**
     * 新的版本号（同步后）
     */
    private Long newVersion;

    /**
     * 同步后的密码库数据
     */
    private VaultResponse vault;

    /**
     * 冲突时的服务器数据（供客户端合并）
     */
    private VaultResponse serverVault;

    /**
     * 最后同步时间
     */
    private String lastSyncedAt;

    public VaultSyncResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isHasConflict() {
        return hasConflict;
    }

    public void setHasConflict(boolean hasConflict) {
        this.hasConflict = hasConflict;
    }

    public String getConflictMessage() {
        return conflictMessage;
    }

    public void setConflictMessage(String conflictMessage) {
        this.conflictMessage = conflictMessage;
    }

    public Long getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(Long serverVersion) {
        this.serverVersion = serverVersion;
    }

    public Long getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(Long clientVersion) {
        this.clientVersion = clientVersion;
    }

    public Long getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(Long newVersion) {
        this.newVersion = newVersion;
    }

    public VaultResponse getVault() {
        return vault;
    }

    public void setVault(VaultResponse vault) {
        this.vault = vault;
    }

    public VaultResponse getServerVault() {
        return serverVault;
    }

    public void setServerVault(VaultResponse serverVault) {
        this.serverVault = serverVault;
    }

    public String getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(String lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
