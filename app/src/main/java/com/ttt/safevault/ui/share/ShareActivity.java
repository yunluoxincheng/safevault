package com.ttt.safevault.ui.share;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.WriterException;
import com.ttt.safevault.R;
import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.crypto.KeyDerivationManager;
import com.ttt.safevault.crypto.ShareEncryptionManager;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.EncryptedSharePacket;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.ShareDataPacket;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.security.BiometricAuthHelper;
import com.ttt.safevault.service.manager.ContactManager;
import com.ttt.safevault.service.manager.ShareRecordManager;
import com.ttt.safevault.utils.QRCodeUtils;
import com.ttt.safevault.utils.ShareQRGenerator;
import com.ttt.safevault.viewmodel.ShareViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 密码分享界面
 * 使用端到端加密将密码分享给联系人
 */
public class ShareActivity extends AppCompatActivity {
    private static final String TAG = "ShareActivity";

    public static final String EXTRA_PASSWORD_ID = "password_id";
    public static final String EXTRA_CONTACT_ID = "contact_id";
    private static final int REQUEST_CODE_CONTACT_LIST = 100;

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
    private ImageView qrCodeImage;
    private Button btnShare;
    private Button btnSelectContact;

    private int passwordId;
    private String contactId;
    private Contact selectedContact;
    private PasswordItem passwordToShare;
    private String generatedQrContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

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
        qrCodeImage = findViewById(R.id.qrCodeImage);
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
                startActivityForResult(intent, REQUEST_CODE_CONTACT_LIST);
            });
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
        List<Contact> contacts = contactManager.getAllContacts();
        for (Contact contact : contacts) {
            if (contact.contactId.equals(contactId)) {
                selectedContact = contact;
                if (textContactName != null) {
                    textContactName.setText(contact.displayName != null && !contact.displayName.isEmpty()
                        ? contact.displayName
                        : contact.username);
                }
                break;
            }
        }
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

        // 在后台线程执行加密
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
                        Toast.makeText(this, "分享生成失败", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // 5. 生成QR码内容
                String packetJson = new com.google.gson.Gson().toJson(encryptedPacket);
                generatedQrContent = "safevault://share/" + Base64.encodeToString(
                    packetJson.getBytes(),
                    Base64.NO_WRAP | Base64.URL_SAFE
                );

                // 6. 保存分享记录
                recordManager.createShareRecord(
                    passwordId,
                    "sent",
                    selectedContact.contactId,
                    encryptedPacket.getEncryptedData(),
                    dataPacket.permission,
                    dataPacket.expireAt
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
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "分享生成失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
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

    private void displayQRCode(String content) {
        try {
            Bitmap qrBitmap = ShareQRGenerator.generateQRCode(content, 512);
            if (qrCodeImage != null) {
                qrCodeImage.setImageBitmap(qrBitmap);
                qrCodeImage.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "QR码生成失败", Toast.LENGTH_SHORT).show();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CONTACT_LIST && resultCode == RESULT_OK && data != null) {
            String selectedContactId = data.getStringExtra(ContactListActivity.EXTRA_CONTACT_ID);
            if (selectedContactId != null) {
                loadContact(selectedContactId);
            }
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
