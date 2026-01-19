package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.crypto.CryptoManager;
import com.ttt.safevault.dto.DeviceRecoveryResult;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.security.BiometricKeyManager;
import com.ttt.safevault.security.KeyManager;
import com.ttt.safevault.security.SecurityConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.Cipher;

/**
 * 账户管理器
 * 负责账户操作，包括删除账户、登出、生物识别等
 */
public class AccountManager {
    private static final String TAG = "AccountManager";
    private static final String PREF_BIOMETRIC_ENCRYPTED_PASSWORD = "biometric_encrypted_password";
    private static final String PREF_BIOMETRIC_IV = "biometric_iv";
    private static final String KEY_USER_EMAIL = "user_email";

    private final Context context;
    private final CryptoManager cryptoManager;
    private final PasswordManager passwordManager;
    private final SecurityConfig securityConfig;
    private final android.content.SharedPreferences prefs;
    private final BiometricKeyManager biometricKeyManager;
    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;

    // 会话期间临时存储的主密码（用于当前会话）
    private String sessionMasterPassword;

    public AccountManager(@NonNull Context context,
                         @NonNull CryptoManager cryptoManager,
                         @NonNull PasswordManager passwordManager,
                         @NonNull SecurityConfig securityConfig,
                         @NonNull RetrofitClient retrofitClient) {
        this.context = context.getApplicationContext();
        this.cryptoManager = cryptoManager;
        this.passwordManager = passwordManager;
        this.securityConfig = securityConfig;
        this.retrofitClient = retrofitClient;
        this.tokenManager = retrofitClient.getTokenManager();
        this.prefs = context.getSharedPreferences("backend_prefs", Context.MODE_PRIVATE);

        // 初始化生物识别密钥管理器
        BiometricKeyManager keyManager = null;
        try {
            keyManager = BiometricKeyManager.getInstance();
            keyManager.initializeKey();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize biometric key manager", e);
        }
        this.biometricKeyManager = keyManager;
    }

    /**
     * 删除账户
     */
    public boolean deleteAccount() {
        try {
            Log.d(TAG, "Starting account deletion...");

            // 1. 调用后端API删除云端数据
            String token = tokenManager.getAccessToken();
            if (token != null && !token.isEmpty()) {
                try {
                    com.ttt.safevault.dto.response.DeleteAccountResponse response =
                        retrofitClient.getAuthServiceApi()
                            .deleteAccount("Bearer " + token)
                            .blockingFirst();

                    if (response != null && response.isSuccess()) {
                        Log.d(TAG, "Cloud account deleted successfully");
                    } else {
                        Log.w(TAG, "Cloud account deletion failed: " +
                            (response != null ? response.getMessage() : "Unknown error"));
                        // 继续删除本地数据
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to delete cloud account", e);
                    // 继续删除本地数据
                }
            } else {
                Log.w(TAG, "No access token, skipping cloud deletion");
            }

            // 2. 删除所有本地密码数据
            List<com.ttt.safevault.model.PasswordItem> items = passwordManager.getAllItems();
            for (com.ttt.safevault.model.PasswordItem item : items) {
                passwordManager.deleteItem(item.getId());
            }
            Log.d(TAG, "Local password data deleted");

            // 3. 清除加密密钥和生物识别数据
            cryptoManager.lock();
            clearBiometricData();
            Log.d(TAG, "Encryption keys cleared");

            // 4. 清除所有设置和Token
            securityConfig.clear();
            tokenManager.clearTokens();
            prefs.edit().clear().apply();
            Log.d(TAG, "Settings and tokens cleared");

            Log.d(TAG, "Account deletion completed successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete account", e);
            return false;
        }
    }

    /**
     * 登出
     */
    public void logout() {
        cryptoManager.lock();
        // 清除内存中的敏感数据
    }

    /**
     * 使用生物识别解锁
     */
    public boolean unlockWithBiometric() {
        // 检查生物识别是否启用
        if (!securityConfig.isBiometricEnabled()) {
            Log.e(TAG, "Biometric not enabled");
            return false;
        }

        // 检查是否已初始化
        if (!cryptoManager.isInitialized()) {
            Log.e(TAG, "Crypto manager not initialized");
            return false;
        }

        // 获取保存的加密主密码
        String masterPassword = getMasterPasswordForBiometric();
        if (masterPassword == null) {
            Log.e(TAG, "No master password stored for biometric unlock");
            return false;
        }

        // 使用主密码解锁
        boolean success = cryptoManager.unlock(masterPassword);

        // 解锁成功后，保存会话密码用于后续操作（如自动填充）
        if (success) {
            setSessionMasterPassword(masterPassword);
            // 保存密码供自动填充服务使用
            savePasswordForAutofill(masterPassword);
            Log.d(TAG, "Biometric unlock successful, session password set");
        }

        return success;
    }

    /**
     * 保存密码供自动填充服务使用
     */
    private void savePasswordForAutofill(String password) {
        try {
            context.getSharedPreferences("autofill_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("master_password", password)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save password for autofill", e);
        }
    }

    /**
     * 检查是否可以使用生物识别认证
     */
    public boolean canUseBiometricAuthentication() {
        return securityConfig.isBiometricEnabled() && hasMasterPasswordForBiometric();
    }

    /**
     * 保存主密码用于生物识别解锁
     */
    public void saveMasterPasswordForBiometric(String masterPassword) {
        if (biometricKeyManager == null) {
            Log.e(TAG, "BiometricKeyManager not initialized");
            return;
        }

        try {
            // 获取加密Cipher
            Cipher cipher = biometricKeyManager.getEncryptCipher();

            // 加密主密码
            byte[] encrypted = cipher.doFinal(masterPassword.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();

            // 保存加密数据
            prefs.edit()
                .putString(PREF_BIOMETRIC_ENCRYPTED_PASSWORD,
                    android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
                .putString(PREF_BIOMETRIC_IV,
                    android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
                .apply();

            Log.d(TAG, "Master password saved for biometric unlock");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save master password for biometric", e);
        }
    }

    /**
     * 获取用于生物识别解锁的主密码
     */
    private String getMasterPasswordForBiometric() {
        if (biometricKeyManager == null) {
            Log.e(TAG, "BiometricKeyManager not initialized");
            return null;
        }

        try {
            String encryptedPassword = prefs.getString(PREF_BIOMETRIC_ENCRYPTED_PASSWORD, null);
            String ivString = prefs.getString(PREF_BIOMETRIC_IV, null);

            if (encryptedPassword == null || ivString == null) {
                Log.e(TAG, "No encrypted password or IV found");
                return null;
            }

            byte[] encrypted = android.util.Base64.decode(encryptedPassword, android.util.Base64.NO_WRAP);
            byte[] iv = android.util.Base64.decode(ivString, android.util.Base64.NO_WRAP);

            // 获取解密Cipher
            Cipher cipher = biometricKeyManager.getDecryptCipher(iv);

            // 解密主密码
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt master password for biometric", e);
            // 解密失败，可能是密钥已重建，清除旧数据
            clearBiometricData();
            return null;
        }
    }

    /**
     * 清除生物识别加密数据
     */
    private void clearBiometricData() {
        prefs.edit()
            .remove(PREF_BIOMETRIC_ENCRYPTED_PASSWORD)
            .remove(PREF_BIOMETRIC_IV)
            .apply();
        Log.d(TAG, "Biometric data cleared");
    }

    /**
     * 检查是否有保存的生物识别密码
     */
    private boolean hasMasterPasswordForBiometric() {
        return prefs.contains(PREF_BIOMETRIC_ENCRYPTED_PASSWORD) &&
               prefs.contains(PREF_BIOMETRIC_IV);
    }

    // ========== 新设备数据恢复 ==========

    /**
     * 恢复新设备数据
     * 从云端下载加密的私钥，解密并导入到本地
     * 如果云端没有私钥，则生成新的密钥对并上传
     *
     * @param email         用户邮箱
     * @param masterPassword 主密码
     * @return 恢复结果
     */
    public DeviceRecoveryResult recoverDeviceData(String email, String masterPassword) {
        Log.d(TAG, "Starting device data recovery for email: " + email);

        try {
            // 步骤1：下载加密的私钥
            Log.d(TAG, "Step 1: Downloading encrypted private key...");
            EncryptionSyncManager syncManager = new EncryptionSyncManager(context, retrofitClient);
            KeyManager.EncryptedPrivateKey encryptedKey = syncManager.downloadEncryptedPrivateKey();

            // 如果云端没有私钥（旧账号），生成新的密钥对并上传
            if (encryptedKey == null) {
                Log.w(TAG, "No encrypted private key found in cloud, generating new key pair");
                return generateAndUploadNewKeyPair(email, masterPassword);
            }

            Log.d(TAG, "Encrypted private key downloaded successfully");

            // 步骤2：解密私钥
            Log.d(TAG, "Step 2: Decrypting private key...");
            KeyManager keyManager = KeyManager.getInstance(context);
            java.security.PrivateKey privateKey;

            try {
                privateKey = keyManager.decryptPrivateKey(
                        encryptedKey.getEncryptedData(),
                        masterPassword,
                        encryptedKey.getSalt(),  // 使用云端返回的盐值
                        encryptedKey.getIv()
                );
                Log.d(TAG, "Private key decrypted successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to decrypt private key", e);
                return DeviceRecoveryResult.decryptError("可能是主密码错误或数据损坏");
            }

            // 步骤3：验证私钥
            Log.d(TAG, "Step 3: Verifying private key...");
            if (privateKey == null) {
                Log.e(TAG, "Decrypted private key is null");
                return DeviceRecoveryResult.importError("私钥解密后为空");
            }

            Log.d(TAG, "Device data recovery completed successfully");
            return DeviceRecoveryResult.success(DeviceRecoveryResult.Stage.COMPLETE);

        } catch (Exception e) {
            Log.e(TAG, "Device data recovery failed", e);
            return DeviceRecoveryResult.failure("数据恢复失败：" + e.getMessage(), DeviceRecoveryResult.Stage.DOWNLOAD_KEY);
        }
    }

    /**
     * 生成新的密钥对并上传到云端
     * 用于旧账号没有私钥的情况
     *
     * @param email         用户邮箱
     * @param masterPassword 主密码
     * @return 恢复结果
     */
    private DeviceRecoveryResult generateAndUploadNewKeyPair(String email, String masterPassword) {
        Log.d(TAG, "Generating new key pair for email: " + email);

        try {
            KeyManager keyManager = KeyManager.getInstance(context);

            // 获取当前私钥（KeyManager 已经自动生成了密钥对）
            java.security.PrivateKey privateKey = keyManager.getPrivateKey();
            if (privateKey == null) {
                return DeviceRecoveryResult.failure("生成密钥对失败", DeviceRecoveryResult.Stage.IMPORT_KEY);
            }

            // 加密私钥并上传到云端
            KeyManager.EncryptedPrivateKey encryptedKey = keyManager.encryptPrivateKey(
                    privateKey, masterPassword, email);

            EncryptionSyncManager syncManager = new EncryptionSyncManager(context, retrofitClient);
            boolean uploaded = syncManager.uploadEncryptedPrivateKey(
                    encryptedKey.getEncryptedData(),
                    encryptedKey.getIv(),
                    encryptedKey.getSalt()
            );

            if (uploaded) {
                Log.d(TAG, "New key pair generated and uploaded successfully");
                return DeviceRecoveryResult.success(DeviceRecoveryResult.Stage.COMPLETE);
            } else {
                Log.e(TAG, "Failed to upload encrypted private key");
                return DeviceRecoveryResult.failure("上传私钥到云端失败", DeviceRecoveryResult.Stage.IMPORT_KEY);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to generate and upload new key pair", e);
            return DeviceRecoveryResult.failure("生成密钥对失败：" + e.getMessage(), DeviceRecoveryResult.Stage.IMPORT_KEY);
        }
    }

    /**
     * 启用生物识别认证
     *
     * @param masterPassword 主密码
     * @return 是否成功启用
     */
    public boolean enableBiometricAuth(String masterPassword) {
        Log.d(TAG, "Enabling biometric authentication");

        try {
            // 1. 检查设备是否支持生物识别
            if (!com.ttt.safevault.security.BiometricAuthHelper.isBiometricSupported(context)) {
                Log.w(TAG, "Device does not support biometric authentication");
                return false;
            }

            // 2. 保存主密码
            saveMasterPasswordForBiometric(masterPassword);

            // 3. 更新设置
            securityConfig.setBiometricEnabled(true);

            Log.d(TAG, "Biometric authentication enabled successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable biometric authentication", e);
            return false;
        }
    }

    /**
     * 禁用生物识别认证
     */
    public void disableBiometricAuth() {
        Log.d(TAG, "Disabling biometric authentication");

        // 1. 清除保存的主密码
        clearBiometricData();

        // 2. 更新设置
        securityConfig.setBiometricEnabled(false);

        Log.d(TAG, "Biometric authentication disabled");
    }

    // ========== 账户信息获取 ==========

    /**
     * 获取当前用户邮箱
     *
     * @return 当前登录用户的邮箱，未登录返回空字符串
     */
    @NonNull
    public String getCurrentUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    /**
     * 设置当前用户邮箱
     *
     * @param email 用户邮箱
     */
    public void setCurrentUserEmail(@NonNull String email) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply();
        Log.d(TAG, "Current user email saved: " + email);
    }

    /**
     * 获取当前主密码
     * 注意：这个方法返回的是临时存储的主密码（仅用于当前会话）
     *
     * @return 当前会话的主密码，未设置返回null
     */
    @Nullable
    public String getCurrentMasterPassword() {
        return sessionMasterPassword;
    }

    /**
     * 设置当前会话的主密码
     * 注意：主密码仅在内存中临时存储，不持久化到磁盘
     *
     * @param masterPassword 主密码
     */
    public void setSessionMasterPassword(@NonNull String masterPassword) {
        this.sessionMasterPassword = masterPassword;
        Log.d(TAG, "Session master password set");
    }

    /**
     * 清除当前会话的主密码
     */
    public void clearSessionMasterPassword() {
        this.sessionMasterPassword = null;
        Log.d(TAG, "Session master password cleared");
    }
}
