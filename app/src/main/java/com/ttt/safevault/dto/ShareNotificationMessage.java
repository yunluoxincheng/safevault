package com.ttt.safevault.dto;

/**
 * 分享通知消息
 */
public class ShareNotificationMessage {
    private String shareId;
    private String requestId;  // 好友请求ID
    private String fromUserId;
    private String fromDisplayName;
    private String type;  // NEW_SHARE, SHARE_REVOKED, FRIEND_REQUEST, FRIEND_REQUEST_ACCEPTED, FRIEND_DELETED
    private String message;  // 通知消息内容
    private long timestamp;

    public ShareNotificationMessage() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
