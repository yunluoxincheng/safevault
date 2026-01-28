package com.ttt.safevault.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.ttt.safevault.dto.response.AuthResponse;
import com.ttt.safevault.network.api.AuthServiceApi;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

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

    public TokenManager(Context context) {
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
     * 保存Token（带过期时间解析）
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

        // 解析并保存过期时间
        long expiryTime = parseTokenExpiryTime(response.getAccessToken());
        if (expiryTime > 0) {
            saveTokenExpiryTime(expiryTime);
        }

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
        String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        Log.d(TAG, "getAccessToken() - Token present: " + (token != null) + ", Length: " + (token != null ? token.length() : 0));
        return token;
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
            .remove(KEY_TOKEN_EXPIRY_TIME)
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
     * 保存邮箱登录响应中的用户信息
     * 用于保存 EmailLoginResponse 中的 displayName 和 username
     */
    public void saveEmailLoginInfo(String email, String username, String displayName) {
        prefs.edit()
            .putString(KEY_LAST_LOGIN_EMAIL, email)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_VERIFIED_USERNAME, username)
            .apply();

        Log.d(TAG, "Email login info saved - email: " + email + ", username: " + username + ", displayName: " + displayName);
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

    // ========== Token 过期检查与主动刷新 ==========

    private static final String KEY_TOKEN_EXPIRY_TIME = "token_expiry_time";
    private static final long REFRESH_THRESHOLD_MINUTES = 5; // 剩余5分钟时主动刷新

    /**
     * 从 JWT Token 中解析过期时间
     * @param token JWT Token
     * @return 过期时间的 Unix 时间戳（毫秒），解析失败返回 -1
     */
    private long parseTokenExpiryTime(String token) {
        if (token == null || token.isEmpty()) {
            return -1;
        }

        try {
            // JWT 格式: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                Log.e(TAG, "Invalid JWT token format");
                return -1;
            }

            // 解码 payload (Base64 URL safe)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), "UTF-8");
            JSONObject json = new JSONObject(payload);

            // 获取 exp 字段（Unix 时间戳，秒）
            if (json.has("exp")) {
                long expSeconds = json.getLong("exp");
                // 转换为毫秒
                return expSeconds * 1000;
            }

            Log.e(TAG, "JWT token does not contain 'exp' field");
            return -1;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse JWT payload", e);
            return -1;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to decode Base64 payload", e);
            return -1;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error parsing token", e);
            return -1;
        }
    }

    /**
     * 保存 Token 过期时间
     * @param expiryTime 过期时间的 Unix 时间戳（毫秒）
     */
    private void saveTokenExpiryTime(long expiryTime) {
        prefs.edit()
            .putLong(KEY_TOKEN_EXPIRY_TIME, expiryTime)
            .apply();
    }

    /**
     * 获取保存的 Token 过期时间
     * @return 过期时间的 Unix 时间戳（毫秒），未保存返回 -1
     */
    private long getTokenExpiryTime() {
        return prefs.getLong(KEY_TOKEN_EXPIRY_TIME, -1);
    }

    /**
     * 判断 Token 是否需要刷新（剩余时间少于阈值或已过期）
     * @return true 如果需要刷新，false 否则
     */
    public boolean shouldRefreshToken() {
        String accessToken = getAccessToken();
        if (accessToken == null) {
            return false;
        }

        // 必须有 refresh token 才能刷新
        String refreshToken = getRefreshToken();
        if (refreshToken == null) {
            Log.w(TAG, "No refresh token available, cannot proactive refresh");
            return false;
        }

        // 先尝试从缓存获取过期时间
        long expiryTime = getTokenExpiryTime();

        // 如果缓存中没有，从 token 解析
        if (expiryTime == -1) {
            expiryTime = parseTokenExpiryTime(accessToken);
            if (expiryTime > 0) {
                saveTokenExpiryTime(expiryTime);
            }
        }

        if (expiryTime <= 0) {
            Log.w(TAG, "Cannot determine token expiry time, skipping proactive refresh");
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long timeUntilExpiry = expiryTime - currentTime;
        long thresholdMs = TimeUnit.MINUTES.toMillis(REFRESH_THRESHOLD_MINUTES);

        // 修改逻辑：如果 token 已经过期或即将过期（剩余时间 < 阈值），都需要刷新
        // 移除 && timeUntilExpiry > 0 条件，让已过期的 token 也能触发刷新
        boolean shouldRefresh = timeUntilExpiry <= thresholdMs;

        Log.d(TAG, String.format("Token expiry check: expiry=%d, current=%d, remaining=%dmin, threshold=%dmin, shouldRefresh=%b",
            expiryTime, currentTime,
            TimeUnit.MILLISECONDS.toMinutes(timeUntilExpiry),
            REFRESH_THRESHOLD_MINUTES, shouldRefresh));

        return shouldRefresh;
    }

    /**
     * 如果 Token 快过期，主动刷新
     * 刷新失败时不跳转登录页，仅记录日志
     * @return Observable<String> 发送新的 token，失败时发送错误
     */
    public Observable<String> refreshIfNearExpiry() {
        if (!shouldRefreshToken()) {
            Log.d(TAG, "Token does not need proactive refresh");
            return Observable.just(getAccessToken());
        }

        Log.d(TAG, "Token near expiry, initiating proactive refresh");

        return refreshToken()
            .map(authResponse -> authResponse.getAccessToken())
            .doOnNext(newToken -> {
                // 更新过期时间缓存
                long expiryTime = parseTokenExpiryTime(newToken);
                if (expiryTime > 0) {
                    saveTokenExpiryTime(expiryTime);
                }
                Log.d(TAG, "Proactive token refresh successful");
            })
            .doOnError(error -> {
                Log.w(TAG, "Proactive token refresh failed, will retry on next API call", error);
                // 主动刷新失败时不清除 token，等待下次 API 调用时被动刷新
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
    }
}
