package org.ttt.safevaultbackend.enums;

/**
 * 验证请求类型枚举
 * 定义触发验证邮件的不同场景
 */
public enum VerificationRequestType {
    /**
     * 注册验证
     * 用户注册时发送的邮箱验证
     */
    REGISTRATION,

    /**
     * 密码重置
     * 用户重置密码时发送的验证
     */
    PASSWORD_RESET,

    /**
     * 邮箱变更
     * 用户更改邮箱地址时发送的验证
     */
    EMAIL_CHANGE,

    /**
     * 重发验证
     * 用户请求重新发送验证邮件
     */
    RESEND
}
