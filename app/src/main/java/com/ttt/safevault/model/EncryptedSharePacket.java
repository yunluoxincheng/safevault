package com.ttt.safevault.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * 加密分享包
 * 包含加密后的密码数据和数字签名
 *
 * 版本 2.0 支持混合加密方案（RSA + AES）：
 * - encryptedData: AES-GCM 加密的分享数据
 * - encryptedAESKey: RSA 加密的 AES 密钥
 * - iv: AES-GCM 初始化向量
 * - signature: RSA-SHA256 数字签名
 */
public class EncryptedSharePacket implements Parcelable {
    // 协议版本
    private String version;

    // 加密数据（Base64编码）- 版本 2.0 中为 AES-GCM 加密
    private String encryptedData;

    // RSA 加密的 AES 密钥（Base64编码）- 版本 2.0 新增
    private String encryptedAESKey;

    // AES-GCM 初始化向量（Base64编码）- 版本 2.0 新增
    private String iv;

    // 数字签名（Base64编码）
    private String signature;

    // 发送者信息（元数据，不加密）
    private String senderId;

    // 时间戳（元数据，不加密）
    private long createdAt;
    private long expireAt;

    public EncryptedSharePacket() {
        this.version = "2.0";
        this.createdAt = System.currentTimeMillis();
        this.expireAt = 0;
    }

    public EncryptedSharePacket(
            @NonNull String version,
            @NonNull String encryptedData,
            @NonNull String signature,
            @NonNull String senderId
    ) {
        this.version = version;
        this.encryptedData = encryptedData;
        this.signature = signature;
        this.senderId = senderId;
        this.createdAt = System.currentTimeMillis();
        this.expireAt = 0;
    }

    // Getter 和 Setter 方法
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }

    public String getEncryptedAESKey() {
        return encryptedAESKey;
    }

    public void setEncryptedAESKey(String encryptedAESKey) {
        this.encryptedAESKey = encryptedAESKey;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        if (expireAt == 0) {
            return false; // 永不过期
        }
        return System.currentTimeMillis() > expireAt;
    }

    /**
     * 获取剩余有效时间（毫秒）
     */
    public long getRemainingTime() {
        if (expireAt == 0) {
            return Long.MAX_VALUE;
        }
        long remaining = expireAt - System.currentTimeMillis();
        return remaining > 0 ? remaining : 0;
    }

    /**
     * 验证包的基本完整性
     * 版本 2.0 需要验证：encryptedAESKey 和 iv 字段
     */
    public boolean isValid() {
        if (version == null || version.isEmpty()) {
            return false;
        }

        // 版本 2.0 需要额外的字段验证
        if ("2.0".equals(version)) {
            return encryptedData != null && !encryptedData.isEmpty()
                    && encryptedAESKey != null && !encryptedAESKey.isEmpty()
                    && iv != null && !iv.isEmpty()
                    && signature != null && !signature.isEmpty()
                    && senderId != null && !senderId.isEmpty()
                    && !isExpired();
        }

        // 旧版本兼容性（已弃用）
        return encryptedData != null && !encryptedData.isEmpty()
                && signature != null && !signature.isEmpty()
                && senderId != null && !senderId.isEmpty()
                && !isExpired();
    }

    @Override
    public String toString() {
        return "EncryptedSharePacket{" +
                "version='" + version + '\'' +
                ", senderId='" + senderId + '\'' +
                ", createdAt=" + createdAt +
                ", expireAt=" + expireAt +
                ", isExpired=" + isExpired() +
                ", dataSize=" + (encryptedData != null ? encryptedData.length() : 0) +
                ", hasAESKey=" + (encryptedAESKey != null && !encryptedAESKey.isEmpty()) +
                ", hasIV=" + (iv != null && !iv.isEmpty()) +
                ", hasSignature=" + (signature != null && !signature.isEmpty()) +
                '}';
    }

    // Parcelable implementation
    protected EncryptedSharePacket(Parcel in) {
        version = in.readString();
        encryptedData = in.readString();
        encryptedAESKey = in.readString();
        iv = in.readString();
        signature = in.readString();
        senderId = in.readString();
        createdAt = in.readLong();
        expireAt = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(version);
        dest.writeString(encryptedData);
        dest.writeString(encryptedAESKey);
        dest.writeString(iv);
        dest.writeString(signature);
        dest.writeString(senderId);
        dest.writeLong(createdAt);
        dest.writeLong(expireAt);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<EncryptedSharePacket> CREATOR = new Creator<EncryptedSharePacket>() {
        @Override
        public EncryptedSharePacket createFromParcel(Parcel in) {
            return new EncryptedSharePacket(in);
        }

        @Override
        public EncryptedSharePacket[] newArray(int size) {
            return new EncryptedSharePacket[size];
        }
    };
}
