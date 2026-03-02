package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.security.SecureKeyStorageManager;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.ServiceLocator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 云端认证管理器
 * 负责云端用户注册、登录、Token刷新等认证相关功能
 *
 * SafeVault 3.4.0 更新：
 * - 完全移除 CryptoManager 和 KeyManager 依赖
 * - 使用 SecureKeyStorageManager
 */
public class CloudAuthManager {
    private static final String TAG = "CloudAuthManager";

    private final Context context;
    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;
    private final SecureKeyStorageManager secureKeyStorage;

    public CloudAuthManager(@NonNull Context context,
                           @NonNull RetrofitClient retrofitClient) {
        this.context = context.getApplicationContext();
        this.retrofitClient = retrofitClient;
        this.tokenManager = retrofitClient.getTokenManager();
        this.secureKeyStorage = SecureKeyStorageManager.getInstance(context);
    }

    /**
     * 用户注册
     */
    public com.ttt.safevault.dto.response.AuthResponse register(String username, String password, String displayName) {
        try {
            // 获取设备ID和公钥
            String deviceId = getDeviceId();
            String publicKey = getPublicKey();

            com.ttt.safevault.dto.request.RegisterRequest request = new com.ttt.safevault.dto.request.RegisterRequest(
                deviceId, username, displayName, publicKey
            );

            com.ttt.safevault.dto.response.AuthResponse response = retrofitClient.getAuthServiceApi()
                .register(request)
                .blockingFirst();

            if (response != null) {
                tokenManager.saveTokens(response);
                Log.d(TAG, "User registered successfully: " + response.getUserId());
            }

            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to register", e);
            return null;
        }
    }

    /**
     * 用户登录
     */
    public com.ttt.safevault.dto.response.AuthResponse login(String username, String password) {
        try {
            // 获取保存的userId和设备ID
            String userId = tokenManager.getUserId();
            String deviceId = getDeviceId();

            if (userId == null) {
                Log.e(TAG, "No userId found, please register first");
                return null;
            }

            // 生成时间戳和签名
            long timestamp = System.currentTimeMillis();
            String signature = generateSignature(userId, deviceId, timestamp);

            com.ttt.safevault.dto.request.LoginRequest request = new com.ttt.safevault.dto.request.LoginRequest(
                userId, deviceId, signature, timestamp
            );

            com.ttt.safevault.dto.response.AuthResponse response = retrofitClient.getAuthServiceApi()
                .login(request)
                .blockingFirst();

            if (response != null) {
                tokenManager.saveTokens(response);
                Log.d(TAG, "User logged in successfully: " + response.getUserId());
            }

            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to login", e);
            return null;
        }
    }

    /**
     * 刷新Token
     */
    public com.ttt.safevault.dto.response.AuthResponse refreshToken(String refreshToken) {
        try {
            com.ttt.safevault.dto.response.AuthResponse response = retrofitClient.getAuthServiceApi()
                .refreshToken("Bearer " + refreshToken)
                .blockingFirst();

            if (response != null) {
                tokenManager.saveTokens(response);
                Log.d(TAG, "Token refreshed successfully");
            }

            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh token", e);
            return null;
        }
    }

    /**
     * 检查是否已登录云端
     */
    public boolean isCloudLoggedIn() {
        return tokenManager.isLoggedIn();
    }

    /**
     * 云端登出
     */
    public void logoutCloud() {
        try {
            String userId = tokenManager.getUserId();
            String deviceId = getDeviceId();

            if (userId != null && !userId.isEmpty()) {
                // 构建注销请求
                com.ttt.safevault.dto.request.LogoutRequest request =
                    com.ttt.safevault.dto.request.LogoutRequest.builder()
                        .deviceId(deviceId)
                        .timestamp(System.currentTimeMillis())
                        .build();

                // 调用后端注销 API
                retrofitClient.getAuthServiceApi()
                    .logout(userId, request)
                    .blockingSubscribe();

                Log.d(TAG, "Cloud logout successful");
            }

            // 清除令牌
            tokenManager.clearTokens();
            Log.d(TAG, "Logged out from cloud");
        } catch (Exception e) {
            Log.e(TAG, "Failed to logout from cloud", e);
            // 即使 API 调用失败，也清除本地令牌
            tokenManager.clearTokens();
            throw new RuntimeException("注销失败: " + e.getMessage());
        }
    }

    /**
     * 完成注册
     */
    public com.ttt.safevault.dto.response.CompleteRegistrationResponse completeRegistration(
            String email, String username, String masterPassword) {
        try {
            // 0. 检查是否已初始化本地保险库
            BackendService backendService =
                ServiceLocator.getInstance().getBackendService();

            if (backendService.isInitialized()) {
                // 已存在本地保险库，不允许重新注册
                // 这可能是因为：
                // 1. 用户之前注册失败但本地保险库已创建
                // 2. 用户已经有账户
                Log.e(TAG, "本地保险库已存在，无法重新注册。请先清除应用数据或使用现有账户登录。");
                throw new RuntimeException("本地保险库已存在。如需重新注册，请先在设置中删除账户或清除应用数据。");
            }

            // 本地保险库未初始化，开始初始化
            Log.d(TAG, "本地保险库未初始化，开始初始化...");
            boolean initialized = backendService.initialize(masterPassword);
            if (!initialized) {
                throw new RuntimeException("本地保险库初始化失败");
            }
            Log.d(TAG, "本地保险库初始化成功");

            // 1. 生成盐值
            com.ttt.safevault.crypto.Argon2KeyDerivationManager argon2Manager =
                com.ttt.safevault.crypto.Argon2KeyDerivationManager.getInstance(context);
            String salt = argon2Manager.getOrGenerateUserSalt(email);

            // 2. 派生密钥并生成密码验证器
            javax.crypto.SecretKey derivedKey = argon2Manager.deriveKeyWithArgon2id(masterPassword, salt);
            String passwordVerifier = Base64.getEncoder().encodeToString(derivedKey.getEncoded());

            // 3. 获取 RSA 公钥（从 SecureKeyStorageManager）
            String publicKey = getPublicKey();
            if (publicKey == null) {
                throw new RuntimeException("无法获取 RSA 公钥，请检查本地保险库初始化状态");
            }

            // 4. 获取私钥并加密
            com.ttt.safevault.security.CryptoSession cryptoSession =
                com.ttt.safevault.security.CryptoSession.getInstance();
            if (!cryptoSession.isUnlocked()) {
                throw new RuntimeException("会话未解锁，无法完成注册");
            }

            javax.crypto.SecretKey dataKey = cryptoSession.getDataKey();
            if (dataKey == null) {
                throw new RuntimeException("无法获取 DataKey");
            }

            java.security.PrivateKey privateKey = secureKeyStorage.decryptRsaPrivateKey(dataKey);
            if (privateKey == null) {
                throw new RuntimeException("无法获取私钥");
            }

            // 5. 使用主密码加密私钥（使用 BackupEncryptionManager）
            com.ttt.safevault.security.BackupEncryptionManager backupManager =
                com.ttt.safevault.security.BackupEncryptionManager.getInstance(context);

            com.ttt.safevault.security.BackupEncryptionManager.CloudBackupResult encryptedKey =
                backupManager.encryptForCloudSync(
                    android.util.Base64.encodeToString(privateKey.getEncoded(), android.util.Base64.NO_WRAP),
                    masterPassword,
                    salt
                );

            // 6. 获取设备ID
            String deviceId = getDeviceId();

            // 7. 构建完成注册请求
            com.ttt.safevault.dto.request.CompleteRegistrationRequest request =
                    com.ttt.safevault.dto.request.CompleteRegistrationRequest.builder()
                            .email(email)
                            .username(username)
                            .passwordVerifier(passwordVerifier)
                            .salt(salt)
                            .publicKey(publicKey)
                            .encryptedPrivateKey(encryptedKey.getEncryptedData())
                            .privateKeyIv(encryptedKey.getIv())
                            .authTag(encryptedKey.getAuthTag())
                            .deviceId(deviceId)
                            .build();

            // 8. 调用后端完成注册 API
            com.ttt.safevault.dto.response.CompleteRegistrationResponse response =
                    retrofitClient.getAuthServiceApi()
                            .completeRegistration(request)
                            .blockingFirst();

            if (response != null && response.getSuccess()) {
                // 9. 保存令牌
                tokenManager.saveTokens(response.getUserId(), response.getAccessToken(), response.getRefreshToken());

                Log.d(TAG, "Registration completed successfully for user: " + username);
            }

            return response;

        } catch (Exception e) {
            Log.e(TAG, "Failed to complete registration", e);
            throw new RuntimeException("注册完成失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备ID
     */
    private String getDeviceId() {
        return android.provider.Settings.Secure.getString(
            context.getContentResolver(),
            android.provider.Settings.Secure.ANDROID_ID
        );
    }

    /**
     * 获取公钥
     */
    private String getPublicKey() {
        try {
            java.security.PublicKey publicKey = secureKeyStorage.getRsaPublicKey();
            if (publicKey == null) {
                Log.w(TAG, "RSA公钥尚未生成");
                return null;
            }
            byte[] publicKeyBytes = publicKey.getEncoded();
            return android.util.Base64.encodeToString(publicKeyBytes, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "获取RSA公钥失败", e);
            return null;
        }
    }

    /**
     * 生成签名（简化版本）
     */
    private String generateSignature(String userId, String deviceId, long timestamp) {
        try {
            String data = userId + deviceId + timestamp;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate signature", e);
            return "";
        }
    }

    /**
     * 上传 X25519/Ed25519 公钥到服务器
     *
     * 用于密钥迁移后或新用户初始化
     *
     * @param x25519PublicKey X25519 公钥（Base64 编码）
     * @param ed25519PublicKey Ed25519 公钥（Base64 编码）
     * @param keyVersion 密钥版本（"v3"）
     * @return 上传成功返回 true
     */
    public boolean uploadEccPublicKey(String x25519PublicKey, String ed25519PublicKey, String keyVersion) {
        try {
            if (x25519PublicKey == null || ed25519PublicKey == null || keyVersion == null) {
                Log.e(TAG, "ECC 公钥参数不能为空");
                return false;
            }

            com.ttt.safevault.dto.request.UploadEccPublicKeyRequest request =
                new com.ttt.safevault.dto.request.UploadEccPublicKeyRequest(
                    x25519PublicKey,
                    ed25519PublicKey,
                    keyVersion
                );

            com.ttt.safevault.dto.response.UploadEccPublicKeyResponse response =
                retrofitClient.getAuthServiceApi()
                    .uploadEccPublicKey(request)
                    .blockingFirst();

            if (response != null && response.isSuccess()) {
                Log.d(TAG, "ECC 公钥上传成功");
                return true;
            } else {
                Log.e(TAG, "ECC 公钥上传失败: " + (response != null ? response.getMessage() : "未知错误"));
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "ECC 公钥上传异常", e);
            return false;
        }
    }
}
