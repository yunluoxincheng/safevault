package com.ttt.safevault.security;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Supplier;

/**
 * 安全会话门控（Guarded Execution 模式）和会话锁定管理
 *
 * 设计原则：
 * - Security Boundary Centralization（安全边界集中化）
 * - 所有需要 DataKey 的操作必须通过此门控
 * - 会话锁定检查逻辑统一管理
 * - 防止竞态条件和逻辑绕过
 *
 * 职责：
 * - 统一管理会话状态检查
 * - 确保敏感操作仅在会话解锁时执行
 * - 提供明确的失败信号（SessionLockedException）
 * - 统一管理会话锁定超时检查逻辑
 *
 * 使用模式：
 * <pre>
 * // 同步操作
 * String result = sessionGuard.runWithUnlockedSession(() -> {
 *     return passwordManager.decryptItem(id);
 * });
 *
 * // 异步操作
 * executor.execute(() -> {
 *     sessionGuard.runWithUnlockedSession(() -> {
 *         passwordManager.saveItem(item);
 *         return null;
 *     });
 * });
 * </pre>
 *
 * 会话锁定检查：
 * <pre>
 * // 检查是否需要根据后台时间锁定会话
 * if (sessionGuard.shouldLockBySessionTimeout(backgroundTime)) {
 *     sessionGuard.lockSession();
 *     // 跳转到登录页
 * }
 * </pre>
 *
 * UI 层处理：
 * <pre>
 * try {
 *     backendService.saveItem(item);
 * } catch (SessionLockedException e) {
 *     // 会话已锁定，触发重新认证
 *     showReauthenticationDialog(() -> {
 *         backendService.saveItem(item); // 重试
 *     });
 * }
 * </pre>
 *
 * @since SafeVault 3.4.0 (Guarded Execution 模式)
 * @since SafeVault 3.7.0 (统一会话锁定管理)
 */
public class SessionGuard {
    private static final String TAG = "SessionGuard";

    /** 单例实例 */
    private static volatile SessionGuard INSTANCE;

    /** 加密会话引用 */
    private final CryptoSession cryptoSession;

    /** SecurityConfig 引用（用于获取会话锁定超时设置） */
    private SecurityConfig securityConfig;

    private SessionGuard(@NonNull CryptoSession cryptoSession) {
        this.cryptoSession = cryptoSession;
        Log.i(TAG, "SessionGuard 初始化（Guarded Execution 模式）");
    }

    /**
     * 获取 SessionGuard 单例
     */
    @NonNull
    public static SessionGuard getInstance() {
        if (INSTANCE == null) {
            synchronized (SessionGuard.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SessionGuard(CryptoSession.getInstance());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 设置 SecurityConfig（需要 Context 时调用）
     *
     * @param context 应用上下文
     */
    public void setSecurityConfig(@NonNull android.content.Context context) {
        this.securityConfig = new SecurityConfig(context);
        Log.d(TAG, "SecurityConfig 已设置");
    }

    /**
     * 获取会话锁定超时时间（毫秒）
     *
     * @return 超时时间（毫秒），Long.MAX_VALUE 表示从不锁定
     */
    private long getSessionLockTimeoutMillis() {
        if (securityConfig != null) {
            return securityConfig.getAutoLockTimeoutMillisForMode();
        }
        // 默认值：1分钟
        return 60 * 1000L;
    }

    /**
     * 检查是否需要根据后台时间锁定会话
     *
     * 这是统一的会话锁定检查逻辑，供 MainActivity、SafeVaultAutofillService 等调用
     *
     * @param backgroundTime 后台时间戳（毫秒）
     * @return true 表示需要锁定，false 表示不需要
     */
    public boolean shouldLockBySessionTimeout(long backgroundTime) {
        // 如果没有后台时间记录，不需要锁定（首次启动或刚登录成功）
        if (backgroundTime == 0) {
            Log.d(TAG, "shouldLockBySessionTimeout: 没有后台时间记录，不需要锁定");
            return false;
        }

        long autoLockTimeoutMillis = getSessionLockTimeoutMillis();

        // 从不锁定模式
        if (autoLockTimeoutMillis == Long.MAX_VALUE) {
            Log.d(TAG, "shouldLockBySessionTimeout: 会话锁定模式为从不锁定");
            return false;
        }

        // 立即锁定模式：只要有后台时间记录就锁定（应用进入过后台）
        if (autoLockTimeoutMillis == 0) {
            Log.d(TAG, "shouldLockBySessionTimeout: 立即锁定模式，需要锁定");
            return true;
        }

        // 其他模式：检查是否超时
        long backgroundMillis = System.currentTimeMillis() - backgroundTime;
        boolean shouldLock = backgroundMillis >= autoLockTimeoutMillis;

        if (shouldLock) {
            Log.d(TAG, "shouldLockBySessionTimeout: 后台超时，需要锁定（"
                    + (backgroundMillis / 1000) + "秒 >= "
                    + (autoLockTimeoutMillis / 1000) + "秒）");
        } else {
            Log.d(TAG, "shouldLockBySessionTimeout: 未超时，不需要锁定（"
                    + (backgroundMillis / 1000) + "秒 < "
                    + (autoLockTimeoutMillis / 1000) + "秒）");
        }

        return shouldLock;
    }

    /**
     * 在解锁会话中执行操作（Guarded Execution）
     *
     * 此方法确保：
     * 1. 操作前会话已解锁
     * 2. 如果会话未解锁，抛出 SessionLockedException
     * 3. 防止竞态条件（检查+执行是原子性的）
     *
     * @param task 要执行的任务（需要 DataKey 的敏感操作）
     * @param <T>  返回类型
     * @return 任务执行结果
     * @throws SessionLockedException 如果会话未解锁
     */
    @NonNull
    public <T> T runWithUnlockedSession(@NonNull Supplier<T> task) {
        // 检查会话状态
        if (!cryptoSession.isUnlocked()) {
            Log.w(TAG, "runWithUnlockedSession: 会话未解锁，拒绝执行敏感操作");
            throw new SessionLockedException("会话未解锁，无法执行敏感操作。请先通过主密码验证。");
        }

        // 刷新会话时间（延长超时）
        cryptoSession.refreshSession();

        try {
            // 执行任务
            return task.get();
        } catch (SessionLockedException e) {
            // 重新抛出会话锁定异常
            throw e;
        } catch (Exception e) {
            // 包装其他异常
            Log.e(TAG, "runWithUnlockedSession: 任务执行失败", e);
            throw new RuntimeException("敏感操作执行失败", e);
        }
    }

    /**
     * 在解锁会话中执行操作（无返回值）
     *
     * @param task 要执行的任务
     * @throws SessionLockedException 如果会话未解锁
     */
    public void runWithUnlockedSession(@NonNull Runnable task) {
        runWithUnlockedSession(() -> {
            task.run();
            return null;
        });
    }

    /**
     * 检查会话是否已解锁（不抛异常）
     *
     * 注意：此方法仅供 UI 层做体验优化（如隐藏/显示按钮），
     * 真正的安全检查应该在 runWithUnlockedSession 中进行。
     *
     * @return true 表示已解锁，false 表示未解锁
     */
    public boolean isSessionUnlocked() {
        return cryptoSession.isUnlocked();
    }

    /**
     * 获取会话剩余时间（秒）
     *
     * 用于 UI 展示会话即将过期的警告
     *
     * @return 剩余秒数，已锁定返回 0
     */
    public long getSessionRemainingTimeSeconds() {
        return cryptoSession.getRemainingTimeSeconds();
    }

    /**
     * 锁定会话
     *
     * 清除 DataKey，将所有敏感操作标记为需要重新认证
     */
    public void lockSession() {
        cryptoSession.lock();
        Log.i(TAG, "会话已锁定（通过 SessionGuard）");
    }

    /**
     * 检查会话是否即将过期（用于 UI 警告）
     *
     * @param warningThresholdSeconds 警告阈值（秒）
     * @return true 表示即将过期，false 表示还有充足时间
     */
    public boolean isSessionExpiringSoon(int warningThresholdSeconds) {
        long remaining = getSessionRemainingTimeSeconds();
        return remaining > 0 && remaining <= warningThresholdSeconds;
    }
}
