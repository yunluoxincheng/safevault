package com.ttt.safevault.ui.share;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.ttt.safevault.R;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.security.SafetyNumberManager;

import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 安全码详情页面
 * 显示完整的安全码信息和验证历史
 */
public class SafetyNumberDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CONTACT_USERNAME = "contact_username";
    public static final String EXTRA_CONTACT_DISPLAY_NAME = "contact_display_name";
    public static final String EXTRA_CONTACT_PUBLIC_KEY = "contact_public_key";

    private SafetyNumberManager safetyNumberManager;
    private String contactUsername;
    private String contactDisplayName;
    private PublicKey publicKey;

    private TextView textContactName;
    private TextView textShortFingerprint;
    private TextView textFullFingerprint;
    private TextView textVerificationStatus;
    private TextView textVerificationTime;
    private MaterialButton btnCopyShort;
    private MaterialButton btnCopyFull;
    private MaterialButton btnVerify;
    private MaterialButton btnShare;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safety_number_detail);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("安全码详情");
        }

        safetyNumberManager = SafetyNumberManager.getInstance(this);

        // 获取传入的数据
        Intent intent = getIntent();
        contactUsername = intent.getStringExtra(EXTRA_CONTACT_USERNAME);
        contactDisplayName = intent.getStringExtra(EXTRA_CONTACT_DISPLAY_NAME);
        String publicKeyBase64 = intent.getStringExtra(EXTRA_CONTACT_PUBLIC_KEY);

        if (contactUsername == null || publicKeyBase64 == null) {
            Toast.makeText(this, "缺少必要参数", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 解析公钥
        try {
            byte[] keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP);
            java.security.spec.X509EncodedKeySpec spec =
                new java.security.spec.X509EncodedKeySpec(keyBytes);
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA");
            publicKey = factory.generatePublic(spec);
        } catch (Exception e) {
            Toast.makeText(this, "公钥解析失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        displaySafetyNumberInfo();
    }

    private void initViews() {
        textContactName = findViewById(R.id.textContactName);
        textShortFingerprint = findViewById(R.id.textShortFingerprint);
        textFullFingerprint = findViewById(R.id.textFullFingerprint);
        textVerificationStatus = findViewById(R.id.textVerificationStatus);
        textVerificationTime = findViewById(R.id.textVerificationTime);
        btnCopyShort = findViewById(R.id.btnCopyShort);
        btnCopyFull = findViewById(R.id.btnCopyFull);
        btnVerify = findViewById(R.id.btnVerify);
        btnShare = findViewById(R.id.btnShare);

        // 设置联系人名称
        if (textContactName != null) {
            String displayName = (contactDisplayName != null && !contactDisplayName.isEmpty())
                ? contactDisplayName
                : contactUsername;
            textContactName.setText(displayName);
        }

        // 复制短指纹按钮
        if (btnCopyShort != null) {
            btnCopyShort.setOnClickListener(v -> {
                String shortCode = safetyNumberManager.generateShortFingerprint(publicKey);
                copyToClipboard("安全码（短）", shortCode);
            });
        }

        // 复制完整指纹按钮
        if (btnCopyFull != null) {
            btnCopyFull.setOnClickListener(v -> {
                String fullCode = safetyNumberManager.generateFullFingerprint(publicKey);
                // 去除空格后复制
                String codeWithoutSpaces = fullCode.replace(" ", "");
                copyToClipboard("安全码（完整）", codeWithoutSpaces);
            });
        }

        // 验证按钮
        if (btnVerify != null) {
            btnVerify.setOnClickListener(v -> {
                safetyNumberManager.markAsVerified(contactUsername, publicKey);
                Toast.makeText(this, "已验证", Toast.LENGTH_SHORT).show();
                displaySafetyNumberInfo();
            });
        }

        // 分享按钮
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                shareSafetyNumber();
            });
        }
    }

    private void displaySafetyNumberInfo() {
        // 生成指纹
        String shortFingerprint = safetyNumberManager.generateShortFingerprint(publicKey);
        String fullFingerprint = safetyNumberManager.generateFullFingerprint(publicKey);

        // 显示短指纹
        if (textShortFingerprint != null) {
            textShortFingerprint.setText(shortFingerprint);
        }

        // 显示完整指纹
        if (textFullFingerprint != null) {
            textFullFingerprint.setText(fullFingerprint);
        }

        // 检查验证状态
        boolean isVerified = safetyNumberManager.isVerified(contactUsername, publicKey);
        boolean publicKeyChanged = safetyNumberManager.hasPublicKeyChanged(contactUsername, publicKey);

        // 更新验证状态
        if (textVerificationStatus != null) {
            if (publicKeyChanged) {
                textVerificationStatus.setText("⚠️ 公钥已变化（需要重新验证）");
                textVerificationStatus.setTextColor(getColor(R.color.warning_color));
            } else if (isVerified) {
                textVerificationStatus.setText("✓ 已验证");
                textVerificationStatus.setTextColor(getColor(R.color.verified_color));
            } else {
                textVerificationStatus.setText("未验证");
                textVerificationStatus.setTextColor(getColor(R.color.unverified_color));
            }
        }

        // 显示验证时间
        if (textVerificationTime != null) {
            long verificationTime = safetyNumberManager.getVerificationTime(contactUsername);
            if (verificationTime > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                String timeStr = sdf.format(new Date(verificationTime));
                textVerificationTime.setText("验证时间: " + timeStr);
            } else {
                textVerificationTime.setText("尚未验证");
            }
        }

        // 更新验证按钮状态
        if (btnVerify != null) {
            if (isVerified && !publicKeyChanged) {
                btnVerify.setText("已验证");
                btnVerify.setEnabled(false);
            } else {
                btnVerify.setText("标记为已验证");
                btnVerify.setEnabled(true);
            }
        }
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(label, text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSafetyNumber() {
        String shortCode = safetyNumberManager.generateShortFingerprint(publicKey);
        String fullCode = safetyNumberManager.generateFullFingerprint(publicKey).replace(" ", "");

        String shareText = "SafeVault 安全码验证\n\n"
            + "用户: " + contactDisplayName + "\n\n"
            + "短安全码: " + shortCode + "\n\n"
            + "完整安全码:\n" + fullCode + "\n\n"
            + "请在 SafeVault 应用中验证此安全码。";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "分享安全码"));
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
