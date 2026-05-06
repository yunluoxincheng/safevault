package com.ttt.safevault.core;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.SecureKeyStorageManager;
import com.ttt.safevault.security.SecurityConfig;
import com.ttt.safevault.security.SecurityManager;
import com.ttt.safevault.security.SessionGuard;
import com.ttt.safevault.security.biometric.BiometricAuthManager;
import com.ttt.safevault.service.BackendServiceImpl;

/**
 * Service locator for app-wide singletons.
 */
public final class ServiceLocator {

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
            throw new IllegalStateException("ServiceLocator must be initialized in Application.onCreate()");
        }
        return INSTANCE;
    }

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

    public SessionGuard getSessionGuard() {
        return SessionGuard.getInstance();
    }

    public SecureKeyStorageManager getSecureKeyStorageManager() {
        return SecureKeyStorageManager.getInstance(applicationContext);
    }

    public BiometricAuthManager getBiometricAuthManager() {
        return BiometricAuthManager.getInstance(applicationContext);
    }

    public Context getApplicationContext() {
        return applicationContext;
    }
}
