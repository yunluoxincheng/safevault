package com.ttt.safevault.dto.request;

/**
 * 接受分享请求 DTO
 * 用于调用接受分享 API
 */
public class AcceptShareRequest {
    /** 可选：接受时的备注 */
    private String note;

    public AcceptShareRequest() {
    }

    public AcceptShareRequest(String note) {
        this.note = note;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
