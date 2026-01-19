package com.ttt.safevault.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 分享数据包
 * 包含密码数据和分享元数据（明文，用于加密传输）
 */
public class ShareDataPacket implements Parcelable {
    // 协议版本
    public String version = "1.0";

    // 发送者信息
    public String senderId;              // 发送者用户ID
    public String senderPublicKey;       // 发送者公钥（Base64编码）

    // 时间戳
    public long createdAt;               // 创建时间
    public long expireAt;                // 过期时间（0表示永不过期）

    // 权限
    public SharePermission permission;   // 分享权限

    // 密码数据
    public PasswordItem password;        // 密码条目

    public ShareDataPacket() {
        this.createdAt = System.currentTimeMillis();
        this.expireAt = 0;
        this.permission = new SharePermission();
        this.password = new PasswordItem();
    }

    public ShareDataPacket(
            @NonNull String senderId,
            @NonNull String senderPublicKey,
            @NonNull PasswordItem password,
            @NonNull SharePermission permission
    ) {
        this();
        this.senderId = senderId;
        this.senderPublicKey = senderPublicKey;
        this.password = password;
        this.permission = permission;
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

    @Override
    public String toString() {
        return "ShareDataPacket{" +
                "version='" + version + '\'' +
                ", senderId='" + senderId + '\'' +
                ", createdAt=" + createdAt +
                ", expireAt=" + expireAt +
                ", isExpired=" + isExpired() +
                ", permission=" + permission +
                ", password=" + (password != null ? password.getDisplayName() : "null") +
                '}';
    }

    // Parcelable implementation
    protected ShareDataPacket(Parcel in) {
        version = in.readString();
        senderId = in.readString();
        senderPublicKey = in.readString();
        createdAt = in.readLong();
        expireAt = in.readLong();
        permission = in.readParcelable(SharePermission.class.getClassLoader());
        password = in.readParcelable(PasswordItem.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(version);
        dest.writeString(senderId);
        dest.writeString(senderPublicKey);
        dest.writeLong(createdAt);
        dest.writeLong(expireAt);
        dest.writeParcelable(permission, flags);
        dest.writeParcelable(password, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ShareDataPacket> CREATOR = new Creator<ShareDataPacket>() {
        @Override
        public ShareDataPacket createFromParcel(Parcel in) {
            return new ShareDataPacket(in);
        }

        @Override
        public ShareDataPacket[] newArray(int size) {
            return new ShareDataPacket[size];
        }
    };
}
