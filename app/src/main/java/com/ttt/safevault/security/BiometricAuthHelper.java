package com.ttt.safevault.security;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import java.util.concurrent.Executor;

public class BiometricAuthHelper {

    public interface BiometricAuthCallback {
        void onSuccess();
        void onFailure(String error);
        void onCancel();
    }

    private final FragmentActivity activity;
    private final Executor executor;
    private final BiometricPrompt.PromptInfo promptInfo;

    public BiometricAuthHelper(@NonNull FragmentActivity activity) {
        this.activity = activity;
        this.executor = ContextCompat.getMainExecutor(activity);

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("生物识别解锁")
                .setSubtitle("使用指纹或面部识别解锁SafeVault")
                .setDescription("触摸传感器进行身份验证")
                .setNegativeButtonText("取消")
                .build();
    }

    /**
     * 检查设备是否支持生物识别认证
     */
    public static boolean isBiometricSupported(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        BiometricManager biometricManager = BiometricManager.from(context);
        int canAuthenticateResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | 
                                                                   BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        
        return canAuthenticateResult == BiometricManager.BIOMETRIC_SUCCESS;
    }
    
    /**
     * 获取生物识别不可用的原因
     */
    public static String getBiometricNotSupportedReason(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return "设备版本过低，不支持生物识别";
        }

        BiometricManager biometricManager = BiometricManager.from(context);
        int canAuthenticateResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | 
                                                                   BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        
        switch (canAuthenticateResult) {
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return "设备没有生物识别硬件";
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return "生物识别硬件不可用";
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return "未设置生物识别信息，请在系统设置中添加";
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
                return "需要安全更新";
            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
                return "设备不支持生物识别";
            case BiometricManager.BIOMETRIC_STATUS_UNKNOWN:
                return "未知状态";
            default:
                return "生物识别不可用";
        }
    }

    /**
     * 启动生物识别认证（不使用 CryptoObject）
     */
    public void authenticate(BiometricAuthCallback callback) {
        authenticate(null, callback);
    }

    /**
     * 启动生物识别认证（支持 CryptoObject）
     * @param cryptoObject 可选的加密对象，用于授权密钥使用
     * @param callback 认证回调
     */
    public void authenticate(BiometricPrompt.CryptoObject cryptoObject, BiometricAuthCallback callback) {
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            callback.onCancel();
                        } else {
                            callback.onFailure(errString.toString());
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        // 传递 CryptoObject 给回调，以便使用已授权的 Cipher
                        callback.onSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        callback.onFailure("生物识别认证失败，请重试");
                    }
                });

        // 如果提供了 CryptoObject，使用它来认证（这样会授权密钥）
        if (cryptoObject != null) {
            biometricPrompt.authenticate(promptInfo, cryptoObject);
        } else {
            biometricPrompt.authenticate(promptInfo);
        }
    }
}