package com.ttt.safevault.security;

/**
 * 会话状态枚举
 *
 * 定义 SafeVault 会话的 6 个核心状态及合法转换。
 * 状态由 SessionGuard 管理，外部组件通过 SessionGuard 的方法触发状态转换。
 *
 * 状态定义:
 * - UNINITIALIZED: 未注册，无本地数据
 * - REGISTERED: 本地有加密密钥材料但无有效 JWT
 * - LOGGED_IN: 已登录，有有效 JWT 但 DataKey 未加载
 * - UNLOCKED: DataKey 在内存中，可访问所有加密数据
 * - LOCKED: DataKey 已从内存清除，需重新解锁
 * - LOGGED_OUT: 已登出，凭证已清除
 *
 * @since SafeVault 3.9.0
 */
public enum SessionState {

    UNINITIALIZED,
    REGISTERED,
    LOGGED_IN,
    UNLOCKED,
    LOCKED,
    LOGGED_OUT;

    /**
     * 检查从当前状态到目标状态的转换是否合法
     *
     * 合法转换表（基于 design.md Decision 1）:
     * UNINITIALIZED → UNLOCKED (注册流程直达)
     * UNINITIALIZED → REGISTERED (异常路径)
     * REGISTERED → LOGGED_IN
     * REGISTERED → UNLOCKED (快速路径)
     * LOGGED_IN → UNLOCKED
     * LOGGED_IN → LOGGED_OUT
     * UNLOCKED → LOCKED
     * UNLOCKED → LOGGED_OUT
     * LOCKED → UNLOCKED
     * LOCKED → LOGGED_OUT
     * LOGGED_OUT → LOGGED_IN
     * LOGGED_OUT → UNLOCKED (快速路径)
     */
    public boolean canTransitionTo(SessionState target) {
        if (this == target) return true;

        switch (this) {
            case UNINITIALIZED:
                return target == UNLOCKED || target == REGISTERED;
            case REGISTERED:
                return target == LOGGED_IN || target == UNLOCKED;
            case LOGGED_IN:
                return target == UNLOCKED || target == LOGGED_OUT;
            case UNLOCKED:
                return target == LOCKED || target == LOGGED_OUT;
            case LOCKED:
                return target == UNLOCKED || target == LOGGED_OUT;
            case LOGGED_OUT:
                return target == LOGGED_IN || target == UNLOCKED;
            default:
                return false;
        }
    }
}
