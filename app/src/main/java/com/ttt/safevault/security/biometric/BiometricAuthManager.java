package com.ttt.safevault.security.biometric;

import android.content.Context;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import com.ttt.safevault.security.BiometricAuthHelper;
import com.ttt.safevault.security.SecureKeyStorageManager;

import java.security.PrivateKey;
import java.util.concurrent.Executor;

/**
 * 生物识别认证管理器（Level 4 认证管理层）
 *
 * 职责：
 * - 认证决策与路由（生物识别/主密码/PIN）
 * - UI 管理（对话框、提示、降级）
 * - 业务级安全策略（失败次数、用户偏好）
 * - 状态管理（防抖动、锁定、防认证风暴）
 *
 * 架构位置：
 * ┌────────────────────────────────┐
 * │   BiometricAuthManager (Level 4) │  ← 本类
 * │  • 认证决策与路由                │
 * │  • UI 管理                       │
 * │  • 业务级安全策略                │
 * │  • 状态管理                      │
 * └────────────┬───────────────────┘
 *              │ 仅触发认证，不做授权判断
 *              ↓
 *      AndroidKeyStore（硬件级访问控制）
 *
 * 设计原则：
 * - 信任硬件，而不是软件实现
 * - 应用层不缓存"成功状态"，完全交给 Keystore 验证
 * - 分离 UI 认证和密钥授权（两阶段回调）
 * - 防止工程问题（防抖动、防认证风暴）
 *
 * @since SafeVault 3.3.0 (生物识别架构重构)
 */
public class BiometricAuthManager {
    private static final String TAG = "BiometricAuthManager";
    private static volatile BiometricAuthManager INSTANCE;

    private final Context context;
    private final SecureKeyStorageManager secureStorage;
    private final BiometricState state;
    private final Executor executor;

    private BiometricAuthManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.secureStorage = SecureKeyStorageManager.getInstance(context);
        this.state = new BiometricState();
        this.executor = java.util.concurrent.Executors.newSingleThreadExecutor();

        Log.i(TAG, "BiometricAuthManager initialized");
    }

    /**
     * 获取 BiometricAuthManager 单例
     *
     * @param context 上下文
     * @return BiometricAuthManager 实例
     */
    @NonNull
    public static BiometricAuthManager getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (BiometricAuthManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new BiometricAuthManager(context);
                }
            }
        }
        return INSTANCE;
    }

    // ========== 核心认证流程 ==========

    /**
     * 执行生物识别认证（主入口）
     *
     * 流程：
     * 1. 检查是否需要认证（shouldAuthenticate）
     * 2. 检查防抖动（shouldDebouncePrompt）
     * 3. 检查业务级锁定（isLockedOut）
     * 4. 触发生物识别 UI（triggerBiometricPrompt）
     *
     * @param activity 当前 Activity（用于显示 BiometricPrompt）
     * @param scenario 认证场景
     * @param callback 认证回调
     */
    public void authenticate(@NonNull FragmentActivity activity,
                            @NonNull AuthScenario scenario,
                            @NonNull AuthCallback callback) {
        Log.d(TAG, "Authentication requested for scenario: " + scenario.getCode());

        // 1. 检查是否需要认证
        if (!shouldAuthenticate(scenario)) {
            Log.w(TAG, "Authentication not required for scenario: " + scenario.getCode());
            callback.onFailure(AuthError.UNKNOWN, "认证条件不满足", false);
            return;
        }

        // 2. 检查防抖动
        if (state.shouldDebouncePrompt()) {
            Log.w(TAG, "Authentication debounced");
            callback.onFailure(AuthError.DEBOUNCED, "认证处理中，请稍候", true);
            return;
        }

        // 3. 检查业务级锁定
        if (state.isLockedOut()) {
            long remainingTime = state.getRemainingLockoutTime();
            Log.w(TAG, "Biometric locked out. Remaining time: " + remainingTime + "ms");
            callback.onFailure(AuthError.LOCKED_OUT,
                    "生物识别已被锁定，请 " + (remainingTime / 1000) + " 秒后再试或使用主密码",
                    true);
            return;
        }

        // 4. 触发生物识别 UI
        triggerBiometricPrompt(activity, scenario, callback);
    }

    /**
     * 检查是否应该执行生物识别认证
     *
     * @param scenario 认证场景
     * @return true 表示应该认证，false 表示不需要
     */
    private boolean shouldAuthenticate(@NonNull AuthScenario scenario) {
        // 检查设备是否支持生物识别
        if (!canUseBiometric()) {
            Log.w(TAG, "Device does not support biometric authentication");
            return false;
        }

        // 检查是否有必要的密钥数据
        if (!secureStorage.hasDeviceKey()) {
            Log.w(TAG, "DeviceKey not found, biometric authentication not available");
            return false;
        }

        if (!secureStorage.hasEncryptedDataKey()) {
            Log.w(TAG, "Encrypted DataKey not found, biometric authentication not available");
            return false;
        }

        return true;
    }

    /**
     * 触发生物识别 UI（BiometricPrompt）
     *
     * @param activity 当前 Activity
     * @param scenario 认证场景
     * @param callback 认证回调
     */
    private void triggerBiometricPrompt(@NonNull FragmentActivity activity,
                                       @NonNull AuthScenario scenario,
                                       @NonNull AuthCallback callback) {
        Log.d(TAG, "Triggering biometric prompt for scenario: " + scenario.getCode());

        BiometricAuthHelper helper = new BiometricAuthHelper(activity);

        helper.authenticate(new BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Biometric UI authentication successful");
                state.recordSuccess();
                callback.onUserVerified();

                // 尝试使用 Keystore 解锁
                tryUnlockWithKeystore(callback);
            }

            @Override
            public void onFailure(String error) {
                Log.w(TAG, "Biometric authentication failed: " + error);
                state.recordFailure();
                callback.onFailure(AuthError.AUTH_FAILED, error, true);
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "Biometric authentication cancelled by user");
                callback.onCancel();
            }
        });
    }

    // ========== 密钥访问流程 ==========

    /**
     * 尝试使用 Keystore 解锁
     *
     * 流程：
     * 1. 检查防认证风暴（recentlyReauthenticated）
     * 2. 调用 SecureKeyStorageManager.unlockWithBiometric()
     * 3. 根据结果调用回调
     *
     * 注意：KeyPermanentlyInvalidatedException 不会从此方法抛出，
     * 因为 SecureKeyStorageManager.unlockWithBiometric() 内部会捕获它并返回 null。
     *
     * @param callback 认证回调
     */
    private void tryUnlockWithKeystore(@NonNull AuthCallback callback) {
        Log.d(TAG, "Attempting to unlock with Keystore");

        // 检查防认证风暴
        if (state.recentlyReauthenticated()) {
            Log.w(TAG, "Recently reauthenticated but failed again, ROM auth window too short");
            state.resetLastAuthTime();
            callback.onFailure(AuthError.KEYSTORE_AUTH_EXPIRED,
                    "认证已过期，请使用主密码解锁",
                    true);
            return;
        }

        // 尝试使用生物识别解锁
        PrivateKey privateKey = secureStorage.unlockWithBiometric();

        if (privateKey != null) {
            Log.i(TAG, "Keystore unlock successful");
            state.updateLastAuthTime();
            callback.onKeyAccessGranted();
        } else {
            Log.w(TAG, "Keystore unlock returned null");
            // null 返回可能是因为：
            // 1. 认证窗口过期（需要重新认证）
            // 2. DeviceKey 被永久失效（生物识别变更）
            // 3. 其他错误
            callback.onFailure(AuthError.KEYSTORE_AUTH_EXPIRED,
                    "认证已过期，请重新验证",
                    true);
        }
    }

    /**
     * 触发重新认证
     *
     * 当 Keystore 认证过期时，再次触发生物识别 UI。
     *
     * @param activity 当前 Activity
     * @param callback 认证回调
     */
    public void triggerReauthentication(@NonNull FragmentActivity activity,
                                       @NonNull AuthCallback callback) {
        Log.d(TAG, "Triggering reauthentication");
        authenticate(activity, AuthScenario.QUICK_UNLOCK, callback);
    }

    // ========== 降级流程 ==========

    /**
     * 降级到主密码
     *
     * 当生物识别失败且不能重试时，调用此方法提示使用主密码。
     *
     * @param callback 认证回调
     */
    public void fallbackToPassword(@NonNull AuthCallback callback) {
        Log.d(TAG, "Falling back to password authentication");
        callback.onFallbackToPassword();
    }

    /**
     * 检查是否应该降级到主密码
     *
     * @param error 错误类型
     * @return true 表示应该降级，false 表示不需要
     */
    public boolean shouldFallbackToPassword(@NonNull AuthError error) {
        switch (error.getCategory()) {
            case PERMANENT:
            case SEVERE:
            case CONFIGURABLE:
                return true;
            default:
                return false;
        }
    }

    // ========== 工具方法 ==========

    /**
     * 检查是否可以使用生物识别
     *
     * @return true 表示可以使用，false 表示不能使用
     */
    public boolean canUseBiometric() {
        return BiometricAuthHelper.isBiometricSupported(context);
    }

    /**
     * 获取生物识别不可用的原因
     *
     * @return 原因描述
     */
    @NonNull
    public String getBiometricNotAvailableReason() {
        return BiometricAuthHelper.getBiometricNotSupportedReason(context);
    }

    /**
     * 禁用生物识别
     *
     * 清除所有生物识别相关状态和数据。
     */
    public void disableBiometric() {
        Log.d(TAG, "Disabling biometric authentication");

        // 重置状态
        state.reset();

        // 清除生物识别数据
        secureStorage.clearBiometricData();

        Log.i(TAG, "Biometric authentication disabled");
    }

    /**
     * 重置失败计数
     *
     * 通常在成功使用主密码解锁后调用。
     */
    public void resetFailures() {
        state.resetFailures();
    }

    /**
     * 获取当前状态
     *
     * @return BiometricState 实例
     */
    @NonNull
    public BiometricState getState() {
        return state;
    }

    /**
     * 获取 SecureKeyStorageManager 实例
     *
     * @return SecureKeyStorageManager 实例
     */
    @NonNull
    public SecureKeyStorageManager getSecureStorage() {
        return secureStorage;
    }

    /**
     * 获取连续失败次数
     *
     * @return 连续失败次数
     */
    public int getConsecutiveFailures() {
        return state.getConsecutiveFailures();
    }

    /**
     * 获取锁定剩余时间
     *
     * @return 锁定剩余时间（毫秒），未锁定返回 0
     */
    public long getRemainingLockoutTime() {
        return state.getRemainingLockoutTime();
    }

    /**
     * 检查是否被锁定
     *
     * @return true 表示被锁定，false 表示未锁定
     */
    public boolean isLockedOut() {
        return state.isLockedOut();
    }
}
