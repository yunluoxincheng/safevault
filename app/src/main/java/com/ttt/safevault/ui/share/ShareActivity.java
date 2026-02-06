package com.ttt.safevault.ui.share;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.crypto.KeyDerivationManager;
import com.ttt.safevault.crypto.ShareEncryptionManager;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.dto.request.CreateShareRequest;
import com.ttt.safevault.dto.response.ShareResponse;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.EncryptedSharePacket;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.ShareDataPacket;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.security.BiometricAuthHelper;
import com.ttt.safevault.security.biometric.BiometricAuthManager;
import com.ttt.safevault.service.manager.ContactManager;
import com.ttt.safevault.service.manager.ShareRecordManager;
import com.ttt.safevault.viewmodel.ShareViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 密码分享界面
 * 使用端到端加密将密码分享给联系人（云端分享）
 */
public class ShareActivity extends AppCompatActivity {
    private static final String TAG = "ShareActivity";

    public static final String EXTRA_PASSWORD_ID = "password_id";
    public static final String EXTRA_CONTACT_ID = "contact_id";

    // 使用 ActivityResultLauncher 替代 startActivityForResult
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
    private ShareEncryptionManager encryptionManager;
    private ShareRecordManager recordManager;

    private TextView textContactName;
    private TextView textUsername;
    private RadioGroup groupExpireTime;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_e2e);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("分享密码");
        }

        // 获取传入的密码ID和联系人ID
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
        encryptionManager = new ShareEncryptionManager();
        recordManager = new ShareRecordManager(this);
    }

    private void initViews() {
        textContactName = findViewById(R.id.textContactName);
        textUsername = findViewById(R.id.textUsername);
        groupExpireTime = findViewById(R.id.groupExpireTime);
        radio1Hour = findViewById(R.id.radio1Hour);
        radio1Day = findViewById(R.id.radio1Day);
        radio7Days = findViewById(R.id.radio7Days);
        radioNever = findViewById(R.id.radioNever);
        progressIndicator = findViewById(R.id.progressIndicator);
        cardProgress = findViewById(R.id.cardProgress);
        textStatus = findViewById(R.id.textStatus);
        btnShare = findViewById(R.id.btnShare);
        btnSelectContact = findViewById(R.id.btnSelectContact);

        // 默认选中7天
        if (radio7Days != null) {
            radio7Days.setChecked(true);
        }

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> authenticateAndShare());
        }

        if (btnSelectContact != null) {
            btnSelectContact.setOnClickListener(v -> {
                // 跳转到联系人列表
                Intent intent = new Intent(this, ContactListActivity.class);
                contactListLauncher.launch(intent);
            });
        }

        // 观察分享结果
        observeShareResult();
    }

    /**
     * 观察云端分享结果
     */
    private void observeShareResult() {
        viewModel.getShareSuccess().observe(this, success -> {
            if (success) {
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

    /**
     * 显示加载状态
     */
    private void showLoading(String message) {
        if (cardProgress != null) {
            cardProgress.setVisibility(View.VISIBLE);
        }
        if (textStatus != null) {
            textStatus.setText(message);
        }
        if (btnShare != null) {
            btnShare.setEnabled(false);
        }
    }

    /**
     * 隐藏加载状态
     */
    private void hideLoading() {
        if (cardProgress != null) {
            cardProgress.setVisibility(View.GONE);
        }
        if (btnShare != null) {
            btnShare.setEnabled(true);
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

    private void loadContact(String contactId) {
        // 在后台线程查询联系人数据，避免主线程访问数据库异常
        new Thread(() -> {
            List<Contact> contacts = contactManager.getAllContacts();
            for (Contact contact : contacts) {
                if (contact.contactId.equals(contactId)) {
                    selectedContact = contact;
                    runOnUiThread(() -> {
                        if (textContactName != null) {
                            textContactName.setText(contact.displayName != null && !contact.displayName.isEmpty()
                                ? contact.displayName
                                : contact.username);
                        }
                    });
                    break;
                }
            }
        }).start();
    }

    /**
     * 验证用户身份后执行分享
     */
    private void authenticateAndShare() {
        if (selectedContact == null) {
            Toast.makeText(this, "请先选择联系人", Toast.LENGTH_SHORT).show();
            return;
        }

        if (passwordToShare == null) {
            Toast.makeText(this, "密码数据未加载", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否可以使用生物识别
        boolean canUseBiometric = BiometricAuthHelper.isBiometricSupported(this)
                && BiometricAuthManager.getInstance(this).canUseBiometric();

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
                    Toast.makeText(ShareActivity.this,
                        "生物识别验证失败: " + error, Toast.LENGTH_SHORT).show();
                    showPasswordAuthAndShare();
                });
            }

            @Override
            public void onCancel() {
                runOnUiThread(() ->
                    Toast.makeText(ShareActivity.this, "已取消分享", Toast.LENGTH_SHORT).show()
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
        if (selectedContact == null) {
            Toast.makeText(this, "请先选择联系人", Toast.LENGTH_SHORT).show();
            return;
        }

        if (passwordToShare == null) {
            Toast.makeText(this, "密码数据未加载", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否已登录云端
        if (!viewModel.isCloudLoggedIn()) {
            Toast.makeText(this, "请先登录云端服务", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查联系人是否有云端用户ID
        if (selectedContact.cloudUserId == null || selectedContact.cloudUserId.isEmpty()) {
            Toast.makeText(this, "该联系人未绑定云端账号，无法进行云端分享", Toast.LENGTH_LONG).show();
            return;
        }

        // 获取主密码和用户邮箱
        String masterPassword = getMasterPassword();
        String userEmail = getUserEmail();

        if (masterPassword == null) {
            Toast.makeText(this, "请先解锁应用", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userEmail == null) {
            Toast.makeText(this, "无法获取用户信息", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("正在加密并创建云端分享...");

        // 在后台线程执行加密和云端分享
        new Thread(() -> {
            try {
                KeyDerivationManager keyManager = new KeyDerivationManager(this);

                // 1. 获取接收方公钥
                String receiverPublicKeyBase64 = selectedContact.publicKey;
                PublicKey receiverPublicKey = parsePublicKey(receiverPublicKeyBase64);

                // 2. 获取发送方密钥对
                KeyPair senderKeyPair = keyManager.deriveKeyPairFromMasterPassword(
                    masterPassword,
                    userEmail
                );

                // 3. 创建分享数据包
                ShareDataPacket dataPacket = new ShareDataPacket();
                dataPacket.version = "1.0";
                dataPacket.senderId = keyManager.generateUserId(userEmail);
                dataPacket.senderPublicKey = Base64.encodeToString(
                    senderKeyPair.getPublic().getEncoded(),
                    Base64.NO_WRAP
                );
                dataPacket.createdAt = System.currentTimeMillis();
                dataPacket.expireAt = getExpireTime();
                dataPacket.permission = new SharePermission(true, true, true);
                dataPacket.password = passwordToShare;

                // 4. 创建加密包
                EncryptedSharePacket encryptedPacket = encryptionManager.createEncryptedPacket(
                    dataPacket,
                    receiverPublicKey,
                    senderKeyPair.getPrivate()
                );

                if (encryptedPacket == null) {
                    runOnUiThread(() -> {
                        hideLoading();
                        Toast.makeText(this, "加密失败", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // 5. 创建云端分享请求
                CreateShareRequest request = new CreateShareRequest();
                request.setPasswordId(String.valueOf(passwordId));
                request.setToUserId(selectedContact.cloudUserId);
                request.setEncryptedPassword(encryptedPacket.getEncryptedData());
                request.setTitle(passwordToShare.getTitle());
                request.setUsername(passwordToShare.getUsername());
                request.setUrl(passwordToShare.getUrl());
                request.setNotes(passwordToShare.getNotes());
                request.setExpireInMinutes(getExpireMinutes());
                request.setPermission(dataPacket.permission);

                // 6. 调用云端API创建分享（必须在主线程调用ViewModel）
                int expireMinutes = getExpireMinutes();
                runOnUiThread(() -> {
                    viewModel.createCloudShare(
                        passwordId,
                        selectedContact.cloudUserId,
                        expireMinutes,
                        dataPacket.permission
                    );
                });

                // 7. 保存本地分享记录
                recordManager.createShareRecord(
                    passwordId,
                    "sent",
                    selectedContact.contactId,
                    encryptedPacket.getEncryptedData(),
                    dataPacket.permission,
                    dataPacket.expireAt
                );

            } catch (Exception e) {
                Log.e(TAG, "创建云端分享失败", e);
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(this, "分享失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * 获取过期时间（分钟）
     */
    private int getExpireMinutes() {
        if (radio1Hour != null && radio1Hour.isChecked()) {
            return 60;
        } else if (radio1Day != null && radio1Day.isChecked()) {
            return 60 * 24;
        } else if (radio7Days != null && radio7Days.isChecked()) {
            return 60 * 24 * 7;
        } else {
            return 0; // 永不过期
        }
    }

    private long getExpireTime() {
        long now = System.currentTimeMillis();

        if (radio1Hour != null && radio1Hour.isChecked()) {
            return now + TimeUnit.HOURS.toMillis(1);
        } else if (radio1Day != null && radio1Day.isChecked()) {
            return now + TimeUnit.DAYS.toMillis(1);
        } else if (radio7Days != null && radio7Days.isChecked()) {
            return now + TimeUnit.DAYS.toMillis(7);
        } else {
            return 0; // 永不过期
        }
    }

    private PublicKey parsePublicKey(String base64) throws Exception {
        byte[] keyBytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP);
        java.security.spec.X509EncodedKeySpec spec =
            new java.security.spec.X509EncodedKeySpec(keyBytes);
        java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }

    private String getMasterPassword() {
        // 从 CryptoManager 获取主密码
        // 简化实现：假设已经通过生物识别或主密码验证解锁
        return backendService.getMasterPassword();
    }

    private String getUserEmail() {
        // 从 SharedPreferences 获取用户邮箱
        return getSharedPreferences("backend_prefs", MODE_PRIVATE)
            .getString("user_email", null);
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
