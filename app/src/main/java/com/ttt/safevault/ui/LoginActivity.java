package com.ttt.safevault.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.core.splashscreen.SplashScreen;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ttt.safevault.R;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.BiometricAuthHelper;
import com.ttt.safevault.security.biometric.BiometricAuthManager;
import com.ttt.safevault.security.biometric.AuthCallback;
import com.ttt.safevault.security.biometric.AuthError;
import com.ttt.safevault.security.biometric.AuthScenario;
import com.ttt.safevault.viewmodel.AuthViewModel;
import com.ttt.safevault.viewmodel.LoginViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

/**
 * 登录/解锁页面
 * 使用完全云端登录模式，通过邮箱+主密码进行云端验证
 */
public class LoginActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "LoginActivity";

    // UI组件
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private Button loginButton;
    private Button biometricButton;
    private TextView errorText;
    private TextView titleText;
    private TextView subtitleText;
    private TextView registerText;
    private ProgressBar progressBar;

    // ViewModel
    private AuthViewModel authViewModel;
    private LoginViewModel loginViewModel;

    // 状态标志
    private boolean isPasswordVisible = false;
    private boolean fromAutofill = false;  // 是否从自动填充跳转过来
    private boolean fromAutofillSave = false;  // 是否从自动填充保存跳转过来
    private boolean biometricAutoTriggered = false;  // 是否已自动触发生物识别

    // 生物识别认证助手（保留用于基础检查）
    private BiometricAuthHelper biometricAuthHelper;
    // 生物识别认证管理器（新架构）
    private BiometricAuthManager biometricAuthManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 安装启动画面 (兼容API 29+)
        SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 防止截图 - 根据 SecurityConfig 设置决定
        com.ttt.safevault.security.SecurityConfig securityConfig =
            new com.ttt.safevault.security.SecurityConfig(this);
        if (securityConfig.isScreenshotProtectionEnabled()) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }

        // 检查是否从自动填充跳转过来
        if (getIntent() != null) {
            fromAutofill = getIntent().getBooleanExtra("from_autofill", false);
            fromAutofillSave = getIntent().getBooleanExtra("from_autofill_save", false);
        }

        // 初始化ViewModel
        ViewModelProvider.Factory factory = new ViewModelFactory(getApplication());
        authViewModel = new ViewModelProvider(this, factory).get(AuthViewModel.class);
        loginViewModel = new ViewModelProvider(this, factory).get(LoginViewModel.class);

        initViews();
        setupObservers();
        setupClickListeners();
        setupTextWatchers();
        initBiometricAuth();
    }

    private void initViews() {
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        emailLayout = findViewById(R.id.email_layout);
        passwordLayout = findViewById(R.id.password_layout);
        loginButton = findViewById(R.id.login_button);
        biometricButton = findViewById(R.id.biometric_button);
        errorText = findViewById(R.id.error_text);
        titleText = findViewById(R.id.title_text);
        subtitleText = findViewById(R.id.subtitle_text);
        registerText = findViewById(R.id.register_text);
        progressBar = findViewById(R.id.progress_bar);

        // 设置密码输入框默认图标为闭眼
        if (passwordLayout != null) {
            passwordLayout.setEndIconDrawable(R.drawable.ic_visibility);
        }

        // 预填充上次登录的邮箱
        String lastEmail = com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext())
                .getTokenManager().getLastLoginEmail();
        if (emailInput != null && lastEmail != null && !lastEmail.isEmpty()) {
            emailInput.setText(lastEmail);
        }
    }

    private void setupObservers() {
        // 移除对不存在的 canUseBiometric 的观察
        // 生物识别按钮的可见性将直接在 onResume() 和 checkAndRequestBiometricPermission() 中检查

        // 观察邮箱登录响应
        authViewModel.getEmailLoginResponse().observe(this, response -> {
            // 添加日志以便调试
            Log.d(TAG, "收到邮箱登录响应: " + (response != null ? "有数据" : "null"));
            if (response != null) {
                Log.d(TAG, "response.getUserId() = " + response.getUserId());
                Log.d(TAG, "response.getUsername() = " + response.getUsername());
                Log.d(TAG, "response.getDisplayName() = " + response.getDisplayName());
            }

            // 检查响应是否有效（userId 不为空表示登录成功）
            if (response != null && response.getUserId() != null) {
                // 登录成功，初始化 CryptoManager 并进入主页
                handleCloudLoginSuccess(response);
            }
        });

        // 观察错误信息
        authViewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showError(error);
            } else {
                hideError();
            }
        });

        // 观察加载状态
        authViewModel.getLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                updateLoadingState(isLoading);
            }
        });
    }

    private void setupClickListeners() {
        // 登录按钮
        loginButton.setOnClickListener(v -> {
            handleCloudLogin();
        });

        // 生物识别按钮
        biometricButton.setOnClickListener(v -> {
            performBiometricAuthentication();
        });

        // 注册账号链接
        registerText.setOnClickListener(v -> {
            // 清除验证状态（开始新的注册流程）
            com.ttt.safevault.network.TokenManager tokenManager =
                com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext()).getTokenManager();
            tokenManager.clearEmailVerificationStatus();

            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        });

        // 清除错误
        if (emailInput != null) {
            emailInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    hideError();
                }
            });
        }
        if (passwordInput != null) {
            passwordInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    hideError();
                }
            });
        }

        // 密码显示/隐藏按钮
        if (passwordLayout != null) {
            passwordLayout.setEndIconOnClickListener(v -> {
                togglePasswordVisibility();
            });
        }
    }

    private void setupTextWatchers() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLoginButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        if (emailInput != null) {
            emailInput.addTextChangedListener(textWatcher);
        }
        if (passwordInput != null) {
            passwordInput.addTextChangedListener(textWatcher);
        }
    }

    private void updateLoginButtonState() {
        String email = emailInput != null ? emailInput.getText().toString().trim() : "";
        String password = passwordInput != null ? passwordInput.getText().toString().trim() : "";

        // 验证邮箱格式和密码非空
        boolean emailValid = !TextUtils.isEmpty(email) && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
        boolean passwordValid = !TextUtils.isEmpty(password);
        boolean enabled = emailValid && passwordValid;

        if (loginButton != null) {
            Boolean isLoading = authViewModel.getLoading().getValue();
            loginButton.setEnabled(enabled && (isLoading == null || !isLoading));
        }
    }

    private void updateLoadingState(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }

        if (passwordInput != null) {
            passwordInput.setEnabled(!isLoading);
        }

        if (loginButton != null) {
            loginButton.setEnabled(!isLoading);
        }

        if (biometricButton != null) {
            biometricButton.setEnabled(!isLoading);
        }

        updateLoginButtonState();
    }

    private void showError(String error) {
        if (errorText != null) {
            errorText.setText(error);
            errorText.setVisibility(View.VISIBLE);
        }

        if (passwordLayout != null) {
            passwordLayout.setError(error);
        }
    }

    private void hideError() {
        if (errorText != null) {
            errorText.setVisibility(View.GONE);
        }

        if (passwordLayout != null) {
            passwordLayout.setError(null);
        }
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 处理云端登录
     */
    private void handleCloudLogin() {
        String email = emailInput != null ? emailInput.getText().toString().trim() : "";
        String password = passwordInput != null ? passwordInput.getText().toString().trim() : "";

        // 验证邮箱
        if (TextUtils.isEmpty(email)) {
            showError("请输入邮箱");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("请输入有效的邮箱地址");
            return;
        }

        // 验证密码
        if (TextUtils.isEmpty(password)) {
            showError("请输入主密码");
            return;
        }

        // 保存邮箱到本地（下次自动填充）
        com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext())
                .getTokenManager().saveLastLoginEmail(email);

        // 调用云端登录
        authViewModel.loginWithEmail(email, password);
    }

    /**
     * 处理云端登录成功
     */
    private void handleCloudLoginSuccess(com.ttt.safevault.dto.response.EmailLoginResponse response) {
        try {
            // 添加详细日志以调试服务器返回的数据
            android.util.Log.d(TAG, "=== EmailLoginResponse 详情 ===");
            android.util.Log.d(TAG, "userId: " + response.getUserId());
            android.util.Log.d(TAG, "email: " + response.getEmail());
            android.util.Log.d(TAG, "username: " + response.getUsername());
            android.util.Log.d(TAG, "displayName: " + response.getDisplayName());
            android.util.Log.d(TAG, "accessToken: " + (response.getAccessToken() != null ? "有值" : "null"));
            android.util.Log.d(TAG, "========================");

            String password = passwordInput != null ? passwordInput.getText().toString().trim() : "";
            String email = emailInput != null ? emailInput.getText().toString().trim() : "";

            // 保存邮箱登录信息到 TokenManager（包括 displayName 和 username）
            String responseUsername = response.getUsername();
            String responseDisplayName = response.getDisplayName();
            com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext())
                    .getTokenManager().saveEmailLoginInfo(email, responseUsername, responseDisplayName);
            android.util.Log.d(TAG, "用户信息已保存 - email: " + email + ", username: " + responseUsername + ", displayName: " + responseDisplayName);

            // 保存当前用户邮箱到 AccountManager（通过 BackendService）
            com.ttt.safevault.model.BackendService backendService =
                    com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

            // 检查用户是否已初始化（老用户使用 unlock，新用户使用 initialize）
            boolean isInitialized = backendService.isInitialized();
            android.util.Log.d(TAG, "用户已初始化状态: " + isInitialized);

            boolean unlockSuccess;
            if (isInitialized) {
                // 老用户：使用 unlock()，保持原有盐值
                android.util.Log.d(TAG, "老用户登录，使用 unlock() 保持原有盐值");
                unlockSuccess = backendService.unlock(password);
            } else {
                // 新用户：使用 initialize()，生成新盐值
                android.util.Log.d(TAG, "新用户登录，使用 initialize() 生成新盐值");
                unlockSuccess = backendService.initialize(password);
            }

            if (!unlockSuccess) {
                showError("初始化加密环境失败");
                return;
            }
            android.util.Log.d(TAG, "加密环境已成功解锁，密码已保存到生物识别存储");

            // 处理新设备数据恢复
            if (response.getIsNewDevice() != null && response.getIsNewDevice()) {
                android.util.Log.d(TAG, "新设备登录，开始云端数据恢复");
                handleNewDeviceRecovery(email, password);
                return;
            }

            // 保存会话密码到 AccountManager（通过 BackendServiceImpl）
            // 注意：SafeVault 3.4.0 不再保存密码到生物识别存储
            com.ttt.safevault.ServiceLocator.getInstance().getBackendService()
                    .setSessionMasterPassword(password);
            android.util.Log.d(TAG, "会话密码已保存到 AccountManager（仅内存）");

            // 老设备登录，执行云端数据同步
            android.util.Log.d(TAG, "老设备登录，执行云端数据同步");
            handleCloudDataSync();

            // 登录成功，导航到主页
            hideError();
            navigateToMain();

        } catch (Exception e) {
            showError("登录处理失败: " + e.getMessage());
            android.util.Log.e(TAG, "Failed to handle login success", e);
        }
    }

    /**
     * 处理新设备数据恢复
     */
    private void handleNewDeviceRecovery(String email, String password) {
        android.util.Log.d(TAG, "开始新设备数据恢复");

        // 使用 final 变量以便在 AsyncTask 中访问
        final String masterPassword = password;

        // 使用后台线程执行数据恢复
        new android.os.AsyncTask<Void, Void, com.ttt.safevault.dto.DeviceRecoveryResult>() {
            @Override
            protected com.ttt.safevault.dto.DeviceRecoveryResult doInBackground(Void... voids) {
                try {
                    com.ttt.safevault.model.BackendService backendService =
                            com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

                    // SafeVault 3.4.0：通过反射获取 BackendServiceImpl 的 AccountManager
                    try {
                        java.lang.reflect.Field accountManagerField =
                            backendService.getClass().getDeclaredField("accountManager");
                        accountManagerField.setAccessible(true);
                        com.ttt.safevault.service.manager.AccountManager accountManager =
                            (com.ttt.safevault.service.manager.AccountManager) accountManagerField.get(backendService);

                        return accountManager.recoverDeviceData(email, masterPassword);
                    } catch (Exception reflectEx) {
                        android.util.Log.e(TAG, "反射获取 AccountManager 失败", reflectEx);
                        return com.ttt.safevault.dto.DeviceRecoveryResult.failure("内部错误", null);
                    }
                } catch (Exception e) {
                    android.util.Log.e(TAG, "设备数据恢复失败", e);
                    return com.ttt.safevault.dto.DeviceRecoveryResult.failure(e.getMessage(), null);
                }
            }

            @Override
            protected void onPostExecute(com.ttt.safevault.dto.DeviceRecoveryResult result) {
                if (result.isSuccess()) {
                    android.util.Log.d(TAG, "设备数据恢复成功");
                    // 保存会话密码到内存（通过 BackendService）
                    com.ttt.safevault.ServiceLocator.getInstance().getBackendService()
                            .setSessionMasterPassword(masterPassword);
                    // 恢复成功后，继续执行数据同步
                    handleCloudDataSync();
                    // 导航到主页
                    hideError();
                    navigateToMain();
                } else {
                    android.util.Log.e(TAG, "设备数据恢复失败: " + result.getMessage());
                    // 使用错误处理器显示错误
                    com.ttt.safevault.utils.LoginErrorHandler.showError(
                            LoginActivity.this,
                            new RuntimeException(result.getMessage())
                    );
                }
            }
        }.execute();
    }

    /**
     * 处理云端数据同步
     */
    private void handleCloudDataSync() {
        android.util.Log.d(TAG, "开始云端数据同步");

        try {
            com.ttt.safevault.service.manager.EncryptionSyncManager syncManager =
                    new com.ttt.safevault.service.manager.EncryptionSyncManager(
                            this,
                            com.ttt.safevault.network.RetrofitClient.getInstance(this)
                    );

            com.ttt.safevault.service.manager.EncryptionSyncManager.SyncResult syncResult =
                    syncManager.syncVaultData();

            if (syncResult.isSuccess()) {
                android.util.Log.d(TAG, "云端数据同步成功，版本: " + syncResult.getNewVersion());
            } else if (!syncResult.getMessage().contains("冲突")) {
                // 如果不是冲突错误，记录日志但不阻止登录
                android.util.Log.w(TAG, "云端数据同步失败: " + syncResult.getMessage());
            }

        } catch (Exception e) {
            android.util.Log.e(TAG, "云端数据同步异常", e);
            // 同步失败不影响登录，只记录日志
        }
    }

    private void navigateToMain() {
        if (fromAutofill || fromAutofillSave) {
            // 从自动填充或自动填充保存跳转过来，返回结果
            setResult(RESULT_OK);
            finish();
        } else {
            // 正常登录，跳转到主界面
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void togglePasswordVisibility() {
        if (passwordInput == null || passwordLayout == null) return;

        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            passwordLayout.setEndIconDrawable(R.drawable.ic_visibility_off);
            passwordLayout.setEndIconContentDescription(getString(R.string.hide_password));
        } else {
            passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordLayout.setEndIconDrawable(R.drawable.ic_visibility);
            passwordLayout.setEndIconContentDescription(getString(R.string.show_password));
        }

        passwordInput.setSelection(passwordInput.getText().length());
    }

    /**
     * 执行生物识别认证（使用 BiometricAuthManager）
     * 采用两阶段回调设计：onUserVerified() → onKeyAccessGranted()
     */
    private void performBiometricAuthentication() {
        if (biometricAuthManager == null) {
            showError("生物识别认证未初始化");
            return;
        }

        biometricAuthManager.authenticate(this, AuthScenario.LOGIN, new AuthCallback() {
            @Override
            public void onUserVerified() {
                // 用户通过 UI 认证，但此时密钥可能仍不可用
                // BiometricAuthManager 会自动尝试 Keystore 解锁
                // 等待 onKeyAccessGranted() 或 onFailure() 回调
            }

            @Override
            public void onKeyAccessGranted() {
                // Keystore 授权成功，需要恢复 DataKey 到 CryptoSession
                runOnUiThread(() -> {
                    try {
                        // 1. 使用生物识别恢复 DataKey
                        com.ttt.safevault.security.SecureKeyStorageManager secureStorage =
                                com.ttt.safevault.security.SecureKeyStorageManager.getInstance(
                                        getApplicationContext());
                        javax.crypto.SecretKey dataKey = secureStorage.unlockDataKeyWithBiometric();

                        if (dataKey == null) {
                            showError("生物识别解锁失败：无法恢复加密密钥");
                            android.util.Log.e(TAG, "unlockDataKeyWithBiometric() 返回 null");
                            return;
                        }

                        // 2. 缓存 DataKey 到 CryptoSession（解锁会话）
                        com.ttt.safevault.security.CryptoSession.getInstance()
                                .unlockWithDataKey(dataKey);
                        android.util.Log.i(TAG, "CryptoSession 已通过生物识别解锁");

                        // 3. 导航到主界面
                        hideError();
                        navigateToMain();

                    } catch (Exception e) {
                        showError("生物识别解锁失败: " + e.getMessage());
                        android.util.Log.e(TAG, "生物识别解锁异常", e);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull AuthError error, @NonNull String message, boolean canRetry) {
                runOnUiThread(() -> {
                    // 处理防抖动错误 - 使用轻量级 Toast
                    if (error == AuthError.DEBOUNCED) {
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 处理生物识别变更
                    if (error == AuthError.BIOMETRIC_CHANGED || error == AuthError.KEYSTORE_INVALIDATED) {
                        showBiometricChangedDialog();
                        return;
                    }

                    // 其他错误显示错误提示
                    showError(message);
                });
            }

            @Override
            public void onCancel() {
                // 用户取消认证，不做处理
            }

            @Override
            public void onBiometricChanged() {
                // 生物识别信息已变更
                runOnUiThread(() -> showBiometricChangedDialog());
            }

            @Override
            public void onFallbackToPassword() {
                // 降级到主密码，提示用户
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, "请使用主密码解锁", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 显示生物识别变更对话框
     * 当用户添加新指纹、面部或清除生物识别数据时调用
     */
    private void showBiometricChangedDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("生物识别信息已变更")
                .setMessage("检测到您的生物识别信息已变更（添加了新指纹/面部或清除了数据）。\n\n" +
                           "为了安全起见，请使用主密码解锁后重新启用生物识别功能。")
                .setPositiveButton("我知道了", (dialog, which) -> {
                    // 关闭对话框，用户需要使用主密码解锁
                    // 生物识别功能将在下次成功登录后保持禁用状态
                })
                .setCancelable(false)
                .show();
    }

    private void initBiometricAuth() {
        biometricAuthHelper = new BiometricAuthHelper(this);
        biometricAuthManager = BiometricAuthManager.getInstance(this);
        checkAndRequestBiometricPermission();
    }

    private void checkAndRequestBiometricPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.USE_BIOMETRIC)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.USE_BIOMETRIC},
                        PERMISSION_REQUEST_CODE);
            }
        }

        // 更新生物识别按钮的可见性
        updateBiometricButtonVisibility();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 更新生物识别按钮的可见性
            updateBiometricButtonVisibility();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 检查并更新生物识别按钮的可见性
        updateBiometricButtonVisibility();

        // 自动触发生物识别（如果支持且未触发过）
        if (biometricButton != null && biometricButton.getVisibility() == View.VISIBLE && !biometricAutoTriggered) {
            biometricAutoTriggered = true;
            performBiometricAuthentication();
        }
    }

    /**
     * 更新生物识别按钮的可见性
     * 检查三个条件：
     * 1. 设备支持生物识别
     * 2. 用户已启用生物识别功能
     * 3. CryptoManager 已初始化（表示已设置过主密码）
     * 4. 三层密钥存储已迁移（DeviceKey 和 DataKey 已创建）
     */
    private void updateBiometricButtonVisibility() {
        if (biometricButton == null) {
            return;
        }

        boolean biometricSupported = BiometricAuthHelper.isBiometricSupported(this);
        com.ttt.safevault.security.SecurityConfig securityConfig =
                new com.ttt.safevault.security.SecurityConfig(this);
        com.ttt.safevault.model.BackendService backendService =
                com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
        com.ttt.safevault.security.SecureKeyStorageManager secureStorage =
                com.ttt.safevault.security.SecureKeyStorageManager.getInstance(this);

        boolean userEnabled = securityConfig.isBiometricEnabled();
        boolean hasCredentials = backendService.isInitialized();
        boolean isMigrated = secureStorage.isMigrated();

        boolean shouldShow = biometricSupported && userEnabled && hasCredentials && isMigrated;
        biometricButton.setVisibility(shouldShow ? View.VISIBLE : View.GONE);

        android.util.Log.d(TAG, "updateBiometricButtonVisibility: " + shouldShow +
                " (deviceSupported=" + biometricSupported +
                ", userEnabled=" + userEnabled +
                ", hasCredentials=" + hasCredentials +
                ", isMigrated=" + isMigrated + ")");
    }

    @Override
    @SuppressLint("MissingSuperCall")
    public void onBackPressed() {
        // 如果正在加载，不允许返回
        Boolean isLoading = authViewModel.getLoading().getValue();
        if (isLoading != null && isLoading) {
            return;
        }

        // 最小化应用
        moveTaskToBack(true);
    }
}
