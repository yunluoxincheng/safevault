package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.crypto.Argon2KeyDerivationManager;
import com.ttt.safevault.dto.response.AuthResponse;
import com.ttt.safevault.dto.response.EmailLoginResponse;
import com.ttt.safevault.dto.response.EmailRegistrationResponse;
import com.ttt.safevault.dto.response.LoginPrecheckResponse;
import com.ttt.safevault.dto.response.VerifyEmailResponse;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.security.SecureKeyStorageManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 用户认证ViewModel
 *
 * 三层安全架构：移除 KeyManager 依赖，使用 SecureKeyStorageManager 和 Argon2KeyDerivationManager
 *
 * @since SafeVault 3.4.0 (移除旧安全架构，完全迁移到三层架构)
 */
public class AuthViewModel extends AndroidViewModel {
    private static final String TAG = "AuthViewModel";

    private final BackendService backendService;
    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;
    private final SecureKeyStorageManager secureKeyStorage;
    private final Argon2KeyDerivationManager argon2Manager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<AuthResponse> authResponseLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    // 邮箱认证相关 LiveData
    private final MutableLiveData<EmailRegistrationResponse> emailRegistrationLiveData = new MutableLiveData<>();
    private final MutableLiveData<VerifyEmailResponse> emailVerificationLiveData = new MutableLiveData<>();
    private final MutableLiveData<EmailLoginResponse> emailLoginLiveData = new MutableLiveData<>();
    private final MutableLiveData<LoginPrecheckResponse> loginPrecheckLiveData = new MutableLiveData<>();
    private final MutableLiveData<com.ttt.safevault.dto.response.VerificationStatusResponse> verificationStatusLiveData = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application application) {
        super(application);
        this.backendService = com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
        this.retrofitClient = RetrofitClient.getInstance(application);
        this.tokenManager = retrofitClient.getTokenManager();
        this.secureKeyStorage = SecureKeyStorageManager.getInstance(application);
        this.argon2Manager = Argon2KeyDerivationManager.getInstance(application);
    }

    /**
     * 获取设备ID
     */
    private String getDeviceId() {
        return Settings.Secure.getString(
            getApplication().getContentResolver(),
            Settings.Secure.ANDROID_ID
        );
    }

    /**
     * 获取RSA公钥
     */
    private String getPublicKey() {
        try {
            java.security.PublicKey publicKey = secureKeyStorage.getRsaPublicKey();
            if (publicKey == null) {
                Log.w(TAG, "RSA公钥尚未生成");
                return null;
            }
            byte[] publicKeyBytes = publicKey.getEncoded();
            return Base64.getEncoder().encodeToString(publicKeyBytes);
        } catch (Exception e) {
            Log.e(TAG, "获取RSA公钥失败", e);
            return null;
        }
    }

    /**
     * 用户注册
     */
    public void register(String username, String displayName) {
        loadingLiveData.setValue(true);

        // 获取设备ID和公钥
        String deviceId = getDeviceId();
        String publicKey = getPublicKey();

        if (deviceId == null || publicKey == null) {
            loadingLiveData.setValue(false);
            errorLiveData.setValue("密钥初始化失败");
            return;
        }

        Disposable disposable = retrofitClient.getAuthServiceApi()
            .register(new com.ttt.safevault.dto.request.RegisterRequest(deviceId, username, displayName, publicKey))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    tokenManager.saveTokens(response);
                    authResponseLiveData.setValue(response);
                    Log.d(TAG, "Register success: " + response.getUserId());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    String message = "注册失败: " + error.getMessage();
                    errorLiveData.setValue(message);
                    Log.e(TAG, "Register failed", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 用户登录
     */
    public void login() {
        loadingLiveData.setValue(true);

        // 获取保存的userId
        String userId = tokenManager.getUserId();
        if (userId == null) {
            loadingLiveData.setValue(false);
            errorLiveData.setValue("请先注册账号");
            return;
        }

        // 获取设备ID
        String deviceId = getDeviceId();
        if (deviceId == null) {
            loadingLiveData.setValue(false);
            errorLiveData.setValue("设备ID获取失败");
            return;
        }

        // 生成时间戳和签名
        long timestamp = System.currentTimeMillis();
        String signature = generateSignature(userId, deviceId, timestamp);

        Disposable disposable = retrofitClient.getAuthServiceApi()
            .login(new com.ttt.safevault.dto.request.LoginRequest(userId, deviceId, signature, timestamp))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    tokenManager.saveTokens(response);
                    authResponseLiveData.setValue(response);
                    Log.d(TAG, "Login success: " + response.getUserId());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    String message = "登录失败: " + error.getMessage();
                    errorLiveData.setValue(message);
                    Log.e(TAG, "Login failed", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 通过用户名登录
     */
    public void loginWithUsername(String username) {
        loadingLiveData.setValue(true);

        // 获取设备ID
        String deviceId = getDeviceId();
        if (deviceId == null) {
            loadingLiveData.setValue(false);
            errorLiveData.setValue("设备ID获取失败");
            return;
        }

        // 生成时间戳和签名
        long timestamp = System.currentTimeMillis();
        String signature = generateSignature(username, deviceId, timestamp);

        Disposable disposable = retrofitClient.getAuthServiceApi()
            .loginByUsername(new com.ttt.safevault.dto.request.LoginByUsernameRequest(username, signature, timestamp))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    tokenManager.saveTokens(response);
                    authResponseLiveData.setValue(response);
                    Log.d(TAG, "Login by username success: " + response.getUserId());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    String message = "登录失败: " + error.getMessage();
                    errorLiveData.setValue(message);
                    Log.e(TAG, "Login by username failed", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 生成签名（简化版本）
     * 生产环境应该使用RSA私钥签名
     *
     * @param identifier 用户ID或用户名
     * @param deviceId   设备ID
     * @param timestamp  时间戳
     * @return Base64编码的SHA-256签名
     */
    private String generateSignature(String identifier, String deviceId, long timestamp) {
        try {
            String data = identifier + deviceId + timestamp;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate signature", e);
            return "";
        }
    }

    /**
     * 刷新Token
     */
    public void refreshToken() {
        String refreshToken = tokenManager.getRefreshToken();
        if (refreshToken == null) {
            errorLiveData.setValue("无刷新Token");
            return;
        }

        Disposable disposable = retrofitClient.getAuthServiceApi()
            .refreshToken("Bearer " + refreshToken)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    tokenManager.saveTokens(response);
                    authResponseLiveData.setValue(response);
                    Log.d(TAG, "Token refreshed");
                },
                error -> {
                    tokenManager.clearTokens();
                    errorLiveData.setValue("Token刷新失败，请重新登录");
                    Log.e(TAG, "Token refresh failed", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 检查登录状态
     */
    public boolean isLoggedIn() {
        return tokenManager.isLoggedIn();
    }

    /**
     * 登出
     */
    public void logout() {
        tokenManager.clearTokens();
        authResponseLiveData.setValue(null);
    }

    // ========== 统一邮箱认证方法 ==========

    /**
     * 邮箱注册第一步：发起注册并发送验证邮件
     *
     * @param email    邮箱
     * @param username 用户名
     */
    public void registerWithEmail(String email, String username) {
        loadingLiveData.setValue(true);

        Disposable disposable = retrofitClient.getAuthServiceApi()
            .registerWithEmail(new com.ttt.safevault.dto.request.EmailRegistrationRequest(email, username))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    emailRegistrationLiveData.setValue(response);
                    Log.d(TAG, "Email registration success: " + response.getMessage());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    String message = "注册失败: " + error.getMessage();
                    errorLiveData.setValue(message);
                    Log.e(TAG, "Email registration failed", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 验证邮箱
     *
     * @param token 验证令牌
     */
    public void verifyEmail(String token) {
        loadingLiveData.setValue(true);

        Disposable disposable = retrofitClient.getAuthServiceApi()
            .verifyEmail(new com.ttt.safevault.dto.request.VerifyEmailRequest(token))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    emailVerificationLiveData.setValue(response);
                    Log.d(TAG, "Email verification success: " + response.getMessage());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    String message = "验证失败: " + error.getMessage();
                    errorLiveData.setValue(message);
                    Log.e(TAG, "Email verification failed", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 重新发送验证邮件
     *
     * @param email 邮箱
     */
    public void resendVerificationEmail(String email) {
        loadingLiveData.setValue(true);

        Disposable disposable = retrofitClient.getAuthServiceApi()
            .resendVerification(new com.ttt.safevault.dto.request.ResendVerificationRequest(email))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    emailRegistrationLiveData.setValue(response);
                    Log.d(TAG, "Resend verification success: " + response.getMessage());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    String message = "重发失败: " + error.getMessage();
                    errorLiveData.setValue(message);
                    Log.e(TAG, "Resend verification failed", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 检查邮箱验证状态
     * 用于轮询检查用户是否已在 Web 页面完成验证
     *
     * @param email 邮箱
     */
    public void checkVerificationStatus(String email) {
        Disposable disposable = retrofitClient.getAuthServiceApi()
            .checkVerificationStatus(email)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    verificationStatusLiveData.setValue(response);
                    Log.d(TAG, "Verification status check: " + response.getStatus() +
                               ", email: " + response.getEmail());
                },
                error -> {
                    String message = "检查验证状态失败: " + error.getMessage();
                    Log.e(TAG, "Verification status check failed", error);
                    // 静默失败，不显示错误给用户
                }
            );

        disposables.add(disposable);
    }

    /**
     * 邮箱登录（支持设备管理，使用 Challenge-Response 机制防止重放攻击）
     *
     * @param email       邮箱
     * @param masterPassword 主密码（用于生成签名）
     */
    public void loginWithEmail(String email, String masterPassword) {
        loadingLiveData.setValue(true);

        // 获取设备ID
        String deviceId = getDeviceId();
        if (deviceId == null) {
            loadingLiveData.setValue(false);
            errorLiveData.setValue("设备ID获取失败");
            return;
        }

        // 第一步：调用登录预检查获取 nonce
        Disposable precheckDisposable = retrofitClient.getAuthServiceApi()
            .loginPrecheck(new com.ttt.safevault.dto.request.LoginPrecheckRequest(email))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                precheckResponse -> {
                    loginPrecheckLiveData.setValue(precheckResponse);
                    // 第二步：使用 nonce 和 passwordVerifier 进行登录
                    performLoginWithEmail(email, masterPassword, deviceId, precheckResponse);
                },
                error -> {
                    loadingLiveData.setValue(false);
                    String message = "登录预检查失败: " + error.getMessage();
                    errorLiveData.setValue(message);
                    Log.e(TAG, "Login precheck failed", error);
                }
            );

        disposables.add(precheckDisposable);
    }

    /**
     * 执行邮箱登录（使用 nonce 和 passwordVerifier 进行签名）
     *
     * Challenge-Response 机制：
     * 1. 服务器在 loginPrecheck 中返回 nonce 和 passwordVerifier
     * 2. 客户端使用 passwordVerifier 作为 HMAC 密钥对 (email + deviceId + nonce) 进行签名
     * 3. 服务器使用相同的 passwordVerifier 验证签名
     *
     * @param email 邮箱
     * @param masterPassword 主密码（不再需要，保留参数用于兼容）
     * @param deviceId 设备ID
     * @param precheckResponse 登录预检查响应（包含 nonce 和 passwordVerifier）
     */
    private void performLoginWithEmail(String email, String masterPassword, String deviceId,
                                       com.ttt.safevault.dto.response.LoginPrecheckResponse precheckResponse) {
        // 获取设备信息
        String deviceName = getDeviceName();
        String deviceType = "android";
        String osVersion = "Android " + Build.VERSION.RELEASE;

        // 使用服务器返回的 passwordVerifier 生成签名
        String nonce = precheckResponse.getNonce();
        String passwordVerifier = precheckResponse.getPasswordVerifier();
        String signature = generateSignatureWithVerifier(email, deviceId, nonce, passwordVerifier);

        Disposable disposable = retrofitClient.getAuthServiceApi()
            .loginByEmail(new com.ttt.safevault.dto.request.LoginByEmailRequest(
                email, deviceId, deviceName, signature, nonce, deviceType, osVersion))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    // 保存Token信息
                    tokenManager.saveTokens(
                        response.getUserId(),
                        response.getAccessToken(),
                        response.getRefreshToken()
                    );
                    emailLoginLiveData.setValue(response);
                    Log.d(TAG, "Email login success: " + response.getUserId() +
                               ", isNewDevice: " + response.getIsNewDevice());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    String message = "登录失败: " + error.getMessage();
                    errorLiveData.setValue(message);
                    Log.e(TAG, "Email login failed", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 使用服务器返回的 passwordVerifier 生成 HMAC-SHA256 签名
     *
     * Challenge-Response 机制：
     * - passwordVerifier 是用户注册时使用 Argon2id 派生的密钥
     * - 服务器在 loginPrecheck 中返回此密钥
     * - 客户端使用此密钥对 (email + deviceId + nonce) 进行 HMAC 签名
     *
     * @param email 邮箱
     * @param deviceId 设备ID
     * @param nonce 服务器生成的挑战码
     * @param passwordVerifier Base64 编码的派生密钥
     * @return Base64 编码的 HMAC-SHA256 签名
     */
    private String generateSignatureWithVerifier(String email, String deviceId, String nonce, String passwordVerifier) {
        try {
            // 解码 passwordVerifier 获取派生密钥字节数组
            byte[] derivedKeyBytes = Base64.getDecoder().decode(passwordVerifier);

            // 构造待签名数据：email + deviceId + nonce
            String data = email + deviceId + nonce;
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);

            // 使用派生密钥进行 HMAC-SHA256 签名
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                    derivedKeyBytes, "HmacSHA256");
            mac.init(secretKey);
            byte[] signatureBytes = mac.doFinal(dataBytes);

            String signature = Base64.getEncoder().encodeToString(signatureBytes);

            Log.d(TAG, "Signature generated with server-provided passwordVerifier");
            return signature;
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate signature with passwordVerifier", e);
            return "";
        }
    }

    /**
     * @deprecated 旧方法，已被 generateSignatureWithVerifier 替代
     * 保留此方法仅为向后兼容，不应再使用
     */
    @Deprecated
    private String generateDerivedKeySignatureWithNonce(String email, String deviceId, String masterPassword, String nonce) {
        // 此方法已废弃，不再使用
        Log.w(TAG, "Using deprecated generateDerivedKeySignatureWithNonce method");
        return "";
    }

    /**
     * 获取设备名称
     */
    private String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // Getters for LiveData
    public LiveData<AuthResponse> getAuthResponse() {
        return authResponseLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<Boolean> getLoading() {
        return loadingLiveData;
    }

    public LiveData<EmailRegistrationResponse> getEmailRegistrationResponse() {
        return emailRegistrationLiveData;
    }

    public LiveData<VerifyEmailResponse> getEmailVerificationResponse() {
        return emailVerificationLiveData;
    }

    public LiveData<EmailLoginResponse> getEmailLoginResponse() {
        return emailLoginLiveData;
    }

    public LiveData<LoginPrecheckResponse> getLoginPrecheckResponse() {
        return loginPrecheckLiveData;
    }

    public LiveData<com.ttt.safevault.dto.response.VerificationStatusResponse> getVerificationStatusResponse() {
        return verificationStatusLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}
