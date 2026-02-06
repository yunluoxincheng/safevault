package com.ttt.safevault.security.biometric;

import androidx.annotation.NonNull;

/**
 * 生物识别认证回调接口
 *
 * 采用两阶段回调设计，清晰区分 UI 认证和密钥授权：
 * 1. onUserVerified() - 用户通过 UI 认证（弱语义）
 * 2. onKeyAccessGranted() - Keystore 授权成功（强语义）
 *
 * 这种设计防止误用：UI 认证成功 ≠ 密钥可用（Keystore 可能已过期）
 *
 * 使用示例：
 * <pre>
 * BiometricAuthManager.authenticate(AuthScenario.QUICK_UNLOCK, new AuthCallback() {
 *     @Override
 *     public void onUserVerified() {
 *         // 此时不能直接访问敏感数据
 *         // 尝试使用 DeviceKey 解密
 *         try {
 *             PrivateKey key = secureStorage.unlockWithBiometric();
 *             if (key != null) {
 *                 onKeyAccessGranted();
 *             }
 *         } catch (KeyPermanentlyInvalidatedException e) {
 *             onBiometricChanged();
 *         }
 *     }
 *
 *     @Override
 *     public void onKeyAccessGranted() {
 *         // 现在可以安全访问敏感数据
 *         showPasswords();
 *     }
 * });
 * </pre>
 *
 * @since SafeVault 3.3.0 (生物识别架构重构)
 */
public interface AuthCallback {

    /**
     * 用户通过 UI 认证（阶段 1 - 弱语义）
     *
     * 表示用户通过了生物识别 UI 认证（指纹识别成功等）。
     * 但此时密钥可能仍然不可用（Keystore 认证窗口已过）。
     *
     * 在此回调中：
     * - ❌ 不要直接访问敏感数据
     * - ✅ 尝试使用 DeviceKey 解密
     * - ✅ 根据结果调用 onKeyAccessGranted() 或 onFailure()
     */
    void onUserVerified();

    /**
     * Keystore 授权成功（阶段 2 - 强语义）
     *
     * 表示 Keystore 已成功授权密钥使用，可以安全访问敏感数据。
     *
     * 在此回调中：
     * - ✅ 可以安全访问敏感数据
     * - ✅ 可以执行解密操作
     */
    void onKeyAccessGranted();

    /**
     * 认证失败
     *
     * @param error 错误类型
     * @param message 错误消息（可展示给用户）
     * @param canRetry 是否可以重试（true 表示可以重试，false 表示需要降级到主密码）
     */
    void onFailure(@NonNull AuthError error, @NonNull String message, boolean canRetry);

    /**
     * 用户取消认证
     *
     * 用户主动点击取消按钮或返回键。
     * UI 层可以选择是否提示或直接返回。
     */
    void onCancel();

    /**
     * 生物识别信息已变更
     *
     * 用户添加了新指纹、面部或清除了生物识别数据，
     * 导致 DeviceKey 被系统标记为永久失效。
     *
     * UI 层应该：
     * 1. 提示用户"生物识别信息已变更"
     * 2. 引导用户重新启用生物识别
     * 3. 降级到主密码解锁
     */
    void onBiometricChanged();

    /**
     * 降级到主密码（可选实现）
     *
     * 当生物认证失败且不能重试时，调用此方法提示使用主密码。
     * UI 层可以：
     * 1. 自动切换到主密码输入界面
     * 2. 显示"请使用主密码解锁"提示
     *
     * 默认实现为空，UI 层可选择实现。
     */
    default void onFallbackToPassword() {
        // 默认空实现，UI 层可选择实现
    }
}
