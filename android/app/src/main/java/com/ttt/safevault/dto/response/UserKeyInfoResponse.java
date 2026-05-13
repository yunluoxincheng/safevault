package com.ttt.safevault.dto.response;

/**
 * 用户密钥信息响应 DTO
 * 与后端 GET /v1/users/{userId}/keys 对应，用于获取接收方公钥以进行端到端加密
 */
public class UserKeyInfoResponse {
    private String userId;
    private String rsaPublicKey;
    private String x25519PublicKey;
    private String ed25519PublicKey;
    private String keyVersion;

    public UserKeyInfoResponse() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRsaPublicKey() {
        return rsaPublicKey;
    }

    public void setRsaPublicKey(String rsaPublicKey) {
        this.rsaPublicKey = rsaPublicKey;
    }

    public String getX25519PublicKey() {
        return x25519PublicKey;
    }

    public void setX25519PublicKey(String x25519PublicKey) {
        this.x25519PublicKey = x25519PublicKey;
    }

    public String getEd25519PublicKey() {
        return ed25519PublicKey;
    }

    public void setEd25519PublicKey(String ed25519PublicKey) {
        this.ed25519PublicKey = ed25519PublicKey;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(String keyVersion) {
        this.keyVersion = keyVersion;
    }
}
