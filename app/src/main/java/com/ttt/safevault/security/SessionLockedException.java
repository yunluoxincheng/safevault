package com.ttt.safevault.security;

/**
 * 会话锁定异常
 *
 * 当尝试在会话未解锁时执行需要 DataKey 的敏感操作时抛出。
 *
 * 设计目的：
 * - 将"会话未解锁"从隐式失败模式转变为显式协议
 * - UI 层可以捕获此异常并触发重新认证流程
 * - 防止敏感操作在未认证状态下静默失败
 *
 * 使用场景：
 * - 保存密码项时
 * - 加密数据时
 * - 解密数据时（如果要确保会话仍然有效）
 *
 * @since SafeVault 3.4.0 (Guarded Execution 模式)
 */
public class SessionLockedException extends SecurityException {

    /** 默认错误消息 */
    private static final String DEFAULT_MESSAGE = "会话未解锁，无法执行敏感操作";

    /**
     * 构造函数（默认消息）
     */
    public SessionLockedException() {
        super(DEFAULT_MESSAGE);
    }

    /**
     * 构造函数（自定义消息）
     *
     * @param message 错误消息
     */
    public SessionLockedException(String message) {
        super(message);
    }

    /**
     * 构造函数（带原因）
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public SessionLockedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数（仅原因）
     *
     * @param cause 原始异常
     */
    public SessionLockedException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
    }
}
