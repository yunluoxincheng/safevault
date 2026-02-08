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
 */
public class CryptoSession {
    private static final String TAG = "CryptoSession";

    /** 单例实例 */
    private static volatile CryptoSession INSTANCE;

    /** 缓存的 DataKey（仅在解锁态存在） */
    @Nullable
    private SecretKey dataKey;

    /** 会话解锁状态 */
    private volatile boolean unlocked;

    /** 会话创建时间（用于超时检查） */
    private long sessionStartTime;

    /** 会话超时时间（毫秒，默认 5 分钟） */
    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000;

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

        // 检查会话是否超时
        if (isSessionExpired()) {
            long elapsed = System.currentTimeMillis() - sessionStartTime;
            Log.w(TAG, "会话已超时，自动锁定 - 已过去: " + (elapsed / 1000) + " 秒，超时阈值: " + (SESSION_TIMEOUT_MS / 1000) + " 秒");
            clear();
            return false;
        }

        Log.d(TAG, "会话已解锁，剩余时间: " + getRemainingTimeSeconds() + " 秒");
        return true;
    }

    /**
     * 获取缓存的 DataKey（仅在解锁态可用）
     *
     * @return DataKey，未解锁返回 null
     */
    @Nullable
    public SecretKey getDataKey() {
        if (!isUnlocked()) {
            Log.w(TAG, "尝试获取 DataKey，但会话未解锁");
            return null;
        }
        return dataKey;
    }

    /**
     * 设置 DataKey（解锁会话）
     *
     * 调用此方法表示用户已通过主密码验证，DataKey 进入内存
     *
     * @param dataKey 解密后的 DataKey
     */
    public void unlockWithDataKey(@NonNull SecretKey dataKey) {
        Log.d(TAG, "unlockWithDataKey() 被调用 - dataKey=" + (dataKey != null ? "非null" : "null"));

        // 先清除旧的 DataKey（如果存在）
        if (this.dataKey != null) {
            Log.d(TAG, "清除旧的 DataKey");
            zeroize(this.dataKey);
        }

        this.dataKey = dataKey;
        this.unlocked = true;
        this.sessionStartTime = System.currentTimeMillis();

        Log.i(TAG, "会话已解锁（DataKey 已缓存） - sessionStartTime=" + sessionStartTime);
    }

    /**
     * 清除会话（锁定）
     *
     * 安全清除 DataKey，将内存清零
     */
    public void clear() {
        if (dataKey != null) {
            zeroize(dataKey);
            dataKey = null;
        }

        unlocked = false;
        sessionStartTime = 0;

        Log.i(TAG, "会话已清除（DataKey 已从内存移除）");
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

        long elapsed = System.currentTimeMillis() - sessionStartTime;
        return elapsed > SESSION_TIMEOUT_MS;
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
        long remaining = SESSION_TIMEOUT_MS - elapsed;

        return Math.max(0, remaining / 1000);
    }

    /**
     * 安全清零 SecretKey
     *
     * 尝试清除密钥的内存内容（尽最大努力）
     *
     * @param key 待清除的密钥
     */
    private void zeroize(@NonNull SecretKey key) {
        try {
            byte[] keyBytes = key.getEncoded();
            if (keyBytes != null) {
                // 用随机数据覆写密钥内存
                java.security.SecureRandom random = new java.security.SecureRandom();
                random.nextBytes(keyBytes);
                // 再次用零覆写
                java.util.Arrays.fill(keyBytes, (byte) 0);
            }
            Log.d(TAG, "SecretKey 已安全清零");
        } catch (Exception e) {
            Log.w(TAG, "清零 SecretKey 时出现异常（可能已被 GC 回收）", e);
        }
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
     * 与 getDataKey() 的区别：
     * - getDataKey(): 返回 null（静默失败）
     * - requireDataKey(): 抛出 SessionLockedException（显式失败）
     *
     * 此方法用于 Guarded Execution 模式，确保调用方必须处理会话锁定的情况。
     *
     * @return DataKey（保证非 null）
     * @throws SessionLockedException 如果会话未解锁
     */
    @NonNull
    public SecretKey requireDataKey() {
        if (!isUnlocked()) {
            Log.w(TAG, "requireDataKey: 会话未解锁，抛出 SessionLockedException");
            throw new SessionLockedException("会话未解锁，无法获取 DataKey");
        }
        return dataKey;
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
