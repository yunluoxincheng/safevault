package com.ttt.safevault.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.ttt.safevault.dto.response.AuthResponse;
import com.ttt.safevault.network.api.AuthServiceApi;

import io.reactivex.rxjava3.core.Observable;

/**
 * Token管理器
 * 负责Token的存储、获取、刷新
 */
public class TokenManager {
    private static final String TAG = "TokenManager";
    private static final String PREFS_NAME = "token_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_DISPLAY_NAME = "display_name";

    // 邮箱验证状态相关
    private static final String KEY_EMAIL_VERIFIED = "email_verified";
    private static final String KEY_VERIFIED_EMAIL = "verified_email";
    private static final String KEY_VERIFIED_USERNAME = "verified_username";
    
    private final SharedPreferences prefs;
    private AuthServiceApi authApi;
    private static TokenManager instance;
    
    private TokenManager(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized TokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new TokenManager(context);
        }
        return instance;
    }
    
    public void setAuthApi(AuthServiceApi authApi) {
        this.authApi = authApi;
    }
    
    /**
     * 保存Token
     */
    public void saveTokens(AuthResponse response) {
        if (response == null) {
            return;
        }

        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, response.getAccessToken())
            .putString(KEY_REFRESH_TOKEN, response.getRefreshToken())
            .putString(KEY_USER_ID, response.getUserId())
            .putString(KEY_DISPLAY_NAME, response.getDisplayName())
            .commit();  // 使用 commit() 确保同步保存，避免后续读取时 Token 未就绪

        Log.d(TAG, "Tokens saved for user: " + response.getDisplayName());
    }

    /**
     * 保存Token（使用单独的参数）
     */
    public void saveTokens(String userId, String accessToken, String refreshToken) {
        if (userId == null || accessToken == null || refreshToken == null) {
            return;
        }

        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, userId)
            .commit();  // 使用 commit() 确保同步保存，避免后续读取时 Token 未就绪

        Log.d(TAG, "Tokens saved for user: " + userId);
    }
    
    /**
     * 获取访问Token
     */
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }
    
    /**
     * 获取刷新Token
     */
    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }
    
    /**
     * 获取用户ID
     */
    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }
    
    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return prefs.getString(KEY_DISPLAY_NAME, null);
    }
    
    /**
     * 刷新Token
     */
    public Observable<AuthResponse> refreshToken() {
        String refreshToken = getRefreshToken();
        if (refreshToken == null || authApi == null) {
            return Observable.error(new IllegalStateException("No refresh token or auth API not set"));
        }
        
        return authApi.refreshToken("Bearer " + refreshToken)
            .doOnNext(this::saveTokens)
            .doOnError(error -> {
                Log.e(TAG, "Failed to refresh token", error);
                clearTokens();
            });
    }
    
    /**
     * 清除Token
     */
    public void clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_DISPLAY_NAME)
            .apply();
        
        Log.d(TAG, "Tokens cleared");
    }
    
    /**
     * 是否已登录
     */
    public boolean isLoggedIn() {
        return getAccessToken() != null;
    }

    // ========== 邮箱验证状态管理 ==========

    /**
     * 保存邮箱验证状态
     */
    public void saveEmailVerificationStatus(String email, String username) {
        Log.d(TAG, "saveEmailVerificationStatus called with email=" + email + ", username=" + username);
        Log.d(TAG, "PREFS_NAME=" + PREFS_NAME);

        prefs.edit()
            .putBoolean(KEY_EMAIL_VERIFIED, true)
            .putString(KEY_VERIFIED_EMAIL, email)
            .putString(KEY_VERIFIED_USERNAME, username)
            .commit();  // 使用 commit() 而不是 apply() 确保立即写入

        // 验证保存是否成功
        boolean saved = prefs.getBoolean(KEY_EMAIL_VERIFIED, false);
        String savedEmail = prefs.getString(KEY_VERIFIED_EMAIL, null);
        String savedUsername = prefs.getString(KEY_VERIFIED_USERNAME, null);
        Log.d(TAG, "Email verification status saved. verified=" + saved + ", email=" + savedEmail + ", username=" + savedUsername);
    }

    /**
     * 检查邮箱是否已验证
     */
    public boolean isEmailVerified() {
        boolean verified = prefs.getBoolean(KEY_EMAIL_VERIFIED, false);
        Log.d(TAG, "isEmailVerified: " + verified);
        return verified;
    }

    /**
     * 获取已验证的邮箱
     */
    public String getVerifiedEmail() {
        String email = prefs.getString(KEY_VERIFIED_EMAIL, null);
        Log.d(TAG, "getVerifiedEmail: " + email);
        return email;
    }

    /**
     * 获取已验证的用户名
     */
    public String getVerifiedUsername() {
        String username = prefs.getString(KEY_VERIFIED_USERNAME, null);
        Log.d(TAG, "getVerifiedUsername: " + username);
        return username;
    }

    /**
     * 清除邮箱验证状态
     */
    public void clearEmailVerificationStatus() {
        prefs.edit()
            .remove(KEY_EMAIL_VERIFIED)
            .remove(KEY_VERIFIED_EMAIL)
            .remove(KEY_VERIFIED_USERNAME)
            .apply();

        Log.d(TAG, "Email verification status cleared");
    }

    // ========== 登录邮箱记忆 ==========

    private static final String KEY_LAST_LOGIN_EMAIL = "last_login_email";

    /**
     * 保存上次登录的邮箱
     */
    public void saveLastLoginEmail(String email) {
        prefs.edit()
            .putString(KEY_LAST_LOGIN_EMAIL, email)
            .apply();

        Log.d(TAG, "Last login email saved: " + email);
    }

    /**
     * 获取上次登录的邮箱
     */
    public String getLastLoginEmail() {
        return prefs.getString(KEY_LAST_LOGIN_EMAIL, null);
    }

    /**
     * 清除上次登录的邮箱
     */
    public void clearLastLoginEmail() {
        prefs.edit()
            .remove(KEY_LAST_LOGIN_EMAIL)
            .apply();

        Log.d(TAG, "Last login email cleared");
    }
}
