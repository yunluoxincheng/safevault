package org.ttt.safevaultbackend.enums;

/**
 * 验证事件类型枚举
 * 定义邮箱验证过程中可能发生的所有事件类型
 */
public enum VerificationEventType {
    /**
     * 令牌已生成
     * 当生成新的验证令牌时记录此事件
     */
    TOKEN_GENERATED,

    /**
     * 令牌已验证
     * 用户成功验证令牌时记录此事件
     */
    TOKEN_VERIFIED,

    /**
     * 令牌已过期
     * 令牌超过有效期时记录此事件
     */
    TOKEN_EXPIRED,

    /**
     * 令牌无效
     * 提交的令牌不存在或格式错误时记录此事件
     */
    TOKEN_INVALID,

    /**
     * 邮件已发送
     * 验证邮件成功发送时记录此事件
     */
    EMAIL_SENT,

    /**
     * 邮件发送失败
     * 验证邮件发送失败时记录此事件
     */
    EMAIL_FAILED,

    /**
     * 重发次数超限
     * 用户在短时间内请求重发邮件次数超限时记录此事件
     */
    RESEND_LIMIT_EXCEEDED,

    /**
     * 验证成功
     * 整个邮箱验证流程成功完成时记录此事件
     */
    VERIFICATION_SUCCESS,

    /**
     * 验证失败
     * 邮箱验证流程失败时记录此事件
     */
    VERIFICATION_FAILED,

    /**
     * 验证页面访问
     * 用户访问验证网页时记录此事件
     */
    PAGE_VISIT,

    /**
     * Deep Link点击
     * 用户点击Deep Link尝试打开App时记录此事件
     */
    DEEP_LINK_CLICK,

    /**
     * 注册超时
     * 用户验证邮箱后未在规定时间内完成注册时记录此事件
     */
    REGISTRATION_TIMEOUT,

    /**
     * 注册清理
     * 定时任务清理超时用户时记录此事件
     */
    REGISTRATION_CLEANUP
}
