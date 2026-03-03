package com.ttt.safevault.ui.share;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.ttt.safevault.R;
import com.ttt.safevault.security.biometric.BiometricAuthManager;
import com.ttt.safevault.service.manager.AccountManager;
import com.ttt.safevault.service.manager.ContactManager;

import java.util.concurrent.Executors;

/**
 * 我的身份码界面
 * 显示用户的身份QR码，其他用户可以扫描添加为联系人
 */
public class MyIdentityActivity extends AppCompatActivity {
    private static final String TAG = "MyIdentityActivity";
    private static final int QR_CODE_SIZE = 500;

    private com.google.android.material.appbar.MaterialToolbar toolbar;
    private ImageView imageQrCode;
    private CircularProgressIndicator progressBar;
    private TextView textError;
    private TextView textDisplayName;
    private TextView textUsername;
    private MaterialButton btnShare;
    private MaterialButton btnRegenerate;

    private ContactManager contactManager;
    private AccountManager accountManager;
    private BiometricAuthManager biometricAuthManager;
    private String currentQRContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_identity);

        contactManager = new ContactManager(this);

        // 使用正确的构造函数创建 AccountManager
        com.ttt.safevault.model.BackendService backendService =
            com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
        com.ttt.safevault.data.PasswordDao passwordDao =
            com.ttt.safevault.data.AppDatabase.getInstance(this).passwordDao();
        com.ttt.safevault.service.manager.PasswordManager passwordManager =
            new com.ttt.safevault.service.manager.PasswordManager(backendService, passwordDao);

        accountManager = new AccountManager(
                this,
                passwordManager,
                new com.ttt.safevault.security.SecurityConfig(this),
                com.ttt.safevault.network.RetrofitClient.getInstance(this)
        );

        // 初始化 BiometricAuthManager
        biometricAuthManager = BiometricAuthManager.getInstance(this);

        initViews();
        loadUserInfo();

        // 检查身份并验证
        checkAndVerifyIdentity();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        imageQrCode = findViewById(R.id.image_qr_code);
        progressBar = findViewById(R.id.progress_bar);
        textError = findViewById(R.id.text_error);
        textDisplayName = findViewById(R.id.text_display_name);
        textUsername = findViewById(R.id.text_username);
        btnShare = findViewById(R.id.btn_share);
        btnRegenerate = findViewById(R.id.btn_regenerate);

        btnShare.setOnClickListener(v -> shareQRCode());
        btnRegenerate.setOnClickListener(v -> generateQRCode());
    }

    private void loadUserInfo() {
        // 从TokenManager获取用户信息
        com.ttt.safevault.network.TokenManager tokenManager =
            com.ttt.safevault.network.RetrofitClient.getInstance(this)
                .getTokenManager();

        // 获取显示名称、验证用户名和邮箱
        String displayName = tokenManager.getDisplayName();
        String verifiedUsername = tokenManager.getVerifiedUsername();
        String userEmail = tokenManager.getLastLoginEmail();

        // 添加详细日志
        Log.d(TAG, "=== TokenManager 获取的用户信息 ===");
        Log.d(TAG, "displayName: " + displayName);
        Log.d(TAG, "verifiedUsername: " + verifiedUsername);
        Log.d(TAG, "userEmail: " + userEmail);
        Log.d(TAG, "================================");

        // 确定最终显示的名称（优先使用displayName）
        String finalDisplayName = displayName;
        if (finalDisplayName == null || finalDisplayName.isEmpty()) {
            finalDisplayName = verifiedUsername;
        }
        if (finalDisplayName == null || finalDisplayName.isEmpty()) {
            // 最后从邮箱提取
            if (userEmail != null && userEmail.contains("@")) {
                finalDisplayName = userEmail.substring(0, userEmail.indexOf('@'));
            } else {
                finalDisplayName = "未知用户";
            }
        }

        Log.d(TAG, "最终显示 - displayName: " + finalDisplayName + ", email: " + userEmail);

        // 上方显示displayName，下方显示邮箱
        textDisplayName.setText(finalDisplayName);
        if (userEmail != null && !userEmail.isEmpty()) {
            textUsername.setText(userEmail);
        } else {
            textUsername.setText("未知邮箱");
        }
    }

    /**
     * 检查身份并验证
     * 如果CryptoManager已解锁，直接生成QR码；否则触发身份验证
     */
    private void checkAndVerifyIdentity() {
        // 检查CryptoManager是否已解锁（即是否有主密码）
        com.ttt.safevault.model.BackendService backendService =
            com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

        if (backendService.isUnlocked()) {
            // 已解锁，直接生成QR码
            Log.d(TAG, "CryptoManager已解锁，直接生成QR码");
            generateQRCode();
            return;
        }

        // 未解锁，检查是否可以使用生物识别
        if (biometricAuthManager.canUseBiometric()) {
            Log.d(TAG, "CryptoManager未解锁，启用生物识别验证");
            showBiometricPrompt();
        } else {
            Log.d(TAG, "CryptoManager未解锁，显示主密码输入对话框");
            showPasswordInputDialog();
        }
    }

    private void generateQRCode() {
        // 显示加载指示器
        progressBar.setVisibility(View.VISIBLE);
        imageQrCode.setVisibility(View.GONE);
        textError.setVisibility(View.GONE);
        btnShare.setEnabled(false);
        btnRegenerate.setEnabled(false);

        // 在后台线程生成QR码
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 从TokenManager获取当前登录的邮箱
                String userEmail = com.ttt.safevault.network.RetrofitClient.getInstance(
                        MyIdentityActivity.this)
                        .getTokenManager().getLastLoginEmail();

                // 从 BackendService 获取 DataKey（用于解密私钥）
                // 注意：生成身份码只需要公钥，不需要主密码
                // 公钥已经存储在 SecureKeyStorageManager 中

                // 检查邮箱
                if (userEmail == null || userEmail.isEmpty()) {
                    runOnUiThread(() -> {
                        showError("无法获取用户邮箱，请重新登录");
                        Log.e(TAG, "生成身份码失败：无法获取用户邮箱");
                    });
                    return;
                }

                // 检查邮箱格式
                if (!userEmail.contains("@")) {
                    runOnUiThread(() -> {
                        showError("用户邮箱格式无效");
                        Log.e(TAG, "生成身份码失败：邮箱格式无效 - " + userEmail);
                    });
                    return;
                }

                Log.d(TAG, "开始生成身份码，用户邮箱: " + userEmail);

                // 生成身份QR码内容（不依赖主密码）
                currentQRContent = contactManager.generateMyIdentityQR(userEmail);

                if (currentQRContent == null) {
                    Log.e(TAG, "生成身份码失败：密钥派生错误");
                    runOnUiThread(() -> showError("生成身份码内容失败: 密钥派生错误"));
                    return;
                }

                Log.d(TAG, "身份码内容生成成功，长度: " + currentQRContent.length());

                // 生成QR码图片
                Bitmap qrBitmap = generateQRCodeBitmap(currentQRContent);

                if (qrBitmap == null) {
                    Log.e(TAG, "生成QR码失败：ZXing编码错误");
                    runOnUiThread(() -> showError("生成QR码图片失败"));
                    return;
                }

                Log.d(TAG, "QR码图片生成成功");

                // 在UI线程显示QR码
                runOnUiThread(() -> {
                    imageQrCode.setImageBitmap(qrBitmap);
                    imageQrCode.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.GONE);
                    btnShare.setEnabled(true);
                    btnRegenerate.setEnabled(true);
                });

            } catch (Exception e) {
                Log.e(TAG, "生成身份码时发生异常", e);
                runOnUiThread(() -> showError("生成失败: " + e.getMessage()));
            }
        });
    }

    private Bitmap generateQRCodeBitmap(String content) {
        try {
            Log.d(TAG, "开始生成QR码图片，内容长度: " + content.length());
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }

            Log.d(TAG, "QR码图片生成完成，尺寸: " + width + "x" + height);
            return bitmap;

        } catch (WriterException e) {
            Log.e(TAG, "ZXing编码失败", e);
            return null;
        }
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        imageQrCode.setVisibility(View.GONE);
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
        btnShare.setEnabled(false);
        btnRegenerate.setEnabled(true);
    }

    private void shareQRCode() {
        if (currentQRContent == null) {
            Toast.makeText(this, "QR码未生成", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建分享Intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, currentQRContent);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "我的SafeVault身份码");

        // 检查是否有可以处理的应用
        if (shareIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(Intent.createChooser(shareIntent, "分享身份码"));
        } else {
            Toast.makeText(this, "没有可用的分享应用", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示生物识别验证提示
     */
    private void showBiometricPrompt() {
        // 显示加载状态
        progressBar.setVisibility(View.VISIBLE);
        imageQrCode.setVisibility(View.GONE);
        textError.setVisibility(View.GONE);

        com.ttt.safevault.security.BiometricAuthHelper biometricHelper =
            new com.ttt.safevault.security.BiometricAuthHelper(this);

        biometricHelper.authenticate(new com.ttt.safevault.security.BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "生物识别验证成功");
                // 生物识别成功后，SessionGuard 应该已经解锁
                // 生成身份码只需要公钥，不需要主密码
                com.ttt.safevault.model.BackendService backendService =
                    com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

                if (backendService.isUnlocked()) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        generateQRCode();
                    });
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        showError("会话未解锁，请重试");
                    });
                }
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "生物识别验证失败: " + error);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showError("生物识别验证失败: " + error);
                    // 失败后尝试显示密码输入
                    showPasswordInputDialog();
                });
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "用户取消生物识别验证");
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MyIdentityActivity.this,
                            "已取消验证", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    /**
     * 显示主密码输入对话框
     */
    private void showPasswordInputDialog() {
        // 显示加载状态
        progressBar.setVisibility(View.VISIBLE);
        imageQrCode.setVisibility(View.GONE);
        textError.setVisibility(View.GONE);

        // 创建密码输入对话框
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("输入主密码");

        // 添加密码输入框
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("请输入主密码");
        builder.setView(input);

        // 设置确定按钮
        builder.setPositiveButton("确定", (dialog, which) -> {
            String password = input.getText().toString();
            if (password != null && !password.isEmpty()) {
                // 验证密码（会解锁CryptoManager）
                boolean isValid = verifyMasterPassword(password);
                if (isValid) {
                    progressBar.setVisibility(View.GONE);
                    generateQRCode();
                } else {
                    progressBar.setVisibility(View.GONE);
                    showError("主密码错误，请重试");
                    // 重新显示密码输入对话框
                    showPasswordInputDialog();
                }
            } else {
                progressBar.setVisibility(View.GONE);
                showError("密码不能为空");
                finish();
            }
        });

        // 设置取消按钮
        builder.setNegativeButton("取消", (dialog, which) -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "已取消", Toast.LENGTH_SHORT).show();
            finish();
        });

        builder.setCancelable(false);
        builder.show();
    }

    /**
     * 验证主密码是否正确
     */
    private boolean verifyMasterPassword(String password) {
        try {
            // 尝试使用密码解锁 CryptoManager
            com.ttt.safevault.model.BackendService backendService =
                com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
            return backendService.unlock(password);
        } catch (Exception e) {
            Log.e(TAG, "验证主密码时发生异常", e);
            return false;
        }
    }
}
