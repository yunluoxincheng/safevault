package com.ttt.safevault.service.manager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;
import io.reactivex.rxjava3.core.Observable;

/**
 * Centralized session/token access for UI-facing flows.
 * Keeps Activities/Fragments from directly wiring RetrofitClient/TokenManager.
 */
public class AuthSessionManager {

    private final TokenManager tokenManager;

    public AuthSessionManager(@NonNull Context context) {
        this.tokenManager = RetrofitClient.getInstance(context.getApplicationContext()).getTokenManager();
    }

    public boolean isLoggedIn() {
        return tokenManager.isLoggedIn();
    }

    @NonNull
    public Observable<String> refreshIfNearExpiry() {
        return tokenManager.refreshIfNearExpiry();
    }

    @Nullable
    public String getLastLoginEmail() {
        return tokenManager.getLastLoginEmail();
    }

    public void saveLastLoginEmail(@NonNull String email) {
        tokenManager.saveLastLoginEmail(email);
    }

    public void clearEmailVerificationStatus() {
        tokenManager.clearEmailVerificationStatus();
    }

    public void saveEmailLoginInfo(@NonNull String email, @Nullable String username, @Nullable String displayName) {
        tokenManager.saveEmailLoginInfo(email, username, displayName);
    }

    @Nullable
    public String getDisplayName() {
        return tokenManager.getDisplayName();
    }

    @Nullable
    public String getVerifiedUsername() {
        return tokenManager.getVerifiedUsername();
    }
}
