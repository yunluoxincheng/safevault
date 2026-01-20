package com.ttt.safevault.dto.response;

/**
 * 好友请求响应DTO
 */
public class FriendRequestDto {
    private String requestId;
    private String fromUserId;
    private String fromUsername;
    private String fromDisplayName;
    private String message;
    private String status;
    private Long createdAt;
    private Long respondedAt;

    public FriendRequestDto() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getFromUsername() {
        return fromUsername;
    }

    public void setFromUsername(String fromUsername) {
        this.fromUsername = fromUsername;
    }

    public String getFromDisplayName() {
        return fromDisplayName;
    }

    public void setFromDisplayName(String fromDisplayName) {
        this.fromDisplayName = fromDisplayName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Long respondedAt) {
        this.respondedAt = respondedAt;
    }
}
