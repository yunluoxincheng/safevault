package com.ttt.safevault.ui;

import android.content.Intent;
import android.os.Bundle;
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
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ttt.safevault.R;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordStrength;
import com.ttt.safevault.viewmodel.AuthViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

/**
 * 注册页面
 * 处理用户注册流程：
 * - 第一步：邮箱验证（邮箱+用户名）
 * - 第二步：设置主密码
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    // UI组件
    private TextInputEditText emailInput;
    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private TextInputLayout emailLayout;
    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmPasswordLayout;
    private Button continueButton;
    private Button resendVerificationButton;
    private TextView errorText;
    private TextView titleText;
    private TextView subtitleText;
    private TextView backToLoginText;
    private ProgressBar progressBar;
    private View confirmPasswordSection;
    private View passwordStrengthContainer;
    private View passwordCard;
    private ConstraintLayout constraintLayout;
    private TextView passwordStrengthText;
    private LinearProgressIndicator passwordStrengthBar;

    // ViewModel
    private AuthViewModel authViewModel;

    // 状态标志
    private boolean isStepOne = true; // 是否为第一步（邮箱验证）
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    // 第一步保存的数据
    private String registeredEmail;
    private String registeredUsername;

    // 轮询相关
    private android.os.Handler pollingHandler;
    private Runnable pollingRunnable;
    private static final long POLLING_INTERVAL_MS = 3000; // 3秒轮询一次
    private static final long MAX_POLLING_TIME_MS = 10 * 60 * 1000; // 最多轮询10分钟
    private long pollingStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // 防止截图
        com.ttt.safevault.security.SecurityConfig securityConfig =
            new com.ttt.safevault.security.SecurityConfig(this);
        if (securityConfig.isScreenshotProtectionEnabled()) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }

        // 清除之前的验证状态（用户点击"注册"意味着开始新的注册流程）
        com.ttt.safevault.network.TokenManager tokenManager =
            com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext()).getTokenManager();
        tokenManager.clearEmailVerificationStatus();

        // 初始化ViewModel
        ViewModelProvider.Factory factory = new ViewModelFactory(getApplication());
        authViewModel = new ViewModelProvider(this, factory).get(AuthViewModel.class);

        initViews();
        setupObservers();
        setupClickListeners();
        setupTextWatchers();
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
        continueButton = findViewById(R.id.continue_button);
        resendVerificationButton = findViewById(R.id.resend_verification_button);
        errorText = findViewById(R.id.error_text);
        titleText = findViewById(R.id.title_text);
        subtitleText = findViewById(R.id.subtitle_text);
        backToLoginText = findViewById(R.id.back_to_login_text);
        progressBar = findViewById(R.id.progress_bar);
        confirmPasswordSection = findViewById(R.id.confirm_password_section);
        passwordStrengthContainer = findViewById(R.id.password_strength_container);
        passwordCard = findViewById(R.id.password_card);
        constraintLayout = findViewById(R.id.root_layout);
        passwordStrengthText = findViewById(R.id.password_strength_text);
        passwordStrengthBar = findViewById(R.id.password_strength_bar);

        // 设置密码输入框默认图标
        if (passwordLayout != null) {
            passwordLayout.setEndIconDrawable(R.drawable.ic_visibility);
        }
        if (confirmPasswordLayout != null) {
            confirmPasswordLayout.setEndIconDrawable(R.drawable.ic_visibility);
        }
    }

    private void setupObservers() {
        // 观察邮箱注册响应
        authViewModel.getEmailRegistrationResponse().observe(this, response -> {
            if (response != null) {
                Toast.makeText(this, response.getMessage(), Toast.LENGTH_SHORT).show();
                if (response.isEmailSent()) {
                    // 邮件已发送，显示重发按钮
                    if (resendVerificationButton != null) {
                        resendVerificationButton.setVisibility(View.VISIBLE);
                    }
                    showMessage("验证邮件已发送到 " + response.getEmail() + "，请查收");

                    // 开始轮询检查验证状态
                    startVerificationPolling(response.getEmail());
                }
            }
        });

        // 观察验证状态响应
        authViewModel.getVerificationStatusResponse().observe(this, response -> {
            if (response != null && isStepOne) {
                if (response.isVerified()) {
                    // 验证成功，停止轮询并进入第二步
                    stopVerificationPolling();
                    registeredEmail = response.getEmail();
                    registeredUsername = response.getUsername();
                    showStepTwo();
                } else if (response.isNotFound()) {
                    // 验证记录不存在（可能过期），停止轮询
                    stopVerificationPolling();
                    showError("验证链接已过期，请重新发送验证邮件");
                }
                // PENDING 状态继续轮询
            }
        });

        // 观察邮箱验证响应
        authViewModel.getEmailVerificationResponse().observe(this, response -> {
            if (response != null && response.isSuccess()) {
                // 验证成功，进入第二步：设置主密码
                registeredEmail = response.getEmail();
                registeredUsername = response.getUsername();
                showStepTwo();
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
    }

    private void setupClickListeners() {
        // 继续/完成按钮
        continueButton.setOnClickListener(v -> {
            if (isStepOne) {
                // 第一步：邮箱+用户名验证
                handleEmailRegistration();
            } else {
                // 第二步：设置主密码
                handleSetMasterPassword();
            }
        });

        // 重发验证邮件按钮
        resendVerificationButton.setOnClickListener(v -> {
            String email = emailInput != null ? emailInput.getText().toString().trim() : "";
            if (!TextUtils.isEmpty(email) && isValidEmail(email)) {
                authViewModel.resendVerificationEmail(email);
            }
        });

        // 返回登录
        backToLoginText.setOnClickListener(v -> {
            // 清除验证状态（用户取消注册流程）
            com.ttt.safevault.network.TokenManager tokenManager =
                com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext()).getTokenManager();
            tokenManager.clearEmailVerificationStatus();
            finish();
        });

        // 清除错误
        if (emailInput != null) {
            emailInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    hideError();
                }
            });
        }
        if (usernameInput != null) {
            usernameInput.setOnFocusChangeListener((v, hasFocus) -> {
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
                updateContinueButtonState();
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
        if (passwordInput != null) {
            passwordInput.addTextChangedListener(textWatcher);
        }
        if (confirmPasswordInput != null) {
            confirmPasswordInput.addTextChangedListener(textWatcher);
        }

        // 添加密码强度监听器
        if (passwordInput != null) {
            passwordInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // 仅在第二步时更新密码强度
                    if (!isStepOne) {
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
        PasswordStrength strength = com.ttt.safevault.utils.PasswordStrengthCalculator.calculate(password);
        int percentage = com.ttt.safevault.utils.PasswordStrengthCalculator.getStrengthPercentage(password);

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
     * 获取强度等级对应的颜色资源 ID
     */
    private int getStrengthColor(PasswordStrength.Level level) {
        return switch (level) {
            case WEAK -> R.color.strength_weak;
            case MEDIUM -> R.color.strength_medium;
            case STRONG -> R.color.strength_strong;
        };
    }

    /**
     * 更新继续按钮状态
     */
    private void updateContinueButtonState() {
        boolean enabled = false;

        if (isStepOne) {
            // 第一步：需要邮箱和用户名
            String email = emailInput != null ? emailInput.getText().toString().trim() : "";
            String username = usernameInput != null ? usernameInput.getText().toString().trim() : "";
            enabled = isValidEmail(email) && !TextUtils.isEmpty(username);

            // 更新重发按钮状态
            if (resendVerificationButton != null) {
                boolean emailValid = isValidEmail(email);
                resendVerificationButton.setEnabled(emailValid);
                resendVerificationButton.setAlpha(emailValid ? 1.0f : 0.5f);
            }
        } else {
            // 第二步：需要密码和确认密码
            String password = passwordInput.getText().toString().trim();
            String confirmPassword = confirmPasswordInput.getText().toString().trim();
            enabled = !TextUtils.isEmpty(password) && !TextUtils.isEmpty(confirmPassword);
        }

        if (continueButton != null) {
            Boolean loading = authViewModel.getLoading().getValue();
            continueButton.setEnabled(enabled && (loading == null || !loading));
        }
    }

    /**
     * 更新加载状态
     */
    private void updateLoadingState(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }

        if (emailInput != null) {
            emailInput.setEnabled(!isLoading);
        }
        if (usernameInput != null) {
            usernameInput.setEnabled(!isLoading);
        }
        if (passwordInput != null) {
            passwordInput.setEnabled(!isLoading);
        }
        if (confirmPasswordInput != null) {
            confirmPasswordInput.setEnabled(!isLoading);
        }
        if (continueButton != null) {
            continueButton.setEnabled(!isLoading);
        }

        updateContinueButtonState();
    }

    /**
     * 显示错误
     */
    private void showError(String error) {
        if (errorText != null) {
            errorText.setText(error);
            errorText.setVisibility(View.VISIBLE);
            errorText.setTextColor(getColor(R.color.error_red));
        }
    }

    /**
     * 隐藏错误
     */
    private void hideError() {
        if (errorText != null) {
            errorText.setVisibility(View.GONE);
        }
        if (emailLayout != null) {
            emailLayout.setError(null);
        }
        if (usernameLayout != null) {
            usernameLayout.setError(null);
        }
        if (passwordLayout != null) {
            passwordLayout.setError(null);
        }
        if (confirmPasswordLayout != null) {
            confirmPasswordLayout.setError(null);
        }
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

        // 保存输入的数据
        registeredEmail = email;
        registeredUsername = username;

        // 调用注册 API
        authViewModel.registerWithEmail(email, username);
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

        // 显示进度提示
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (continueButton != null) {
            continueButton.setEnabled(false);
        }

        // 在后台线程执行注册完成
        new android.os.Handler().post(() -> {
            try {
                // 调用后端服务完成注册
                BackendService backendService =
                        com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

                com.ttt.safevault.dto.response.CompleteRegistrationResponse response =
                        backendService.completeRegistration(registeredEmail, registeredUsername, password);

                // 注册成功
                runOnUiThread(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    if (continueButton != null) {
                        continueButton.setEnabled(true);
                    }

                    // 保存邮箱到 TokenManager
                    com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext())
                            .getTokenManager().saveLastLoginEmail(registeredEmail);

                    // 清除验证状态
                    com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext())
                            .getTokenManager().clearEmailVerificationStatus();

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
                    if (continueButton != null) {
                        continueButton.setEnabled(true);
                    }
                    showError("注册失败: " + e.getMessage());
                });
            }
        });
    }

    /**
     * 切换密码可见性
     */
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
     * 切换确认密码可见性
     */
    private void toggleConfirmPasswordVisibility() {
        if (confirmPasswordInput == null || confirmPasswordLayout == null) return;

        isConfirmPasswordVisible = !isConfirmPasswordVisible;

        if (isConfirmPasswordVisible) {
            confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            confirmPasswordLayout.setEndIconDrawable(R.drawable.ic_visibility);
            confirmPasswordLayout.setEndIconContentDescription(getString(R.string.hide_password));
        } else {
            confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            confirmPasswordLayout.setEndIconDrawable(R.drawable.ic_visibility_off);
            confirmPasswordLayout.setEndIconContentDescription(getString(R.string.show_password));
        }

        confirmPasswordInput.setSelection(confirmPasswordInput.getText().length());
    }

    /**
     * 显示第二步：设置主密码
     */
    private void showStepTwo() {
        isStepOne = false;

        // 隐藏第一步的输入框
        if (emailLayout != null) {
            emailLayout.setVisibility(View.GONE);
        }
        if (usernameLayout != null) {
            usernameLayout.setVisibility(View.GONE);
        }
        if (resendVerificationButton != null) {
            resendVerificationButton.setVisibility(View.GONE);
        }

        // 显示第二步的密码卡片
        if (passwordCard != null) {
            passwordCard.setVisibility(View.VISIBLE);
        }
        if (passwordLayout != null) {
            passwordLayout.setVisibility(View.VISIBLE);
        }
        if (confirmPasswordSection != null) {
            confirmPasswordSection.setVisibility(View.VISIBLE);
        }
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
        if (continueButton != null) {
            continueButton.setText(R.string.initialize);
        }

        updateContinueButtonState();
    }

    /**
     * 导航到主界面
     */
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // 如果正在加载，不允许返回
        Boolean loading = authViewModel.getLoading().getValue();
        if (loading != null && loading) {
            return;
        }

        // 第二步不允许返回
        if (!isStepOne) {
            return;
        }

        // 第一步：停止轮询并清除验证状态
        stopVerificationPolling();
        com.ttt.safevault.network.TokenManager tokenManager =
            com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext()).getTokenManager();
        tokenManager.clearEmailVerificationStatus();

        super.onBackPressed();
    }

    /**
     * 开始轮询检查验证状态
     */
    private void startVerificationPolling(String email) {
        stopVerificationPolling(); // 先停止之前的轮询

        pollingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        pollingStartTime = System.currentTimeMillis();

        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                // 检查是否超过最大轮询时间
                if (System.currentTimeMillis() - pollingStartTime > MAX_POLLING_TIME_MS) {
                    stopVerificationPolling();
                    return;
                }

                // 检查是否还在第一步
                if (!isStepOne) {
                    stopVerificationPolling();
                    return;
                }

                // 调用检查验证状态
                authViewModel.checkVerificationStatus(email);

                // 继续下一次轮询
                pollingHandler.postDelayed(this, POLLING_INTERVAL_MS);
            }
        };

        // 立即执行第一次检查
        pollingHandler.post(pollingRunnable);
    }

    /**
     * 停止轮询
     */
    private void stopVerificationPolling() {
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
            pollingHandler = null;
            pollingRunnable = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止轮询
        stopVerificationPolling();
    }
}
