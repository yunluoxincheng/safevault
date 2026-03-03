package com.ttt.safevault.security;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import javax.crypto.SecretKey;

import java.util.function.Supplier;

/**
 * 会话管理器
 *
 * 职责：
 * - 管理 DataKey 的内存缓存（会话解锁态）
 * - 提供会话状态查询
 * - 安全清除敏感数据
 * - 会话锁定检查（基于后台时间）
 *
 * 设计原则：
 * - DataKey 是"已通过主密码验证"的唯一凭证
 * - 会话锁定只通过后台时间判断，不在应用内倒计时
 * - 统一的会话状态管理和安全边界
 *
 * 会话锁定机制（基于后台时间）：
 * - 立即锁定模式：应用进入后台即锁定（timeoutMs == 0）
 * - 超时锁定模式：后台超过设定时间后锁定
 * - 从不锁定：不锁定
 *
 * 会话生命周期：
 * ┌─────────────────────────────────────────┐
 * │  主密码验证成功 → DataKey 进入内存        │
 * │       ↓                                  │
 * │  会话解锁态 (unlocked = true)            │
 * │       ↓                                  │
 * │  应用进入后台超过设定时间 → 清除 DataKey  │
 * │       ↓                                  │
 * │  会话锁定态 (unlocked = false)           │
 * └─────────────────────────────────────────┘
 *
 * @since SafeVault 3.8.0 (合并 CryptoSession 功能)
 */
public class SessionGuard {
    private static final String TAG = "SessionGuard";

    /** 单例实例 */
    private static volatile SessionGuard INSTANCE;

    /** 缓存的 DataKey（仅在解锁态存在）- 使用 SensitiveData 包装实现内存安全强化 */
    @Nullable
    private SensitiveData<byte[]> dataKey;

    /** 会话解锁状态 */
    private volatile boolean unlocked;

    /** SecurityConfig 引用（用于获取会话锁定超时设置） */
    private SecurityConfig securityConfig;

    private SessionGuard() {
        this.dataKey = null;
        this.unlocked = false;
        Log.i(TAG, "SessionGuard 初始化");
    }

    /**
     * 获取 SessionGuard 单例
     */
    @NonNull
    public static SessionGuard getInstance() {
        if (INSTANCE == null) {
            synchronized (SessionGuard.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SessionGuard();
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
        if (!unlocked) {
            Log.d(TAG, "会话未解锁：unlocked=false");
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

        return true;
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
        return isUnlocked();
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

        Log.i(TAG, "会话已解锁（DataKey 已使用 SensitiveData 包装）");
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
     * 锁定会话（别名方法，与 lock() 相同）
     *
     * 清除 DataKey，将所有敏感操作标记为需要重新认证
     */
    public void lockSession() {
        lock();
        Log.i(TAG, "会话已锁定（通过 SessionGuard）");
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
            return "会话已解锁";
        } else {
            return "会话未解锁";
        }
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
        if (!isUnlocked()) {
            Log.w(TAG, "runWithUnlockedSession: 会话未解锁，拒绝执行敏感操作");
            throw new SessionLockedException("会话未解锁，无法执行敏感操作。请先通过主密码验证。");
        }

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
}