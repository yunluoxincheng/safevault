package com.ttt.safevault.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.ttt.safevault.R;
import com.ttt.safevault.viewmodel.AuthViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

/**
 * 邮箱验证Activity
 * 处理从验证邮件 Deep Link 跳转过来的验证请求
 */
public class EmailVerificationActivity extends AppCompatActivity {

    private static final String TAG = "EmailVerificationActivity";

    private AuthViewModel authViewModel;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView messageText;
    private MaterialCardView resultCard;
    private Button continueButton;
    private Button resendButton;

    private String email;
    private String username;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);

        // 防止截图
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);

        // 初始化 ViewModel
        ViewModelProvider.Factory factory = new ViewModelFactory(getApplication());
        authViewModel = new ViewModelProvider(this, factory).get(AuthViewModel.class);

        initViews();
        parseIntent();
        setupObservers();
        verifyToken();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);
        messageText = findViewById(R.id.message_text);
        resultCard = findViewById(R.id.result_card);
        continueButton = findViewById(R.id.continue_button);
        resendButton = findViewById(R.id.resend_button);

        resultCard.setVisibility(View.GONE);

        // 重发验证邮件按钮（仅在验证失败时显示）
        resendButton.setOnClickListener(v -> {
            if (email != null && !email.isEmpty()) {
                authViewModel.resendVerificationEmail(email);
            }
        });
    }

    private void parseIntent() {
        Intent intent = getIntent();
        Uri data = intent.getData();

        if (data != null) {
            // 处理 Deep Link: safevault://verify-email?token=xxx&email=xxx
            token = data.getQueryParameter("token");
            email = data.getQueryParameter("email");
            username = intent.getStringExtra("username");
        }

        // 如果 URL 中没有 email，尝试从 Intent extra 获取
        if (email == null || email.isEmpty()) {
            email = intent.getStringExtra("email");
        }

        if (token == null || token.isEmpty()) {
            showError("验证链接无效");
        }
    }

    private void setupObservers() {
        // 观察验证结果
        authViewModel.getEmailVerificationResponse().observe(this, response -> {
            if (response != null) {
                if (response.isSuccess()) {
                    showSuccess(response.getMessage());
                    email = response.getEmail();
                    username = response.getUsername();

                    android.util.Log.d("EmailVerificationActivity", "验证成功！email=" + email + ", username=" + username);

                    // 保存验证状态到持久化存储
                    com.ttt.safevault.network.TokenManager tokenManager =
                        com.ttt.safevault.network.RetrofitClient.getInstance(getApplicationContext()).getTokenManager();

                    android.util.Log.d("EmailVerificationActivity", "TokenManager instance: " + tokenManager);
                    tokenManager.saveEmailVerificationStatus(email, username);

                    android.util.Log.d("EmailVerificationActivity", "验证状态已保存。isEmailVerified=" + tokenManager.isEmailVerified());
                    android.util.Log.d("EmailVerificationActivity", "getVerifiedEmail=" + tokenManager.getVerifiedEmail());
                    android.util.Log.d("EmailVerificationActivity", "getVerifiedUsername=" + tokenManager.getVerifiedUsername());

                    // 验证成功后，延迟关闭页面，让用户看到成功提示
                    resultCard.postDelayed(() -> {
                        finish();
                    }, 1500);
                } else {
                    showError(response.getMessage());
                }
            }
        });

        // 观察加载状态
        authViewModel.getLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
        });

        // 观察错误
        authViewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showError(error);
            }
        });

        // 观察重发结果
        authViewModel.getEmailRegistrationResponse().observe(this, response -> {
            if (response != null) {
                Toast.makeText(this, response.getMessage(), Toast.LENGTH_SHORT).show();
                if (response.isEmailSent()) {
                    messageText.setText("验证邮件已重新发送，请查收");
                }
            }
        });
    }

    private void verifyToken() {
        if (token != null && !token.isEmpty()) {
            authViewModel.verifyEmail(token);
        }
    }

    private void showSuccess(String message) {
        resultCard.setVisibility(View.VISIBLE);
        statusText.setText("验证成功");
        statusText.setTextColor(getColor(R.color.success_green));
        messageText.setText(message);
        continueButton.setVisibility(View.GONE);
        resendButton.setVisibility(View.GONE);
    }

    private void showError(String error) {
        resultCard.setVisibility(View.VISIBLE);
        statusText.setText("验证失败");
        statusText.setTextColor(getColor(R.color.error_red));
        messageText.setText(error);
        continueButton.setVisibility(View.GONE);

        // 显示重发按钮，如果没有 email 则禁用
        resendButton.setVisibility(View.VISIBLE);
        if (email != null && !email.isEmpty()) {
            resendButton.setEnabled(true);
            resendButton.setAlpha(1.0f);
        } else {
            resendButton.setEnabled(false);
            resendButton.setAlpha(0.5f);
            messageText.setText(error + "\n\n无法重新发送验证邮件，请返回注册页面重试。");
        }
    }

    @Override
    public void onBackPressed() {
        // 验证完成后不允许返回
        if (continueButton.getVisibility() == View.VISIBLE) {
            finish();
        } else {
            super.onBackPressed();
        }
    }
}
