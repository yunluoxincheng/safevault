package com.ttt.safevault.dto.request;

/**
 * 拒绝分享请求 DTO
 * 用于调用拒绝分享 API
 */
public class RejectShareRequest {
    /** 可选：拒绝原因 */
    private String reason;

    public RejectShareRequest() {
    }

    public RejectShareRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
