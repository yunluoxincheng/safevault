package com.ttt.safevault.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * 分享记录实体
 */
@Entity(tableName = "share_records")
public class ShareRecord implements Serializable {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "share_id")
    public String shareId;          // 分享唯一ID

    @ColumnInfo(name = "password_id")
    public int passwordId;          // 密码ID

    @ColumnInfo(name = "type")
    @NonNull
    public String type;             // 'sent' 或 'received'

    @ColumnInfo(name = "contact_id")
    public String contactId;        // 联系人ID

    @ColumnInfo(name = "remote_user_id")
    public String remoteUserId;     // 远程用户ID

    @ColumnInfo(name = "encrypted_data")
    public String encryptedData;    // 加密的分享数据

    @ColumnInfo(name = "permission")
    public String permission;       // JSON格式的权限

    @ColumnInfo(name = "expire_at")
    public long expireAt;           // 过期时间戳

    @ColumnInfo(name = "status")
    @NonNull
    public String status;           // 'active', 'expired', 'revoked', 'accepted'

    @ColumnInfo(name = "created_at")
    public long createdAt;          // 创建时间

    @ColumnInfo(name = "accessed_at")
    public long accessedAt;         // 最后访问时间

    public ShareRecord() {
        this.createdAt = System.currentTimeMillis();
        this.accessedAt = System.currentTimeMillis();
    }

    // Getters and Setters
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public String getRemoteUserId() {
        return remoteUserId;
    }

    public void setRemoteUserId(String remoteUserId) {
        this.remoteUserId = remoteUserId;
    }

    public String getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(long accessedAt) {
        this.accessedAt = accessedAt;
    }
}
