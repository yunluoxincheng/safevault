package com.ttt.safevault.network;

import com.ttt.safevault.BuildConfig;

import android.util.Log;

import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * SSL配置工具类
 *
 * 生产环境使用系统默认TLS校验。
 * Debug环境仅对域名白名单启用自签证书支持。
 */
public class SslUtils {
    private static final String TAG = "SslUtils";

    /**
     * 为OkHttpClient.Builder配置SSL（Debug域名白名单例外）
     */
    public static OkHttpClient.Builder configureSsl(OkHttpClient.Builder builder) {
        if (BuildConfig.DEBUG) {
            X509TrustManager debugTrustManager = DebugSslProvider.getTrustManager();
            SSLSocketFactory sslSocketFactory = DebugSslProvider.getSSLSocketFactory();
            if (sslSocketFactory != null && debugTrustManager != null) {
                builder.sslSocketFactory(sslSocketFactory, debugTrustManager);
                builder.hostnameVerifier((hostname, session) ->
                    DebugSslProvider.isDomainAllowed(hostname));
                Log.d(TAG, "Debug SSL whitelist enabled for WebSocket");
            } else {
                Log.w(TAG, "Debug SSL provider unavailable, fallback to system trust");
            }
        } else {
            Log.i(TAG, "Release build uses system default TLS validation");
        }

        return builder;
    }

    /**
     * 创建预配置的OkHttpClient（用于WebSocket）
     *
     * @param readTimeoutMs 读超时时间（毫秒），0表示无限
     * @param pingIntervalMs 心跳间隔（毫秒）
     * @return 配置好的OkHttpClient
     */
    public static OkHttpClient createOkHttpClient(long readTimeoutMs, long pingIntervalMs) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .pingInterval(pingIntervalMs, TimeUnit.MILLISECONDS);

        return configureSsl(builder).build();
    }
}
