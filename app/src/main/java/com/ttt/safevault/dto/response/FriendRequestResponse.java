package com.ttt.safevault.dto.response;

/**
 * 好友请求响应DTO（发送请求后返回）
 */
public class FriendRequestResponse {
    private String requestId;

    public FriendRequestResponse() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
