package com.ttt.safevault.dto.request;

/**
 * 密码库同步请求
 */
public class VaultSyncRequest {

    /**
     * 加密的密码库数据（Base64 编码）
     */
    private String encryptedData;

    /**
     * IV（初始化向量，Base64 编码）
     */
    private String dataIv;

    /**
     * GCM 认证标签（Base64 编码）
     */
    private String dataAuthTag;

    /**
     * 客户端版本号（用于冲突检测）
     */
    private Long clientVersion;

    /**
     * 强制同步标志（为 true 时覆盖服务器数据）
     */
    private boolean forceSync;

    public VaultSyncRequest() {
    }

    public VaultSyncRequest(String encryptedData, String dataIv, String dataAuthTag, Long clientVersion, boolean forceSync) {
        this.encryptedData = encryptedData;
        this.dataIv = dataIv;
        this.dataAuthTag = dataAuthTag;
        this.clientVersion = clientVersion;
        this.forceSync = forceSync;
    }

    public String getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }

    public String getDataIv() {
        return dataIv;
    }

    public void setDataIv(String dataIv) {
        this.dataIv = dataIv;
    }

    public String getDataAuthTag() {
        return dataAuthTag;
    }

    public void setDataAuthTag(String dataAuthTag) {
        this.dataAuthTag = dataAuthTag;
    }

    public Long getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(Long clientVersion) {
        this.clientVersion = clientVersion;
    }

    public boolean isForceSync() {
        return forceSync;
    }

    public void setForceSync(boolean forceSync) {
        this.forceSync = forceSync;
    }
}
