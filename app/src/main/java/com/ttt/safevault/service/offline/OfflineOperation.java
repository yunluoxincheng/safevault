package com.ttt.safevault.service.offline;

/**
 * 离线操作基类
 * 用于在网络不可用时暂存操作，网络恢复后自动执行
 */
public abstract class OfflineOperation {
    protected String type;
    protected long timestamp;
    protected int retryCount = 0;
    protected static final int MAX_RETRY = 3;

    /**
     * 执行操作
     * @return true表示成功，false表示失败
     * @throws Exception 执行过程中的异常
     */
    public abstract boolean execute() throws Exception;

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

    /**
     * 检查是否可以重试
     */
    public boolean canRetry() {
        return retryCount < MAX_RETRY;
    }

    /**
     * 增加重试次数
     */
    public void incrementRetry() {
        retryCount++;
    }
}
