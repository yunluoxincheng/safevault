package com.ttt.safevault.service.offline;

/**
 * 离线操作数据类
 * 用于序列化存储
 */
public class OfflineOperationData {
    private String type;
    private long timestamp;
    private int retryCount;
    private String data; // JSON格式的操作数据

    public OfflineOperationData() {
    }

    public OfflineOperationData(String type, long timestamp, int retryCount, String data) {
        this.type = type;
        this.timestamp = timestamp;
        this.retryCount = retryCount;
        this.data = data;
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

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
