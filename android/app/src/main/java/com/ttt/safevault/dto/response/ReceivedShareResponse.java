package com.ttt.safevault.dto.response;

import com.ttt.safevault.dto.PasswordData;
import com.ttt.safevault.model.SharePermission;

/**
 * 接收分享响应DTO
 */
public class ReceivedShareResponse {
    private String shareId;
    private String fromUserId;
    private String fromDisplayName;
    private PasswordData passwordData;
    private SharePermission permission;
    private String status;           // PENDING, ACCEPTED, EXPIRED, REVOKED
    private String shareType;        // DIRECT, USER_TO_USER, NEARBY
    private long createdAt;
    private long expiresAt;

    public ReceivedShareResponse() {
    }

    public String getShareId() {
        return shareId;
    }

    public void setShareId(String shareId) {
        this.shareId = shareId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getFromDisplayName() {
        return fromDisplayName;
    }

    public void setFromDisplayName(String fromDisplayName) {
        this.fromDisplayName = fromDisplayName;
    }

    public PasswordData getPasswordData() {
        return passwordData;
    }

    public void setPasswordData(PasswordData passwordData) {
        this.passwordData = passwordData;
    }

    public SharePermission getPermission() {
        return permission;
    }

    public void setPermission(SharePermission permission) {
        this.permission = permission;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getShareType() {
        return shareType;
    }

    public void setShareType(String shareType) {
        this.shareType = shareType;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    // 便捷方法：从passwordData获取标题
    public String getTitle() {
        return passwordData != null ? passwordData.getTitle() : null;
    }

    // 便捷方法：从passwordData获取用户名
    public String getUsername() {
        return passwordData != null ? passwordData.getUsername() : null;
    }

    // 便捷方法：从passwordData获取密码
    public String getDecryptedPassword() {
        return passwordData != null ? passwordData.getPassword() : null;
    }

    // 便捷方法：从passwordData获取URL
    public String getUrl() {
        return passwordData != null ? passwordData.getUrl() : null;
    }

    // 便捷方法：获取fromDisplayName（兼容原有的getFromUserDisplayName）
    public String getFromUserDisplayName() {
        return fromDisplayName;
    }

    // 便捷方法：获取过期时间（兼容原有的getExpireTime）
    public Long getExpireTime() {
        return expiresAt > 0 ? expiresAt : null;
    }

    // 便捷方法：获取目标用户显示名称（用于发送的分享）
    private String toUserDisplayName;
    private String toUserId;

    public String getToUserDisplayName() {
        return toUserDisplayName;
    }

    public void setToUserDisplayName(String toUserDisplayName) {
        this.toUserDisplayName = toUserDisplayName;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }
}
