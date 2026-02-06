package com.ttt.safevault.ui.autofill;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 自动填充保存凭据前的认证 Activity
 * 强制检查应用锁定状态，清除会话密钥后要求用户重新登录
 * 认证通过后再跳转到 AutofillSaveActivity
 */
public class AutofillSaveAuthActivity extends AppCompatActivity {
    private static final String TAG = "AutofillSaveAuthActivity";

    // 待传递给 AutofillSaveActivity 的数据
    private String username;
    private String password;
    private String domain;
    private String packageName;
    private String title;
    private boolean isWeb;

    private ActivityResultLauncher<Intent> loginActivityLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 防止截屏
        com.ttt.safevault.security.SecurityConfig securityConfig =
            new com.ttt.safevault.security.SecurityConfig(this);
        if (securityConfig.isScreenshotProtectionEnabled()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        // 获取 Intent 数据
        Intent intent = getIntent();
        if (intent != null) {
            username = intent.getStringExtra("username");
            password = intent.getStringExtra("password");
            domain = intent.getStringExtra("domain");
            packageName = intent.getStringExtra("packageName");
            title = intent.getStringExtra("title");
            isWeb = intent.getBooleanExtra("isWeb", false);
        }

        // 初始化 ActivityResultLauncher
        loginActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // 只有登录成功才继续
                    if (result.getResultCode() == RESULT_OK) {
                        startAutofillSaveActivity();
                    } else {
                        // 登录被取消或失败，直接关闭
                        finish();
                    }
                }
        );

        // 清除可能存在的会话密钥，强制要求登录
        clearSessionKey();

        // 始终跳转到登录界面进行认证
        Intent loginIntent = new Intent(this, com.ttt.safevault.ui.LoginActivity.class);
        loginIntent.putExtra("from_autofill_save", true);
        loginActivityLauncher.launch(loginIntent);
    }

    /**
     * 清除 SharedPreferences 中的会话密钥
     */
    private void clearSessionKey() {
        try {
            SharedPreferences prefs = getSharedPreferences("crypto_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("session_master_key");
            editor.remove("session_master_iv");
            editor.remove("unlock_time");
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "清除会话主密钥失败", e);
        }
    }

    /**
     * 启动 AutofillSaveActivity
     */
    private void startAutofillSaveActivity() {
        Intent saveIntent = new Intent(this, AutofillSaveActivity.class);
        saveIntent.putExtra("username", username);
        saveIntent.putExtra("password", password);
        saveIntent.putExtra("domain", domain);
        saveIntent.putExtra("packageName", packageName);
        saveIntent.putExtra("title", title);
        saveIntent.putExtra("isWeb", isWeb);
        startActivity(saveIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 清除敏感数据
        username = null;
        password = null;
        domain = null;
        packageName = null;
        title = null;
    }
}
