package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Log;

import com.ttt.safevault.security.SecurityConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * PIN码管理器
 * 负责PIN码的设置、验证和清除
 */
public class PinCodeManager {
    private static final String TAG = "PinCodeManager";
    private static final String PIN_SALT = "SafeVaultPinSalt2024";

    private final Context context;
    private final SecurityConfig securityConfig;
    private final android.content.SharedPreferences prefs;

    public PinCodeManager(Context context, SecurityConfig securityConfig) {
        this.context = context.getApplicationContext();
        this.securityConfig = securityConfig;
        this.prefs = context.getSharedPreferences("backend_prefs", Context.MODE_PRIVATE);
    }

    /**
     * 设置PIN码
     */
    public boolean setPinCode(String pinCode) {
        // 验证PIN码格式（4-6位数字）
        if (pinCode == null || !pinCode.matches("\\d{4,6}")) {
            Log.e(TAG, "Invalid PIN code format");
            return false;
        }

        try {
            // 哈希PIN码
            String hashedPin = hashPinCode(pinCode);

            prefs.edit()
                    .putString("pin_code", hashedPin)
                    .apply();

            // 标记PIN码已启用
            securityConfig.setPinCodeEnabled(true);

            Log.d(TAG, "PIN code set successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set PIN code", e);
            return false;
        }
    }

    /**
     * 验证PIN码
     */
    public boolean verifyPinCode(String pinCode) {
        String storedHashedPin = prefs.getString("pin_code", null);
        if (storedHashedPin == null) {
            return false;
        }

        String hashedInput = hashPinCode(pinCode);
        return storedHashedPin.equals(hashedInput);
    }

    /**
     * 清除PIN码
     */
    public boolean clearPinCode() {
        try {
            prefs.edit()
                    .remove("pin_code")
                    .apply();

            // 标记PIN码已禁用
            securityConfig.setPinCodeEnabled(false);

            Log.d(TAG, "PIN code cleared successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear PIN code", e);
            return false;
        }
    }

    /**
     * 检查PIN码是否已启用
     */
    public boolean isPinCodeEnabled() {
        return securityConfig.isPinCodeEnabled();
    }

    /**
     * 对PIN码进行哈希（使用SHA-256）
     */
    private String hashPinCode(String pinCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // 添加固定的盐值
            String saltedPin = pinCode + PIN_SALT;
            byte[] hash = digest.digest(saltedPin.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            Log.e(TAG, "Failed to hash PIN code", e);
            throw new RuntimeException("PIN码哈希失败", e);
        }
    }
}
