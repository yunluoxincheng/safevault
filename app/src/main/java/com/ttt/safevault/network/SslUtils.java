package com.ttt.safevault.network;

import android.util.Log;

import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * SSL配置工具类
 * 用于为OkHttpClient配置自定义SSL（开发环境信任所有证书）
 *
 * 注意：仅用于开发测试环境，生产环境应使用有效证书
 */
public class SslUtils {
    private static final String TAG = "SslUtils";

    /**
     * 创建信任所有证书的TrustManager（仅用于开发测试）
     */
    public static X509TrustManager createTrustAllCertsTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // 信任所有客户端证书
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // 信任所有服务器证书
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }
        };
    }

    /**
     * 创建SSLContext（信任所有证书）
     */
    public static SSLContext createTrustAllSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{createTrustAllCertsTrustManager()}, null);
            return sslContext;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create SSL context", e);
            return null;
        }
    }

    /**
     * 为OkHttpClient.Builder配置SSL（信任所有证书）
     *
     * @param builder OkHttpClient.Builder
     * @return 配置后的builder（支持链式调用）
     */
    public static OkHttpClient.Builder configureSsl(OkHttpClient.Builder builder) {
        X509TrustManager trustAllCerts = createTrustAllCertsTrustManager();
        SSLContext sslContext = createTrustAllSslContext();

        if (sslContext != null) {
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts);
            builder.hostnameVerifier((hostname, session) -> true);
            Log.d(TAG, "SSL configured to trust all certificates (development mode)");
        } else {
            Log.e(TAG, "Failed to configure SSL");
        }

        return builder;
    }

    /**
     * 创建预配置的OkHttpClient（信任所有证书，用于WebSocket）
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
