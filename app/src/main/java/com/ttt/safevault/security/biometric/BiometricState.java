package com.ttt.safevault.security.biometric;

import android.util.Log;

import androidx.annotation.NonNull;

/**
 * 生物识别认证状态管理器
 *
 * 职责：
 * 1. 失败追踪 - 防抖动（防止用户短时间内重复触发）
 * 2. 业务级锁定 - 连续失败次数过多时锁定
 * 3. 防认证风暴 - 某些 ROM 认证窗口过短，避免重复弹窗
 *
 * 安全设计原则：
 * - ✅ 只缓存失败状态（用于防抖动和锁定）
 * - ❌ 不缓存成功状态（交给 Keystore 验证）
 *
 * @since SafeVault 3.3.0 (生物识别架构重构)
 */
public class BiometricState {
    private static final String TAG = "BiometricState";

    // ========== 防抖动配置 ==========
    /** 防抖动时间窗口（毫秒） - 用户失败后需要等待的时间 */
    private static final long DEBOUNCE_WINDOW_MS = 1000;

    // ========== 业务级锁定配置 ==========
    /** 最大连续失败次数 - 超过后将锁定 */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    /** 锁定时间（毫秒） - 锁定后需要等待的时间 */
    private static final long LOCKOUT_DURATION_MS = 30_000; // 30秒

    // ========== 防认证风暴配置 ==========
    /** 防认证风暴时间窗口（毫秒） - 刚认证后不需要再认证的时间 */
    private static final long AUTH_STORM_WINDOW_MS = 1000;

    // ========== 状态字段 ==========
    /** 最后一次失败时间（毫秒时间戳） */
    private long lastFailureTime = 0;

    /** 连续失败次数 */
    private int consecutiveFailures = 0;

    /** 锁定结束时间（毫秒时间戳），0 表示未锁定 */
    private long lockoutEndTime = 0;

    /** 最后一次认证时间（毫秒时间戳） - 仅用于防认证风暴 */
    private long lastAuthTime = 0;

    /**
     * 检查是否应该防抖动
     *
     * 如果用户在失败后短时间内再次尝试，应该返回 true 进行防抖。
     * 调用方应该返回 DEBOUNCED 错误，而不是静默失败。
     *
     * @return true 表示应该防抖，false 表示可以继续
     */
    public boolean shouldDebouncePrompt() {
        long now = System.currentTimeMillis();
        long timeSinceLastFailure = now - lastFailureTime;

        if (timeSinceLastFailure < DEBOUNCE_WINDOW_MS) {
            Log.d(TAG, "Debounce prompt triggered: " + timeSinceLastFailure + "ms since last failure");
            return true;
        }
        return false;
    }

    /**
     * 记录认证失败
     *
     * 更新失败时间和失败次数，检查是否需要锁定。
     */
    public void recordFailure() {
        long now = System.currentTimeMillis();
        lastFailureTime = now;
        consecutiveFailures++;

        Log.d(TAG, "Authentication failure recorded. Consecutive failures: " + consecutiveFailures);

        // 检查是否需要锁定
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            lockoutEndTime = now + LOCKOUT_DURATION_MS;
            Log.w(TAG, "Biometric locked out due to too many failures. Duration: " + LOCKOUT_DURATION_MS + "ms");
        }
    }

    /**
     * 记录认证成功（UI 层面）
     *
     * 重置失败计数。
     *
     * 注意：不更新 lastAuthTime，因为这只是 UI 认证成功，不是 Keystore 访问成功。
     * lastAuthTime 应该在 Keystore 解锁成功后才更新，用于防认证风暴。
     */
    public void recordSuccess() {
        consecutiveFailures = 0;
        Log.d(TAG, "Authentication success recorded (UI level). Failures reset.");
    }

    /**
     * 检查是否被锁定
     *
     * @return true 表示被锁定，false 表示未锁定
     */
    public boolean isLockedOut() {
        long now = System.currentTimeMillis();
        if (now < lockoutEndTime) {
            long remainingTime = lockoutEndTime - now;
            Log.d(TAG, "Biometric is locked out. Remaining time: " + remainingTime + "ms");
            return true;
        }
        return false;
    }

    /**
     * 获取锁定剩余时间
     *
     * @return 锁定剩余时间（毫秒），未锁定返回 0
     */
    public long getRemainingLockoutTime() {
        long now = System.currentTimeMillis();
        if (now < lockoutEndTime) {
            return lockoutEndTime - now;
        }
        return 0;
    }

    /**
     * 重置失败计数
     *
     * 通常在成功认证后调用，或者在用户使用主密码解锁后调用。
     */
    public void resetFailures() {
        consecutiveFailures = 0;
        Log.d(TAG, "Failures reset");
    }

    /**
     * 检查是否刚认证过（防认证风暴）
     *
     * 某些国产 ROM（MIUI、ColorOS 等）认证窗口只有 0 秒，
     * 每次使用 DeviceKey 都要求重新认证，导致循环弹窗。
     *
     * 如果刚认证过，说明 Keystore 认证窗口已过但用户仍然在操作，
     * 此时应该降级到主密码，而不是再次弹窗。
     *
     * @return true 表示刚认证过，false 表示可以再次认证
     */
    public boolean recentlyReauthenticated() {
        long now = System.currentTimeMillis();
        long timeSinceLastAuth = now - lastAuthTime;

        if (timeSinceLastAuth < AUTH_STORM_WINDOW_MS) {
            Log.d(TAG, "Recently authenticated: " + timeSinceLastAuth + "ms since last auth");
            return true;
        }
        return false;
    }

    /**
     * 更新最后认证时间
     *
     * 在成功使用 Keystore 后调用，用于防认证风暴。
     */
    public void updateLastAuthTime() {
        lastAuthTime = System.currentTimeMillis();
    }

    /**
     * 重置最后认证时间
     *
     * 通常在应用退出或会话结束时调用。
     */
    public void resetLastAuthTime() {
        lastAuthTime = 0;
        Log.d(TAG, "Last auth time reset");
    }

    /**
     * 获取连续失败次数
     *
     * @return 连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * 获取最后一次失败时间
     *
     * @return 最后一次失败时间（毫秒时间戳），0 表示从未失败
     */
    public long getLastFailureTime() {
        return lastFailureTime;
    }

    /**
     * 重置所有状态
     *
     * 通常在禁用生物识别或账户登出时调用。
     */
    public void reset() {
        lastFailureTime = 0;
        consecutiveFailures = 0;
        lockoutEndTime = 0;
        lastAuthTime = 0;
        Log.d(TAG, "Biometric state reset");
    }

    @NonNull
    @Override
    public String toString() {
        return "BiometricState{" +
                "lastFailureTime=" + lastFailureTime +
                ", consecutiveFailures=" + consecutiveFailures +
                ", lockoutEndTime=" + lockoutEndTime +
                ", lastAuthTime=" + lastAuthTime +
                '}';
    }
}
