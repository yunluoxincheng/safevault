package com.ttt.safevault.ui.share;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.ttt.safevault.R;
import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.security.BiometricAuthHelper;
import com.ttt.safevault.service.manager.ShareRecordManager;
import com.ttt.safevault.utils.ShareQRGenerator;
import com.ttt.safevault.viewmodel.ShareViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * QR码分享界面（简化版）
 * 直接生成包含加密数据和密钥的QR码，扫描即可获取
 */
public class QRShareActivity extends AppCompatActivity {
    private static final String TAG = "QRShareActivity";

    public static final String EXTRA_PASSWORD_ID = "password_id";

    private ShareViewModel viewModel;
    private BackendService backendService;
    private BiometricAuthHelper biometricAuthHelper;
    private ShareRecordManager recordManager;

    private TextView textUsername;
    private ImageView qrCodeImage;
    private View cardQRCode;
    private Button btnShare;

    private int passwordId;
    private PasswordItem passwordToShare;
    private String generatedQrContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_share);

        // 设置FLAG_SECURE防止截屏
        com.ttt.safevault.security.SecurityConfig securityConfig =
            new com.ttt.safevault.security.SecurityConfig(this);
        if (securityConfig.isScreenshotProtectionEnabled()) {
            getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            );
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("QR码分享");
        }

        // 获取传入的密码ID
        passwordId = getIntent().getIntExtra(EXTRA_PASSWORD_ID, -1);

        if (passwordId == -1) {
            Toast.makeText(this, "无效的密码ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initManagers();
        initViews();
        loadPassword();
    }

    private void initManagers() {
        backendService = ServiceLocator.getInstance().getBackendService();
        biometricAuthHelper = new BiometricAuthHelper(this);
        ViewModelFactory factory = new ViewModelFactory(getApplication(), backendService);
        viewModel = new ViewModelProvider(this, factory).get(ShareViewModel.class);
        recordManager = new ShareRecordManager(this);
    }

    private void initViews() {
        textUsername = findViewById(R.id.textUsername);
        qrCodeImage = findViewById(R.id.qrCodeImage);
        cardQRCode = findViewById(R.id.cardQRCode);
        btnShare = findViewById(R.id.btnShare);

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> authenticateAndShare());
        }
    }

    private void loadPassword() {
        viewModel.loadPasswordItem(passwordId);
        viewModel.getPasswordById(passwordId).observe(this, password -> {
            if (password != null) {
                passwordToShare = password;
                if (textUsername != null) {
                    textUsername.setText(password.getUsername());
                }
            } else {
                Toast.makeText(this, "密码不存在", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /**
     * 验证用户身份后执行分享
     */
    private void authenticateAndShare() {
        if (passwordToShare == null) {
            Toast.makeText(this, "密码数据未加载", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否可以使用生物识别
        boolean canUseBiometric = BiometricAuthHelper.isBiometricSupported(this)
                && backendService.canUseBiometricAuthentication();

        if (canUseBiometric) {
            showBiometricAuthAndShare();
        } else {
            showPasswordAuthAndShare();
        }
    }

    /**
     * 使用生物识别验证
     */
    private void showBiometricAuthAndShare() {
        biometricAuthHelper.authenticate(new BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> generateShare());
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(QRShareActivity.this,
                        "生物识别验证失败: " + error, Toast.LENGTH_SHORT).show();
                    showPasswordAuthAndShare();
                });
            }

            @Override
            public void onCancel() {
                runOnUiThread(() ->
                    Toast.makeText(QRShareActivity.this, "已取消分享", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    /**
     * 使用主密码验证
     */
    private void showPasswordAuthAndShare() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_input, null);
        com.google.android.material.textfield.TextInputLayout passwordLayout =
            dialogView.findViewById(R.id.passwordLayout);
        com.google.android.material.textfield.TextInputEditText passwordInput =
            dialogView.findViewById(R.id.passwordInput);

        if (passwordInput != null) {
            passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        if (passwordLayout != null) {
            passwordLayout.setEndIconDrawable(getDrawable(R.drawable.ic_visibility));

            passwordLayout.setEndIconOnClickListener(v -> {
                if (passwordInput == null) return;

                int selection = passwordInput.getSelectionEnd();
                int currentInputType = passwordInput.getInputType();
                int variation = currentInputType & android.text.InputType.TYPE_MASK_VARIATION;

                if (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                    passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    passwordLayout.setEndIconDrawable(getDrawable(R.drawable.ic_visibility_off));
                } else {
                    passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    passwordLayout.setEndIconDrawable(getDrawable(R.drawable.ic_visibility));
                }
                if (selection >= 0) {
                    passwordInput.setSelection(selection);
                }
            });
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("验证身份")
            .setMessage("请输入主密码以验证身份")
            .setView(dialogView)
            .setPositiveButton("确认", (dialog, which) -> {
                if (passwordInput == null) return;

                String password = passwordInput.getText().toString();
                if (password.isEmpty()) {
                    Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
                    return;
                }

                new Thread(() -> {
                    boolean verified = backendService.unlock(password);
                    runOnUiThread(() -> {
                        if (verified) {
                            generateShare();
                        } else {
                            Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start();
            })
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show();
    }

    private void generateShare() {
        if (passwordToShare == null) {
            Toast.makeText(this, "密码数据未加载", Toast.LENGTH_SHORT).show();
            return;
        }

        // 在后台线程生成分享
        new Thread(() -> {
            try {
                // 1. 生成临时AES密钥
                byte[] keyBytes = new byte[32]; // 256-bit key
                new java.security.SecureRandom().nextBytes(keyBytes);
                String tempKeyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP);

                // 2. 将密码数据转换为JSON
                Map<String, Object> passwordData = new HashMap<>();
                passwordData.put("title", passwordToShare.getTitle());
                passwordData.put("username", passwordToShare.getUsername());
                passwordData.put("password", passwordToShare.getPassword());
                passwordData.put("url", passwordToShare.getUrl());
                passwordData.put("notes", passwordToShare.getNotes());
                String passwordJson = new Gson().toJson(passwordData);

                // 3. 用临时密钥加密密码数据
                byte[] iv = new byte[12]; // GCM IV
                new java.security.SecureRandom().nextBytes(iv);

                SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

                byte[] encryptedData = cipher.doFinal(passwordJson.getBytes("UTF-8"));
                String encryptedDataBase64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP);
                String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP);

                // 4. 构建分享数据包（离线分享无法强制过期，设置为永不过期）
                Map<String, Object> sharePacket = new HashMap<>();
                sharePacket.put("v", "2.0");
                sharePacket.put("data", encryptedDataBase64);
                sharePacket.put("key", tempKeyBase64);
                sharePacket.put("iv", ivBase64);
                sharePacket.put("meta", Map.of(
                    "created", System.currentTimeMillis(),
                    "expire", 0,  // 离线分享无法强制过期
                    "perm", Map.of("view", true, "save", true)
                ));

                // 5. 生成QR码内容
                String packetJson = new Gson().toJson(sharePacket);
                generatedQrContent = "safevault://qr/" + Base64.encodeToString(
                    packetJson.getBytes("UTF-8"),
                    Base64.NO_WRAP | Base64.URL_SAFE
                );

                // 6. 保存分享记录
                recordManager.createShareRecord(
                    passwordId,
                    "sent_qr",
                    null,
                    packetJson,
                    new SharePermission(true, true, true),
                    0  // 离线分享无法强制过期
                );

                // 7. 在UI线程显示QR码
                runOnUiThread(() -> {
                    displayQRCode(generatedQrContent);
                    if (btnShare != null) {
                        btnShare.setEnabled(false);
                        btnShare.setText("分享已生成");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "生成离线分享失败", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "分享生成失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void displayQRCode(String content) {
        try {
            Bitmap qrBitmap = ShareQRGenerator.generateQRCode(content, 512);
            if (qrCodeImage != null) {
                qrCodeImage.setImageBitmap(qrBitmap);
            }
            if (cardQRCode != null) {
                cardQRCode.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "QR码生成失败", e);
            Toast.makeText(this, "QR码生成失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
