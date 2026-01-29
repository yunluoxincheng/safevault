package com.ttt.safevault.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 联系人实体
 */
@Entity(tableName = "contacts")
public class Contact {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "contact_id")
    public String contactId;        // 联系人唯一ID

    @NonNull
    @ColumnInfo(name = "user_id")
    public String userId;           // 对方的用户ID

    @ColumnInfo(name = "username")
    @NonNull
    public String username;         // 对方的用户名（邮箱）

    @ColumnInfo(name = "display_name")
    @NonNull
    public String displayName;      // 对方的显示名称

    @ColumnInfo(name = "public_key")
    @NonNull
    public String publicKey;        // 对方的RSA公钥（Base64）

    @ColumnInfo(name = "my_note")
    public String myNote;           // 我的备注

    @ColumnInfo(name = "added_at")
    public long addedAt;            // 添加时间

    @ColumnInfo(name = "last_used_at")
    public long lastUsedAt;         // 最后使用时间

    @ColumnInfo(name = "cloud_user_id")
    public String cloudUserId;      // 云端用户ID（用于云端分享）

    @ColumnInfo(name = "email")
    public String email;            // 邮箱地址（用于显示）

    @ColumnInfo(name = "is_online")
    public boolean isOnline;        // 是否在线

    public Contact() {
        this.addedAt = System.currentTimeMillis();
        this.lastUsedAt = System.currentTimeMillis();
        this.isOnline = false;
    }

    @Ignore
    public Contact(String contactId, String userId, String username, String displayName, String publicKey) {
        this.contactId = contactId;
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.publicKey = publicKey;
        this.addedAt = System.currentTimeMillis();
        this.lastUsedAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getMyNote() {
        return myNote;
    }

    public void setMyNote(String myNote) {
        this.myNote = myNote;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(long addedAt) {
        this.addedAt = addedAt;
    }

    public long getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(long lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public String getCloudUserId() {
        return cloudUserId;
    }

    public void setCloudUserId(String cloudUserId) {
        this.cloudUserId = cloudUserId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        this.isOnline = online;
    }
}
