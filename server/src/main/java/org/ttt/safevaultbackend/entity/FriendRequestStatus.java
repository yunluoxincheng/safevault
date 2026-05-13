package org.ttt.safevaultbackend.entity;

/**
 * 好友请求状态
 */
public enum FriendRequestStatus {
    /**
     * 待处理
     */
    PENDING,
    /**
     * 已接受
     */
    ACCEPTED,
    /**
     * 已拒绝
     */
    REJECTED,
    /**
     * 已取消
     */
    CANCELLED
}
