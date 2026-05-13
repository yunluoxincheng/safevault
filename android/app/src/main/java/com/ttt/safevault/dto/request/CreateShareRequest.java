package com.ttt.safevault.dto.request;

import com.ttt.safevault.model.SharePermission;

/**
 * 创建分享请求DTO
 * 仅支持用户对用户的端到端加密分享
 */
public class CreateShareRequest {
    private String passwordId;
    private String title;
    private String username;
    private String encryptedPassword;
    private String url;
    private String notes;
    private String toUserId;          // 接收方用户ID
    private Integer expireInMinutes;  // 过期时间（分钟）
    private SharePermission permission; // 分享权限

    public CreateShareRequest() {
    }

    // Getters and Setters
    public String getPasswordId() {
        return passwordId;
    }

    public void setPasswordId(String passwordId) {
        this.passwordId = passwordId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public Integer getExpireInMinutes() {
        return expireInMinutes;
    }

    public void setExpireInMinutes(Integer expireInMinutes) {
        this.expireInMinutes = expireInMinutes;
    }

    public SharePermission getPermission() {
        return permission;
    }

    public void setPermission(SharePermission permission) {
        this.permission = permission;
    }
}
