package com.ttt.safevault.ui.share;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
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
import com.ttt.safevault.viewmodel.ShareViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * NFC发送界面
 * 通过NFC发送加密的密码数据
 */
public class NFCSendActivity extends AppCompatActivity {

    private static final String TAG = "NFCSendActivity";
    public static final String EXTRA_PASSWORD_ID = "password_id";

    private ShareViewModel viewModel;
    private BackendService backendService;
    private BiometricAuthHelper biometricAuthHelper;
    private NfcAdapter nfcAdapter;
    private ShareRecordManager recordManager;

    private TextView textUsername;
    private View cardReady;
    private View cardAuth;
    private View cardSuccess;
    private TextView textStatus;

    private int passwordId;
    private PasswordItem passwordToShare;
    private String shareData;
    private boolean isAuthenticated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_send);

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
            getSupportActionBar().setTitle("NFC分享");
        }

        passwordId = getIntent().getIntExtra(EXTRA_PASSWORD_ID, -1);

        if (passwordId == -1) {
            Toast.makeText(this, "无效的密码ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initManagers();
        initViews();
        loadPassword();
        setupNfc();
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
        cardReady = findViewById(R.id.cardReady);
        cardAuth = findViewById(R.id.cardAuth);
        cardSuccess = findViewById(R.id.cardSuccess);
        textStatus = findViewById(R.id.textStatus);

        findViewById(R.id.btnAuthenticate).setOnClickListener(v -> authenticateAndPrepare());
    }

    private void loadPassword() {
        viewModel.loadPasswordItem(passwordId);
        viewModel.getPasswordById(passwordId).observe(this, password -> {
            if (password != null) {
                passwordToShare = password;
                textUsername.setText(password.getUsername());
            } else {
                Toast.makeText(this, "密码不存在", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (nfcAdapter == null) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("NFC不可用")
                .setMessage("此设备不支持NFC功能")
                .setPositiveButton("确定", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
            return;
        }

        if (!nfcAdapter.isEnabled()) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("NFC未开启")
                .setMessage("请在系统设置中开启NFC功能")
                .setPositiveButton("去设置", (dialog, which) -> {
                    // 无法直接跳转到NFC设置，提示用户手动开启
                    Toast.makeText(this, "请在系统设置中开启NFC", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
        }
    }

    private void authenticateAndPrepare() {
        if (passwordToShare == null) {
            Toast.makeText(this, "密码数据未加载", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean canUseBiometric = BiometricAuthHelper.isBiometricSupported(this)
                && backendService.canUseBiometricAuthentication();

        if (canUseBiometric) {
            showBiometricAuthAndPrepare();
        } else {
            showPasswordAuthAndPrepare();
        }
    }

    private void showBiometricAuthAndPrepare() {
        biometricAuthHelper.authenticate(new BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> prepareShareData());
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(NFCSendActivity.this,
                        "生物识别验证失败: " + error, Toast.LENGTH_SHORT).show();
                    showPasswordAuthAndPrepare();
                });
            }

            @Override
            public void onCancel() {
                runOnUiThread(() ->
                    Toast.makeText(NFCSendActivity.this, "已取消", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showPasswordAuthAndPrepare() {
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
                            prepareShareData();
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

    private void prepareShareData() {
        new Thread(() -> {
            try {
                shareData = generateSharePacket();

                runOnUiThread(() -> {
                    isAuthenticated = true;
                    showReadyState();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "数据准备失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private String generateSharePacket() throws Exception {
        // 1. 生成临时AES密钥
        byte[] keyBytes = new byte[32];
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
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);

        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

        byte[] encryptedData = cipher.doFinal(passwordJson.getBytes("UTF-8"));
        String encryptedDataBase64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP);
        String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP);

        // 4. 构建分享数据包（离线分享无法强制过期）
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

        return new Gson().toJson(sharePacket);
    }

    private void showReadyState() {
        cardAuth.setVisibility(View.GONE);
        cardReady.setVisibility(View.VISIBLE);
        textStatus.setText("就绪，请将设备靠近接收方");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Android Beam API已被弃用，NFC发送需要依赖接收端主动扫描
        // 此处仅作为就绪状态显示
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
