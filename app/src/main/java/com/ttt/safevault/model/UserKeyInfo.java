package com.ttt.safevault.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 用户密钥信息
 *
 * 用于版本协商，包含用户的所有公钥信息
 */
public class UserKeyInfo {
    // 用户 ID
    private String userId;

    // RSA 公钥（版本 2.0）- 可选
    private String rsaPublicKey;

    // X25519 公钥（版本 3.0）
    private String x25519PublicKey;

    // Ed25519 公钥（版本 3.0）
    private String ed25519PublicKey;

    // 当前活跃的密钥版本（"v2" 或 "v3"）
    private String keyVersion;

    /**
     * 默认构造函数
     */
    public UserKeyInfo() {
    }

    /**
     * 完整构造函数
     */
    public UserKeyInfo(
            @NonNull String userId,
            @Nullable String rsaPublicKey,
            @Nullable String x25519PublicKey,
            @Nullable String ed25519PublicKey,
            @NonNull String keyVersion
    ) {
        this.userId = userId;
        this.rsaPublicKey = rsaPublicKey;
        this.x25519PublicKey = x25519PublicKey;
        this.ed25519PublicKey = ed25519PublicKey;
        this.keyVersion = keyVersion;
    }

    // Getter 和 Setter 方法
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

    /**
     * 检查是否支持协议版本 3.0
     *
     * @return true 表示支持 v3.0
     */
    public boolean supportsV3() {
        return x25519PublicKey != null && !x25519PublicKey.isEmpty()
                && ed25519PublicKey != null && !ed25519PublicKey.isEmpty();
    }

    /**
     * 检查是否支持协议版本 2.0
     *
     * @return true 表示支持 v2.0
     */
    public boolean supportsV2() {
        return rsaPublicKey != null && !rsaPublicKey.isEmpty();
    }

    /**
     * 获取最佳的协议版本
     *
     * @return "3.0" 或 "2.0"
     */
    public String getBestProtocolVersion() {
        if (supportsV3()) {
            return "3.0";
        } else if (supportsV2()) {
            return "2.0";
        }
        throw new SecurityException("No supported protocol version available");
    }

    @Override
    public String toString() {
        return "UserKeyInfo{" +
                "userId='" + userId + '\'' +
                ", keyVersion='" + keyVersion + '\'' +
                ", supportsV3=" + supportsV3() +
                ", supportsV2=" + supportsV2() +
                '}';
    }
}