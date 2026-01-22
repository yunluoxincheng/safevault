package com.ttt.safevault.sync;

/**
 * 同步状态枚举
 */
public enum SyncStatus {
    /**
     * 空闲状态（已同步）
     */
    IDLE,

    /**
     * 同步中
     */
    SYNCING,

    /**
     * 同步成功
     */
    SUCCESS,

    /**
     * 同步失败
     */
    FAILED,

    /**
     * 有冲突
     */
    CONFLICT,

    /**
     * 离线模式
     */
    OFFLINE
}
