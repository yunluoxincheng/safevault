package com.ttt.safevault.network;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ttt.safevault.BuildConfig;
import com.ttt.safevault.network.api.AuthServiceApi;
import com.ttt.safevault.network.api.FriendServiceApi;
import com.ttt.safevault.network.api.ShareServiceApi;
import com.ttt.safevault.network.api.VaultServiceApi;

import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit客户端单例
 */
public class RetrofitClient {
    private static RetrofitClient instance;
    private final Retrofit retrofit;
    private final TokenManager tokenManager;

    private AuthServiceApi authServiceApi;
    private ShareServiceApi shareServiceApi;
    private VaultServiceApi vaultServiceApi;
    private FriendServiceApi friendServiceApi;

    private RetrofitClient(Context context) {
        tokenManager = TokenManager.getInstance(context);

        // 日志拦截器 - 使用 HEADERS 级别以查看 Authorization 头
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        // 认证拦截器
        AuthInterceptor authInterceptor = new AuthInterceptor(tokenManager, context);

        // 构建 OkHttpClient
        OkHttpClient.Builder okHttpBuilder = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authInterceptor)
                .connectTimeout(ApiConstants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(ApiConstants.READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(ApiConstants.WRITE_TIMEOUT, TimeUnit.SECONDS);

        // SSL 配置：根据构建类型区分
        if (BuildConfig.DEBUG) {
            // Debug 版本：只对指定域名信任自签证书
            // 符合 security-hardening-phase1 网络安全规格的 Debug 构建例外条款
            Log.d("RetrofitClient", "DEBUG BUILD: 使用域名白名单 SSL 配置");

            X509TrustManager debugTrustManager = DebugSslProvider.getTrustManager();
            SSLSocketFactory sslSocketFactory = DebugSslProvider.getSSLSocketFactory();

            if (sslSocketFactory != null && debugTrustManager != null) {
                okHttpBuilder.sslSocketFactory(sslSocketFactory, debugTrustManager);
                // 只对白名单域名跳过主机名验证
                okHttpBuilder.hostnameVerifier((hostname, session) -> {
                    boolean allowed = DebugSslProvider.isDomainAllowed(hostname);
                    Log.d("RetrofitClient", "Hostname verification: " + hostname + " -> " + allowed);
                    return allowed;
                });
            } else {
                Log.w("RetrofitClient", "Failed to create debug SSL config, using default");
            }
        } else {
            // Release 版本：使用系统默认证书验证
            Log.i("RetrofitClient", "RELEASE BUILD: 使用系统默认证书验证");
        }
        // 系统默认 SSL 验证自动生效，无需自定义配置

        OkHttpClient okHttpClient = okHttpBuilder.build();
        
        // 构建Gson
        Gson gson = new GsonBuilder()
            .setLenient()
            .create();
        
        // 构建Retrofit
        retrofit = new Retrofit.Builder()
            .baseUrl(ApiConstants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .build();
        
        // 创建API服务
        authServiceApi = retrofit.create(AuthServiceApi.class);
        shareServiceApi = retrofit.create(ShareServiceApi.class);
        vaultServiceApi = retrofit.create(VaultServiceApi.class);
        friendServiceApi = retrofit.create(FriendServiceApi.class);
        
        // 设置TokenManager的authApi
        tokenManager.setAuthApi(authServiceApi);
    }
    
    public static synchronized RetrofitClient getInstance(Context context) {
        if (instance == null) {
            instance = new RetrofitClient(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 获取Retrofit实例（用于创建API服务）
     */
    public static Retrofit getClient(Context context) {
        return getInstance(context).retrofit;
    }

    /**
     * 获取Retrofit实例（使用TokenManager）
     */
    public static Retrofit getClient(TokenManager tokenManager) {
        // 如果需要使用TokenManager，可以使用getInstance方法
        // 这里为了兼容性，我们返回已存在的实例
        if (instance != null) {
            return instance.retrofit;
        }
        // 如果instance为null，需要从Context创建
        throw new IllegalStateException("RetrofitClient not initialized. Call getInstance(Context) first.");
    }

    public AuthServiceApi getAuthServiceApi() {
        return authServiceApi;
    }

    public ShareServiceApi getShareServiceApi() {
        return shareServiceApi;
    }

    public VaultServiceApi getVaultServiceApi() {
        return vaultServiceApi;
    }

    public FriendServiceApi getFriendServiceApi() {
        return friendServiceApi;
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }
}
