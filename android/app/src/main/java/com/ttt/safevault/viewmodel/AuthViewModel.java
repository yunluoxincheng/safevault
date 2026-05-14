package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.dto.response.AuthResponse;
import com.ttt.safevault.dto.response.EmailLoginResponse;
import com.ttt.safevault.dto.response.EmailRegistrationResponse;
import com.ttt.safevault.dto.response.LoginPrecheckResponse;
import com.ttt.safevault.dto.response.VerifyEmailResponse;
import com.ttt.safevault.model.BackendService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 用户认证ViewModel
 *
 * 所有网络、Token、加密操作通过 BackendService facade 路由，
 * 不再直接导入 RetrofitClient、TokenManager、SecureKeyStorageManager、Argon2KeyDerivationManager。
 */
public class AuthViewModel extends AndroidViewModel {
    private static final String TAG = "AuthViewModel";

    private final BackendService backendService;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<AuthResponse> authResponseLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    private final MutableLiveData<EmailRegistrationResponse> emailRegistrationLiveData = new MutableLiveData<>();
    private final MutableLiveData<VerifyEmailResponse> emailVerificationLiveData = new MutableLiveData<>();
    private final MutableLiveData<EmailLoginResponse> emailLoginLiveData = new MutableLiveData<>();
    private final MutableLiveData<LoginPrecheckResponse> loginPrecheckLiveData = new MutableLiveData<>();
    private final MutableLiveData<com.ttt.safevault.dto.response.VerificationStatusResponse> verificationStatusLiveData = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application application, BackendService backendService) {
        super(application);
        this.backendService = backendService;
    }

    // ========== Legacy Auth (device-key based) ==========

    public void register(String username, String displayName) {
        loadingLiveData.setValue(true);

        Disposable disposable = Observable.fromCallable(() -> backendService.registerWithDeviceKey(username, displayName))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    authResponseLiveData.setValue(response);
                    Log.d(TAG, "Register success: " + response.getUserId());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    errorLiveData.setValue("注册失败: " + error.getMessage());
                    Log.e(TAG, "Register failed", error);
                }
            );

        disposables.add(disposable);
    }

    public void login() {
        loadingLiveData.setValue(true);

        String userId = backendService.getCloudUserId();
        if (userId == null) {
            loadingLiveData.setValue(false);
            errorLiveData.setValue("请先注册账号");
            return;
        }

        Disposable disposable = Observable.fromCallable(() -> backendService.loginWithDeviceKey())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    authResponseLiveData.setValue(response);
                    Log.d(TAG, "Login success: " + response.getUserId());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    errorLiveData.setValue("登录失败: " + error.getMessage());
                    Log.e(TAG, "Login failed", error);
                }
            );

        disposables.add(disposable);
    }

    public void loginWithUsername(String username) {
        loadingLiveData.setValue(true);

        Disposable disposable = Observable.fromCallable(() -> backendService.loginByUsername(username))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    authResponseLiveData.setValue(response);
                    Log.d(TAG, "Login by username success: " + response.getUserId());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    errorLiveData.setValue("登录失败: " + error.getMessage());
                    Log.e(TAG, "Login by username failed", error);
                }
            );

        disposables.add(disposable);
    }

    public void refreshToken() {
        String refreshToken = backendService.getCloudRefreshToken();
        if (refreshToken == null) {
            errorLiveData.setValue("无刷新Token");
            return;
        }

        Disposable disposable = Observable.fromCallable(() -> backendService.refreshCurrentToken())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    authResponseLiveData.setValue(response);
                    Log.d(TAG, "Token refreshed");
                },
                error -> {
                    backendService.clearLocalCloudTokens();
                    errorLiveData.setValue("Token刷新失败，请重新登录");
                    Log.e(TAG, "Token refresh failed", error);
                }
            );

        disposables.add(disposable);
    }

    public boolean isLoggedIn() {
        return backendService.isCloudLoggedIn();
    }

    public void logout() {
        backendService.clearLocalCloudTokens();
        authResponseLiveData.setValue(null);
    }

    // ========== Email Auth ==========

    public void registerWithEmail(String email, String username) {
        loadingLiveData.setValue(true);

        Disposable disposable = Observable.fromCallable(() -> backendService.registerWithEmail(email, username))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    emailRegistrationLiveData.setValue(response);
                    Log.d(TAG, "Email registration success: " + response.getMessage());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    errorLiveData.setValue("注册失败: " + error.getMessage());
                    Log.e(TAG, "Email registration failed", error);
                }
            );

        disposables.add(disposable);
    }

    public void verifyEmail(String token) {
        loadingLiveData.setValue(true);

        Disposable disposable = Observable.fromCallable(() -> backendService.verifyEmail(token))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    emailVerificationLiveData.setValue(response);
                    Log.d(TAG, "Email verification success: " + response.getMessage());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    errorLiveData.setValue("验证失败: " + error.getMessage());
                    Log.e(TAG, "Email verification failed", error);
                }
            );

        disposables.add(disposable);
    }

    public void resendVerificationEmail(String email) {
        loadingLiveData.setValue(true);

        Disposable disposable = Observable.fromCallable(() -> backendService.resendVerificationEmail(email))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    emailRegistrationLiveData.setValue(response);
                    Log.d(TAG, "Resend verification success: " + response.getMessage());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    errorLiveData.setValue("重发失败: " + error.getMessage());
                    Log.e(TAG, "Resend verification failed", error);
                }
            );

        disposables.add(disposable);
    }

    public void checkVerificationStatus(String email) {
        Disposable disposable = Observable.fromCallable(() -> backendService.checkVerificationStatus(email))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    verificationStatusLiveData.setValue(response);
                    Log.d(TAG, "Verification status check: " + response.getStatus() +
                               ", email: " + response.getEmail());
                },
                error -> {
                    Log.e(TAG, "Verification status check failed", error);
                }
            );

        disposables.add(disposable);
    }

    public void loginWithEmail(String email, String masterPassword) {
        loadingLiveData.setValue(true);

        Disposable disposable = Observable.fromCallable(() -> backendService.loginWithEmail(email, masterPassword))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    emailLoginLiveData.setValue(response);
                    Log.d(TAG, "Email login success: " + response.getUserId() +
                               ", isNewDevice: " + response.getIsNewDevice());
                },
                error -> {
                    loadingLiveData.setValue(false);
                    errorLiveData.setValue("登录失败: " + error.getMessage());
                    Log.e(TAG, "Email login failed", error);
                }
            );

        disposables.add(disposable);
    }

    // ========== RegisterActivity Facades ==========

    public boolean isVaultInitialized() {
        return backendService.isInitialized();
    }

    public boolean resetLocalVault() {
        return backendService.resetLocalVault();
    }

    public void clearEmailVerificationStatus() {
        backendService.clearEmailVerificationStatus();
    }

    public void saveLastLoginEmail(String email) {
        backendService.saveLastLoginEmail(email);
    }

    private final MutableLiveData<com.ttt.safevault.dto.response.CompleteRegistrationResponse> completeRegistrationLiveData = new MutableLiveData<>();

    public LiveData<com.ttt.safevault.dto.response.CompleteRegistrationResponse> getCompleteRegistrationResponse() {
        return completeRegistrationLiveData;
    }

    /**
     * 异步完成注册（邮箱验证后设置主密码）
     */
    public void completeRegistration(String email, String username, String password) {
        loadingLiveData.setValue(true);

        Disposable disposable = Observable.fromCallable(
                () -> backendService.completeRegistration(email, username, password))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    loadingLiveData.setValue(false);
                    completeRegistrationLiveData.setValue(response);
                },
                error -> {
                    loadingLiveData.setValue(false);
                    errorLiveData.setValue("注册失败: " + error.getMessage());
                    Log.e(TAG, "Complete registration failed", error);
                }
            );

        disposables.add(disposable);
    }

    // Getters for LiveData

    public LiveData<AuthResponse> getAuthResponse() {
        return authResponseLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<Boolean> getLoading() {
        return loadingLiveData;
    }

    public LiveData<EmailRegistrationResponse> getEmailRegistrationResponse() {
        return emailRegistrationLiveData;
    }

    public LiveData<VerifyEmailResponse> getEmailVerificationResponse() {
        return emailVerificationLiveData;
    }

    public LiveData<EmailLoginResponse> getEmailLoginResponse() {
        return emailLoginLiveData;
    }

    public LiveData<LoginPrecheckResponse> getLoginPrecheckResponse() {
        return loginPrecheckLiveData;
    }

    public LiveData<com.ttt.safevault.dto.response.VerificationStatusResponse> getVerificationStatusResponse() {
        return verificationStatusLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}
