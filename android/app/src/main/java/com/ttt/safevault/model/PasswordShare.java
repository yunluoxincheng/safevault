package com.ttt.safevault.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 密码分享数据模型
 */
public class PasswordShare implements Parcelable {
    private String shareId;          // 分享唯一ID
    private int passwordId;          // 被分享的密码ID
    private String fromUserId;       // 分享者用户ID
    private String toUserId;         // 接收者用户ID（null表示直接分享）
    private String encryptedData;    // 加密的密码数据
    private long createdAt;          // 创建时间
    private long expireTime;         // 过期时间（0表示永不过期）
    private SharePermission permission; // 分享权限
    private ShareStatus status;      // 分享状态

    public PasswordShare() {
        this.createdAt = System.currentTimeMillis();
        this.expireTime = 0;
        this.status = ShareStatus.PENDING;
        this.permission = new SharePermission();
    }

    public PasswordShare(String shareId, int passwordId, String fromUserId, String toUserId) {
        this();
        this.shareId = shareId;
        this.passwordId = passwordId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
    }

    // Getter 和 Setter 方法
    public String getShareId() {
        return shareId;
    }

    public void setShareId(String shareId) {
        this.shareId = shareId;
    }

    public int getPasswordId() {
        return passwordId;
    }

    public void setPasswordId(int passwordId) {
        this.passwordId = passwordId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public String getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public SharePermission getPermission() {
        return permission;
    }

    public void setPermission(SharePermission permission) {
        this.permission = permission;
    }

    public ShareStatus getStatus() {
        return status;
    }

    public void setStatus(ShareStatus status) {
        this.status = status;
    }

    /**
     * 检查分享是否已过期
     */
    public boolean isExpired() {
        if (expireTime == 0) {
            return false; // 永不过期
        }
        return System.currentTimeMillis() > expireTime;
    }

    /**
     * 检查分享是否可用
     */
    public boolean isAvailable() {
        return status == ShareStatus.ACTIVE && !isExpired();
    }

    @Override
    public String toString() {
        return "PasswordShare{" +
                "shareId='" + shareId + '\'' +
                ", passwordId=" + passwordId +
                ", fromUserId='" + fromUserId + '\'' +
                ", toUserId='" + toUserId + '\'' +
                ", status=" + status +
                ", isExpired=" + isExpired() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PasswordShare that = (PasswordShare) o;
        return shareId != null && shareId.equals(that.shareId);
    }

    @Override
    public int hashCode() {
        return shareId != null ? shareId.hashCode() : 0;
    }

    // Parcelable implementation
    protected PasswordShare(Parcel in) {
        shareId = in.readString();
        passwordId = in.readInt();
        fromUserId = in.readString();
        toUserId = in.readString();
        encryptedData = in.readString();
        createdAt = in.readLong();
        expireTime = in.readLong();
        permission = in.readParcelable(SharePermission.class.getClassLoader());
        int statusOrdinal = in.readInt();
        status = statusOrdinal >= 0 ? ShareStatus.values()[statusOrdinal] : ShareStatus.PENDING;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(shareId);
        dest.writeInt(passwordId);
        dest.writeString(fromUserId);
        dest.writeString(toUserId);
        dest.writeString(encryptedData);
        dest.writeLong(createdAt);
        dest.writeLong(expireTime);
        dest.writeParcelable(permission, flags);
        dest.writeInt(status != null ? status.ordinal() : -1);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PasswordShare> CREATOR = new Creator<PasswordShare>() {
        @Override
        public PasswordShare createFromParcel(Parcel in) {
            return new PasswordShare(in);
        }

        @Override
        public PasswordShare[] newArray(int size) {
            return new PasswordShare[size];
        }
    };
}
