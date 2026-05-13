package com.ttt.safevault.dto.response;

/**
 * 分享响应DTO（创建分享后返回）
 */
public class ShareResponse {
    private String shareId;
    private String shareToken;
    private long expiresAt;

    public ShareResponse() {
    }

    public String getShareId() {
        return shareId;
    }

    public void setShareId(String shareId) {
        this.shareId = shareId;
    }

    public String getShareToken() {
        return shareToken;
    }

    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
