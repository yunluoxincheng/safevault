package com.ttt.safevault.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.core.splashscreen.SplashScreen;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ttt.safevault.R;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.BiometricAuthHelper;
import com.ttt.safevault.viewmodel.LoginViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

/**
 * 登录/解锁页面
 * 只处理登录功能，注册功能已移至 RegisterActivity
 */
public class LoginActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "LoginActivity";

    // UI组件
    private TextInputEditText passwordInput;
    private TextInputLayout passwordLayout;
    private Button loginButton;
    private Button biometricButton;
    private TextView errorText;
    private TextView titleText;
    private TextView subtitleText;
    private TextView registerText;
    private ProgressBar progressBar;

    // ViewModel
    private LoginViewModel viewModel;

    // 状态标志
    private boolean isPasswordVisible = false;
    private boolean fromAutofill = false;  // 是否从自动填充跳转过来
    private boolean fromAutofillSave = false;  // 是否从自动填充保存跳转过来
    private boolean biometricAutoTriggered = false;  // 是否已自动触发生物识别

    // 生物识别认证助手
    private BiometricAuthHelper biometricAuthHelper;

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
        viewModel = new ViewModelProvider(this, factory).get(LoginViewModel.class);

        initViews();
        setupObservers();
        setupClickListeners();
        setupTextWatchers();
        initBiometricAuth();
    }

    private void initViews() {
        passwordInput = findViewById(R.id.password_input);
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
    }

    private void setupObservers() {
        // 观察认证状态
        viewModel.isAuthenticated.observe(this, isAuthenticated -> {
            if (isAuthenticated) {
                navigateToMain();
            }
        });

        // 观察错误信息
        viewModel.errorMessage.observe(this, error -> {
            if (error != null) {
                showError(error);
            } else {
                hideError();
            }
        });

        // 观察加载状态
        viewModel.isLoading.observe(this, isLoading -> {
            updateLoadingState(isLoading);
        });

        // 观察生物识别支持
        viewModel.canUseBiometric.observe(this, canUse -> {
            if (canUse != null && biometricButton != null) {
                biometricButton.setVisibility(canUse ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void setupClickListeners() {
        // 登录按钮
        loginButton.setOnClickListener(v -> {
            handleLogin();
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
        passwordInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                viewModel.clearError();
                hideError();
            }
        });

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

        passwordInput.addTextChangedListener(textWatcher);
    }

    private void updateLoginButtonState() {
        String password = passwordInput != null ? passwordInput.getText().toString().trim() : "";
        boolean enabled = !TextUtils.isEmpty(password);

        if (loginButton != null) {
            Boolean isLoading = viewModel.isLoading.getValue();
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
     * 处理登录
     */
    private void handleLogin() {
        String password = passwordInput.getText().toString().trim();

        // 验证密码
        if (TextUtils.isEmpty(password)) {
            showError("请输入主密码");
            return;
        }

        // 调用登录
        viewModel.loginWithPassword(password);
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

    private void performBiometricAuthentication() {
        if (biometricAuthHelper == null) {
            showError("生物识别认证未初始化");
            return;
        }

        biometricAuthHelper.authenticate(new BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    BackendService backendService = com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
                    try {
                        boolean unlocked = backendService.unlockWithBiometric();
                        if (unlocked) {
                            navigateToMain();
                        } else {
                            showError("生物识别解锁失败，请使用主密码解锁");
                        }
                    } catch (Exception e) {
                        showError("解锁时发生错误: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                showError(error);
            }

            @Override
            public void onCancel() {
                // 用户取消认证
            }
        });
    }

    private void initBiometricAuth() {
        biometricAuthHelper = new BiometricAuthHelper(this);
        checkAndRequestBiometricPermission();
    }

    private void checkAndRequestBiometricPermission() {
        boolean biometricSupported = BiometricAuthHelper.isBiometricSupported(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.USE_BIOMETRIC)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.USE_BIOMETRIC},
                        PERMISSION_REQUEST_CODE);
            }
        }

        if (biometricButton != null) {
            biometricButton.setVisibility(biometricSupported ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (biometricButton != null) {
                boolean biometricSupported = BiometricAuthHelper.isBiometricSupported(this);
                biometricButton.setVisibility(biometricSupported ? View.VISIBLE : View.GONE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 自动触发生物识别（如果支持且未触发过）
        Boolean canUseBiometric = viewModel.canUseBiometric.getValue();
        if (canUseBiometric != null && canUseBiometric && !biometricAutoTriggered) {
            biometricAutoTriggered = true;
            performBiometricAuthentication();
        }
    }

    @Override
    @SuppressLint("MissingSuperCall")
    public void onBackPressed() {
        // 如果正在加载，不允许返回
        Boolean isLoading = viewModel.isLoading.getValue();
        if (isLoading != null && isLoading) {
            return;
        }

        // 最小化应用
        moveTaskToBack(true);
    }
}
