package com.ttt.safevault.ui.share;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.ttt.safevault.R;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * NFC接收界面
 * 监听NFC标签并接收分享数据
 */
public class NFCReceiveActivity extends AppCompatActivity {

    private static final String TAG = "NFCReceiveActivity";

    private NfcAdapter nfcAdapter;
    private TextView textStatus;
    private View cardWaiting;
    private View cardReceived;
    private View cardError;
    private TextView textTitle;
    private TextView textUsername;
    private TextView textPassword;
    private TextView textUrl;
    private Button btnClose;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_receive);

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
            getSupportActionBar().setTitle("NFC接收");
        }

        initViews();
        setupNfc();
    }

    private void initViews() {
        textStatus = findViewById(R.id.textStatus);
        cardWaiting = findViewById(R.id.cardWaiting);
        cardReceived = findViewById(R.id.cardReceived);
        cardError = findViewById(R.id.cardError);
        textTitle = findViewById(R.id.textTitle);
        textUsername = findViewById(R.id.textUsername);
        textPassword = findViewById(R.id.textPassword);
        textUrl = findViewById(R.id.textUrl);
        btnClose = findViewById(R.id.btnClose);
        btnSave = findViewById(R.id.btnSave);

        btnClose.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveToLocal());
    }

    private void setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (nfcAdapter == null) {
            showError("此设备不支持NFC功能");
            return;
        }

        if (!nfcAdapter.isEnabled()) {
            showError("NFC未开启，请在系统设置中开启");
            return;
        }

        textStatus.setText("等待NFC设备靠近...");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (nfcAdapter != null) {
            Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            IntentFilter[] intentFiltersArray = new IntentFilter[]{
                new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
            };
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
            || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
            || NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {

            processNfcIntent(intent);
        }
    }

    private void processNfcIntent(Intent intent) {
        try {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag == null) {
                showError("无法读取NFC标签");
                return;
            }

            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                showError("NFC标签不支持NDEF格式");
                return;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();
            if (ndefMessage == null) {
                ndef.connect();
                ndefMessage = ndef.getNdefMessage();
                ndef.close();
            }

            if (ndefMessage == null) {
                showError("无法读取NFC数据");
                return;
            }

            byte[] payload = ndefMessage.getRecords()[0].getPayload();
            String data = new String(payload, StandardCharsets.UTF_8);

            processShareData(data);

        } catch (Exception e) {
            e.printStackTrace();
            showError("读取NFC数据失败: " + e.getMessage());
        }
    }

    private void processShareData(String data) {
        try {
            Map<String, Object> sharePacket = new Gson().fromJson(data, Map.class);

            // 验证版本
            String version = (String) sharePacket.get("v");
            if (!"2.0".equals(version)) {
                showError("不支持的分享版本");
                return;
            }

            // 提取数据
            String encryptedData = (String) sharePacket.get("data");
            String keyBase64 = (String) sharePacket.get("key");
            String ivBase64 = (String) sharePacket.get("iv");

            // 解密数据
            byte[] keyBytes = android.util.Base64.decode(keyBase64, android.util.Base64.NO_WRAP);
            byte[] ivBytes = android.util.Base64.decode(ivBase64, android.util.Base64.NO_WRAP);
            byte[] encryptedBytes = android.util.Base64.decode(encryptedData, android.util.Base64.NO_WRAP);

            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            String passwordJson = new String(decryptedBytes, StandardCharsets.UTF_8);

            // 解析密码数据
            Map<String, Object> passwordData = new Gson().fromJson(passwordJson, Map.class);

            // 显示密码
            showPasswordData(passwordData);

        } catch (Exception e) {
            e.printStackTrace();
            showError("解密失败: " + e.getMessage());
        }
    }

    private void showPasswordData(Map<String, Object> passwordData) {
        cardWaiting.setVisibility(View.GONE);
        cardReceived.setVisibility(View.VISIBLE);

        String title = (String) passwordData.get("title");
        String username = (String) passwordData.get("username");
        String password = (String) passwordData.get("password");
        String url = (String) passwordData.get("url");

        textTitle.setText(title != null ? title : "");
        textUsername.setText(username != null ? username : "");
        textPassword.setText(password != null ? password : "");

        if (url != null && !url.isEmpty()) {
            textUrl.setText(url);
            textUrl.setVisibility(View.VISIBLE);
        } else {
            textUrl.setVisibility(View.GONE);
        }

        textStatus.setText("接收成功");
    }

    private void showError(String message) {
        cardWaiting.setVisibility(View.GONE);
        cardError.setVisibility(View.VISIBLE);
        textStatus.setText("接收失败");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void saveToLocal() {
        // TODO: 实现保存到本地的功能
        new MaterialAlertDialogBuilder(this)
            .setTitle("保存到本地")
            .setMessage("此功能将在后续版本中实现")
            .setPositiveButton("确定", null)
            .show();
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
