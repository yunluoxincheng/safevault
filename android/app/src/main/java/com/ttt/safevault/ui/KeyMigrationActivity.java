package com.ttt.safevault.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.ttt.safevault.R;
import com.ttt.safevault.databinding.ActivityKeyMigrationBinding;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.service.KeyMigrationService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 密钥迁移页面（v2 -> v3）。
 */
public class KeyMigrationActivity extends BaseActivity {

    private static final String TAG = "KeyMigrationActivity";

    private ActivityKeyMigrationBinding binding;
    private KeyMigrationService migrationService;
    private KeyMigrationService.MigrationStartAction lastMigrationAction =
            KeyMigrationService.MigrationStartAction.NEED_PASSWORD;

    private String masterPassword;
    private String saltBase64;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            binding = ActivityKeyMigrationBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            migrationService = new KeyMigrationService(this);

            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                masterPassword = extras.getString("master_password");
                saltBase64 = extras.getString("salt_base64");
            }

            setupToolbar();
            setupViews();
            updateKeyStatus();
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupViews() {
        binding.btnMigrate.setOnClickListener(v -> {
            KeyMigrationService.MigrationStartDecision decision =
                    migrationService.resolveMigrationStartDecision(masterPassword, saltBase64);
            executeMigrationStartDecision(decision);
        });

        binding.btnRollback.setOnClickListener(v -> showRollbackConfirmationDialog());
        binding.btnViewDetails.setOnClickListener(v -> showKeyDetailsDialog());
    }

    private void updateKeyStatus() {
        try {
            String currentVersion = migrationService.getCurrentKeyVersion();
            boolean isV3 = migrationService.hasMigratedToV3();

            if (isV3) {
                binding.tvCurrentVersion.setText("v3.0 (X25519/Ed25519)");
                binding.tvCurrentVersion.setTextColor(getColor(R.color.md_theme_light_onPrimaryContainer));
                binding.cardCurrentVersion.setBackgroundTintList(getColorStateList(R.color.md_theme_light_primaryContainer));

                binding.tvMigrationStatus.setText("已迁移");
                binding.tvMigrationStatus.setTextColor(getColor(R.color.md_theme_light_primary));
                binding.ivStatusIcon.setImageResource(R.drawable.ic_check_circle);

                long timestamp = migrationService.getMigrationTimestamp();
                if (timestamp > 0) {
                    String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .format(new Date(timestamp));
                    binding.tvMigrationTime.setText("迁移时间: " + dateStr);
                    binding.tvMigrationTime.setVisibility(View.VISIBLE);
                } else {
                    binding.tvMigrationTime.setVisibility(View.GONE);
                }

                binding.btnMigrate.setVisibility(View.GONE);
                binding.btnRollback.setVisibility(View.VISIBLE);
                binding.layoutButtons.setVisibility(View.VISIBLE);
            } else {
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
                binding.btnMigrate.setVisibility(View.VISIBLE);
                binding.btnRollback.setVisibility(View.GONE);
                binding.layoutButtons.setVisibility(View.VISIBLE);
            }

            updateKeyExistsStatus();
        } catch (Exception e) {
            Log.e(TAG, "updateKeyStatus failed", e);
        }
    }

    private void updateKeyExistsStatus() {
        boolean hasRsa = migrationService.hasRsaPublicKey();
        boolean hasX25519 = migrationService.hasX25519PublicKey();
        boolean hasEd25519 = migrationService.hasEd25519PublicKey();

        binding.tvRsaStatus.setText(hasRsa ? "存在" : "不存在");
        binding.tvRsaStatus.setTextColor(hasRsa ? getColor(R.color.success_green) : getColor(R.color.error_red));

        binding.tvX25519Status.setText(hasX25519 ? "存在" : "不存在");
        binding.tvX25519Status.setTextColor(hasX25519 ? getColor(R.color.success_green) : getColor(R.color.gray_500));

        binding.tvEd25519Status.setText(hasEd25519 ? "存在" : "不存在");
        binding.tvEd25519Status.setTextColor(hasEd25519 ? getColor(R.color.success_green) : getColor(R.color.gray_500));
    }

    private void showPasswordInputDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_input, null);
        com.google.android.material.textfield.TextInputEditText passwordInput =
                dialogView.findViewById(R.id.passwordInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.verify_password)
                .setMessage("迁移需要验证您的身份，请输入主密码。")
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";
                    if (password.isEmpty()) {
                        Snackbar.make(binding.getRoot(), "密码不能为空", Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    verifyPasswordAndMigrate(password);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void verifyPasswordAndMigrate(@NonNull String password) {
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
                KeyMigrationService.MigrationStartDecision decision =
                        migrationService.resolveMigrationStartDecision(masterPassword, saltBase64);
                executeMigrationStartDecision(decision);
            } else {
                Snackbar.make(binding.getRoot(), "密码错误", Snackbar.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "verifyPasswordAndMigrate failed", e);
            Snackbar.make(binding.getRoot(), "验证失败: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    @Nullable
    private String getSaltFromPrefs() {
        try {
            android.content.SharedPreferences cryptoPrefs =
                    getSharedPreferences("crypto_prefs", Context.MODE_PRIVATE);
            return cryptoPrefs.getString("master_salt", null);
        } catch (Exception e) {
            Log.e(TAG, "getSaltFromPrefs failed", e);
            return null;
        }
    }

    private void executeMigrationStartDecision(@NonNull KeyMigrationService.MigrationStartDecision decision) {
        lastMigrationAction = decision.getAction();
        switch (decision.getAction()) {
            case CAN_MIGRATE_WITH_SESSION:
                startMigrationWithSession();
                break;
            case CAN_MIGRATE_WITH_PASSWORD:
                startMigration();
                break;
            case NEED_PASSWORD:
                showPasswordInputDialog();
                break;
            case NOT_AVAILABLE:
            default:
                String reason = decision.getReason() != null ? decision.getReason() : "当前状态不可迁移";
                Snackbar.make(binding.getRoot(), reason, Snackbar.LENGTH_LONG).show();
                break;
        }
    }

    private void startMigrationWithSession() {
        lastMigrationAction = KeyMigrationService.MigrationStartAction.CAN_MIGRATE_WITH_SESSION;

        binding.layoutButtons.setVisibility(View.GONE);
        binding.layoutProgress.setVisibility(View.VISIBLE);
        binding.progressBar.setIndeterminate(true);
        binding.tvProgressText.setText(R.string.migration_in_progress);
        binding.toolbar.setNavigationOnClickListener(null);

        BackendService backendService = getBackendService();
        if (backendService == null) {
            Snackbar.make(binding.getRoot(), "无法连接到服务", Snackbar.LENGTH_LONG).show();
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

    private void startMigration() {
        lastMigrationAction = KeyMigrationService.MigrationStartAction.CAN_MIGRATE_WITH_PASSWORD;
        if (masterPassword == null || saltBase64 == null) {
            Snackbar.make(binding.getRoot(), "缺少必要的认证信息", Snackbar.LENGTH_LONG).show();
            return;
        }

        binding.layoutButtons.setVisibility(View.GONE);
        binding.layoutProgress.setVisibility(View.VISIBLE);
        binding.progressBar.setIndeterminate(true);
        binding.tvProgressText.setText(R.string.migration_in_progress);
        binding.toolbar.setNavigationOnClickListener(null);

        BackendService backendService = getBackendService();
        if (backendService == null) {
            Snackbar.make(binding.getRoot(), "无法连接到服务", Snackbar.LENGTH_LONG).show();
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

    private void handleMigrationResult(@NonNull KeyMigrationService.MigrationResult result) {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        if (result.isSuccess()) {
            updateKeyStatus();
            showMigrationSuccess();
        } else {
            showMigrationError(result);
        }
    }

    private void hideProgress() {
        binding.layoutProgress.setVisibility(View.GONE);
        binding.layoutButtons.setVisibility(View.VISIBLE);
    }

    private void showMigrationSuccess() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.migration_success_title)
                .setMessage(R.string.migration_success_message)
                .setPositiveButton(R.string.ok, null)
                .setCancelable(false)
                .show();
    }

    private void showMigrationError(@NonNull KeyMigrationService.MigrationResult result) {
        KeyMigrationService.MigrationErrorType errorType = result.getErrorType();
        String detail = result.getErrorMessage() != null ? result.getErrorMessage() : "未知错误";
        boolean retryable = migrationService.isRetryableError(errorType);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.migration_failed_title)
                .setMessage(buildMigrationErrorMessage(errorType, detail))
                .setNegativeButton(R.string.cancel, null);

        if (retryable) {
            builder.setPositiveButton(R.string.retry, (dialog, which) -> retryLastMigration());
        } else {
            builder.setPositiveButton(R.string.ok, null);
        }
        builder.show();
    }

    private void retryLastMigration() {
        if (lastMigrationAction == KeyMigrationService.MigrationStartAction.CAN_MIGRATE_WITH_SESSION) {
            startMigrationWithSession();
            return;
        }
        if (lastMigrationAction == KeyMigrationService.MigrationStartAction.CAN_MIGRATE_WITH_PASSWORD) {
            startMigration();
            return;
        }
        KeyMigrationService.MigrationStartDecision decision =
                migrationService.resolveMigrationStartDecision(masterPassword, saltBase64);
        executeMigrationStartDecision(decision);
    }

    private String buildMigrationErrorMessage(
            @Nullable KeyMigrationService.MigrationErrorType errorType,
            @NonNull String detail
    ) {
        if (errorType == null) {
            return getString(R.string.migration_failed_message, detail);
        }
        switch (errorType) {
            case SESSION_REQUIRED:
                return "会话不可用，请重新登录后再试。";
            case AUTH_REQUIRED:
                return "认证信息失效，请重新验证主密码后再试。";
            case NETWORK:
                return "网络或服务异常，请稍后重试。";
            case CRYPTO:
            case STORAGE:
                return "本地密钥处理失败，请重试；若持续失败请稍后再试。";
            case UNKNOWN:
            default:
                return getString(R.string.migration_failed_message, detail);
        }
    }

    private void showRollbackConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("确认回滚")
                .setMessage("回滚将删除 v3.0 (X25519/Ed25519) 密钥，恢复使用 v2.0 (RSA) 密钥。\n\n注意：此操作不可逆！")
                .setPositiveButton("确认回滚", (dialog, which) -> performRollback())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performRollback() {
        try {
            migrationService.rollbackMigration();
            Toast.makeText(this, "回滚成功", Toast.LENGTH_SHORT).show();
            updateKeyStatus();
        } catch (Exception e) {
            Log.e(TAG, "performRollback failed", e);
            Snackbar.make(binding.getRoot(), "回滚失败: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    private void showKeyDetailsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_key_details, null);

        TextView tvRsaPublicKey = dialogView.findViewById(R.id.tv_rsa_public_key);
        TextView tvX25519PublicKey = dialogView.findViewById(R.id.tv_x25519_public_key);
        TextView tvEd25519PublicKey = dialogView.findViewById(R.id.tv_ed25519_public_key);

        String rsaKey = migrationService.getRsaPublicKeyBase64();
        if (rsaKey != null) {
            String displayKey = rsaKey.length() > 50 ? rsaKey.substring(0, 50) + "..." : rsaKey;
            tvRsaPublicKey.setText(displayKey);
            tvRsaPublicKey.setTextColor(getColor(R.color.success_green));
        } else {
            tvRsaPublicKey.setText("不存在");
            tvRsaPublicKey.setTextColor(getColor(R.color.gray_500));
        }

        String x25519Key = migrationService.getX25519PublicKeyBase64();
        if (x25519Key != null) {
            String displayKey = x25519Key.length() > 50 ? x25519Key.substring(0, 50) + "..." : x25519Key;
            tvX25519PublicKey.setText(displayKey);
            tvX25519PublicKey.setTextColor(getColor(R.color.success_green));
        } else {
            tvX25519PublicKey.setText("不存在");
            tvX25519PublicKey.setTextColor(getColor(R.color.gray_500));
        }

        String ed25519Key = migrationService.getEd25519PublicKeyBase64();
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

    private BackendService getBackendService() {
        return com.ttt.safevault.core.ServiceLocator.getInstance().getBackendService();
    }
}
