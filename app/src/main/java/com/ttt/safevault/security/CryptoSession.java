package com.ttt.safevault.security;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import javax.crypto.SecretKey;

/**
 * 加密会话管理器（Level 0 - 会话层）
 *
 * 职责：
 * - 管理 DataKey 的内存缓存（会话解锁态）
 * - 提供会话状态查询
 * - 安全清除敏感数据
 *
 * 设计原则：
 * - DataKey 是"已通过主密码验证"的唯一凭证
 * - 不是字符串、不是 hash、不是 token
 * - 而是能否访问 DataKey
 *
 * 会话锁定机制（两种方式）：
 *
 * 1. 后台时间锁定（由 SafeVaultApplication 和 SessionGuard 管理）
 *    - 立即锁定模式：应用进入后台即锁定（timeoutMs == 0）
 *    - 超时锁定模式：后台超过设定时间后锁定
 *    - 从不锁定：不根据后台时间锁定
 *
 * 2. 会话超时锁定（由 CryptoSession 内部管理）
 *    - 当用户在应用内持续不活动时，会话自动过期
 *    - 注意：立即锁定模式不会触发会话超时检查，仅由后台时间管理
 *
 * 会话生命周期：
 * ┌─────────────────────────────────────────┐
 * │  主密码验证成功 → DataKey 进入内存        │
 * │       ↓                                  │
 * │  会话解锁态 (unlocked = true)            │
 * │       ↓                                  │
 * │  应用锁定/超时 → 清除 DataKey            │
 * │       ↓                                  │
 * │  会话锁定态 (unlocked = false)           │
 * └─────────────────────────────────────────┘
 *
 * @since SafeVault 3.3.0 (会话管理层)
 * @since SafeVault 3.6.0 (会话超时与会话锁定时间同步)
 * @since SafeVault 3.7.0 (修复立即锁定模式问题)
 */
public class CryptoSession {
    private static final String TAG = "CryptoSession";

    /** 单例实例 */
    private static volatile CryptoSession INSTANCE;

    /** 缓存的 DataKey（仅在解锁态存在）- 使用 SensitiveData 包装实现内存安全强化 */
    @Nullable
    private SensitiveData<byte[]> dataKey;

    /** 会话解锁状态 */
    private volatile boolean unlocked;

    /** 会话创建时间（用于超时检查） */
    private long sessionStartTime;

    /** 默认会话超时时间（毫秒）- 仅在无法获取配置时使用 */
    private static final long DEFAULT_SESSION_TIMEOUT_MS = 5 * 60 * 1000;

    private CryptoSession() {
        this.dataKey = null;
        this.unlocked = false;
        this.sessionStartTime = 0;
        Log.i(TAG, "CryptoSession 初始化");
    }

    /**
     * 获取 CryptoSession 单例
     */
    @NonNull
    public static CryptoSession getInstance() {
        if (INSTANCE == null) {
            synchronized (CryptoSession.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CryptoSession();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 检查会话是否已解锁
     *
     * 这是"用户已通过主密码验证"的唯一标准：
     * - 不是字符串
     * - 不是 hash
     * - 不是 token
     * - 而是能否访问 DataKey
     *
     * @return true 表示已解锁（DataKey 在内存中），false 表示未解锁
     */
    public boolean isUnlocked() {
        // 添加详细的调试日志
        Log.d(TAG, "isUnlocked() 被调用 - unlocked=" + unlocked + ", dataKey=" + (dataKey != null ? "非null" : "null") + ", sessionStartTime=" + sessionStartTime);

        if (!unlocked) {
            Log.w(TAG, "会话未解锁：unlocked=false");
            return false;
        }

        if (dataKey == null) {
            Log.w(TAG, "会话未解锁：dataKey=null（unlocked=true 但 dataKey 为 null，这是不一致状态）");
            // 修复不一致状态
            unlocked = false;
            return false;
        }

        // 检查 SensitiveData 是否已关闭
        if (dataKey.isClosed()) {
            Log.w(TAG, "会话未解锁：dataKey 已关闭（unlocked=true 但 dataKey 已关闭，这是不一致状态）");
            unlocked = false;
            return false;
        }

        // 检查会话是否超时
        if (isSessionExpired()) {
            long elapsed = System.currentTimeMillis() - sessionStartTime;
            long timeoutMs = getSessionTimeoutMs();
            Log.w(TAG, "会话已超时，自动锁定 - 已过去: " + (elapsed / 1000) + " 秒，超时阈值: " + (timeoutMs / 1000) + " 秒");
            clear();
            return false;
        }

        Log.d(TAG, "会话已解锁，剩余时间: " + getRemainingTimeSeconds() + " 秒");
        return true;
    }

    /**
     * 获取缓存的 DataKey（仅在解锁态可用）
     *
     * @return DataKey（SecretKey 对象），未解锁返回 null
     */
    @Nullable
    public SecretKey getDataKey() {
        return getDataKeyAsSecretKey();
    }

    /**
     * 获取缓存的 DataKey（仅在解锁态可用）
     *
     * @return DataKey 的字节数组（SecretKey 编码），未解锁返回 null
     * @deprecated 使用 {@link #getDataKey()} 或 {@link #getDataKeyAsSecretKey()} 获取 SecretKey 对象
     */
    @Nullable
    @Deprecated
    public byte[] getDataKeyBytes() {
        if (!isUnlocked()) {
            Log.w(TAG, "尝试获取 DataKey，但会话未解锁");
            return null;
        }
        return dataKey != null ? dataKey.get() : null;
    }

    /**
     * 获取缓存的 DataKey 作为 SecretKey（仅在解锁态可用）
     *
     * @return DataKey（SecretKey 对象），未解锁返回 null
     */
    @Nullable
    public SecretKey getDataKeyAsSecretKey() {
        if (!isUnlocked()) {
            Log.w(TAG, "尝试获取 DataKey，但会话未解锁");
            return null;
        }

        byte[] keyBytes = dataKey != null ? dataKey.get() : null;
        if (keyBytes == null) {
            return null;
        }

        // 创建 SecretKey 对象（注意：调用方负责安全处理）
        return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 设置 DataKey（解锁会话）
     *
     * 调用此方法表示用户已通过主密码验证，DataKey 进入内存
     *
     * @param dataKey 解密后的 DataKey（SecretKey 对象）
     */
    public void unlockWithDataKey(@NonNull SecretKey dataKey) {
        Log.d(TAG, "unlockWithDataKey() 被调用 - dataKey=" + (dataKey != null ? "非null" : "null"));

        // 获取密钥编码
        byte[] keyBytes = dataKey.getEncoded();
        if (keyBytes == null) {
            Log.e(TAG, "无法获取 DataKey 编码（可能是 AndroidKeyStore 密钥）");
            throw new IllegalArgumentException("DataKey 必须是可导出的密钥");
        }

        // 先清除旧的 DataKey（如果存在）
        if (this.dataKey != null) {
            Log.d(TAG, "清除旧的 DataKey");
            this.dataKey.close();
        }

        // 使用 SensitiveData 包装新的 DataKey
        this.dataKey = new SensitiveData<>(keyBytes);
        this.unlocked = true;
        this.sessionStartTime = System.currentTimeMillis();

        Log.i(TAG, "会话已解锁（DataKey 已使用 SensitiveData 包装） - sessionStartTime=" + sessionStartTime);
    }

    /**
     * 清除会话（锁定）
     *
     * 安全清除 DataKey，将内存清零（使用 SensitiveData.close()）
     */
    public void clear() {
        if (dataKey != null) {
            // 调用 SensitiveData.close() 执行安全清零
            dataKey.close();
            dataKey = null;
        }

        unlocked = false;
        sessionStartTime = 0;

        Log.i(TAG, "会话已清除（DataKey 已通过 SensitiveData 安全清零）");
    }

    /**
     * 锁定会话（别名方法，与 clear() 相同）
     *
     * 安全清除 DataKey，将内存清零
     */
    public void lock() {
        clear();
    }

    /**
     * 检查会话是否超时
     *
     * @return true 表示已超时，false 表示未超时
     */
    private boolean isSessionExpired() {
        if (sessionStartTime == 0) {
            return false;
        }

        long timeoutMs = getSessionTimeoutMs();

        // 特殊处理：立即锁定模式（timeoutMs == 0）
        // 在这种模式下，会话由后台时间管理，而不是超时时间
        // 这里返回 false，允许在应用内的正常操作
        if (timeoutMs == 0) {
            return false;
        }

        // 从不锁定模式（timeoutMs == Long.MAX_VALUE）
        if (timeoutMs == Long.MAX_VALUE) {
            return false;
        }

        // 其他模式：检查是否超时
        long elapsed = System.currentTimeMillis() - sessionStartTime;
        return elapsed > timeoutMs;
    }

    /**
     * 获取会话超时时间（毫秒）
     *
     * 从 SecurityConfig 读取用户配置的会话锁定时间，与用户设置保持一致。
     * 如果无法获取配置（如 Context 不可用），则使用默认值 5 分钟。
     *
     * 注意：
     * - 立即锁定模式返回 0（由后台时间管理，不使用超时检查）
     * - 从不锁定模式返回 Long.MAX_VALUE
     * - 其他模式返回具体的毫秒数
     *
     * @return 超时时间（毫秒），0 表示立即锁定，Long.MAX_VALUE 表示从不锁定
     */
    private long getSessionTimeoutMs() {
        try {
            // 尝试通过 ServiceLocator 获取 SecurityConfig
            com.ttt.safevault.ServiceLocator serviceLocator = com.ttt.safevault.ServiceLocator.getInstance();
            if (serviceLocator != null) {
                com.ttt.safevault.security.SecurityConfig securityConfig = serviceLocator.getSecurityConfig();
                if (securityConfig != null) {
                    return securityConfig.getAutoLockTimeoutMillisForMode();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "无法从 SecurityConfig 获取超时时间，使用默认值: " + e.getMessage());
        }
        // 返回默认值（5 分钟）
        return DEFAULT_SESSION_TIMEOUT_MS;
    }

    /**
     * 获取会话剩余时间（秒）
     *
     * @return 剩余秒数，已超时返回 0
     */
    public long getRemainingTimeSeconds() {
        if (!unlocked || sessionStartTime == 0) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - sessionStartTime;
        long remaining = getSessionTimeoutMs() - elapsed;

        return Math.max(0, remaining / 1000);
    }

    /**
     * 刷新会话时间（延长会话）
     *
     * 在用户执行敏感操作时调用，重置超时计时器
     */
    public void refreshSession() {
        if (isUnlocked()) {
            sessionStartTime = System.currentTimeMillis();
            Log.d(TAG, "会话已刷新");
        }
    }

    /**
     * 获取 DataKey（Guarded Execution 模式）
     *
     * 与 getDataKeyAsSecretKey() 的区别：
     * - getDataKeyAsSecretKey(): 返回 null（静默失败）
     * - requireDataKey(): 抛出 SessionLockedException（显式失败）
     *
     * 此方法用于 Guarded Execution 模式，确保调用方必须处理会话锁定的情况。
     *
     * @return DataKey（SecretKey 对象，保证非 null）
     * @throws SessionLockedException 如果会话未解锁
     */
    @NonNull
    public SecretKey requireDataKey() {
        if (!isUnlocked()) {
            Log.w(TAG, "requireDataKey: 会话未解锁，抛出 SessionLockedException");
            throw new SessionLockedException("会话未解锁，无法获取 DataKey");
        }

        byte[] keyBytes = dataKey != null ? dataKey.get() : null;
        if (keyBytes == null) {
            Log.e(TAG, "requireDataKey: SensitiveData 内部数据为 null（不一致状态）");
            throw new SessionLockedException("会话未解锁，无法获取 DataKey");
        }

        // 创建 SecretKey 对象
        return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 获取会话信息（用于调试）
     *
     * @return 会话状态描述
     */
    @NonNull
    public String getSessionInfo() {
        if (isUnlocked()) {
            return String.format("会话已解锁，剩余时间: %d 秒", getRemainingTimeSeconds());
        } else {
            return "会话未解锁";
        }
    }
}
