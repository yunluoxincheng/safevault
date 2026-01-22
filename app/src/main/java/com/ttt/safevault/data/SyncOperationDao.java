package com.ttt.safevault.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;

import java.util.List;

/**
 * 离线同步操作 DAO
 */
@Dao
public interface SyncOperationDao {

    /**
     * 插入操作记录
     */
    @Insert
    void insert(SyncOperationEntity operation);

    /**
     * 删除操作记录
     */
    @Delete
    void delete(SyncOperationEntity operation);

    /**
     * 获取所有待处理操作
     */
    @Query("SELECT * FROM sync_operations WHERE status = 'PENDING' ORDER BY timestamp ASC")
    List<SyncOperationEntity> getPendingOperations();

    /**
     * 获取所有操作
     */
    @Query("SELECT * FROM sync_operations ORDER BY timestamp DESC")
    List<SyncOperationEntity> getAllOperations();

    /**
     * 清空所有操作
     */
    @Query("DELETE FROM sync_operations")
    void clearAll();

    /**
     * 更新操作状态
     */
    @Query("UPDATE sync_operations SET status = :status WHERE operationId = :operationId")
    void updateStatus(String operationId, String status);

    /**
     * 增加重试次数
     */
    @Query("UPDATE sync_operations SET retryCount = retryCount + 1 WHERE operationId = :operationId")
    void incrementRetryCount(String operationId);

    /**
     * 获取待处理操作数量
     */
    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = 'PENDING'")
    int getPendingCount();
}
