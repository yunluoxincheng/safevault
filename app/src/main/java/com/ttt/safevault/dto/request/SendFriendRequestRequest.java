package com.ttt.safevault.dto.request;

/**
 * 发送好友请求DTO
 */
public class SendFriendRequestRequest {
    private String toUserId;
    private String message;

    public SendFriendRequestRequest() {
    }

    public SendFriendRequestRequest(String toUserId, String message) {
        this.toUserId = toUserId;
        this.message = message;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
