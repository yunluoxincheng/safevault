package com.ttt.safevault.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 加密密码条目数据库实体
 * 所有敏感字段都以加密形式存储
 */
@Entity(tableName = "passwords")
public class EncryptedPasswordEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    // 加密存储的标题
    private String encryptedTitle;

    // 加密存储的用户名
    private String encryptedUsername;

    // 加密存储的密码
    private String encryptedPassword;

    // 加密存储的URL
    private String encryptedUrl;

    // 加密存储的备注
    private String encryptedNotes;

    // 加密存储的标签（JSON格式）
    private String encryptedTags;

    // 更新时间戳
    private long updatedAt;

    // IV（初始化向量），每条记录独立IV
    private String iv;

    public EncryptedPasswordEntity() {
        this.updatedAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getEncryptedTitle() {
        return encryptedTitle;
    }

    public void setEncryptedTitle(String encryptedTitle) {
        this.encryptedTitle = encryptedTitle;
    }

    public String getEncryptedUsername() {
        return encryptedUsername;
    }

    public void setEncryptedUsername(String encryptedUsername) {
        this.encryptedUsername = encryptedUsername;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public String getEncryptedUrl() {
        return encryptedUrl;
    }

    public void setEncryptedUrl(String encryptedUrl) {
        this.encryptedUrl = encryptedUrl;
    }

    public String getEncryptedNotes() {
        return encryptedNotes;
    }

    public void setEncryptedNotes(String encryptedNotes) {
        this.encryptedNotes = encryptedNotes;
    }

    public String getEncryptedTags() {
        return encryptedTags;
    }

    public void setEncryptedTags(String encryptedTags) {
        this.encryptedTags = encryptedTags;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }
}
