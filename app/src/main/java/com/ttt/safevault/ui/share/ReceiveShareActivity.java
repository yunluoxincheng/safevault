package com.ttt.safevault.ui.share;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import com.ttt.safevault.crypto.ShareEncryptionManager;
import com.ttt.safevault.databinding.ActivityReceiveShareBinding;
import com.ttt.safevault.dto.response.ReceivedShareResponse;
import com.ttt.safevault.model.EncryptedSharePacket;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.PasswordShare;
import com.ttt.safevault.model.ShareDataPacket;
import com.ttt.safevault.model.SharePermission;
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
 *
 * 注意：NFC 功能已移除，因为 Android 10+ 不支持点对点传输
 */
public class ReceiveShareActivity extends AppCompatActivity {

    private static final String TAG = "ReceiveShareActivity";
    private static final String EXTRA_SHARE_DATA = "share_data";
    private static final String EXTRA_QR_CONTENT = "qr_content";

    private ActivityReceiveShareBinding binding;
    private ReceiveShareViewModel viewModel;

    // 加密管理器
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
        encryptionManager = new ShareEncryptionManager();

        // 获取分享数据，支持多种方式：
        // 1. 通过Intent Extra传递：getStringExtra(EXTRA_SHARE_DATA) 或 EXTRA_QR_CONTENT
        // 2. 通过Intent Extra传递（兼容旧版）：getStringExtra("SHARE_ID") 或 "SHARE_TOKEN"
        // 3. 通过URI传递：safevault://share/{shareId} 或 safevault://offline/{data}
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
     * 支持版本 1.0 和 2.0
     */
    private boolean isEncryptedSharePacket(String content) {
        try {
            // 尝试解析为Base64
            String decoded = new String(
                android.util.Base64.decode(content, android.util.Base64.URL_SAFE),
                "UTF-8"
            );
            // 检查是否包含EncryptedSharePacket的特征字段
            boolean hasBasicFields = decoded.contains("\"version\"") &&
                   decoded.contains("\"encryptedData\"") &&
                   decoded.contains("\"signature\"");

            // 版本 2.0 还包含 encryptedAESKey 和 iv 字段
            boolean isV2 = decoded.contains("\"encryptedAESKey\"") &&
                    decoded.contains("\"iv\"");

            return hasBasicFields && (isV2 || decoded.contains("\"1.0\""));
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
                Log.e(TAG, "解析分享数据失败", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "解析分享数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    /**
     * 解密分享数据（端到端加密，混合方案版本 2.0）
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

            // 获取自己的密钥对（接收方）- SafeVault 3.4.0：使用 SecureKeyStorageManager
            com.ttt.safevault.security.SecureKeyStorageManager secureStorage =
                com.ttt.safevault.security.SecureKeyStorageManager.getInstance(this);
            com.ttt.safevault.security.CryptoSession cryptoSession =
                com.ttt.safevault.security.CryptoSession.getInstance();

            if (!cryptoSession.isUnlocked()) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.scrollView.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "会话未锁定", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            javax.crypto.SecretKey dataKey = cryptoSession.getDataKey();
            java.security.PrivateKey privateKey = secureStorage.decryptRsaPrivateKey(dataKey);
            java.security.PublicKey publicKey = secureStorage.getRsaPublicKey();

            if (privateKey == null || publicKey == null) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.scrollView.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "无法获取密钥对", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            KeyPair receiverKeyPair = new java.security.KeyPair(publicKey, privateKey);

            // 从解密后的数据中获取发送方公钥
            // 由于新版本使用混合加密，我们需要使用 openEncryptedPacket() 方法
            // 但发送方公钥需要从加密包的元数据或从服务器获取
            // 这里我们先尝试解密，然后从解密后的数据中提取发送方公钥进行签名验证

            // 版本 2.0 使用 openEncryptedPacket() 方法
            String version = encryptedPacket.getVersion();
            if ("2.0".equals(version)) {
                // 新版本混合加密方案
                // 首先需要获取发送方公钥
                // 在实际实现中，发送方公钥可能需要从服务器获取
                // 这里我们从加密包中获取发送方ID，然后通过其他方式获取公钥

                // 由于发送方公钥无法直接从加密包中获取（它是加密数据的一部分），
                // 我们需要先解密数据，然后从 ShareDataPacket 中提取发送方公钥
                // 但是验证签名需要发送方公钥，这是一个两步过程：
                // 1. 先解密数据（不验证签名）
                // 2. 从数据中提取发送方公钥，然后验证签名

                // 临时方案：使用两步解密法
                // 注意：这在安全上不是最佳实践，生产环境应该从可信渠道获取发送方公钥
                decryptedData = openEncryptedPacketWithoutSignature(
                    encryptedPacket,
                    receiverKeyPair.getPrivate()
                );

                if (decryptedData == null) {
                    runOnUiThread(() -> {
                        showError("解密失败");
                    });
                    return;
                }

                // 从解密后的数据中提取发送方公钥
                PublicKey senderPublicKey = parsePublicKey(decryptedData.senderPublicKey);

                // 现在验证签名
                if (!encryptionManager.verifySignature(
                        decryptedData,
                        encryptedPacket.getSignature(),
                        senderPublicKey)) {
                    runOnUiThread(() -> {
                        showError("签名验证失败，数据可能被篡改");
                    });
                    return;
                }
            } else {
                // 旧版本兼容（使用纯 RSA 加密）
                decryptedData = encryptionManager.decryptShare(
                    encryptedPacket.getEncryptedData(),
                    receiverKeyPair.getPrivate()
                );

                if (decryptedData == null) {
                    runOnUiThread(() -> {
                        showError("解密失败");
                    });
                    return;
                }
            }

            // 在主线程显示数据
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.scrollView.setVisibility(View.VISIBLE);
                displayDecryptedPasswordData();
            });

        } catch (Exception e) {
            Log.e(TAG, "解密分享数据失败", e);
            runOnUiThread(() -> {
                showError("解密失败: " + e.getMessage());
            });
        }
    }

    /**
     * 解开加密包但不验证签名（用于获取发送方公钥）
     * 这是版本 2.0 混合加密方案的临时解决方案
     *
     * 注意：这不是最安全的做法，生产环境应该从可信渠道（如服务器）获取发送方公钥
     */
    private ShareDataPacket openEncryptedPacketWithoutSignature(
            EncryptedSharePacket packet,
            PrivateKey receiverPrivateKey
    ) {
        try {
            // 检查版本
            String version = packet.getVersion();
            if (!"2.0".equals(version)) {
                return null; // 不支持的版本
            }

            // 解密 AES 密钥
            String encryptedAESKey = packet.getEncryptedAESKey();
            if (encryptedAESKey == null || encryptedAESKey.isEmpty()) {
                return null;
            }

            // 这里需要调用 ShareEncryptionManager 的私有方法
            // 由于方法是私有的，我们需要通过反射或者修改访问权限
            // 临时方案：在这里重新实现解密逻辑

            byte[] encryptedKeyBytes = android.util.Base64.decode(encryptedAESKey, android.util.Base64.NO_WRAP);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, receiverPrivateKey);
            byte[] aesKeyBytes = cipher.doFinal(encryptedKeyBytes);

            if (aesKeyBytes.length != 32) {
                return null;
            }

            javax.crypto.SecretKey aesKey = new javax.crypto.spec.SecretKeySpec(aesKeyBytes, "AES");

            // 解密数据
            String ivBase64 = packet.getIv();
            if (ivBase64 == null || ivBase64.isEmpty()) {
                return null;
            }
            byte[] iv = android.util.Base64.decode(ivBase64, android.util.Base64.NO_WRAP);

            String encryptedData = packet.getEncryptedData();
            byte[] encryptedDataBytes = android.util.Base64.decode(encryptedData, android.util.Base64.NO_WRAP);

            javax.crypto.Cipher dataCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec spec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            dataCipher.init(javax.crypto.Cipher.DECRYPT_MODE, aesKey, spec);
            byte[] decryptedBytes = dataCipher.doFinal(encryptedDataBytes);

            // 反序列化
            String json = new String(decryptedBytes, "UTF-8");
            return deserializeShareDataPacket(json);

        } catch (Exception e) {
            Log.e(TAG, "打开加密包失败", e);
            return null;
        }
    }

    /**
     * 解析公钥
     */
    private PublicKey parsePublicKey(String base64) throws Exception {
        byte[] keyBytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP);
        java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
        java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }

    /**
     * 从 JSON 反序列化 ShareDataPacket（简化版本）
     */
    private ShareDataPacket deserializeShareDataPacket(String json) {
        ShareDataPacket packet = new ShareDataPacket();

        // 解析基本字段
        packet.version = extractJsonString(json, "version");
        packet.senderId = extractJsonString(json, "senderId");
        packet.senderPublicKey = extractJsonString(json, "senderPublicKey");
        packet.createdAt = extractJsonLong(json, "createdAt");
        packet.expireAt = extractJsonLong(json, "expireAt");

        // 解析权限
        packet.permission = new SharePermission(
            extractJsonBoolean(json, "canView"),
            extractJsonBoolean(json, "canSave"),
            extractJsonBoolean(json, "isRevocable")
        );

        // 解析密码数据
        com.ttt.safevault.model.PasswordItem password = new com.ttt.safevault.model.PasswordItem();
        password.setTitle(extractJsonString(json, "title"));
        password.setUsername(extractJsonString(json, "username"));
        password.setPassword(extractJsonString(json, "password"));
        password.setUrl(extractJsonString(json, "url"));
        password.setNotes(extractJsonString(json, "notes"));
        packet.password = password;

        return packet;
    }

    // JSON 解析辅助方法
    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return "";
        startIndex += searchKey.length();

        int endIndex = startIndex;
        boolean escaped = false;
        while (endIndex < json.length()) {
            char c = json.charAt(endIndex);
            if (!escaped && c == '"') break;
            escaped = (c == '\\' && !escaped);
            endIndex++;
        }

        return json.substring(startIndex, endIndex)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private long extractJsonLong(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return 0;
        startIndex += searchKey.length();

        int endIndex = startIndex;
        while (endIndex < json.length()) {
            char c = json.charAt(endIndex);
            if (c == ',' || c == '}') break;
            endIndex++;
        }

        try {
            return Long.parseLong(json.substring(startIndex, endIndex).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean extractJsonBoolean(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return false;
        startIndex += searchKey.length();

        if (json.substring(startIndex).startsWith("true")) return true;
        return false;
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
                .getBackendService()
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
            Log.e(TAG, "保存分享密码失败", e);
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
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
