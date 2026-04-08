package com.ttt.safevault.core;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.SecureKeyStorageManager;
import com.ttt.safevault.security.SecurityConfig;
import com.ttt.safevault.security.SecurityManager;
import com.ttt.safevault.security.SessionGuard;
import com.ttt.safevault.security.biometric.BiometricAuthManager;

/**
 * Core namespace facade for ServiceLocator.
 *
 * <p>Delegates to legacy package location to keep existing code stable while
 * enabling new imports under {@code com.ttt.safevault.core}.
 */
public final class ServiceLocator {

    private static final ServiceLocator INSTANCE = new ServiceLocator();

    private ServiceLocator() {
    }

    public static void init(@NonNull Context context) {
        com.ttt.safevault.ServiceLocator.init(context);
    }

    public static ServiceLocator getInstance() {
        return INSTANCE;
    }

    private com.ttt.safevault.ServiceLocator delegate() {
        return com.ttt.safevault.ServiceLocator.getInstance();
    }

    public BackendService getBackendService() {
        return delegate().getBackendService();
    }

    public SecurityManager getSecurityManager() {
        return delegate().getSecurityManager();
    }

    public SecurityConfig getSecurityConfig() {
        return delegate().getSecurityConfig();
    }

    public SessionGuard getSessionGuard() {
        return delegate().getSessionGuard();
    }

    public SecureKeyStorageManager getSecureKeyStorageManager() {
        return delegate().getSecureKeyStorageManager();
    }

    public BiometricAuthManager getBiometricAuthManager() {
        return delegate().getBiometricAuthManager();
    }

    public Context getApplicationContext() {
        return delegate().getApplicationContext();
    }
}
