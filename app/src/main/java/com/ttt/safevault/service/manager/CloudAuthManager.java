package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.ttt.safevault.crypto.CryptoManager;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.security.KeyManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 云端认证管理器
 * 负责云端用户注册、登录、Token刷新等认证相关功能
 */
public class CloudAuthManager {
    private static final String TAG = "CloudAuthManager";

    private final Context context;
    private final CryptoManager cryptoManager;
    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;
    private final KeyManager keyManager;

    public CloudAuthManager(@NonNull Context context,
                           @NonNull CryptoManager cryptoManager,
                           @NonNull RetrofitClient retrofitClient) {
        this.context = context.getApplicationContext();
        this.cryptoManager = cryptoManager;
        this.retrofitClient = retrofitClient;
        this.tokenManager = retrofitClient.getTokenManager();
        this.keyManager = KeyManager.getInstance(context);
    }

    /**
     * 用户注册
     */
    public com.ttt.safevault.dto.response.AuthResponse register(String username, String password, String displayName) {
        try {
            // 获取设备ID和公钥
            String deviceId = keyManager.getDeviceId();
            String publicKey = keyManager.getPublicKey();

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
            String deviceId = keyManager.getDeviceId();

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
            String deviceId = keyManager.getDeviceId();

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
            // 1. 生成盐值
            String salt = keyManager.generateSaltForUser(email);
            keyManager.saveUserSalt(email, salt);

            // 2. 派生密钥并生成密码验证器
            javax.crypto.SecretKey derivedKey = keyManager.deriveKeyFromMasterPassword(masterPassword, salt);
            String passwordVerifier = Base64.getEncoder().encodeToString(derivedKey.getEncoded());

            // 3. 生成 RSA 密钥对
            String publicKey = keyManager.getPublicKey();
            java.security.PrivateKey privateKey = keyManager.getPrivateKey();

            if (privateKey == null) {
                throw new RuntimeException("无法生成私钥");
            }

            // 4. 使用主密码加密私钥
            KeyManager.EncryptedPrivateKey encryptedKey =
                    keyManager.encryptPrivateKey(privateKey, masterPassword, email);

            // 5. 获取设备ID
            String deviceId = keyManager.getDeviceId();

            // 6. 构建完成注册请求
            com.ttt.safevault.dto.request.CompleteRegistrationRequest request =
                    com.ttt.safevault.dto.request.CompleteRegistrationRequest.builder()
                            .email(email)
                            .username(username)
                            .passwordVerifier(passwordVerifier)
                            .salt(salt)
                            .publicKey(publicKey)
                            .encryptedPrivateKey(encryptedKey.getEncryptedData())
                            .privateKeyIv(encryptedKey.getIv())
                            .deviceId(deviceId)
                            .build();

            // 7. 调用后端完成注册 API
            com.ttt.safevault.dto.response.CompleteRegistrationResponse response =
                    retrofitClient.getAuthServiceApi()
                            .completeRegistration(request)
                            .blockingFirst();

            if (response != null && response.getSuccess()) {
                // 8. 保存令牌
                tokenManager.saveTokens(response.getUserId(), response.getAccessToken(), response.getRefreshToken());

                // 9. 初始化 CryptoManager
                if (!cryptoManager.isInitialized()) {
                    cryptoManager.initialize(masterPassword);
                }

                // 10. 保存主密码到生物识别存储（即使用户未启用生物识别，也需要保存以便后续操作使用）
                saveMasterPasswordForBiometric(masterPassword);

                Log.d(TAG, "Registration completed successfully for user: " + username);
            }

            return response;

        } catch (Exception e) {
            Log.e(TAG, "Failed to complete registration", e);
            throw new RuntimeException("注册完成失败: " + e.getMessage());
        }
    }

    /**
     * 保存主密码用于生物识别解锁
     * 注意：即使用户未启用生物识别，也需要保存密码以便云端同步等功能正常工作
     */
    private void saveMasterPasswordForBiometric(String masterPassword) {
        try {
            com.ttt.safevault.security.BiometricKeyManager biometricKeyManager =
                    com.ttt.safevault.security.BiometricKeyManager.getInstance();
            if (biometricKeyManager == null) {
                Log.w(TAG, "BiometricKeyManager not initialized, skipping password save");
                return;
            }

            // 获取加密Cipher
            javax.crypto.Cipher cipher = biometricKeyManager.getEncryptCipher();

            // 加密主密码
            byte[] encrypted = cipher.doFinal(masterPassword.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();

            // 保存到 SharedPreferences
            context.getSharedPreferences("backend_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("biometric_encrypted_password",
                            android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
                    .putString("biometric_iv",
                            android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
                    .apply();

            Log.d(TAG, "Master password saved for biometric unlock");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save master password for biometric", e);
            // 不抛出异常，允许注册继续完成
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
}
