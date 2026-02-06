package com.ttt.safevault.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 离线同步操作实体
 * 用于在网络不可用时缓存操作
 */
@Entity(tableName = "sync_operations")
public class SyncOperationEntity {

    @PrimaryKey
    @NonNull
    private String operationId;

    /**
     * 操作类型：CREATE, UPDATE, DELETE
     */
    @NonNull
    private String operationType;

    /**
     * 密码条目ID（如果适用）
     */
    @Nullable
    private Integer passwordId;

    /**
     * 操作时间戳
     */
    private long timestamp;

    /**
     * 重试次数
     */
    private int retryCount;

    /**
     * 操作状态：PENDING, IN_PROGRESS, SUCCESS, FAILED
     */
    @NonNull
    private String status;

    public SyncOperationEntity() {
        this.timestamp = System.currentTimeMillis();
        this.retryCount = 0;
        this.status = "PENDING";
    }

    @Ignore
    public SyncOperationEntity(String operationId, String operationType, Integer passwordId) {
        this();
        this.operationId = operationId;
        this.operationType = operationType;
        this.passwordId = passwordId;
    }

    // Getters and Setters
    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public Integer getPasswordId() {
        return passwordId;
    }

    public void setPasswordId(Integer passwordId) {
        this.passwordId = passwordId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
