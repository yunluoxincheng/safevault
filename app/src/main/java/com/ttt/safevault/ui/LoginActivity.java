package com.ttt.safevault.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.core.splashscreen.SplashScreen;
import androidx.constraintlayout.widget.ConstraintSet;

import java.util.ArrayList;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ttt.safevault.R;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordStrength;
import com.ttt.safevault.security.BiometricAuthHelper;
import com.ttt.safevault.utils.AnimationUtils;
import com.ttt.safevault.utils.PasswordStrengthCalculator;
import com.ttt.safevault.viewmodel.AuthViewModel;
import com.ttt.safevault.viewmodel.LoginViewModel;

/**
 * 登录/解锁页面
 * 统一认证模式：邮箱+主密码
 */
public class LoginActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private LoginViewModel viewModel;
    private AuthViewModel authViewModel;
    private TextInputEditText emailInput;
    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private TextInputLayout emailLayout;
    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmPasswordLayout;
    private Button loginButton;
    private Button biometricButton;
    private Button resendVerificationButton;
    private TextView errorText;
    private TextView titleText;
    private TextView subtitleText;
    private ProgressBar progressBar;
    private View confirmPasswordSection;
    private View passwordStrengthContainer;
    private View passwordCard;
    private ConstraintLayout constraintLayout;
    private TextView passwordStrengthText;
    private LinearProgressIndicator passwordStrengthBar;

    // 状态标志
    private boolean isInitializing = false;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private boolean fromAutofill = false;  // 是否从自动填充跳转过来
    private boolean fromAutofillSave = false;  // 是否从自动填充保存跳转过来
    private boolean isRegistrationStep = false;  // 是否为注册第一步（邮箱验证）
    private boolean isSetPasswordStep = false;  // 是否为设置主密码步骤
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

            // 检查是否从邮箱验证页面跳转过来（设置主密码步骤）
            String action = getIntent().getStringExtra("action");
            if ("set_password".equals(action)) {
                isSetPasswordStep = true;
                String email = getIntent().getStringExtra("email");
                String username = getIntent().getStringExtra("username");
                // 进入设置主密码模式
                updateUiForSetPasswordStep(email, username);
            }
        }

        // 获取BackendService实例
        BackendService backendService = com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

        // 初始化ViewModel
        ViewModelProvider.Factory factory = new com.ttt.safevault.viewmodel.ViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, factory).get(LoginViewModel.class);
        authViewModel = new ViewModelProvider(this, factory).get(AuthViewModel.class);

        initViews();
        setupObservers();
        setupClickListeners();
        setupTextWatchers();
        initBiometricAuth();

        // 检查用户状态并显示相应界面
        checkUserStatusAndShowUi();
    }

    private void initViews() {
        emailInput = findViewById(R.id.email_input);
        usernameInput = findViewById(R.id.username_input);
        passwordInput = findViewById(R.id.password_input);
        confirmPasswordInput = findViewById(R.id.confirm_password_input);
        emailLayout = findViewById(R.id.email_layout);
        usernameLayout = findViewById(R.id.username_layout);
        passwordLayout = findViewById(R.id.password_layout);
        confirmPasswordLayout = findViewById(R.id.confirm_password_layout);
        loginButton = findViewById(R.id.login_button);
        biometricButton = findViewById(R.id.biometric_button);
        resendVerificationButton = findViewById(R.id.resend_verification_button);
        errorText = findViewById(R.id.error_text);
        titleText = findViewById(R.id.title_text);
        subtitleText = findViewById(R.id.subtitle_text);
        progressBar = findViewById(R.id.progress_bar);
        confirmPasswordSection = findViewById(R.id.confirm_password_section);
        passwordStrengthContainer = findViewById(R.id.password_strength_container);
        passwordCard = findViewById(R.id.password_card);
        constraintLayout = findViewById(R.id.root_layout);
        passwordStrengthText = findViewById(R.id.password_strength_text);
        passwordStrengthBar = findViewById(R.id.password_strength_bar);

        // 设置密码输入框默认图标为闭眼
        if (passwordLayout != null) {
            passwordLayout.setEndIconDrawable(R.drawable.ic_visibility);
        }
        if (confirmPasswordLayout != null) {
            confirmPasswordLayout.setEndIconDrawable(R.drawable.ic_visibility);
        }
    }

    private void setupObservers() {
        // 观察本地认证状态
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

        // 观察初始化状态 - 已禁用，改用邮箱认证模式
        // viewModel.isInitialized.observe(this, isInitialized -> {
        //     if (isInitialized != null) {
        //         updateUiForInitializationState(!isInitialized);
        //     }
        // });

        // 观察生物识别支持
        viewModel.canUseBiometric.observe(this, canUse -> {
            if (canUse != null && biometricButton != null) {
                // 在本地登录模式下，根据生物识别支持状态更新按钮可见性
                if (!isRegistrationStep && !isSetPasswordStep) {
                    // 本地登录模式：显示生物识别按钮（如果支持）
                    boolean emailVisible = emailLayout != null && emailLayout.getVisibility() == View.VISIBLE;
                    if (!emailVisible) {
                        biometricButton.setVisibility(canUse ? View.VISIBLE : View.GONE);
                    }
                }
            }
        });

        // 观察邮箱注册响应
        authViewModel.getEmailRegistrationResponse().observe(this, response -> {
            if (response != null) {
                Toast.makeText(this, response.getMessage(), Toast.LENGTH_SHORT).show();
                if (response.isEmailSent()) {
                    // 邮件已发送，提示用户检查邮箱
                    showMessage("验证邮件已发送到 " + response.getEmail() + "，请查收");
                }
            }
        });

        // 观察邮箱验证响应
        authViewModel.getEmailVerificationResponse().observe(this, response -> {
            if (response != null && response.isSuccess()) {
                // 验证成功，进入设置主密码步骤
                isSetPasswordStep = true;
                updateUiForSetPasswordStep(response.getEmail(), response.getUsername());
            }
        });

        // 观察认证错误
        authViewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showError(error);
            }
        });

        // 观察加载状态
        authViewModel.getLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                updateLoadingState(isLoading);
            }
        });

        // 观察邮箱登录响应
        authViewModel.getEmailLoginResponse().observe(this, response -> {
            if (response != null) {
                // 邮箱登录成功
                if (response.getIsNewDevice() != null && response.getIsNewDevice()) {
                    // 新设备登录，显示提示
                    showMessage("新设备登录成功");
                }
                navigateToMain();
            }
        });
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> {
            if (isRegistrationStep) {
                // 注册第一步：邮箱+用户名验证
                handleEmailRegistration();
            } else if (isSetPasswordStep) {
                // 设置主密码步骤
                handleSetMasterPassword();
            } else {
                // 检查是本地登录模式还是邮箱登录模式
                boolean emailVisible = emailLayout != null && emailLayout.getVisibility() == View.VISIBLE;
                if (emailVisible) {
                    // 邮箱登录模式
                    handleEmailLogin();
                } else {
                    // 本地登录模式：只使用密码
                    handleLocalLogin();
                }
            }
        });

        biometricButton.setOnClickListener(v -> {
            performBiometricAuthentication();
        });

        // 重发验证邮件按钮
        resendVerificationButton.setOnClickListener(v -> {
            String email = emailInput != null ? emailInput.getText().toString().trim() : "";
            if (!TextUtils.isEmpty(email) && isValidEmail(email)) {
                authViewModel.resendVerificationEmail(email);
            }
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

        // 确认密码显示/隐藏按钮
        if (confirmPasswordLayout != null) {
            confirmPasswordLayout.setEndIconOnClickListener(v -> {
                toggleConfirmPasswordVisibility();
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
        if (usernameInput != null) {
            usernameInput.addTextChangedListener(textWatcher);
        }
        passwordInput.addTextChangedListener(textWatcher);
        if (confirmPasswordInput != null) {
            confirmPasswordInput.addTextChangedListener(textWatcher);
        }

        // 添加密码强度监听器（仅在设置主密码步骤时显示）
        if (passwordInput != null) {
            passwordInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // 仅在设置主密码步骤时更新密码强度
                    if (isSetPasswordStep) {
                        updatePasswordStrength(s.toString());
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    /**
     * 更新密码强度指示器
     */
    private void updatePasswordStrength(String password) {
        if (passwordStrengthContainer == null || passwordStrengthText == null || passwordStrengthBar == null) {
            return;
        }

        if (password.isEmpty()) {
            passwordStrengthContainer.setVisibility(View.GONE);
            return;
        }

        passwordStrengthContainer.setVisibility(View.VISIBLE);

        // 计算密码强度
        PasswordStrength strength = PasswordStrengthCalculator.calculate(password);
        int percentage = PasswordStrengthCalculator.getStrengthPercentage(password);

        // 更新文本
        passwordStrengthText.setText(strength.level().getLabel());

        // 更新进度条
        passwordStrengthBar.setProgressCompat(percentage, true);

        // 更新颜色
        int colorRes = getStrengthColor(strength.level());
        passwordStrengthBar.setIndicatorColor(getColor(colorRes));
        passwordStrengthText.setTextColor(getColor(colorRes));
    }

    /**
     * 更新约束：注册步骤时 error_text 约束到 input_barrier
     */
    private void updateConstraintsForRegistration() {
        if (constraintLayout == null || errorText == null) return;

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(constraintLayout);
        // 将 error_text 的 top 约束到 input_barrier
        constraintSet.connect(R.id.error_text, ConstraintSet.TOP, R.id.input_barrier, ConstraintSet.BOTTOM, 0);
        constraintSet.applyTo(constraintLayout);
    }

    /**
     * 更新约束：设置主密码步骤时 error_text 约束到 password_card
     */
    private void updateConstraintsForSetPassword() {
        if (constraintLayout == null || errorText == null) return;

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(constraintLayout);
        // 将 error_text 的 top 约束到 password_card
        constraintSet.connect(R.id.error_text, ConstraintSet.TOP, R.id.password_card, ConstraintSet.BOTTOM, 0);
        constraintSet.applyTo(constraintLayout);
    }

    /**
     * 获取强度等级对应的颜色资源 ID
     */
    private int getStrengthColor(PasswordStrength.Level level) {
        return switch (level) {
            case WEAK -> R.color.strength_weak;
            case MEDIUM -> R.color.strength_medium;
            case STRONG -> R.color.strength_strong;
        };
    }

    private void updateUiForInitializationState(boolean initializing) {
        isInitializing = initializing;

        if (titleText != null) {
            titleText.setText(initializing ? R.string.set_master_password : R.string.unlock_safevault);
        }

        if (loginButton != null) {
            loginButton.setText(initializing ? R.string.initialize : R.string.unlock);
        }

        // 显示/隐藏确认密码输入框
        if (confirmPasswordSection != null && confirmPasswordLayout != null) {
            confirmPasswordSection.setVisibility(initializing ? View.VISIBLE : View.GONE);
            confirmPasswordLayout.setVisibility(initializing ? View.VISIBLE : View.GONE);
        }

        // 初始化时不显示生物识别按钮
        if (biometricButton != null) {
            Boolean canUseBiometric = viewModel.canUseBiometric.getValue();
            biometricButton.setVisibility(initializing ? View.GONE :
                (canUseBiometric != null && canUseBiometric ? View.VISIBLE : View.GONE));
        }

        updateLoginButtonState();
    }

    private void updateLoginButtonState() {
        boolean enabled = false;

        if (isRegistrationStep) {
            // 注册第一步：需要邮箱和用户名
            String email = emailInput != null ? emailInput.getText().toString().trim() : "";
            String username = usernameInput != null ? usernameInput.getText().toString().trim() : "";
            enabled = isValidEmail(email) && !TextUtils.isEmpty(username);

            // 更新重发按钮状态：邮箱有效时启用
            if (resendVerificationButton != null) {
                boolean emailValid = isValidEmail(email);
                resendVerificationButton.setEnabled(emailValid);
                resendVerificationButton.setAlpha(emailValid ? 1.0f : 0.5f);
            }
        } else if (isSetPasswordStep) {
            // 设置主密码：需要密码和确认密码
            String password = passwordInput.getText().toString().trim();
            String confirmPassword = confirmPasswordInput.getText().toString().trim();
            enabled = !TextUtils.isEmpty(password) && !TextUtils.isEmpty(confirmPassword);
        } else if (!isRegistrationStep && !isSetPasswordStep) {
            // 本地登录模式或邮箱登录模式
            // 如果邮箱输入框可见，需要邮箱和密码；否则只需要密码
            String email = emailInput != null ? emailInput.getText().toString().trim() : "";
            String password = passwordInput.getText().toString().trim();
            boolean emailVisible = emailLayout != null && emailLayout.getVisibility() == View.VISIBLE;

            if (emailVisible) {
                enabled = isValidEmail(email) && !TextUtils.isEmpty(password);
            } else {
                enabled = !TextUtils.isEmpty(password);
            }
        }

        if (loginButton != null) {
            Boolean localLoading = viewModel.isLoading.getValue();
            Boolean cloudLoading = authViewModel.getLoading().getValue();
            boolean isLoading = (localLoading != null && localLoading) || (cloudLoading != null && cloudLoading);
            loginButton.setEnabled(enabled && !isLoading);
        }
    }

    private void updateLoadingState(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }

        if (passwordInput != null) {
            passwordInput.setEnabled(!isLoading);
        }

        if (confirmPasswordInput != null) {
            confirmPasswordInput.setEnabled(!isLoading);
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

        // 震动反馈
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

        if (confirmPasswordLayout != null) {
            confirmPasswordLayout.setError(null);
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

        // 切换密码输入类型
        if (isPasswordVisible) {
            // 显示密码
            passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            passwordLayout.setEndIconDrawable(R.drawable.ic_visibility_off);
            passwordLayout.setEndIconContentDescription(getString(R.string.hide_password));
        } else {
            // 隐藏密码
            passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordLayout.setEndIconDrawable(R.drawable.ic_visibility);
            passwordLayout.setEndIconContentDescription(getString(R.string.show_password));
        }

        // 将光标移到末尾
        passwordInput.setSelection(passwordInput.getText().length());

        // 添加动画反馈（getEndIconView 不是公开 API，这里简化处理）
    }

    private void toggleConfirmPasswordVisibility() {
        if (confirmPasswordInput == null || confirmPasswordLayout == null) return;

        isConfirmPasswordVisible = !isConfirmPasswordVisible;

        // 切换密码输入类型
        if (isConfirmPasswordVisible) {
            // 显示密码
            confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            confirmPasswordLayout.setEndIconDrawable(R.drawable.ic_visibility);
            confirmPasswordLayout.setEndIconContentDescription(getString(R.string.hide_password));
        } else {
            // 隐藏密码
            confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            confirmPasswordLayout.setEndIconDrawable(R.drawable.ic_visibility_off);
            confirmPasswordLayout.setEndIconContentDescription(getString(R.string.show_password));
        }

        // 将光标移到末尾
        confirmPasswordInput.setSelection(confirmPasswordInput.getText().length());

        // 添加动画反馈（getEndIconView 不是公开 API，这里简化处理）
    }

    private void performBiometricAuthentication() {
        if (biometricAuthHelper == null) {
            showError("生物识别认证未初始化");
            return;
        }

        biometricAuthHelper.authenticate(new BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                // 生物识别认证成功，现在调用后端服务解锁
                runOnUiThread(() -> {
                    // 直接调用后端服务进行解锁
                    BackendService backendService = com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
                    try {
                        boolean unlocked = backendService.unlockWithBiometric();
                        if (unlocked) {
                            // 生物识别解锁成功，导航到主界面
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
                // 用户取消认证，不做任何操作或显示提示
                // 可选：显示一条消息告知用户认证已取消
            }
        });
    }

    private void initBiometricAuth() {
        biometricAuthHelper = new BiometricAuthHelper(this);
        
        // 检查并请求生物识别权限
        checkAndRequestBiometricPermission();
    }
    
    private void checkAndRequestBiometricPermission() {
        // 检查设备是否支持生物识别，并更新按钮可见性
        boolean biometricSupported = BiometricAuthHelper.isBiometricSupported(this);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要 BIOMETRIC 权限
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.USE_BIOMETRIC)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 权限未授予，请求权限
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
            // 权限已处理，生物识别权限检查完成
            if (biometricButton != null) {
                boolean biometricSupported = BiometricAuthHelper.isBiometricSupported(this);
                biometricButton.setVisibility(biometricSupported ? View.VISIBLE : View.GONE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 在邮箱认证模式下，生物识别由具体的登录步骤控制
        // 不再自动触发生物识别验证

        // 检查邮箱验证状态，如果已验证则跳转到设置主密码步骤
        checkEmailVerificationAndNavigate();
    }

    /**
     * 检查邮箱验证状态并跳转
     */
    private void checkEmailVerificationAndNavigate() {
        // 如果已经在设置主密码步骤，不需要检查
        if (isSetPasswordStep) {
            return;
        }

        com.ttt.safevault.network.TokenManager tokenManager =
            com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext()).getTokenManager();

        // 检查邮箱是否已验证
        if (tokenManager.isEmailVerified()) {
            String email = tokenManager.getVerifiedEmail();
            String username = tokenManager.getVerifiedUsername();

            if (email != null && username != null) {
                // 跳转到设置主密码步骤
                isSetPasswordStep = true;
                updateUiForSetPasswordStep(email, username);
            }
        }
    }

    @Override
    @SuppressLint("MissingSuperCall")
    public void onBackPressed() {
        // 如果正在加载，不允许返回
        Boolean localLoading = viewModel.isLoading.getValue();
        Boolean cloudLoading = authViewModel.getLoading().getValue();
        if ((localLoading != null && localLoading) || (cloudLoading != null && cloudLoading)) {
            return;
        }

        // 在邮箱认证模式下的返回逻辑
        if (isRegistrationStep) {
            // 注册步骤：允许退出应用
            finish();
        } else if (isSetPasswordStep) {
            // 设置主密码步骤：不允许返回
            return;
        } else {
            // 邮箱登录步骤：最小化应用
            moveTaskToBack(true);
        }
    }

    // ========== 统一邮箱认证相关方法 ==========

    /**
     * 验证邮箱格式
     */
    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    /**
     * 处理邮箱注册第一步
     */
    private void handleEmailRegistration() {
        String email = emailInput != null ? emailInput.getText().toString().trim() : "";
        String username = usernameInput != null ? usernameInput.getText().toString().trim() : "";

        // 验证邮箱格式
        if (!isValidEmail(email)) {
            showError("请输入有效的邮箱地址");
            return;
        }

        // 验证用户名
        if (TextUtils.isEmpty(username)) {
            showError("用户名不能为空");
            return;
        }

        // 调用注册 API
        authViewModel.registerWithEmail(email, username);
    }

    /**
     * 处理本地登录（只使用密码，不需要邮箱）
     */
    private void handleLocalLogin() {
        String password = passwordInput.getText().toString().trim();

        // 验证密码
        if (TextUtils.isEmpty(password)) {
            showError("请输入主密码");
            return;
        }

        // 调用本地登录
        viewModel.loginWithPassword(password);
    }

    /**
     * 处理设置主密码步骤
     */
    private void handleSetMasterPassword() {
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        // 验证密码
        if (TextUtils.isEmpty(password)) {
            showError("主密码不能为空");
            return;
        }

        // 验证密码长度
        if (password.length() < 8) {
            showError("主密码至少需要8个字符");
            return;
        }

        // 验证确认密码
        if (!password.equals(confirmPassword)) {
            showError("两次输入的密码不一致");
            return;
        }

        // 获取保存的验证状态
        com.ttt.safevault.network.TokenManager tokenManager =
            com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext()).getTokenManager();

        // 调试日志
        android.util.Log.d("LoginActivity", "TokenManager instance: " + tokenManager);
        android.util.Log.d("LoginActivity", "isEmailVerified: " + tokenManager.isEmailVerified());
        String email = tokenManager.getVerifiedEmail();
        String username = tokenManager.getVerifiedUsername();
        android.util.Log.d("LoginActivity", "verifiedEmail: " + email);
        android.util.Log.d("LoginActivity", "verifiedUsername: " + username);

        if (email == null || email.isEmpty() || username == null || username.isEmpty()) {
            android.util.Log.e("LoginActivity", "验证状态为空！email=" + email + ", username=" + username);
            showError("验证状态已过期，请重新验证邮箱");
            // 跳转到注册界面
            showRegistrationStep();
            return;
        }

        // 显示进度提示
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (loginButton != null) {
            loginButton.setEnabled(false);
        }

        // 在后台线程执行注册完成
        new android.os.Handler().post(() -> {
            try {
                // 调用后端服务完成注册
                com.ttt.safevault.model.BackendService backendService =
                        com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

                com.ttt.safevault.dto.response.CompleteRegistrationResponse response =
                        backendService.completeRegistration(email, username, password);

                // 注册成功
                runOnUiThread(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    if (loginButton != null) {
                        loginButton.setEnabled(true);
                    }

                    // 清除验证状态
                    tokenManager.clearEmailVerificationStatus();

                    showMessage("注册成功！正在进入应用...");

                    // 延迟跳转到主界面
                    new android.os.Handler().postDelayed(() -> {
                        navigateToMain();
                    }, 1000);
                });

            } catch (Exception e) {
                // 注册失败
                runOnUiThread(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    if (loginButton != null) {
                        loginButton.setEnabled(true);
                    }
                    showError("注册失败: " + e.getMessage());
                });
            }
        });
    }

    /**
     * 更新 UI 以显示设置主密码步骤
     */
    private void updateUiForSetPasswordStep(String email, String username) {
        isSetPasswordStep = true;
        isRegistrationStep = false;

        // 更新约束：error_text 约束到 password_card
        updateConstraintsForSetPassword();

        // 隐藏邮箱和用户名输入框
        if (emailLayout != null) {
            emailLayout.setVisibility(View.GONE);
        }
        if (usernameLayout != null) {
            usernameLayout.setVisibility(View.GONE);
        }

        // 显示密码卡片
        if (passwordCard != null) {
            passwordCard.setVisibility(View.VISIBLE);
        }

        // 显示密码输入框和确认密码输入框
        if (passwordLayout != null) {
            passwordLayout.setVisibility(View.VISIBLE);
        }
        if (confirmPasswordSection != null) {
            confirmPasswordSection.setVisibility(View.VISIBLE);
        }

        // 显示密码强度指示器
        if (passwordStrengthContainer != null) {
            passwordStrengthContainer.setVisibility(View.VISIBLE);
        }

        // 更新标题和按钮文本
        if (titleText != null) {
            titleText.setText(R.string.set_master_password);
        }
        if (subtitleText != null) {
            subtitleText.setText("请设置您的主密码，用于加密和解密数据");
        }
        if (loginButton != null) {
            loginButton.setText(R.string.initialize);
        }

        // 隐藏生物识别按钮和重发验证邮件按钮
        if (biometricButton != null) {
            biometricButton.setVisibility(View.GONE);
        }
        if (resendVerificationButton != null) {
            resendVerificationButton.setVisibility(View.GONE);
        }

        updateLoginButtonState();
    }

    /**
     * 显示消息提示（非错误）
     */
    private void showMessage(String message) {
        if (errorText != null) {
            errorText.setText(message);
            errorText.setVisibility(View.VISIBLE);
            errorText.setTextColor(getColor(R.color.success_green));
        }

        // 3秒后自动隐藏
        errorText.postDelayed(() -> {
            hideError();
        }, 3000);
    }

    /**
     * 处理邮箱登录
     */
    private void handleEmailLogin() {
        String email = emailInput != null ? emailInput.getText().toString().trim() : "";
        String password = passwordInput.getText().toString().trim();

        // 验证邮箱格式
        if (!isValidEmail(email)) {
            showError("请输入有效的邮箱地址");
            return;
        }

        // 验证密码
        if (TextUtils.isEmpty(password)) {
            showError("请输入主密码");
            return;
        }

        // 调用邮箱登录 API
        authViewModel.loginWithEmail(email, password);
    }

    /**
     * 切换到邮箱登录步骤（显示邮箱和密码输入）
     * 此方法可由外部调用（例如检测到已注册用户时）
     */
    public void showEmailLoginStep() {
        isRegistrationStep = false;
        isSetPasswordStep = false;

        // 显示邮箱和密码输入框
        if (emailLayout != null) {
            emailLayout.setVisibility(View.VISIBLE);
        }
        if (passwordLayout != null) {
            passwordLayout.setVisibility(View.VISIBLE);
        }

        // 隐藏用户名和确认密码输入框
        if (usernameLayout != null) {
            usernameLayout.setVisibility(View.GONE);
        }
        if (confirmPasswordSection != null) {
            confirmPasswordSection.setVisibility(View.GONE);
        }

        // 更新标题和按钮文本
        if (titleText != null) {
            titleText.setText("登录 SafeVault");
        }
        if (subtitleText != null) {
            subtitleText.setText("输入邮箱和主密码登录");
        }
        if (loginButton != null) {
            loginButton.setText("登录");
        }

        // 显示生物识别按钮（如果支持）
        Boolean canUseBiometric = viewModel.canUseBiometric.getValue();
        if (biometricButton != null) {
            biometricButton.setVisibility(canUseBiometric != null && canUseBiometric ? View.VISIBLE : View.GONE);
        }
        // 隐藏重发验证邮件按钮
        if (resendVerificationButton != null) {
            resendVerificationButton.setVisibility(View.GONE);
        }

        updateLoginButtonState();
    }

    /**
     * 切换到注册步骤（显示邮箱和用户名输入）
     * 此方法可由外部调用（例如检测到新用户时）
     */
    public void showRegistrationStep() {
        isRegistrationStep = true;
        isSetPasswordStep = false;

        // 更新约束：error_text 约束到 input_barrier（因为 password_card 会隐藏）
        updateConstraintsForRegistration();

        // 显示邮箱和用户名输入框
        if (emailLayout != null) {
            emailLayout.setVisibility(View.VISIBLE);
        }
        if (usernameLayout != null) {
            usernameLayout.setVisibility(View.VISIBLE);
        }

        // 隐藏密码卡片（包括密码输入框和密码强度指示器）
        if (passwordCard != null) {
            passwordCard.setVisibility(View.GONE);
        }

        // 更新标题和按钮文本
        if (titleText != null) {
            titleText.setText("创建账号");
        }
        if (subtitleText != null) {
            subtitleText.setText("输入邮箱和用户名开始注册");
        }
        if (loginButton != null) {
            loginButton.setText("继续");
        }

        // 隐藏生物识别按钮
        if (biometricButton != null) {
            biometricButton.setVisibility(View.GONE);
        }

        // 显示重发验证邮件按钮（但初始禁用，需要在输入有效邮箱后才启用）
        if (resendVerificationButton != null) {
            resendVerificationButton.setVisibility(View.VISIBLE);
            resendVerificationButton.setEnabled(false);
            resendVerificationButton.setAlpha(0.5f);
        }

        updateLoginButtonState();
    }

    /**
     * 切换到本地登录步骤（只显示密码输入框，不显示邮箱）
     * 用于已注册用户快速登录
     */
    private void showLocalLoginStep() {
        isRegistrationStep = false;
        isSetPasswordStep = false;

        // 更新约束：error_text 约束到 password_card
        updateConstraintsForSetPassword();

        // 隐藏邮箱和用户名输入框
        if (emailLayout != null) {
            emailLayout.setVisibility(View.GONE);
        }
        if (usernameLayout != null) {
            usernameLayout.setVisibility(View.GONE);
        }

        // 显示密码卡片和密码输入框
        if (passwordCard != null) {
            passwordCard.setVisibility(View.VISIBLE);
        }
        if (passwordLayout != null) {
            passwordLayout.setVisibility(View.VISIBLE);
        }

        // 隐藏确认密码输入框和密码强度指示器
        if (confirmPasswordSection != null) {
            confirmPasswordSection.setVisibility(View.GONE);
        }
        if (passwordStrengthContainer != null) {
            passwordStrengthContainer.setVisibility(View.GONE);
        }

        // 更新标题和按钮文本
        if (titleText != null) {
            titleText.setText(R.string.unlock_safevault);
        }
        if (subtitleText != null) {
            subtitleText.setText("请输入主密码解锁应用");
        }
        if (loginButton != null) {
            loginButton.setText(R.string.unlock);
        }

        // 显示生物识别按钮（如果支持）
        Boolean canUseBiometric = viewModel.canUseBiometric.getValue();
        if (biometricButton != null) {
            biometricButton.setVisibility(canUseBiometric != null && canUseBiometric ? View.VISIBLE : View.GONE);
        }

        // 隐藏重发验证邮件按钮
        if (resendVerificationButton != null) {
            resendVerificationButton.setVisibility(View.GONE);
        }

        updateLoginButtonState();
    }

    /**
     * 检查用户状态并显示相应界面
     * - 如果从邮箱验证跳转来（action=set_password） → 显示设置主密码界面
     * - 如果已有登录凭证 → 显示邮箱登录界面
     * - 否则 → 显示注册界面
     */
    private void checkUserStatusAndShowUi() {
        // 如果已经设置了特定的步骤（从 Intent 跳转），则不处理
        if (isSetPasswordStep) {
            return;
        }

        // 检查是否从邮箱验证页面跳转来（带有 action=set_password 参数）
        String action = getIntent() != null ? getIntent().getStringExtra("action") : null;
        if ("set_password".equals(action)) {
            String email = getIntent().getStringExtra("email");
            String username = getIntent().getStringExtra("username");
            if (email != null && username != null) {
                isSetPasswordStep = true;
                updateUiForSetPasswordStep(email, username);
                return;
            }
        }

        // 获取本地初始化状态
        boolean isLocallyInitialized = viewModel.isInitialized.getValue() != null && viewModel.isInitialized.getValue();
        boolean hasCloudToken = authViewModel.isLoggedIn();

        // 检查是否可以使用生物识别
        Boolean canUseBiometric = viewModel.canUseBiometric.getValue();

        if (isLocallyInitialized || hasCloudToken) {
            // 已注册用户：优先显示本地登录模式（无邮箱输入框）
            showLocalLoginStep();
        } else {
            // 新用户：显示注册界面
            showRegistrationStep();
        }
    }
}