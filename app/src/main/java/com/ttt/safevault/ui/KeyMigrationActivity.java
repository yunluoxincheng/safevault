package com.ttt.safevault.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.ttt.safevault.R;
import com.ttt.safevault.databinding.ActivityKeyMigrationBinding;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.SecureKeyStorageManager;
import com.ttt.safevault.service.KeyMigrationService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 密钥状态 Activity
 * 显示当前加密密钥状态，支持迁移和回滚操作
 *
 * 功能：
 * 1. 查看密钥版本和状态
 * 2. 从 RSA (v2.0) 迁移到 X25519/Ed25519 (v3.0)
 * 3. 从 v3.0 回滚到 v2.0（保留 RSA 密钥）
 * 4. 查看各密钥的存在状态
 *
 * @since SafeVault 3.0.0
 */
public class KeyMigrationActivity extends BaseActivity {

    private static final String TAG = "KeyMigrationActivity";

    private ActivityKeyMigrationBinding binding;
    private KeyMigrationService migrationService;
    private SecureKeyStorageManager keyStorage;

    private String masterPassword;
    private String saltBase64;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "=== onCreate() 开始 ===");
        super.onCreate(savedInstanceState);

        try {
            binding = ActivityKeyMigrationBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            // 初始化服务
            migrationService = new KeyMigrationService(this);
            keyStorage = SecureKeyStorageManager.getInstance(this);

            // 获取传入的参数（如果有，表示从其他页面跳转过来）
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                masterPassword = extras.getString("master_password");
                saltBase64 = extras.getString("salt_base64");
                Log.d(TAG, "接收到参数: masterPassword=" + (masterPassword != null ? "有" : "无"));
            }

            setupToolbar();
            setupViews();
            updateKeyStatus();
            Log.d(TAG, "=== onCreate() 完成 ===");
        } catch (Exception e) {
            Log.e(TAG, "onCreate() 中发生异常", e);
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "=== onStart() 开始 ===");
        super.onStart();
        Log.d(TAG, "=== onStart() 完成 ===");
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "=== onResume() 开始 ===");
        super.onResume();
        Log.d(TAG, "=== onResume() 完成 ===");
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "=== onPause() 开始 ===");
        super.onPause();
        Log.d(TAG, "=== onPause() 完成 ===");
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "=== onStop() 开始 ===");
        super.onStop();
        Log.d(TAG, "=== onStop() 完成 ===");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "=== onDestroy() 开始 ===");
        super.onDestroy();
        Log.d(TAG, "=== onDestroy() 完成 ===");
        binding = null;
    }

    /**
     * 设置工具栏
     */
    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    /**
     * 设置视图
     */
    private void setupViews() {
        // 开始迁移按钮（未迁移时显示）
        binding.btnMigrate.setOnClickListener(v -> {
            // 优先检查会话是否已解锁
            if (migrationService.isSessionUnlocked()) {
                // 会话已解锁，直接使用会话中的 DataKey 进行迁移
                Log.d(TAG, "会话已解锁，直接使用会话 DataKey 迁移");
                startMigrationWithSession();
            } else if (masterPassword != null && saltBase64 != null) {
                // 会话未解锁，但已有密码参数，直接迁移
                startMigration();
            } else {
                // 会话未解锁，需要验证密码
                showPasswordInputDialog();
            }
        });

        // 回滚按钮（已迁移时显示）
        binding.btnRollback.setOnClickListener(v -> showRollbackConfirmationDialog());

        // 查看详情按钮（显示密钥详情对话框）
        binding.btnViewDetails.setOnClickListener(v -> showKeyDetailsDialog());
    }

    /**
     * 更新密钥状态 UI
     */
    private void updateKeyStatus() {
        Log.d(TAG, "=== updateKeyStatus() 开始 ===");

        try {
            // 获取当前密钥版本
            String currentVersion = migrationService.getCurrentKeyVersion();
            Log.d(TAG, "currentVersion = " + currentVersion);

            boolean isV3 = migrationService.hasMigratedToV3();

            // 更新版本状态卡片
            if (isV3) {
                binding.tvCurrentVersion.setText("v3.0 (X25519/Ed25519)");
                binding.tvCurrentVersion.setTextColor(getColor(R.color.md_theme_light_onPrimaryContainer));
                binding.cardCurrentVersion.setBackgroundTintList(getColorStateList(R.color.md_theme_light_primaryContainer));

                binding.tvMigrationStatus.setText("已迁移");
                binding.tvMigrationStatus.setTextColor(getColor(R.color.md_theme_light_primary));
                binding.ivStatusIcon.setImageResource(R.drawable.ic_check_circle);

                // 显示迁移时间
                long timestamp = migrationService.getMigrationTimestamp();
                if (timestamp > 0) {
                    String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .format(new Date(timestamp));
                    binding.tvMigrationTime.setText("迁移时间: " + dateStr);
                    binding.tvMigrationTime.setVisibility(View.VISIBLE);
                } else {
                    binding.tvMigrationTime.setVisibility(View.GONE);
                }

                // 隐藏迁移按钮，显示回滚按钮
                binding.btnMigrate.setVisibility(View.GONE);
                binding.btnRollback.setVisibility(View.VISIBLE);
                binding.layoutButtons.setVisibility(View.VISIBLE);

            } else {
                // 未迁移到 v3.0
                if (currentVersion != null) {
                    binding.tvCurrentVersion.setText("v2.0 (RSA-2048)");
                } else {
                    binding.tvCurrentVersion.setText("v2.0 (RSA-2048)");
                }
                binding.tvCurrentVersion.setTextColor(getColor(R.color.md_theme_light_onSurface));
                binding.cardCurrentVersion.setBackgroundTintList(getColorStateList(R.color.md_theme_light_surfaceVariant));

                binding.tvMigrationStatus.setText("未迁移");
                binding.tvMigrationStatus.setTextColor(getColor(R.color.md_theme_light_onSurfaceVariant));
                binding.ivStatusIcon.setImageResource(R.drawable.ic_info_outline);

                binding.tvMigrationTime.setVisibility(View.GONE);

                // 显示迁移按钮，隐藏回滚按钮
                binding.btnMigrate.setVisibility(View.VISIBLE);
                binding.btnRollback.setVisibility(View.GONE);
                binding.layoutButtons.setVisibility(View.VISIBLE);
            }

            // 更新密钥存在状态
            updateKeyExistsStatus();

            Log.d(TAG, "=== updateKeyStatus() 完成 ===");
        } catch (Exception e) {
            Log.e(TAG, "updateKeyStatus() 中发生异常", e);
        }
    }

    /**
     * 更新密钥存在状态
     */
    private void updateKeyExistsStatus() {
        // RSA 密钥
        binding.tvRsaStatus.setText(keyStorage.getRsaPublicKey() != null ? "存在" : "不存在");
        binding.tvRsaStatus.setTextColor(
            keyStorage.getRsaPublicKey() != null ? getColor(R.color.success_green) : getColor(R.color.error_red)
        );

        // X25519 密钥
        binding.tvX25519Status.setText(keyStorage.getX25519PublicKeyBase64() != null ? "存在" : "不存在");
        binding.tvX25519Status.setTextColor(
            keyStorage.getX25519PublicKeyBase64() != null ? getColor(R.color.success_green) : getColor(R.color.gray_500)
        );

        // Ed25519 密钥
        binding.tvEd25519Status.setText(keyStorage.getEd25519PublicKeyBase64() != null ? "存在" : "不存在");
        binding.tvEd25519Status.setTextColor(
            keyStorage.getEd25519PublicKeyBase64() != null ? getColor(R.color.success_green) : getColor(R.color.gray_500)
        );
    }

    /**
     * 显示密码输入对话框
     */
    private void showPasswordInputDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_input, null);
        com.google.android.material.textfield.TextInputLayout passwordLayout =
                dialogView.findViewById(R.id.passwordLayout);
        com.google.android.material.textfield.TextInputEditText passwordInput =
                dialogView.findViewById(R.id.passwordInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.verify_password)
                .setMessage("迁移需要验证您的身份，请输入主密码。")
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    String password = passwordInput.getText().toString();
                    if (password.isEmpty()) {
                        Snackbar.make(binding.getRoot(), "密码不能为空", Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    verifyPasswordAndMigrate(password);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 验证密码并启动迁移
     */
    private void verifyPasswordAndMigrate(String password) {
        BackendService backendService = getBackendService();
        if (backendService == null) {
            Snackbar.make(binding.getRoot(), "无法获取服务", Snackbar.LENGTH_LONG).show();
            return;
        }

        try {
            boolean authenticated = backendService.unlock(password);
            if (authenticated) {
                this.masterPassword = password;
                this.saltBase64 = getSaltFromPrefs();
                startMigration();
            } else {
                Snackbar.make(binding.getRoot(), "密码错误", Snackbar.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "密码验证失败", e);
            Snackbar.make(binding.getRoot(), "验证失败: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * 从 SharedPreferences 获取盐值
     */
    private String getSaltFromPrefs() {
        try {
            android.content.SharedPreferences cryptoPrefs =
                    getSharedPreferences("crypto_prefs", Context.MODE_PRIVATE);
            String salt = cryptoPrefs.getString("master_salt", null);
            if (salt == null) {
                throw new IllegalStateException("盐值未找到");
            }
            return salt;
        } catch (Exception e) {
            Log.e(TAG, "获取盐值失败", e);
            return null;
        }
    }

    /**
     * 开始迁移（使用会话中的 DataKey）
     */
    private void startMigrationWithSession() {
        // 显示进度
        binding.layoutButtons.setVisibility(View.GONE);
        binding.layoutProgress.setVisibility(View.VISIBLE);
        binding.progressBar.setIndeterminate(true);
        binding.tvProgressText.setText(R.string.migration_in_progress);

        // 禁用返回按钮
        binding.toolbar.setNavigationOnClickListener(null);

        // 开始迁移
        BackendService backendService = getBackendService();
        if (backendService == null) {
            Log.e(TAG, "Backend service is null");
            Snackbar.make(binding.getRoot(),
                    "无法连接到服务器",
                    Snackbar.LENGTH_LONG).show();
            hideProgress();
            return;
        }

        migrationService.migrateToX25519AsyncWithSession(
                backendService,
                new KeyMigrationService.MigrationProgressListener() {
                    @Override
                    public void onProgress(int progress, String message) {
                        runOnUiThread(() -> {
                            binding.progressBar.setIndeterminate(false);
                            binding.progressBar.setProgress(progress);
                            binding.tvProgressText.setText(message);
                        });
                    }

                    @Override
                    public void onComplete(@NonNull KeyMigrationService.MigrationResult result) {
                        runOnUiThread(() -> {
                            hideProgress();
                            handleMigrationResult(result);
                        });
                    }
                }
        );
    }

    /**
     * 开始迁移
     */
    private void startMigration() {
        if (masterPassword == null || saltBase64 == null) {
            Log.e(TAG, "Master password or salt is null");
            Snackbar.make(binding.getRoot(),
                    "缺少必要的认证信息",
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        // 显示进度
        binding.layoutButtons.setVisibility(View.GONE);
        binding.layoutProgress.setVisibility(View.VISIBLE);
        binding.progressBar.setIndeterminate(true);
        binding.tvProgressText.setText(R.string.migration_in_progress);

        // 禁用返回按钮
        binding.toolbar.setNavigationOnClickListener(null);

        // 开始迁移
        BackendService backendService = getBackendService();
        if (backendService == null) {
            Log.e(TAG, "Backend service is null");
            Snackbar.make(binding.getRoot(),
                    "无法连接到服务器",
                    Snackbar.LENGTH_LONG).show();
            hideProgress();
            return;
        }

        migrationService.migrateToX25519Async(
                masterPassword,
                saltBase64,
                backendService,
                new KeyMigrationService.MigrationProgressListener() {
                    @Override
                    public void onProgress(int progress, String message) {
                        runOnUiThread(() -> {
                            binding.progressBar.setIndeterminate(false);
                            binding.progressBar.setProgress(progress);
                            binding.tvProgressText.setText(message);
                        });
                    }

                    @Override
                    public void onComplete(@NonNull KeyMigrationService.MigrationResult result) {
                        runOnUiThread(() -> {
                            hideProgress();
                            handleMigrationResult(result);
                        });
                    }
                }
        );
    }

    /**
     * 处理迁移结果
     */
    private void handleMigrationResult(KeyMigrationService.MigrationResult result) {
        // 恢复返回按钮
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        if (result.isSuccess()) {
            // 迁移成功，更新 UI
            updateKeyStatus();
            showMigrationSuccess();
        } else {
            // 迁移失败
            showMigrationError(result.getErrorMessage());
        }
    }

    /**
     * 隐藏进度条
     */
    private void hideProgress() {
        binding.layoutProgress.setVisibility(View.GONE);
        binding.layoutButtons.setVisibility(View.VISIBLE);
    }

    /**
     * 显示迁移成功
     */
    private void showMigrationSuccess() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.migration_success_title)
                .setMessage(R.string.migration_success_message)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    // 不自动关闭，让用户查看状态
                })
                .setCancelable(false)
                .show();
    }

    /**
     * 显示迁移错误
     */
    private void showMigrationError(String errorMessage) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.migration_failed_title)
                .setMessage(getString(R.string.migration_failed_message, errorMessage))
                .setPositiveButton(R.string.retry, (dialog, which) -> {
                    startMigration();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 显示回滚确认对话框
     */
    private void showRollbackConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("确认回滚")
                .setMessage("回滚将删除 v3.0 (X25519/Ed25519) 密钥，恢复使用 v2.0 (RSA) 密钥。\n\n注意：此操作不可逆！")
                .setPositiveButton("确认回滚", (dialog, which) -> {
                    performRollback();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 执行回滚
     */
    private void performRollback() {
        try {
            migrationService.rollbackMigration();
            Toast.makeText(this, "回滚成功", Toast.LENGTH_SHORT).show();
            updateKeyStatus();
        } catch (Exception e) {
            Log.e(TAG, "回滚失败", e);
            Snackbar.make(binding.getRoot(), "回滚失败: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * 显示密钥详情对话框
     */
    private void showKeyDetailsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_key_details, null);

        // 填充密钥详情
        TextView tvRsaPublicKey = dialogView.findViewById(R.id.tv_rsa_public_key);
        TextView tvX25519PublicKey = dialogView.findViewById(R.id.tv_x25519_public_key);
        TextView tvEd25519PublicKey = dialogView.findViewById(R.id.tv_ed25519_public_key);

        String rsaKey = keyStorage.getRsaPublicKeyBase64();
        if (rsaKey != null) {
            String displayKey = rsaKey.length() > 50 ? rsaKey.substring(0, 50) + "..." : rsaKey;
            tvRsaPublicKey.setText(displayKey);
            tvRsaPublicKey.setTextColor(getColor(R.color.success_green));
        } else {
            tvRsaPublicKey.setText("不存在");
            tvRsaPublicKey.setTextColor(getColor(R.color.gray_500));
        }

        String x25519Key = keyStorage.getX25519PublicKeyBase64();
        if (x25519Key != null) {
            String displayKey = x25519Key.length() > 50 ? x25519Key.substring(0, 50) + "..." : x25519Key;
            tvX25519PublicKey.setText(displayKey);
            tvX25519PublicKey.setTextColor(getColor(R.color.success_green));
        } else {
            tvX25519PublicKey.setText("不存在");
            tvX25519PublicKey.setTextColor(getColor(R.color.gray_500));
        }

        String ed25519Key = keyStorage.getEd25519PublicKeyBase64();
        if (ed25519Key != null) {
            String displayKey = ed25519Key.length() > 50 ? ed25519Key.substring(0, 50) + "..." : ed25519Key;
            tvEd25519PublicKey.setText(displayKey);
            tvEd25519PublicKey.setTextColor(getColor(R.color.success_green));
        } else {
            tvEd25519PublicKey.setText("不存在");
            tvEd25519PublicKey.setTextColor(getColor(R.color.gray_500));
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("密钥详情")
                .setView(dialogView)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    /**
     * 获取后端服务
     */
    private BackendService getBackendService() {
        return com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
    }
}