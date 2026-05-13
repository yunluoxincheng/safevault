package org.ttt.safevaultbackend.entity;

/**
 * 联系人分享状态枚举
 */
public enum ContactShareStatus {
    /**
     * 待接收
     */
    PENDING,

    /**
     * 已接受
     */
    ACCEPTED,

    /**
     * 已过期
     */
    EXPIRED,

    /**
     * 已撤销
     */
    REVOKED
}
