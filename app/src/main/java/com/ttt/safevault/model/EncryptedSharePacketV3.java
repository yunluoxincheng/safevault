package com.ttt.safevault.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * 加密分享包 - 版本 3.0
 *
 * 使用 X25519/Ed25519 混合加密方案实现前向保密：
 * - ephemeralPublicKey: 发送方 X25519 ephemeral 公钥（32 字节，Base64 编码）
 * - encryptedData: AES-256-GCM 加密的分享数据
 * - iv: AES-GCM 初始化向量（12 字节，Base64 编码）
 * - signature: Ed25519 签名（64 字节，Base64 编码）
 * - createdAt: 创建时间（防重放攻击）
 * - expireAt: 过期时间
 * - senderId: 发送方用户 ID
 *
 * 版本 3.0 特性：
 * - 前向保密：每次分享使用新的 ephemeral key
 * - 性能优化：X25519/Ed25519 比 RSA 快 10-100 倍
 * - 密钥尺寸小：公私钥各 32 字节（RSA 的 1/8）
 * - 身份绑定：HKDF info 参数混合双方身份，防止密钥混淆攻击
 */
public class EncryptedSharePacketV3 implements Parcelable {
    // 协议版本（固定为 "3.0"）
    private String version = "3.0";

    // 发送方 X25519 ephemeral 公钥（32 字节，Base64 编码）
    private String ephemeralPublicKey;

    // AES-256-GCM 加密的数据（Base64 编码）
    private String encryptedData;

    // AES-GCM 初始化向量（12 字节，Base64 编码）
    private String iv;

    // Ed25519 签名（64 字节，Base64 编码）
    private String signature;

    // 创建时间（Unix 时间戳，毫秒）- 用于防重放攻击
    private long createdAt;

    // 过期时间（Unix 时间戳，毫秒，0 表示永不过期）
    private long expireAt;

    // 发送方用户 ID
    private String senderId;

    /**
     * 默认构造函数
     */
    public EncryptedSharePacketV3() {
        this.createdAt = System.currentTimeMillis();
        this.expireAt = 0;
    }

    /**
     * 完整构造函数
     */
    public EncryptedSharePacketV3(
            @NonNull String version,
            @NonNull String ephemeralPublicKey,
            @NonNull String encryptedData,
            @NonNull String iv,
            @NonNull String signature,
            long createdAt,
            long expireAt,
            @NonNull String senderId
    ) {
        this.version = version;
        this.ephemeralPublicKey = ephemeralPublicKey;
        this.encryptedData = encryptedData;
        this.iv = iv;
        this.signature = signature;
        this.createdAt = createdAt;
        this.expireAt = expireAt;
        this.senderId = senderId;
    }

    // Getter 和 Setter 方法
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getEphemeralPublicKey() {
        return ephemeralPublicKey;
    }

    public void setEphemeralPublicKey(String ephemeralPublicKey) {
        this.ephemeralPublicKey = ephemeralPublicKey;
    }

    public String getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
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

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
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
     * 检查时间戳是否有效（防重放攻击）
     *
     * @param maxDrift 最大允许的时间偏差（毫秒）
     * @return true 表示时间戳有效
     */
    public boolean isTimestampValid(long maxDrift) {
        long now = System.currentTimeMillis();

        // 检查是否过期
        if (expireAt > 0 && now > expireAt) {
            return false;
        }

        // 检查时间戳偏差（未来或过去太远的时间）
        long timeDifference = Math.abs(now - createdAt);
        return timeDifference <= maxDrift;
    }

    /**
     * 验证包的基本完整性
     */
    public boolean isValid() {
        // 检查版本
        if (!"3.0".equals(version)) {
            return false;
        }

        // 检查必需字段
        if (ephemeralPublicKey == null || ephemeralPublicKey.isEmpty()) {
            return false;
        }

        if (encryptedData == null || encryptedData.isEmpty()) {
            return false;
        }

        if (iv == null || iv.isEmpty()) {
            return false;
        }

        if (signature == null || signature.isEmpty()) {
            return false;
        }

        if (senderId == null || senderId.isEmpty()) {
            return false;
        }

        // 检查时间戳
        if (createdAt <= 0) {
            return false;
        }

        // 检查是否过期
        if (isExpired()) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "EncryptedSharePacketV3{" +
                "version='" + version + '\'' +
                ", senderId='" + senderId + '\'' +
                ", createdAt=" + createdAt +
                ", expireAt=" + expireAt +
                ", isExpired=" + isExpired() +
                ", hasEphemeralKey=" + (ephemeralPublicKey != null && !ephemeralPublicKey.isEmpty()) +
                ", dataSize=" + (encryptedData != null ? encryptedData.length() : 0) +
                ", hasSignature=" + (signature != null && !signature.isEmpty()) +
                '}';
    }

    // Parcelable implementation
    protected EncryptedSharePacketV3(Parcel in) {
        version = in.readString();
        ephemeralPublicKey = in.readString();
        encryptedData = in.readString();
        iv = in.readString();
        signature = in.readString();
        createdAt = in.readLong();
        expireAt = in.readLong();
        senderId = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(version);
        dest.writeString(ephemeralPublicKey);
        dest.writeString(encryptedData);
        dest.writeString(iv);
        dest.writeString(signature);
        dest.writeLong(createdAt);
        dest.writeLong(expireAt);
        dest.writeString(senderId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<EncryptedSharePacketV3> CREATOR = new Creator<EncryptedSharePacketV3>() {
        @Override
        public EncryptedSharePacketV3 createFromParcel(Parcel in) {
            return new EncryptedSharePacketV3(in);
        }

        @Override
        public EncryptedSharePacketV3[] newArray(int size) {
            return new EncryptedSharePacketV3[size];
        }
    };
}