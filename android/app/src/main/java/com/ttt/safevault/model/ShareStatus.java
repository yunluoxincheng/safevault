package com.ttt.safevault.model;

/**
 * 分享状态枚举
 */
public enum ShareStatus {
    PENDING,     // 等待接收
    ACTIVE,      // 活跃状态
    EXPIRED,     // 已过期
    REVOKED,     // 已撤销
    ACCEPTED     // 已接收
}
