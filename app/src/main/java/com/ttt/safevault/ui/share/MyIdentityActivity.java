package com.ttt.safevault.ui.share;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
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
    private String currentQRContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_identity);

        contactManager = new ContactManager(this);

        // 使用正确的构造函数创建 AccountManager
        com.ttt.safevault.crypto.CryptoManager cryptoManager =
            com.ttt.safevault.ServiceLocator.getInstance().getCryptoManager();
        com.ttt.safevault.data.PasswordDao passwordDao =
            com.ttt.safevault.data.AppDatabase.getInstance(this).passwordDao();
        com.ttt.safevault.service.manager.PasswordManager passwordManager =
            new com.ttt.safevault.service.manager.PasswordManager(cryptoManager, passwordDao);

        accountManager = new AccountManager(
                this,
                cryptoManager,
                passwordManager,
                new com.ttt.safevault.security.SecurityConfig(this),
                com.ttt.safevault.network.RetrofitClient.getInstance(this)
        );

        initViews();
        loadUserInfo();
        generateQRCode();
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
        // 从账户管理器获取当前用户信息
        String userEmail = accountManager.getCurrentUserEmail();
        if (userEmail != null) {
            textUsername.setText(userEmail);
            // 从邮箱提取显示名
            String displayName = userEmail.substring(0, userEmail.indexOf('@'));
            textDisplayName.setText(displayName);
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
                // 获取当前用户信息
                String userEmail = accountManager.getCurrentUserEmail();
                String masterPassword = accountManager.getCurrentMasterPassword();

                if (userEmail == null || masterPassword == null) {
                    runOnUiThread(() -> showError("未找到用户信息，请重新登录"));
                    return;
                }

                // 生成身份QR码内容
                currentQRContent = contactManager.generateMyIdentityQR(userEmail, masterPassword);

                if (currentQRContent == null) {
                    runOnUiThread(() -> showError("生成身份码失败"));
                    return;
                }

                // 生成QR码图片
                Bitmap qrBitmap = generateQRCodeBitmap(currentQRContent);

                if (qrBitmap == null) {
                    runOnUiThread(() -> showError("生成QR码图片失败"));
                    return;
                }

                // 在UI线程显示QR码
                runOnUiThread(() -> {
                    imageQrCode.setImageBitmap(qrBitmap);
                    imageQrCode.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.GONE);
                    btnShare.setEnabled(true);
                    btnRegenerate.setEnabled(true);
                });

            } catch (Exception e) {
                runOnUiThread(() -> showError("生成失败: " + e.getMessage()));
            }
        });
    }

    private Bitmap generateQRCodeBitmap(String content) {
        try {
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

            return bitmap;

        } catch (WriterException e) {
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
}
