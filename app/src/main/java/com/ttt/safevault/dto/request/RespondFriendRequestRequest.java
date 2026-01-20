package com.ttt.safevault.dto.request;

/**
 * 响应好友请求DTO
 */
public class RespondFriendRequestRequest {
    private Boolean accept;

    public RespondFriendRequestRequest() {
    }

    public RespondFriendRequestRequest(Boolean accept) {
        this.accept = accept;
    }

    public Boolean getAccept() {
        return accept;
    }

    public void setAccept(Boolean accept) {
        this.accept = accept;
    }
}
