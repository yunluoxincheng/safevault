package com.ttt.safevault;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.CryptoSession;
import com.ttt.safevault.security.SecureKeyStorageManager;
import com.ttt.safevault.security.SecurityConfig;
import com.ttt.safevault.security.SecurityManager;
import com.ttt.safevault.security.biometric.BiometricAuthManager;
import com.ttt.safevault.service.BackendServiceImpl;

/**
 * 服务定位器
 * 提供全局单例服务的访问点
 *
 * 三层安全架构：提供新架构组件的访问点
 *
 * @since SafeVault 3.4.0 (移除旧安全架构，完全迁移到三层架构)
 */
public class ServiceLocator {

    private static volatile ServiceLocator INSTANCE;

    private final Context applicationContext;
    private BackendService backendService;
    private SecurityManager securityManager;
    private SecurityConfig securityConfig;

    private ServiceLocator(@NonNull Context context) {
        this.applicationContext = context.getApplicationContext();
    }

    public static void init(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (ServiceLocator.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ServiceLocator(context);
                }
            }
        }
    }

    public static ServiceLocator getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("ServiceLocator must be initialized first. Call init() in Application.onCreate()");
        }
        return INSTANCE;
    }

    /**
     * 获取后端服务
     */
    public BackendService getBackendService() {
        if (backendService == null) {
            synchronized (this) {
                if (backendService == null) {
                    backendService = new BackendServiceImpl(applicationContext);
                }
            }
        }
        return backendService;
    }

    /**
     * 获取安全管理器
     */
    public SecurityManager getSecurityManager() {
        if (securityManager == null) {
            synchronized (this) {
                if (securityManager == null) {
                    securityManager = SecurityManager.getInstance(applicationContext);
                }
            }
        }
        return securityManager;
    }

    /**
     * 获取安全配置
     */
    public SecurityConfig getSecurityConfig() {
        if (securityConfig == null) {
            synchronized (this) {
                if (securityConfig == null) {
                    securityConfig = new SecurityConfig(applicationContext);
                }
            }
        }
        return securityConfig;
    }

    // ========== 三层安全架构组件 ==========

    /**
     * 获取加密会话（CryptoSession）
     *
     * @return CryptoSession 单例
     */
    public CryptoSession getCryptoSession() {
        return CryptoSession.getInstance();
    }

    /**
     * 获取安全密钥存储管理器（SecureKeyStorageManager）
     *
     * @return SecureKeyStorageManager 单例
     */
    public SecureKeyStorageManager getSecureKeyStorageManager() {
        return SecureKeyStorageManager.getInstance(applicationContext);
    }

    /**
     * 获取生物识别认证管理器（BiometricAuthManager）
     *
     * @return BiometricAuthManager 单例
     */
    public BiometricAuthManager getBiometricAuthManager() {
        return BiometricAuthManager.getInstance(applicationContext);
    }

    /**
     * 获取应用上下文
     */
    public Context getApplicationContext() {
        return applicationContext;
    }
}
