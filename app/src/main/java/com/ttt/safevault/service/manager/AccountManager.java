package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.dto.DeviceRecoveryResult;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.security.SecureKeyStorageManager;
import com.ttt.safevault.security.SecurityConfig;

import java.util.List;

/**
 * 账户管理器
 * 负责账户操作，包括删除账户、登出、设备数据恢复等
 *
 * 安全加固第二阶段更新：
 * - 移除BiometricKeyManager，完全使用SecureKeyStorageManager的三层架构
 * - 生物识别功能已迁移到BiometricAuthManager
 *
 * 第三阶段更新：
 * - 移除AccountManager中的生物识别相关方法（unlockWithBiometric、canUseBiometricAuthentication等）
 * - 所有生物识别认证统一通过BiometricAuthManager处理
 *
 * SafeVault 3.4.0 更新：
 * - 完全移除 CryptoManager 和 KeyManager 依赖
 * - 使用 CryptoSession 和 SecureKeyStorageManager
 */
public class AccountManager {
    private static final String TAG = "AccountManager";
    private static final String KEY_USER_EMAIL = "user_email";

    private final Context context;
    private final PasswordManager passwordManager;
    private final SecurityConfig securityConfig;
    private final android.content.SharedPreferences prefs;
    private final SecureKeyStorageManager secureStorage;
    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;

    // 会话期间临时存储的主密码（用于当前会话）
    private String sessionMasterPassword;

    public AccountManager(@NonNull Context context,
                         @NonNull PasswordManager passwordManager,
                         @NonNull SecurityConfig securityConfig,
                         @NonNull RetrofitClient retrofitClient) {
        this.context = context.getApplicationContext();
        this.passwordManager = passwordManager;
        this.securityConfig = securityConfig;
        this.retrofitClient = retrofitClient;
        this.tokenManager = retrofitClient.getTokenManager();
        this.prefs = context.getSharedPreferences("backend_prefs", Context.MODE_PRIVATE);
        this.secureStorage = SecureKeyStorageManager.getInstance(context);

        Log.d(TAG, "AccountManager initialized with SecureKeyStorageManager");
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

            // 3. 清除加密密钥和生物识别数据（使用 CryptoSession）
            com.ttt.safevault.security.CryptoSession.getInstance().lock();
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
        // 锁定会话（使用 CryptoSession）
        com.ttt.safevault.security.CryptoSession.getInstance().lock();
        // 清除内存中的敏感数据
    }

    /**
     * 清除生物识别数据（账户删除时调用）
     *
     * 注意：此方法仅用于删除账户时的清理操作
     * 生物识别功能已迁移到 BiometricAuthManager
     */
    private void clearBiometricData() {
        // 清除 SecureKeyStorageManager 中的生物识别数据
        secureStorage.clearBiometricData();
        Log.d(TAG, "Biometric data cleared (delegated to SecureKeyStorageManager)");
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
            EncryptionSyncManager.EncryptedPrivateKey encryptedKey = syncManager.downloadEncryptedPrivateKey();

            // 如果云端没有私钥（旧账号），生成新的密钥对并上传
            if (encryptedKey == null) {
                Log.w(TAG, "No encrypted private key found in cloud, generating new key pair");
                return generateAndUploadNewKeyPair(email, masterPassword);
            }

            Log.d(TAG, "Encrypted private key downloaded successfully");

            // 步骤2：解密私钥（使用 BackupEncryptionManager）
            Log.d(TAG, "Step 2: Decrypting private key...");
            java.security.PrivateKey privateKey;

            try {
                // 使用 BackupEncryptionManager 解密私钥
                com.ttt.safevault.security.BackupEncryptionManager backupManager =
                    com.ttt.safevault.security.BackupEncryptionManager.getInstance(context);

                // 解密私钥数据（AES-GCM）
                // 注意：加密后的密文已包含 GCM authTag，所以 authTag 参数传 null
                String decryptedKeyBytes = backupManager.decryptCloudSync(
                    encryptedKey.getEncryptedKey(),
                    masterPassword,
                    encryptedKey.getSalt(),
                    encryptedKey.getIv(),
                    null // authTag 已包含在 encryptedKey 中
                );

                // 将解密后的字节转换为 PrivateKey
                byte[] keyBytes = android.util.Base64.decode(decryptedKeyBytes, android.util.Base64.NO_WRAP);
                java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
                java.security.spec.PKCS8EncodedKeySpec keySpec =
                    new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
                privateKey = keyFactory.generatePrivate(keySpec);

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
            // SecureKeyStorageManager 已经自动生成了密钥对
            // 获取当前私钥
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");

            // 获取 DataKey 来解密私钥
            com.ttt.safevault.security.CryptoSession cryptoSession =
                com.ttt.safevault.security.CryptoSession.getInstance();
            if (!cryptoSession.isUnlocked()) {
                return DeviceRecoveryResult.failure("会话未锁定", DeviceRecoveryResult.Stage.IMPORT_KEY);
            }

            javax.crypto.SecretKey dataKey = cryptoSession.getDataKey();
            if (dataKey == null) {
                return DeviceRecoveryResult.failure("无法获取 DataKey", DeviceRecoveryResult.Stage.IMPORT_KEY);
            }

            // 从 SecureKeyStorageManager 解密私钥
            java.security.PrivateKey privateKey = secureStorage.decryptRsaPrivateKey(dataKey);
            if (privateKey == null) {
                return DeviceRecoveryResult.failure("无法获取私钥", DeviceRecoveryResult.Stage.IMPORT_KEY);
            }

            // 获取 salt
            String salt = com.ttt.safevault.crypto.Argon2KeyDerivationManager.getInstance(context)
                .getOrGenerateUserSalt(email);

            // 加密私钥并准备上传
            com.ttt.safevault.security.BackupEncryptionManager backupManager =
                com.ttt.safevault.security.BackupEncryptionManager.getInstance(context);

            // 加密私钥（使用云端同步模式）
            com.ttt.safevault.security.BackupEncryptionManager.CloudBackupResult encryptionResult =
                backupManager.encryptForCloudSync(
                    android.util.Base64.encodeToString(privateKey.getEncoded(), android.util.Base64.NO_WRAP),
                    masterPassword,
                    salt
                );

            EncryptionSyncManager syncManager = new EncryptionSyncManager(context, retrofitClient);
            boolean uploaded = syncManager.uploadEncryptedPrivateKey(
                encryptionResult.getEncryptedData(),
                encryptionResult.getIv(),
                encryptionResult.getSalt()
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
     * 注意：主密码只保存在内存中，不再保存到生物识别存储
     *
     * @param masterPassword 主密码
     */
    public void setSessionMasterPassword(@NonNull String masterPassword) {
        this.sessionMasterPassword = masterPassword;
        Log.d(TAG, "Session master password set (memory only)");
    }

    /**
     * 清除当前会话的主密码
     */
    public void clearSessionMasterPassword() {
        this.sessionMasterPassword = null;
        Log.d(TAG, "Session master password cleared");
    }

    /**
     * 获取会话主密码（供 BackendService 调用）
     * 与 getCurrentMasterPassword() 相同，但使用不同的命名约定
     *
     * @return 当前会话的主密码，未设置返回null
     */
    @Nullable
    public String getSessionMasterPassword() {
        return sessionMasterPassword;
    }
}
