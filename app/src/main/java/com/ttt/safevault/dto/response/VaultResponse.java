package com.ttt.safevault.dto.response;

/**
 * 密码库响应
 */
public class VaultResponse {

    /**
     * 密码库 ID
     */
    private String vaultId;

    /**
     * 用户 ID
     */
    private String userId;

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
     * Salt（Base64 编码）
     * 用于 Argon2id 密钥派生
     */
    private String salt;

    /**
     * 版本号
     */
    private Long version;

    /**
     * 最后同步时间
     */
    private String lastSyncedAt;

    /**
     * 创建时间
     */
    private String createdAt;

    /**
     * 更新时间
     */
    private String updatedAt;

    public VaultResponse() {
    }

    public String getVaultId() {
        return vaultId;
    }

    public void setVaultId(String vaultId) {
        this.vaultId = vaultId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(String lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
