package com.ttt.safevault.ui.share;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.ttt.safevault.R;
import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.crypto.KeyDerivationManager;
import com.ttt.safevault.crypto.ShareEncryptionManager;
import com.ttt.safevault.databinding.ActivityReceiveShareBinding;
import com.ttt.safevault.dto.response.ReceivedShareResponse;
import com.ttt.safevault.model.EncryptedSharePacket;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.PasswordShare;
import com.ttt.safevault.model.ShareDataPacket;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.utils.NFCTransferManager;
import com.ttt.safevault.viewmodel.ReceiveShareViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 接收分享界面
 * 解密并显示分享的密码，支持保存到密码库
 *
 * 支持两种分享模式：
 * 1. 离线分享（端到端加密）：使用 RSA-OAEP 加密，接收方用自己的私钥解密
 * 2. 云端分享：通过后端服务器传输，支持直接链接、用户对用户、附近用户
 */
public class ReceiveShareActivity extends AppCompatActivity {

    private static final String TAG = "ReceiveShareActivity";
    private static final String EXTRA_SHARE_DATA = "share_data";
    private static final String EXTRA_QR_CONTENT = "qr_content";

    private ActivityReceiveShareBinding binding;
    private ReceiveShareViewModel viewModel;

    // 加密管理器
    private KeyDerivationManager keyManager;
    private ShareEncryptionManager encryptionManager;

    // 分享数据
    private String shareId;
    private ShareDataPacket decryptedData;  // 离线分享解密后的数据
    private EncryptedSharePacket encryptedPacket;  // 离线分享加密包
    private String senderUserId;  // 发送方用户ID

    // UI 状态
    private boolean isPasswordVisible = false;
    private boolean isCloudShare = false;  // 是否为云端分享
    private boolean isOfflineEncrypted = false;  // 是否为端到端加密的离线分享
    private String actualPassword = "";
    private NFCTransferManager nfcManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置FLAG_SECURE防止截屏 - 根据 SecurityConfig 设置决定
        com.ttt.safevault.security.SecurityConfig securityConfig =
            new com.ttt.safevault.security.SecurityConfig(this);
        if (securityConfig.isScreenshotProtectionEnabled()) {
            getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            );
        }

        binding = ActivityReceiveShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 初始化加密管理器
        keyManager = new KeyDerivationManager(this);
        encryptionManager = new ShareEncryptionManager();
        nfcManager = new NFCTransferManager(this);

        // 获取分享数据，支持多种方式：
        // 1. 通过Intent Extra传递：getStringExtra(EXTRA_SHARE_DATA) 或 EXTRA_QR_CONTENT
        // 2. 通过Intent Extra传递（兼容旧版）：getStringExtra("SHARE_ID") 或 "SHARE_TOKEN"
        // 3. 通过URI传递：safevault://share/{shareId} 或 safevault://offline/{data}
        // 4. 通过NFC传递
        String shareData = getIntent().getStringExtra(EXTRA_SHARE_DATA);
        String qrContent = getIntent().getStringExtra(EXTRA_QR_CONTENT);

        // 兼容旧版
        if (shareData == null) {
            shareData = getIntent().getStringExtra("SHARE_ID");
        }
        if (qrContent == null) {
            qrContent = getIntent().getStringExtra("SHARE_TOKEN");
        }

        // 如果没有Extra，尝试从 URI 中解析
        if (shareData == null && qrContent == null) {
            android.net.Uri uri = getIntent().getData();
            if (uri != null && "safevault".equals(uri.getScheme())) {
                if ("offline".equals(uri.getHost())) {
                    // 离线分享：safevault://offline/{data}
                    shareData = uri.getSchemeSpecificPart();
                    if (shareData.startsWith("//")) {
                        shareData = shareData.substring(2);
                    }
                } else if ("share".equals(uri.getHost())) {
                    // 在线分享：/shareId
                    String path = uri.getPath();
                    if (path != null && path.startsWith("/")) {
                        shareId = path.substring(1);
                    }
                }
            }
        }

        // 尝试从NFC Intent中获取数据
        if (shareData == null && qrContent == null) {
            shareData = handleNfcIntent(getIntent());
        }

        // 优先处理端到端加密的离线分享
        String encryptedContent = shareData != null ? shareData : qrContent;
        if (encryptedContent != null) {
            // 检查是否为端到端加密的离线分享（EncryptedSharePacket格式）
            if (isEncryptedSharePacket(encryptedContent)) {
                isOfflineEncrypted = true;
                isCloudShare = false;
                processEncryptedSharePacket(encryptedContent);
                return;
            }
        }

        // 如果不是端到端加密分享，处理云端分享
        if (shareId != null && !shareId.isEmpty()) {
            isCloudShare = true;
            setupViewModel();
            setupToolbar();
            setupViews();
            observeViewModel();
            viewModel.receiveCloudShare(shareId);
        } else {
            Toast.makeText(this, "分享链接无效", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * 检查是否为端到端加密的离线分享（EncryptedSharePacket格式）
     */
    private boolean isEncryptedSharePacket(String content) {
        try {
            // 尝试解析为Base64
            String decoded = new String(
                android.util.Base64.decode(content, android.util.Base64.URL_SAFE),
                "UTF-8"
            );
            // 检查是否包含EncryptedSharePacket的特征字段
            return decoded.contains("\"version\"") &&
                   decoded.contains("\"encryptedData\"") &&
                   decoded.contains("\"signature\"");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 处理端到端加密的离线分享
     */
    private void processEncryptedSharePacket(String encryptedContent) {
        setupToolbar();
        setupViews();

        // 显示加载状态
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.scrollView.setVisibility(View.GONE);

        // 在后台线程解密
        new Thread(() -> {
            try {
                // 解析加密包
                String json = new String(
                    android.util.Base64.decode(encryptedContent, android.util.Base64.URL_SAFE),
                    "UTF-8"
                );
                encryptedPacket = new Gson().fromJson(json, EncryptedSharePacket.class);

                if (encryptedPacket == null || !encryptedPacket.isValid()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "分享数据格式无效", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                // 检查过期
                if (encryptedPacket.isExpired()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "分享已过期", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                senderUserId = encryptedPacket.getSenderId();

                // 解密数据
                decryptShareData();

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "解析分享数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    /**
     * 解密分享数据（端到端加密）
     */
    private void decryptShareData() {
        try {
            // 获取主密码
            String masterPassword = getMasterPassword();
            String userEmail = getUserEmail();

            if (masterPassword == null) {
                runOnUiThread(() -> {
                    showError("请先解锁应用");
                });
                return;
            }

            // 获取自己的密钥对
            KeyPair keyPair = keyManager.deriveKeyPairFromMasterPassword(
                masterPassword,
                userEmail
            );

            // 注意：这里假设发送方公钥已经在加密包中或可以通过其他方式获取
            // 在实际实现中，可能需要从服务器获取发送方公钥
            // 暂时跳过签名验证，只进行解密
            decryptedData = encryptionManager.decryptShare(
                encryptedPacket.getEncryptedData(),
                keyPair.getPrivate()
            );

            if (decryptedData == null) {
                runOnUiThread(() -> {
                    showError("解密失败");
                });
                return;
            }

            // 在主线程显示数据
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.scrollView.setVisibility(View.VISIBLE);
                displayDecryptedPasswordData();
            });

        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> {
                showError("解密失败: " + e.getMessage());
            });
        }
    }

    /**
     * 显示解密后的密码数据
     */
    private void displayDecryptedPasswordData() {
        if (decryptedData == null || decryptedData.password == null) {
            return;
        }

        PasswordItem password = decryptedData.password;

        binding.textTitle.setText(password.getTitle() != null ? password.getTitle() : "无标题");
        binding.textUsername.setText(password.getUsername() != null ? password.getUsername() : "无用户名");

        // 保存实际密码
        actualPassword = password.getPassword() != null ? password.getPassword() : "";
        binding.textPassword.setText("••••••••");

        // URL
        if (password.getUrl() != null && !password.getUrl().isEmpty()) {
            binding.labelUrl.setVisibility(View.VISIBLE);
            binding.textUrl.setVisibility(View.VISIBLE);
            binding.textUrl.setText(password.getUrl());
        } else {
            binding.labelUrl.setVisibility(View.GONE);
            binding.textUrl.setVisibility(View.GONE);
        }

        // 显示分享者
        binding.textSharer.setText(senderUserId != null ? senderUserId : "未知用户");

        // 显示过期时间
        if (decryptedData.expireAt > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            String expireDate = sdf.format(new Date(decryptedData.expireAt));
            long remainingDays = (decryptedData.expireAt - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
            binding.textExpireTime.setText("有效期：" + remainingDays + "天后过期 (" + expireDate + ")");
        } else {
            binding.textExpireTime.setText("有效期：永久有效");
        }

        // 显示权限
        SharePermission permission = decryptedData.permission;
        if (permission != null) {
            StringBuilder permText = new StringBuilder("权限：");
            if (permission.isCanView()) {
                permText.append("可查看");
            }
            if (permission.isCanSave()) {
                permText.append("、可保存");
            }
            if (permission.isRevocable()) {
                permText.append("、可撤销");
            }
            binding.textPermissions.setText(permText.toString());

            // 根据权限控制保存按钮
            binding.btnSaveToLocal.setEnabled(permission.isCanSave());
        }

        // 显示分享类型
        binding.textShareType.setVisibility(View.VISIBLE);
        binding.textShareType.setText("分享方式：端到端加密（离线）");
    }

    /**
     * 获取主密码
     */
    private String getMasterPassword() {
        try {
            return ServiceLocator.getInstance()
                .getCryptoManager()
                .getMasterPassword();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取用户邮箱
     */
    private String getUserEmail() {
        return getSharedPreferences("backend_prefs", MODE_PRIVATE)
            .getString("user_email", "user@example.com");
    }

    /**
     * 显示错误信息
     */
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void setupViewModel() {
        ViewModelFactory factory = new ViewModelFactory(
            getApplication(),
            ServiceLocator.getInstance().getBackendService()
        );
        viewModel = new ViewModelProvider(this, factory).get(ReceiveShareViewModel.class);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupViews() {
        // 密码显示/隐藏切换
        binding.btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());

        // 拒绝按钮
        binding.btnReject.setOnClickListener(v -> {
            Toast.makeText(this, "已拒绝接收", Toast.LENGTH_SHORT).show();
            finish();
        });

        // 保存到本地按钮
        binding.btnSaveToLocal.setOnClickListener(v -> saveToLocal());
    }

    private void observeViewModel() {
        // 观察加载状态
        viewModel.isLoading.observe(this, isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (!isLoading) {
                binding.scrollView.setVisibility(View.VISIBLE);
            }
        });

        // 观察错误信息
        viewModel.errorMessage.observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewModel.clearError();
                finish();
            }
        });

        // 观察分享的密码
        viewModel.sharedPassword.observe(this, passwordItem -> {
            if (passwordItem != null) {
                displayPasswordItem(passwordItem);
            }
        });

        // 观察分享详情（离线分享）
        viewModel.shareDetails.observe(this, shareDetails -> {
            if (shareDetails != null) {
                displayShareDetails(shareDetails);
            }
        });

        // 观察云端分享详情
        viewModel.cloudShareDetails.observe(this, cloudShareResponse -> {
            if (cloudShareResponse != null) {
                displayCloudShareDetails(cloudShareResponse);
            }
        });

        // 观察保存成功
        viewModel.saveSuccess.observe(this, success -> {
            if (success) {
                Toast.makeText(this, "已保存到密码库", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void displayPasswordItem(PasswordItem item) {
        binding.textTitle.setText(item.getTitle() != null ? item.getTitle() : "无标题");
        binding.textUsername.setText(item.getUsername() != null ? item.getUsername() : "无用户名");
        
        // 保存实际密码
        actualPassword = item.getPassword() != null ? item.getPassword() : "";
        binding.textPassword.setText("••••••••");

        // URL
        if (item.getUrl() != null && !item.getUrl().isEmpty()) {
            binding.labelUrl.setVisibility(View.VISIBLE);
            binding.textUrl.setVisibility(View.VISIBLE);
            binding.textUrl.setText(item.getUrl());
        } else {
            binding.labelUrl.setVisibility(View.GONE);
            binding.textUrl.setVisibility(View.GONE);
        }
    }

    private void displayShareDetails(PasswordShare share) {
        // 显示分享者（这里需要从UserProfile获取，暂时显示ID）
        binding.textSharer.setText(share.getFromUserId() != null ? 
            share.getFromUserId() : "未知用户");

        // 显示过期时间
        if (share.getExpireTime() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            String expireDate = sdf.format(new Date(share.getExpireTime()));
            long remainingDays = (share.getExpireTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
            binding.textExpireTime.setText("有效期：" + remainingDays + "天后过期 (" + expireDate + ")");
        } else {
            binding.textExpireTime.setText("有效期：永久有效");
        }

        // 显示权限
        SharePermission permission = share.getPermission();
        if (permission != null) {
            StringBuilder permText = new StringBuilder("权限：");
            if (permission.isCanView()) {
                permText.append("可查看");
            }
            if (permission.isCanSave()) {
                permText.append("、可保存");
            }
            if (permission.isRevocable()) {
                permText.append("、可撤销");
            }
            binding.textPermissions.setText(permText.toString());

            // 根据权限控制保存按钮
            binding.btnSaveToLocal.setEnabled(permission.isCanSave());
        }
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            binding.textPassword.setText(actualPassword);
            binding.btnTogglePassword.setText("隐藏");
        } else {
            binding.textPassword.setText("••••••••");
            binding.btnTogglePassword.setText("显示");
        }
    }

    private void saveToLocal() {
        // 端到端加密的离线分享
        if (isOfflineEncrypted && decryptedData != null) {
            saveDecryptedPassword();
            return;
        }

        // 云端分享
        if (shareId != null && isCloudShare) {
            viewModel.saveCloudShare(shareId);
        }
    }

    /**
     * 保存解密后的密码（端到端加密分享）
     */
    private void saveDecryptedPassword() {
        if (decryptedData == null || decryptedData.password == null) {
            return;
        }

        if (!decryptedData.permission.isCanSave()) {
            Toast.makeText(this, "此分享不允许保存", Toast.LENGTH_SHORT).show();
            return;
        }

        // 通过 BackendService 保存密码
        try {
            com.ttt.safevault.model.BackendService backendService =
                ServiceLocator.getInstance().getBackendService();

            PasswordItem password = decryptedData.password;
            int newId = backendService.addPassword(
                password.getTitle(),
                password.getUsername(),
                password.getPassword(),
                password.getUrl(),
                password.getNotes()
            );

            if (newId > 0) {
                Toast.makeText(this, "密码已保存", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
    
    /**
     * 处理NFC Intent
     * @param intent 从 NFC 获取的 Intent
     * @return 解析出的分享数据，失败返回null
     */
    private String handleNfcIntent(Intent intent) {
        String action = intent.getAction();
        
        // 检查是否是NDEF发现事件
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMessages != null && rawMessages.length > 0) {
                NdefMessage message = (NdefMessage) rawMessages[0];
                String data = nfcManager.extractDataFromMessage(message);
                
                if (data != null && !data.isEmpty()) {
                    Toast.makeText(this, R.string.nfc_read_success, Toast.LENGTH_SHORT).show();
                    return data;
                } else {
                    Toast.makeText(this, R.string.nfc_read_failed, Toast.LENGTH_SHORT).show();
                }
            }
        }
        
        return null;
    }

    /**
     * 显示云端分享详情
     */
    private void displayCloudShareDetails(ReceivedShareResponse response) {
        // 显示密码信息
        binding.textTitle.setText(response.getTitle() != null ? response.getTitle() : "无标题");
        binding.textUsername.setText(response.getUsername() != null ? response.getUsername() : "无用户名");
        
        // 保存实际密码
        actualPassword = response.getDecryptedPassword() != null ? response.getDecryptedPassword() : "";
        binding.textPassword.setText("••••••••");

        // URL
        if (response.getUrl() != null && !response.getUrl().isEmpty()) {
            binding.labelUrl.setVisibility(View.VISIBLE);
            binding.textUrl.setVisibility(View.VISIBLE);
            binding.textUrl.setText(response.getUrl());
        } else {
            binding.labelUrl.setVisibility(View.GONE);
            binding.textUrl.setVisibility(View.GONE);
        }

        // 显示分享者
        binding.textSharer.setText(response.getFromUserDisplayName() != null ? 
            response.getFromUserDisplayName() : response.getFromUserId());

        // 显示过期时间
        if (response.getExpireTime() != null && response.getExpireTime() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            String expireDate = sdf.format(new Date(response.getExpireTime()));
            long remainingDays = (response.getExpireTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
            binding.textExpireTime.setText("有效期：" + remainingDays + "天后过期 (" + expireDate + ")");
        } else {
            binding.textExpireTime.setText("有效期：永久有效");
        }

        // 显示权限
        SharePermission permission = response.getPermission();
        if (permission != null) {
            StringBuilder permText = new StringBuilder("权限：");
            if (permission.isCanView()) {
                permText.append("可查看");
            }
            if (permission.isCanSave()) {
                permText.append("、可保存");
            }
            if (permission.isRevocable()) {
                permText.append("、可撤销");
            }
            binding.textPermissions.setText(permText.toString());

            // 根据权限控制保存按钮
            binding.btnSaveToLocal.setEnabled(permission.isCanSave());
        }

        // 显示分享类型
        String shareType = response.getShareType();
        if (shareType != null) {
            String typeText = "";
            switch (shareType) {
                case "DIRECT":
                    typeText = "直接分享";
                    break;
                case "USER_TO_USER":
                    typeText = "用户对用户";
                    break;
                case "NEARBY":
                    typeText = "附近用户";
                    break;
            }
            if (!typeText.isEmpty()) {
                binding.textShareType.setVisibility(View.VISIBLE);
                binding.textShareType.setText("分享方式：" + typeText);
            }
        }
    }
}
