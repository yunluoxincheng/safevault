package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.BiometricAuthHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 登录/解锁页面的ViewModel
 * 负责处理用户认证相关的业务逻辑
 */
public class LoginViewModel extends AndroidViewModel {

    private final BackendService backendService;
    private final ExecutorService executor;

    // LiveData用于UI状态管理
    private final MutableLiveData<Boolean> _isAuthenticated = new MutableLiveData<>(false);
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> _isInitialized = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> _canUseBiometric = new MutableLiveData<>(false);

    public LiveData<Boolean> isAuthenticated = _isAuthenticated;
    public LiveData<String> errorMessage = _errorMessage;
    public LiveData<Boolean> isLoading = _isLoading;
    public LiveData<Boolean> isInitialized = _isInitialized;
    public LiveData<Boolean> canUseBiometric = _canUseBiometric;

    public LoginViewModel(@NonNull Application application, BackendService backendService) {
        super(application);
        this.backendService = backendService;
        this.executor = Executors.newSingleThreadExecutor();
        checkInitializationStatus();
    }

    /**
     * 检查应用是否已初始化
     */
    private void checkInitializationStatus() {
        _isLoading.setValue(true);
        executor.execute(() -> {
            try {
                boolean initialized = backendService.isInitialized();
                _isInitialized.postValue(initialized);

                // 检查是否可以使用生物识别
                // 条件：设备支持 + 后端服务确认可用（有保存的生物识别凭证）
                // 注意：移除了对 initialized 的依赖，因为在云端登录模式下，
                // 本地可能未初始化，但仍可能使用生物识别（凭证独立存储）
                boolean biometricSupported = checkBiometricSupport();
                boolean canUseBiometric = biometricSupported && backendService.canUseBiometricAuthentication();
                _canUseBiometric.postValue(canUseBiometric);
            } catch (Exception e) {
                _errorMessage.postValue("检查初始化状态失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 使用主密码登录/解锁
     */
    public void loginWithPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            _errorMessage.setValue("请输入密码");
            return;
        }

        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                boolean success = backendService.unlock(password);
                if (success) {
                    // 登录成功后清除后台时间记录，避免刚登录就被自动锁定
                    backendService.clearBackgroundTime();
                    _isAuthenticated.postValue(true);
                } else {
                    _errorMessage.postValue("密码错误，请重试");
                }
            } catch (Exception e) {
                _errorMessage.postValue("登录失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 初始化应用，设置主密码
     */
    public void initializeWithPassword(String password, String confirmPassword) {
        // 验证密码输入
        String validationError = validatePasswordInput(password, confirmPassword);
        if (validationError != null) {
            _errorMessage.setValue(validationError);
            return;
        }

        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                boolean success = backendService.initialize(password);
                if (success) {
                    // 初始化成功后清除后台时间记录
                    backendService.clearBackgroundTime();
                    _isInitialized.postValue(true);
                    _isAuthenticated.postValue(true);
                } else {
                    _errorMessage.postValue("初始化失败，请重试");
                }
            } catch (Exception e) {
                _errorMessage.postValue("初始化失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 使用生物识别解锁
     */
    public void loginWithBiometric() {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                // 通过BackendService验证是否可以使用生物识别
                boolean canUseBiometric = backendService.canUseBiometricAuthentication();

                if (!canUseBiometric) {
                    _errorMessage.postValue("生物识别认证未启用或不可用");
                    _isLoading.postValue(false);
                    return;
                }

                // 生物识别验证成功，调用后端解锁
                boolean unlocked = backendService.unlockWithBiometric();
                if (unlocked) {
                    // 生物识别解锁成功后清除后台时间记录
                    backendService.clearBackgroundTime();
                    _isAuthenticated.postValue(true);
                } else {
                    _errorMessage.postValue("生物识别解锁失败，请用主密码重试");
                }
            } catch (Exception e) {
                _errorMessage.postValue("生物识别认证出错: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 清除错误信息
     */
    public void clearError() {
        _errorMessage.setValue(null);
    }

    /**
     * 验证密码输入
     */
    private String validatePasswordInput(String password, String confirmPassword) {
        if (password == null || password.trim().isEmpty()) {
            return "请输入密码";
        }

        if (password.length() < 8) {
            return "密码长度至少8位";
        }

        if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
            return "请确认密码";
        }

        if (!password.equals(confirmPassword)) {
            return "两次输入的密码不一致";
        }

        // 检查密码强度
        if (!isStrongPassword(password)) {
            return "密码必须包含大小写字母和数字";
        }

        return null;
    }

    /**
     * 检查密码强度
     */
    private boolean isStrongPassword(String password) {
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }

        return hasUpper && hasLower && hasDigit;
    }

    /**
     * 检查生物识别支持
     */
    private boolean checkBiometricSupport() {
        try {
            BiometricManager biometricManager = BiometricManager.from(getApplication());
            int canAuthenticateResult = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG | 
                BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            return canAuthenticateResult == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}