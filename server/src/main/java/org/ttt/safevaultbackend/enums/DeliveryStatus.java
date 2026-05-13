package org.ttt.safevaultbackend.enums;

/**
 * 邮件投递状态枚举
 * 定义验证邮件的投递状态
 */
public enum DeliveryStatus {
    /**
     * 已发送
     * 邮件已提交给邮件服务器
     */
    SENT,

    /**
     * 已投递
     * 邮件已成功投递到收件人邮箱
     */
    DELIVERED,

    /**
     * 投递失败
     * 邮件投递失败（网络错误、邮箱不存在等）
     */
    FAILED,

    /**
     * 退信
     * 邮件被退回（邮箱满、域名不存在等）
     */
    BOUNCED,

    /**
     * 已打开
     * 收件人已打开邮件
     */
    OPENED
}
