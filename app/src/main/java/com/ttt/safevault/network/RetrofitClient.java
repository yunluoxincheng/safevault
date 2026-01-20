package com.ttt.safevault.network;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ttt.safevault.network.api.AuthServiceApi;
import com.ttt.safevault.network.api.FriendServiceApi;
import com.ttt.safevault.network.api.ShareServiceApi;
import com.ttt.safevault.network.api.VaultServiceApi;

import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
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

        // 日志拦截器（使用 BASIC 级别避免记录敏感数据）
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        // 认证拦截器
        AuthInterceptor authInterceptor = new AuthInterceptor(tokenManager);

        // 创建信任所有证书的 TrustManager (仅用于开发测试)
        X509TrustManager trustAllCerts = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }
        };

        // 创建 SSLContext
        SSLContext sslContext;
        SSLSocketFactory sslSocketFactory;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAllCerts}, null);
            sslSocketFactory = sslContext.getSocketFactory();
            Log.d("RetrofitClient", "SSL context configured to trust all certificates");
        } catch (Exception e) {
            Log.e("RetrofitClient", "Failed to configure SSL", e);
            sslSocketFactory = null;
        }

        // 构建 OkHttpClient
        OkHttpClient.Builder okHttpBuilder = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authInterceptor)
                .connectTimeout(ApiConstants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(ApiConstants.READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(ApiConstants.WRITE_TIMEOUT, TimeUnit.SECONDS);

        // 配置 SSL
        if (sslSocketFactory != null) {
            okHttpBuilder.sslSocketFactory(sslSocketFactory, trustAllCerts);
            okHttpBuilder.hostnameVerifier((hostname, session) -> true);
        }

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
