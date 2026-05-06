package com.ttt.safevault.ui.share;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.ttt.safevault.R;
import com.ttt.safevault.core.ServiceLocator;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.dto.response.ShareResponse;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.security.BiometricAuthHelper;
import com.ttt.safevault.security.SafetyNumberManager;
import com.ttt.safevault.security.biometric.BiometricAuthManager;
import com.ttt.safevault.service.manager.ContactManager;
import com.ttt.safevault.service.manager.ShareRecordManager;
import com.ttt.safevault.viewmodel.ShareViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 密码分享页面（云端分享）。
 */
public class ShareActivity extends AppCompatActivity {
    private static final String TAG = "ShareActivity";

    public static final String EXTRA_PASSWORD_ID = "password_id";
    public static final String EXTRA_CONTACT_ID = "contact_id";

    private final ActivityResultLauncher<Intent> contactListLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String selectedContactId = result.getData().getStringExtra(ContactListActivity.EXTRA_CONTACT_ID);
                    if (selectedContactId != null) {
                        loadContact(selectedContactId);
                    }
                }
            });

    private ShareViewModel viewModel;
    private BackendService backendService;
    private BiometricAuthHelper biometricAuthHelper;
    private ContactManager contactManager;
    private ShareRecordManager recordManager;
    private SafetyNumberManager safetyNumberManager;

    private TextView textContactName;
    private TextView textUsername;
    private RadioButton radio1Hour;
    private RadioButton radio1Day;
    private RadioButton radio7Days;
    private RadioButton radioNever;
    private LinearProgressIndicator progressIndicator;
    private View cardProgress;
    private TextView textStatus;
    private Button btnShare;
    private Button btnSelectContact;

    private int passwordId;
    private String contactId;
    private Contact selectedContact;
    private PasswordItem passwordToShare;

    private ShareResponse latestShareResponse;
    private long pendingShareExpireAt;
    private SharePermission pendingSharePermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_e2e);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("分享密码");
        }

        passwordId = getIntent().getIntExtra(EXTRA_PASSWORD_ID, -1);
        contactId = getIntent().getStringExtra(EXTRA_CONTACT_ID);
        if (passwordId == -1) {
            Toast.makeText(this, "无效的密码ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initManagers();
        initViews();
        loadPassword();

        if (contactId != null) {
            loadContact(contactId);
        }
    }

    private void initManagers() {
        backendService = ServiceLocator.getInstance().getBackendService();
        biometricAuthHelper = new BiometricAuthHelper(this);
        ViewModelFactory factory = new ViewModelFactory(getApplication(), backendService);
        viewModel = new ViewModelProvider(this, factory).get(ShareViewModel.class);
        contactManager = new ContactManager(this);
        recordManager = new ShareRecordManager(this);
        safetyNumberManager = SafetyNumberManager.getInstance(this);
    }

    private void initViews() {
        textContactName = findViewById(R.id.textContactName);
        textUsername = findViewById(R.id.textUsername);
        radio1Hour = findViewById(R.id.radio1Hour);
        radio1Day = findViewById(R.id.radio1Day);
        radio7Days = findViewById(R.id.radio7Days);
        radioNever = findViewById(R.id.radioNever);
        progressIndicator = findViewById(R.id.progressIndicator);
        cardProgress = findViewById(R.id.cardProgress);
        textStatus = findViewById(R.id.textStatus);
        btnShare = findViewById(R.id.btnShare);
        btnSelectContact = findViewById(R.id.btnSelectContact);

        if (radio7Days != null) {
            radio7Days.setChecked(true);
        }

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> authenticateAndShare());
        }
        if (btnSelectContact != null) {
            btnSelectContact.setOnClickListener(v -> {
                Intent intent = new Intent(this, ContactListActivity.class);
                contactListLauncher.launch(intent);
            });
        }

        observeShareResult();
    }

    private void observeShareResult() {
        viewModel.getCloudShareResponse().observe(this, response -> latestShareResponse = response);

        viewModel.getShareSuccess().observe(this, success -> {
            if (success) {
                saveShareRecordAfterCloudSuccess();
                hideLoading();
                Toast.makeText(this, "分享成功", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                hideLoading();
                Toast.makeText(this, "分享失败: " + error, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });
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

    private void loadContact(String selectedContactId) {
        new Thread(() -> {
            List<Contact> contacts = contactManager.getAllContacts();
            for (Contact contact : contacts) {
                if (selectedContactId.equals(contact.contactId)) {
                    selectedContact = contact;
                    runOnUiThread(() -> {
                        if (textContactName != null) {
                            textContactName.setText(
                                    contact.displayName != null && !contact.displayName.isEmpty()
                                            ? contact.displayName
                                            : contact.username
                            );
                        }
                    });
                    break;
                }
            }
        }).start();
    }

    private void authenticateAndShare() {
        if (selectedContact == null) {
            Toast.makeText(this, "请先选择联系人", Toast.LENGTH_SHORT).show();
            return;
        }
        if (passwordToShare == null) {
            Toast.makeText(this, "密码数据未加载", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean canUseBiometric = BiometricAuthHelper.isBiometricSupported(this)
                && BiometricAuthManager.getInstance(this).canUseBiometric();
        if (canUseBiometric) {
            showBiometricAuthAndShare();
        } else {
            showPasswordAuthAndShare();
        }
    }

    private void showBiometricAuthAndShare() {
        biometricAuthHelper.authenticate(new BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> generateShare());
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ShareActivity.this, "生物识别失败: " + error, Toast.LENGTH_SHORT).show();
                    showPasswordAuthAndShare();
                });
            }

            @Override
            public void onCancel() {
                runOnUiThread(() -> Toast.makeText(ShareActivity.this, "已取消分享", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showPasswordAuthAndShare() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_input, null);
        com.google.android.material.textfield.TextInputEditText passwordInput =
                dialogView.findViewById(R.id.passwordInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle("验证身份")
                .setMessage("请输入主密码以验证身份")
                .setView(dialogView)
                .setPositiveButton("确认", (dialog, which) -> {
                    if (passwordInput == null) return;
                    String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";
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
        if (selectedContact == null) {
            Toast.makeText(this, "请先选择联系人", Toast.LENGTH_SHORT).show();
            return;
        }
        if (passwordToShare == null) {
            Toast.makeText(this, "密码数据未加载", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!viewModel.isCloudLoggedIn()) {
            Toast.makeText(this, "请先登录云端服务", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedContact.cloudUserId == null || selectedContact.cloudUserId.isEmpty()) {
            Toast.makeText(this, "该联系人未绑定云端账号，无法云端分享", Toast.LENGTH_LONG).show();
            return;
        }

        checkSafetyNumberAndProceed(this::requestCloudShare);
    }

    private void checkSafetyNumberAndProceed(@NonNull Runnable onProceed) {
        new Thread(() -> {
            try {
                PublicKey receiverPublicKey = parsePublicKey(selectedContact.publicKey);
                java.security.KeyPair senderKeyPair = backendService.getSessionRsaKeyPair();
                if (senderKeyPair == null || senderKeyPair.getPublic() == null) {
                    runOnUiThread(() -> Toast.makeText(this, "会话未解锁", Toast.LENGTH_SHORT).show());
                    return;
                }
                PublicKey senderPublicKey = senderKeyPair.getPublic();

                boolean isVerified = safetyNumberManager.isVerified(selectedContact.username, receiverPublicKey);
                boolean publicKeyChanged = safetyNumberManager.hasPublicKeyChanged(selectedContact.username, receiverPublicKey);

                runOnUiThread(() -> {
                    if (publicKeyChanged || !isVerified) {
                        SafetyNumberVerificationDialog.show(
                                this,
                                selectedContact,
                                receiverPublicKey,
                                senderPublicKey,
                                new SafetyNumberVerificationDialog.Callback() {
                                    @Override
                                    public void onVerified() {
                                        onProceed.run();
                                    }

                                    @Override
                                    public void onNotMatch() {
                                        Toast.makeText(
                                                ShareActivity.this,
                                                "安全码不匹配，分享已取消。请通过其他渠道确认。",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }

                                    @Override
                                    public void onSkip() {
                                        onProceed.run();
                                    }
                                }
                        );
                    } else {
                        onProceed.run();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "安全码校验失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    onProceed.run();
                });
            }
        }).start();
    }

    private void requestCloudShare() {
        if (selectedContact == null) {
            return;
        }
        pendingSharePermission = new SharePermission(true, true, true);
        pendingShareExpireAt = getExpireTime();
        latestShareResponse = null;

        showLoading("正在创建云端分享...");
        viewModel.createCloudShare(
                passwordId,
                selectedContact.cloudUserId,
                getExpireMinutes(),
                pendingSharePermission
        );
    }

    private void saveShareRecordAfterCloudSuccess() {
        if (selectedContact == null || pendingSharePermission == null) {
            return;
        }

        String payload = "";
        if (latestShareResponse != null && latestShareResponse.getShareToken() != null) {
            payload = latestShareResponse.getShareToken();
        }

        recordManager.createShareRecord(
                passwordId,
                "sent",
                selectedContact.contactId,
                payload,
                pendingSharePermission,
                pendingShareExpireAt
        );
    }

    private void showLoading(@NonNull String message) {
        if (cardProgress != null) {
            cardProgress.setVisibility(View.VISIBLE);
        }
        if (progressIndicator != null) {
            progressIndicator.setVisibility(View.VISIBLE);
        }
        if (textStatus != null) {
            textStatus.setText(message);
        }
        if (btnShare != null) {
            btnShare.setEnabled(false);
        }
    }

    private void hideLoading() {
        if (cardProgress != null) {
            cardProgress.setVisibility(View.GONE);
        }
        if (progressIndicator != null) {
            progressIndicator.setVisibility(View.GONE);
        }
        if (btnShare != null) {
            btnShare.setEnabled(true);
        }
    }

    private int getExpireMinutes() {
        if (radio1Hour != null && radio1Hour.isChecked()) {
            return 60;
        }
        if (radio1Day != null && radio1Day.isChecked()) {
            return 60 * 24;
        }
        if (radio7Days != null && radio7Days.isChecked()) {
            return 60 * 24 * 7;
        }
        return 0;
    }

    private long getExpireTime() {
        long now = System.currentTimeMillis();
        if (radio1Hour != null && radio1Hour.isChecked()) {
            return now + TimeUnit.HOURS.toMillis(1);
        }
        if (radio1Day != null && radio1Day.isChecked()) {
            return now + TimeUnit.DAYS.toMillis(1);
        }
        if (radio7Days != null && radio7Days.isChecked()) {
            return now + TimeUnit.DAYS.toMillis(7);
        }
        return 0;
    }

    private PublicKey parsePublicKey(@NonNull String base64) throws Exception {
        byte[] keyBytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP);
        java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
        java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
