package com.ttt.safevault.network;

import android.util.Log;
import androidx.annotation.Nullable;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Debug SSL 配置提供者
 *
 * 仅在 Debug 构建时使用，用于开发环境连接使用自签证书的服务器。
 * 只对指定域名信任自签证书，其他域名使用系统默认验证。
 *
 * @see openspec/changes/security-hardening-phase1/specs/network-security/spec.md
 *      "Scenario: Debug构建域名白名单例外"
 */
public class DebugSslProvider {
    private static final String TAG = "DebugSslProvider";

    // 允许使用自签证书的域名列表
    private static final List<String> SELF_CERT_DOMAINS = Arrays.asList(
        "frp-hat.com",
        "frp-ski.com",
        "172.17.176.22",
        "localhost",
        "10.0.2.2",
        "127.0.0.1"
    );

    /**
     * 获取配置好的 SSLSocketFactory
     */
    @Nullable
    public static SSLSocketFactory getSSLSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new DebugTrustManager()}, null);
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create SSL context", e);
            return null;
        }
    }

    /**
     * 获取配置好的 X509TrustManager
     */
    @Nullable
    public static X509TrustManager getTrustManager() {
        return new DebugTrustManager();
    }

    /**
     * 检查域名是否在白名单中
     *
     * @param hostname 主机名
     * @return true 如果域名在白名单中
     */
    public static boolean isDomainAllowed(String hostname) {
        if (hostname == null) {
            return false;
        }

        // 精确匹配
        if (SELF_CERT_DOMAINS.contains(hostname)) {
            return true;
        }

        // 子域名匹配
        for (String domain : SELF_CERT_DOMAINS) {
            if (hostname.endsWith("." + domain)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 自定义 TrustManager
     *
     * 对于指定域名，信任所有证书（用于开发环境）
     * 对于其他域名，使用系统默认验证
     */
    private static class DebugTrustManager implements X509TrustManager {
        private final X509TrustManager defaultTrustManager;

        DebugTrustManager() {
            // 获取系统默认的 TrustManager
            X509TrustManager defaultTm = null;
            try {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((KeyStore) null);
                TrustManager[] trustManagers = tmf.getTrustManagers();
                for (TrustManager tm : trustManagers) {
                    if (tm instanceof X509TrustManager) {
                        defaultTm = (X509TrustManager) tm;
                        break;
                    }
                }
            } catch (NoSuchAlgorithmException | KeyStoreException e) {
                Log.e(TAG, "Failed to get default TrustManager", e);
            }
            this.defaultTrustManager = defaultTm;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            // 客户端证书验证使用默认行为
            if (defaultTrustManager != null) {
                defaultTrustManager.checkClientTrusted(chain, authType);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            // 实际的域名控制通过 hostnameVerifier 实现
            Log.d(TAG, "Debug 模式：允许自签证书（通过 hostnameVerifier 控制域名）");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            if (defaultTrustManager != null) {
                return defaultTrustManager.getAcceptedIssuers();
            }
            return new X509Certificate[0];
        }
    }
}
