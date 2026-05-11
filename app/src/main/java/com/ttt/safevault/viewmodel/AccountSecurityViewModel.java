package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.core.ServiceLocator;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.SecurityConfig;
import com.ttt.safevault.security.biometric.BiometricAuthManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 账户安全设置 ViewModel
 *
 * 从 AccountSecurityFragment 迁移安全编排逻辑：
 * - 生物识别注册资格判断
 * - DeviceKey 注册执行
 * - 生物识别启用/禁用
 * - 密码验证
 * - 锁定/注销状态处理
 * - 密钥版本状态
 *
 * Fragment 保留职责：BiometricPrompt 托管、UI 渲染、对话框、导航
 */
public class AccountSecurityViewModel extends AndroidViewModel {

    private static final String TAG = "AccountSecurityVM";

    private final BackendService backendService;
    private final BiometricAuthManager biometricAuthManager;
    private final SecurityConfig securityConfig;
    private final ExecutorService executor;

    private String masterPasswordForEnrollment;

    // ========== LiveData ==========

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> _successMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _navigateToLogin = new MutableLiveData<>(false);

    // Biometric state
    private final MutableLiveData<Boolean> _biometricEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _biometricAvailable = new MutableLiveData<>();
    private final MutableLiveData<String> _biometricUnavailableReason = new MutableLiveData<>();

    // Key version state
    private final MutableLiveData<KeyVersionInfo> _keyVersionInfo = new MutableLiveData<>();

    // One-shot enrollment event
    private final MutableLiveData<EnrollmentReadiness> _enrollmentReadiness = new MutableLiveData<>();

    public LiveData<Boolean> isLoading = _isLoading;
    public LiveData<String> errorMessage = _errorMessage;
    public LiveData<String> successMessage = _successMessage;
    public LiveData<Boolean> navigateToLogin = _navigateToLogin;
    public LiveData<Boolean> biometricEnabled = _biometricEnabled;
    public LiveData<Boolean> biometricAvailable = _biometricAvailable;
    public LiveData<String> biometricUnavailableReason = _biometricUnavailableReason;
    public LiveData<KeyVersionInfo> keyVersionInfo = _keyVersionInfo;
    public LiveData<EnrollmentReadiness> enrollmentReadiness = _enrollmentReadiness;

    // ========== Inner classes ==========

    public static class KeyVersionInfo {
        public final String version;
        public final boolean hasMigratedToV3;

        KeyVersionInfo(@Nullable String version, boolean hasMigratedToV3) {
            this.version = version;
            this.hasMigratedToV3 = hasMigratedToV3;
        }
    }

    /**
     * Enrollment eligibility result.
     * Fragment uses this to decide whether to host the BiometricPrompt.
     */
    public static class EnrollmentReadiness {
        public final boolean canEnroll;
        public final boolean needsPassword;
        @Nullable
        public final String notAvailableReason;

        EnrollmentReadiness(boolean canEnroll, boolean needsPassword, @Nullable String notAvailableReason) {
            this.canEnroll = canEnroll;
            this.needsPassword = needsPassword;
            this.notAvailableReason = notAvailableReason;
        }

        public static EnrollmentReadiness notAvailable(String reason) {
            return new EnrollmentReadiness(false, false, reason);
        }

        public static EnrollmentReadiness readyWithSession() {
            return new EnrollmentReadiness(true, false, null);
        }

        public static EnrollmentReadiness readyNeedsPassword() {
            return new EnrollmentReadiness(true, true, null);
        }
    }

    // ========== Constructor ==========

    public AccountSecurityViewModel(@NonNull Application application) {
        this(application,
                ServiceLocator.getInstance().getBackendService(),
                BiometricAuthManager.getInstance(application));
    }

    public AccountSecurityViewModel(@NonNull Application application,
                                    @Nullable BackendService backendService,
                                    @Nullable BiometricAuthManager biometricAuthManager) {
        super(application);
        this.backendService = backendService;
        this.biometricAuthManager = biometricAuthManager;
        this.securityConfig = new SecurityConfig(application);
        this.executor = Executors.newSingleThreadExecutor();
    }

    // ========== Initialization ==========

    public void loadInitialState() {
        boolean canUseBiometric = biometricAuthManager != null && biometricAuthManager.canUseBiometric();
        _biometricAvailable.setValue(canUseBiometric);
        if (!canUseBiometric && biometricAuthManager != null) {
            _biometricUnavailableReason.setValue(biometricAuthManager.getBiometricNotAvailableReason());
        }
        _biometricEnabled.setValue(securityConfig.isBiometricEnabled());
        refreshKeyVersionStatus();
    }

    // ========== Biometric Enrollment ==========

    /**
     * Check enrollment eligibility. Fragment calls this before hosting BiometricPrompt.
     * Result delivered via enrollmentReadiness LiveData.
     */
    public void checkEnrollmentEligibility() {
        if (biometricAuthManager == null || !biometricAuthManager.canUseBiometric()) {
            String reason = biometricAuthManager != null
                    ? biometricAuthManager.getBiometricNotAvailableReason()
                    : "生物识别不可用";
            _enrollmentReadiness.setValue(EnrollmentReadiness.notAvailable(reason));
            return;
        }
        if (backendService == null || !backendService.isBiometricStorageReady()) {
            _enrollmentReadiness.setValue(EnrollmentReadiness.notAvailable(
                    "密钥存储未初始化，请先使用主密码登录"));
            return;
        }
        if (backendService.isUnlocked()) {
            _enrollmentReadiness.setValue(EnrollmentReadiness.readyWithSession());
        } else {
            _enrollmentReadiness.setValue(EnrollmentReadiness.readyNeedsPassword());
        }
    }

    /**
     * Complete DeviceKey enrollment using master password.
     * Called after user verifies password and BiometricPrompt succeeds.
     */
    public void completeEnrollmentWithPassword(@NonNull String masterPassword) {
        if (backendService == null) {
            _errorMessage.setValue("启用生物识别失败：服务不可用");
            return;
        }
        executor.execute(() -> {
            try {
                boolean success = backendService.completeBiometricEnrollmentWithPassword(masterPassword);
                if (success) {
                    securityConfig.setBiometricEnabled(true);
                    _biometricEnabled.postValue(true);
                    _successMessage.postValue("生物识别已启用");
                } else {
                    _errorMessage.postValue("启用生物识别失败：DeviceKey 注册失败");
                }
            } catch (Exception e) {
                Log.e(TAG, "completeEnrollmentWithPassword failed", e);
                _errorMessage.postValue("启用生物识别失败: " + e.getMessage());
            } finally {
                clearEnrollmentPassword();
            }
        });
    }

    /**
     * Complete DeviceKey enrollment using session DataKey (session already unlocked).
     * Called when BiometricPrompt succeeds and session is already unlocked.
     */
    public void completeEnrollmentWithSessionDataKey() {
        if (backendService == null || !backendService.isUnlocked()) {
            _errorMessage.setValue("启用生物识别失败：会话已过期");
            return;
        }

        executor.execute(() -> {
            try {
                boolean success = backendService.completeBiometricEnrollmentWithSessionDataKey();
                if (success) {
                    securityConfig.setBiometricEnabled(true);
                    _biometricEnabled.postValue(true);
                    _successMessage.postValue("生物识别已启用");
                } else {
                    _errorMessage.postValue("启用生物识别失败：DeviceKey 注册失败");
                }
            } catch (Exception e) {
                Log.e(TAG, "completeEnrollmentWithSessionDataKey failed", e);
                _errorMessage.postValue("启用生物识别失败: " + e.getMessage());
            }
        });
    }

    /**
     * Temporarily store master password for enrollment (cleared after use).
     */
    public void saveEnrollmentPassword(@NonNull String password) {
        this.masterPasswordForEnrollment = password;
    }

    @Nullable
    public String getEnrollmentPassword() {
        return masterPasswordForEnrollment;
    }

    public void clearEnrollmentPassword() {
        masterPasswordForEnrollment = null;
    }

    // ========== Biometric Disable ==========

    public void disableBiometric() {
        if (biometricAuthManager != null) {
            biometricAuthManager.disableBiometric();
        }
        securityConfig.setBiometricEnabled(false);
        _biometricEnabled.setValue(false);
    }

    // ========== Password Verification ==========

    /**
     * Verify master password by attempting unlock.
     * Returns true on success.
     */
    public boolean verifyPassword(@NonNull String password) {
        if (backendService == null) return false;
        try {
            return backendService.unlock(password);
        } catch (Exception e) {
            Log.e(TAG, "verifyPassword failed", e);
            return false;
        }
    }

    // ========== Session State ==========

    public boolean isSessionUnlocked() {
        return backendService != null && backendService.isUnlocked();
    }

    // ========== Master Password Change ==========

    public void changeMasterPassword(@Nullable String oldPassword, @NonNull String newPassword) {
        if (backendService == null) {
            _errorMessage.setValue("服务不可用");
            return;
        }
        executor.execute(() -> {
            try {
                boolean success = backendService.changeMasterPassword(oldPassword, newPassword);
                if (success) {
                    _successMessage.postValue("主密码更改成功");
                } else {
                    _errorMessage.postValue("主密码更改失败");
                }
            } catch (Exception e) {
                _errorMessage.postValue("更改失败: " + e.getMessage());
            }
        });
    }

    // ========== Logout ==========

    public void performLogout() {
        if (backendService == null) {
            _errorMessage.setValue("服务不可用");
            return;
        }
        _isLoading.setValue(true);
        executor.execute(() -> {
            try {
                backendService.logoutCloud();
                _navigateToLogin.postValue(true);
            } catch (Exception e) {
                _errorMessage.postValue("注销失败");
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    public void forceLogout() {
        if (backendService == null) {
            _errorMessage.setValue("服务不可用");
            return;
        }
        try {
            backendService.clearLocalCloudTokens();
            _navigateToLogin.setValue(true);
        } catch (Exception e) {
            _errorMessage.setValue("清除失败: " + e.getMessage());
        }
    }

    // ========== Delete Account ==========

    public void deleteAccount() {
        if (backendService == null) {
            _errorMessage.setValue("服务不可用");
            return;
        }
        executor.execute(() -> {
            try {
                boolean success = backendService.deleteAccount();
                if (success) {
                    _navigateToLogin.postValue(true);
                } else {
                    _errorMessage.postValue("账户删除失败");
                }
            } catch (Exception e) {
                _errorMessage.postValue("删除失败: " + e.getMessage());
            }
        });
    }

    // ========== Key Version Status ==========

    public void refreshKeyVersionStatus() {
        if (backendService == null) {
            _keyVersionInfo.setValue(new KeyVersionInfo(null, false));
            return;
        }
        executor.execute(() -> {
            try {
                String version = backendService.getKeyVersion();
                boolean migrated = backendService.hasMigratedToV3();
                _keyVersionInfo.postValue(new KeyVersionInfo(version, migrated));
            } catch (Exception e) {
                _keyVersionInfo.postValue(new KeyVersionInfo(null, false));
            }
        });
    }

    // ========== Key Migration ==========

    public boolean isSessionReadyForMigration() {
        return backendService != null && backendService.isUnlocked();
    }

    // ========== SecurityConfig Delegation ==========

    public SecurityConfig.AutoLockMode getAutoLockMode() {
        return securityConfig.getAutoLockMode();
    }

    public void setAutoLockMode(SecurityConfig.AutoLockMode mode) {
        securityConfig.setAutoLockMode(mode);
    }

    public boolean isScreenshotAllowed() {
        return securityConfig.isScreenshotAllowed();
    }

    public void setScreenshotAllowed(boolean allowed) {
        securityConfig.setScreenshotAllowed(allowed);
    }

    public boolean isPinCodeEnabled() {
        return securityConfig.isPinCodeEnabled();
    }

    public boolean isBiometricEnabled() {
        return securityConfig.isBiometricEnabled();
    }

    public boolean setPinCode(@NonNull String pin) {
        return backendService != null && backendService.setPinCode(pin);
    }

    public boolean verifyPinCode(@NonNull String pin) {
        return backendService != null && backendService.verifyPinCode(pin);
    }

    public void clearPinCode() {
        if (backendService != null) backendService.clearPinCode();
    }

    // ========== Export/Import ==========

    public boolean exportData(@NonNull String filePath) {
        return backendService != null && backendService.exportData(filePath);
    }

    public boolean importData(@NonNull String filePath) {
        return backendService != null && backendService.importData(filePath);
    }

    // ========== Utility ==========

    public void clearError() {
        _errorMessage.setValue(null);
    }

    public void clearSuccess() {
        _successMessage.setValue(null);
    }

    public BiometricAuthManager getBiometricAuthManager() {
        return biometricAuthManager;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        clearEnrollmentPassword();
        executor.shutdown();
    }
}
