package com.ttt.safevault.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.ttt.safevault.R;
import com.ttt.safevault.databinding.FragmentAccountSecurityBinding;
import com.ttt.safevault.security.SecurityConfig;
import com.ttt.safevault.security.biometric.AuthCallback;
import com.ttt.safevault.security.biometric.AuthError;
import com.ttt.safevault.security.biometric.AuthScenario;
import com.ttt.safevault.security.biometric.BiometricAuthManager;
import com.ttt.safevault.viewmodel.AccountSecurityViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

/**
 * 账户安全设置 Fragment
 *
 * 安全编排逻辑已迁移到 AccountSecurityViewModel。
 * Fragment 保留职责：
 * - BiometricPrompt 托管（Android 平台边界例外）
 * - UI 渲染和对话框
 * - 导航
 */
public class AccountSecurityFragment extends BaseFragment {

    private static final String TAG = "AccountSecurity";

    private FragmentAccountSecurityBinding binding;
    private AccountSecurityViewModel viewModel;
    private BiometricAuthManager biometricAuthManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountSecurityBinding.inflate(inflater, container, false);

        ViewModelProvider.Factory factory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(this, factory).get(AccountSecurityViewModel.class);
        biometricAuthManager = viewModel.getBiometricAuthManager();

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupObservers();
        setupClickListeners();
        viewModel.loadInitialState();
        loadSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refreshKeyVersionStatus();
    }

    // ========== Observers ==========

    private void setupObservers() {
        viewModel.biometricEnabled.observe(getViewLifecycleOwner(), enabled -> {
            if (binding != null) {
                binding.switchBiometric.setChecked(enabled != null && enabled);
            }
        });

        viewModel.biometricAvailable.observe(getViewLifecycleOwner(), available -> {
            // Biometric button visibility is driven by loadSettings() below
        });

        viewModel.keyVersionInfo.observe(getViewLifecycleOwner(), info -> {
            if (binding == null || info == null) return;
            updateKeyVersionDisplay(info);
        });

        viewModel.enrollmentReadiness.observe(getViewLifecycleOwner(), readiness -> {
            if (readiness == null) return;
            handleEnrollmentReadiness(readiness);
        });

        viewModel.successMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.errorMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.isLoading.observe(getViewLifecycleOwner(), loading -> {
            // Could show/hide progress indicator
        });

        viewModel.navigateToLogin.observe(getViewLifecycleOwner(), navigate -> {
            if (navigate != null && navigate && getActivity() != null) {
                getActivity().finish();
            }
        });
    }

    // ========== Click Listeners ==========

    private void setupClickListeners() {
        // Auto-lock
        binding.cardAutoLock.setOnClickListener(v -> showAutoLockDialog());

        // Biometric switch
        binding.switchBiometric.setOnClickListener(v -> handleBiometricSwitchClick());

        // Screenshot switch
        binding.switchScreenshot.setOnClickListener(v -> handleScreenshotSwitchClick());

        // PIN code
        binding.cardPinCode.setOnClickListener(v -> {
            if (viewModel.isPinCodeEnabled()) {
                showPinCodeOptionsDialog();
            } else {
                showSetupPinDialog();
            }
        });

        // Change password
        binding.cardChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        // Export data
        binding.cardExportData.setOnClickListener(v -> {
            promptUserAuthentication(() -> performExportData(), null);
        });

        // Import data
        binding.cardImportData.setOnClickListener(v -> {
            promptUserAuthentication(() -> selectImportFile(), null);
        });

        // Logout
        binding.cardLogout.setOnClickListener(v -> showLogoutDialog());

        // Delete account
        binding.cardDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());

        // Key migration
        binding.cardKeyMigration.setOnClickListener(v -> startKeyMigration());
    }

    // ========== Initial Settings Load ==========

    private void loadSettings() {
        // Auto-lock
        SecurityConfig.AutoLockMode autoLockMode = viewModel.getAutoLockMode();
        binding.tvAutoLockValue.setText(autoLockMode.getDisplayName());

        // Biometric switch
        binding.switchBiometric.setChecked(viewModel.isBiometricEnabled());

        // Screenshot switch
        binding.switchScreenshot.setChecked(viewModel.isScreenshotAllowed());

        // PIN status
        binding.tvPinStatus.setText(viewModel.isPinCodeEnabled() ? "已启用" : "未启用");

        // Key version
        viewModel.refreshKeyVersionStatus();
    }

    // ========== Biometric Switch Handler ==========

    private void handleBiometricSwitchClick() {
        boolean newState = binding.switchBiometric.isChecked();

        if (newState) {
            // User wants to enable biometric
            binding.switchBiometric.setChecked(false);
            // Delegate eligibility check to ViewModel
            viewModel.checkEnrollmentEligibility();
        } else {
            // User wants to disable biometric
            binding.switchBiometric.setChecked(false);
            viewModel.disableBiometric();
            Toast.makeText(requireContext(), "生物识别已禁用", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handle enrollment readiness result from ViewModel.
     * This is where the platform-boundary exception applies: Fragment hosts BiometricPrompt.
     */
    private void handleEnrollmentReadiness(@NonNull AccountSecurityViewModel.EnrollmentReadiness readiness) {
        if (!readiness.canEnroll) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(readiness.needsPassword ? "无法启用生物识别" : "生物识别不可用")
                    .setMessage(readiness.notAvailableReason)
                    .setPositiveButton("确定", null)
                    .show();
            return;
        }

        if (readiness.needsPassword) {
            // Session not unlocked, need password first
            showPasswordDialogForEnrollment();
        } else {
            // Session already unlocked, directly host BiometricPrompt
            triggerBiometricEnrollmentPrompt();
        }
    }

    /**
     * Show password dialog when session is not unlocked.
     * ViewModel handles the actual password verification.
     */
    private void showPasswordDialogForEnrollment() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_input, null);
        com.google.android.material.textfield.TextInputLayout passwordLayout =
                dialogView.findViewById(R.id.passwordLayout);
        com.google.android.material.textfield.TextInputEditText passwordInput =
                dialogView.findViewById(R.id.passwordInput);

        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordLayout.setEndIconDrawable(requireContext().getDrawable(R.drawable.ic_visibility));

        passwordLayout.setEndIconOnClickListener(v -> {
            int selection = passwordInput.getSelectionEnd();
            int currentInputType = passwordInput.getInputType();
            int variation = currentInputType & android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;

            if (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                passwordLayout.setEndIconDrawable(requireContext().getDrawable(R.drawable.ic_visibility_off));
            } else {
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordLayout.setEndIconDrawable(requireContext().getDrawable(R.drawable.ic_visibility));
            }
            passwordInput.setSelection(selection);
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("验证主密码")
                .setMessage("为了启用生物识别，请先验证您的主密码")
                .setView(dialogView)
                .setPositiveButton("验证", (dialog, which) -> {
                    String password = passwordInput.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(requireContext(), "密码不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean authenticated = viewModel.verifyPassword(password);
                    if (authenticated) {
                        viewModel.saveEnrollmentPassword(password);
                        triggerBiometricEnrollmentPrompt();
                    } else {
                        Toast.makeText(requireContext(), "密码错误，验证失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * Host BiometricPrompt for enrollment. Platform-boundary exception.
     * On success, delegate enrollment completion to ViewModel.
     */
    private void triggerBiometricEnrollmentPrompt() {
        biometricAuthManager.authenticate(
                (androidx.fragment.app.FragmentActivity) requireActivity(),
                AuthScenario.ENROLLMENT,
                new AuthCallback() {
                    @Override
                    public void onUserVerified() {}

                    @Override
                    public void onKeyAccessGranted() {
                        // Delegate enrollment completion to ViewModel
                        String enrollmentPassword = viewModel.getEnrollmentPassword();
                        if (enrollmentPassword != null) {
                            viewModel.completeEnrollmentWithPassword(enrollmentPassword);
                        } else {
                            viewModel.completeEnrollmentWithSessionDataKey();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull AuthError error, @NonNull String message, boolean canRetry) {
                        viewModel.clearEnrollmentPassword();
                        requireActivity().runOnUiThread(() -> {
                            if (error == AuthError.DEBOUNCED) {
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            Log.e(TAG, "启用生物识别失败: " + error + " - " + message);
                            Toast.makeText(requireContext(), "启用生物识别失败: " + message, Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onCancel() {
                        viewModel.clearEnrollmentPassword();
                    }

                    @Override
                    public void onBiometricChanged() {
                        viewModel.clearEnrollmentPassword();
                        requireActivity().runOnUiThread(() ->
                                new MaterialAlertDialogBuilder(requireContext())
                                        .setTitle("生物识别信息已变更")
                                        .setMessage("检测到您的生物识别信息已变更，请使用主密码登录后重新启用生物识别。")
                                        .setPositiveButton("我知道了", null)
                                        .show()
                        );
                    }
                }
        );
    }

    // ========== Screenshot Switch ==========

    private void handleScreenshotSwitchClick() {
        boolean newState = binding.switchScreenshot.isChecked();

        if (newState) {
            binding.switchScreenshot.setChecked(false);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.enable_screenshot_confirm)
                    .setMessage(R.string.enable_screenshot_message)
                    .setPositiveButton("验证并启用", (dialog, which) -> {
                        promptUserAuthentication(() -> {
                            viewModel.setScreenshotAllowed(true);
                            binding.switchScreenshot.setChecked(true);
                            Toast.makeText(requireContext(), R.string.screenshot_enabled, Toast.LENGTH_SHORT).show();
                            applyScreenshotSettings();
                        }, () -> binding.switchScreenshot.setChecked(false));
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> binding.switchScreenshot.setChecked(false))
                    .show();
        } else {
            binding.switchScreenshot.setChecked(false);
            viewModel.setScreenshotAllowed(false);
            Toast.makeText(requireContext(), R.string.screenshot_disabled, Toast.LENGTH_SHORT).show();
            applyScreenshotSettings();
        }
    }

    // ========== Auto-lock Dialog ==========

    private void showAutoLockDialog() {
        SecurityConfig.AutoLockMode[] modes = SecurityConfig.AutoLockMode.values();
        String[] options = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            options[i] = modes[i].getDisplayName();
        }
        int currentSelection = viewModel.getAutoLockMode().ordinal();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.session_lock)
                .setSingleChoiceItems(options, currentSelection, (dialog, which) -> {
                    SecurityConfig.AutoLockMode selectedMode = modes[which];
                    viewModel.setAutoLockMode(selectedMode);
                    binding.tvAutoLockValue.setText(selectedMode.getDisplayName());
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "会话锁定已设置为: " + selectedMode.getDisplayName(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ========== PIN Code Dialogs ==========

    private void showPinCodeOptionsDialog() {
        String[] options = {"更改PIN码", "移除PIN码"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("PIN码选项")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showChangePinDialog();
                    else showRemovePinDialog();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSetupPinDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pin_setup, null);
        com.google.android.material.textfield.TextInputEditText pinInput =
                dialogView.findViewById(R.id.pinInput);
        com.google.android.material.textfield.TextInputEditText confirmPinInput =
                dialogView.findViewById(R.id.confirmPinInput);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("设置PIN码")
                .setMessage("请输入4-6位数字作为PIN码，用于快速解锁应用")
                .setView(dialogView)
                .setPositiveButton("设置", (dialog, which) -> {
                    String pin = pinInput.getText().toString();
                    String confirmPin = confirmPinInput.getText().toString();

                    if (!pin.matches("\\d{4,6}")) {
                        Toast.makeText(requireContext(), "PIN码必须是4-6位数字", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!pin.equals(confirmPin)) {
                        Toast.makeText(requireContext(), "两次输入的PIN码不一致", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean success = viewModel.setPinCode(pin);
                    if (success) {
                        binding.tvPinStatus.setText("已启用");
                        Toast.makeText(requireContext(), "PIN码设置成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "PIN码设置失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showChangePinDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pin_setup, null);
        com.google.android.material.textfield.TextInputLayout oldPinLayout =
                dialogView.findViewById(R.id.pinLayout);
        com.google.android.material.textfield.TextInputEditText oldPinInput =
                dialogView.findViewById(R.id.pinInput);
        com.google.android.material.textfield.TextInputLayout newPinLayout =
                dialogView.findViewById(R.id.confirmPinLayout);
        com.google.android.material.textfield.TextInputEditText newPinInput =
                dialogView.findViewById(R.id.confirmPinInput);

        oldPinLayout.setHint("请输入当前PIN码");
        newPinLayout.setHint("请输入新PIN码（4-6位数字）");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("更改PIN码")
                .setView(dialogView)
                .setPositiveButton("更改", (dialog, which) -> {
                    String oldPin = oldPinInput.getText().toString();
                    String newPin = newPinInput.getText().toString();

                    if (!viewModel.verifyPinCode(oldPin)) {
                        Toast.makeText(requireContext(), "当前PIN码错误", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPin.matches("\\d{4,6}")) {
                        Toast.makeText(requireContext(), "新PIN码必须是4-6位数字", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean success = viewModel.setPinCode(newPin);
                    Toast.makeText(requireContext(), success ? "PIN码更改成功" : "PIN码更改失败", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRemovePinDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("移除PIN码")
                .setMessage("确定要移除PIN码吗？")
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    viewModel.clearPinCode();
                    binding.tvPinStatus.setText("未启用");
                    Toast.makeText(requireContext(), R.string.pin_removed, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ========== Change Password Dialog ==========

    private void showChangePasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_change, null);
        com.google.android.material.textfield.TextInputEditText oldPasswordInput =
                dialogView.findViewById(R.id.oldPasswordInput);
        com.google.android.material.textfield.TextInputEditText newPasswordInput =
                dialogView.findViewById(R.id.newPasswordInput);
        com.google.android.material.textfield.TextInputEditText confirmPasswordInput =
                dialogView.findViewById(R.id.confirmPasswordInput);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("更改主密码")
                .setMessage("请输入当前主密码和新主密码")
                .setView(dialogView)
                .setPositiveButton("更改", (dialog, which) -> {
                    String oldPassword = oldPasswordInput.getText().toString();
                    String newPassword = newPasswordInput.getText().toString();
                    String confirmPassword = confirmPasswordInput.getText().toString();

                    if (!viewModel.isSessionUnlocked()) {
                        if (!viewModel.verifyPassword(oldPassword)) {
                            Toast.makeText(requireContext(), "当前密码错误", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    if (newPassword.length() < 8) {
                        Toast.makeText(requireContext(), "新密码长度至少为8位", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPassword.equals(confirmPassword)) {
                        Toast.makeText(requireContext(), "两次输入的新密码不一致", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    viewModel.changeMasterPassword(
                            oldPassword.isEmpty() ? null : oldPassword, newPassword);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ========== Logout/Account Dialogs ==========

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    com.ttt.safevault.utils.LoadingDialog loadingDialog =
                            com.ttt.safevault.utils.LoadingDialog.show(requireContext(), "正在注销...");
                    viewModel.performLogout();
                    loadingDialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteAccountDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除账户")
                .setMessage("您确定要删除账户吗？此操作不可撤销！")
                .setPositiveButton("继续", (dialog, which) -> showDeleteAccountSecondConfirmation())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteAccountSecondConfirmation() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("最后确认")
                .setMessage("删除账户将永久清除所有数据，包括：\n\n" +
                        "• 所有密码记录\n• 云端同步数据\n• 设备信息\n• 安全设置\n\n" +
                        "提示：建议您在删除账户前先导出重要数据。\n" +
                        "如需导出，请前往「设置 → 数据管理 → 导出数据」。\n\n" +
                        "此操作无法撤销，确定继续吗？")
                .setPositiveButton("我已了解，继续删除", (dialog, which) -> {
                    promptUserAuthentication(() -> viewModel.deleteAccount(), () ->
                            Toast.makeText(requireContext(), "身份验证失败，无法删除账户", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ========== User Authentication Prompt ==========

    /**
     * Prompt user for authentication (biometric or password).
     * Platform-boundary exception: Fragment hosts BiometricPrompt.
     */
    private void promptUserAuthentication(@Nullable Runnable onSuccess, @Nullable Runnable onFailure) {
        com.ttt.safevault.security.BiometricAuthHelper biometricHelper =
                new com.ttt.safevault.security.BiometricAuthHelper(
                        (androidx.fragment.app.FragmentActivity) requireActivity());
        biometricHelper.authenticate(new com.ttt.safevault.security.BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                if (onSuccess != null) requireActivity().runOnUiThread(onSuccess);
            }

            @Override
            public void onFailure(String error) {
                requireActivity().runOnUiThread(() ->
                        showPasswordAuthenticationDialog(onSuccess, onFailure));
            }

            @Override
            public void onCancel() {
                if (onFailure != null) requireActivity().runOnUiThread(onFailure);
            }
        });
    }

    private void showPasswordAuthenticationDialog(@Nullable Runnable onSuccess, @Nullable Runnable onFailure) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_input, null);
        com.google.android.material.textfield.TextInputLayout passwordLayout =
                dialogView.findViewById(R.id.passwordLayout);
        com.google.android.material.textfield.TextInputEditText passwordInput =
                dialogView.findViewById(R.id.passwordInput);

        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordLayout.setEndIconDrawable(requireContext().getDrawable(R.drawable.ic_visibility));

        passwordLayout.setEndIconOnClickListener(v -> {
            int selection = passwordInput.getSelectionEnd();
            int currentInputType = passwordInput.getInputType();
            int variation = currentInputType & android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;

            if (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                passwordLayout.setEndIconDrawable(requireContext().getDrawable(R.drawable.ic_visibility_off));
            } else {
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordLayout.setEndIconDrawable(requireContext().getDrawable(R.drawable.ic_visibility));
            }
            passwordInput.setSelection(selection);
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("验证身份")
                .setMessage("为了安全起见，请验证您的身份")
                .setView(dialogView)
                .setPositiveButton("验证", (dialog, which) -> {
                    String password = passwordInput.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(requireContext(), "密码不能为空", Toast.LENGTH_SHORT).show();
                        if (onFailure != null) onFailure.run();
                        return;
                    }

                    boolean authenticated = viewModel.verifyPassword(password);
                    if (authenticated) {
                        if (onSuccess != null) onSuccess.run();
                    } else {
                        Toast.makeText(requireContext(), "密码错误，验证失败", Toast.LENGTH_SHORT).show();
                        if (onFailure != null) onFailure.run();
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    if (onFailure != null) onFailure.run();
                })
                .show();
    }

    // ========== Screenshot Settings ==========

    private void applyScreenshotSettings() {
        boolean screenshotAllowed = viewModel.isScreenshotAllowed();
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                getActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                if (!screenshotAllowed) {
                    getActivity().getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                }
            });
        }
    }

    // ========== Export/Import Data ==========

    private void performExportData() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (requireContext().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
                return;
            }
        }

        com.ttt.safevault.utils.LoadingDialog loadingDialog =
                com.ttt.safevault.utils.LoadingDialog.show(requireContext(), "正在导出数据...");

        new android.os.Handler().post(() -> {
            try {
                String fileName = "safevault_backup_" +
                        new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                                .format(new java.util.Date()) + ".svault";

                java.io.File exportDir = new java.io.File(requireContext().getExternalFilesDir(null), "backups");
                if (!exportDir.exists()) exportDir.mkdirs();
                java.io.File exportFile = new java.io.File(exportDir, fileName);

                boolean success = viewModel.exportData(exportFile.getAbsolutePath());

                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    if (success) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("导出成功")
                                .setMessage("数据已导出到:\n" + exportFile.getAbsolutePath() +
                                        "\n\n请妥善保管此文件，其中包含您的加密密码数据。")
                                .setPositiveButton("确定", null)
                                .show();
                    } else {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("导出失败")
                                .setMessage("导出数据时发生错误，请重试。")
                                .setPositiveButton("确定", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("导出失败")
                            .setMessage("导出数据时发生错误:\n" + e.getMessage())
                            .setPositiveButton("确定", null)
                            .show();
                });
            }
        });
    }

    private void selectImportFile() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        startActivityForResult(android.content.Intent.createChooser(intent, "选择备份文件"), 1002);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1002 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                String filePath = getFilePathFromUri(uri);
                if (filePath != null) {
                    showImportConfirmDialog(filePath);
                } else {
                    Toast.makeText(requireContext(), "无法获取文件路径", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String getFilePathFromUri(android.net.Uri uri) {
        try {
            String[] projection = {android.provider.OpenableColumns.DISPLAY_NAME};
            android.database.Cursor cursor = requireContext().getContentResolver()
                    .query(uri, projection, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String fileName = cursor.getString(nameIndex);
                    cursor.close();

                    java.io.File tempDir = new java.io.File(requireContext().getCacheDir(), "import");
                    if (!tempDir.exists()) tempDir.mkdirs();
                    java.io.File tempFile = new java.io.File(tempDir, fileName);

                    java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                    java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);

                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                    outputStream.close();
                    inputStream.close();
                    return tempFile.getAbsolutePath();
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "获取文件路径失败", e);
        }
        return null;
    }

    private void showImportConfirmDialog(String filePath) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认导入")
                .setMessage("即将从备份文件导入数据。\n\n注意：这将覆盖现有的密码数据。\n\n是否继续？")
                .setPositiveButton("导入", (dialog, which) -> performImportData(filePath))
                .setNegativeButton("取消", null)
                .show();
    }

    private void performImportData(String filePath) {
        com.ttt.safevault.utils.LoadingDialog loadingDialog =
                com.ttt.safevault.utils.LoadingDialog.show(requireContext(), "正在导入数据...");

        new android.os.Handler().post(() -> {
            try {
                boolean success = viewModel.importData(filePath);
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    if (success) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("导入成功")
                                .setMessage("数据已成功导入。")
                                .setPositiveButton("确定", (d, w) -> {
                                    if (getActivity() != null) getActivity().recreate();
                                })
                                .show();
                    } else {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("导入失败")
                                .setMessage("导入数据时发生错误，请检查文件是否正确。")
                                .setPositiveButton("确定", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("导入失败")
                            .setMessage("导入数据时发生错误:\n" + e.getMessage())
                            .setPositiveButton("确定", null)
                            .show();
                });
            }
        });
    }

    // ========== Key Version Display ==========

    private void updateKeyVersionDisplay(@NonNull AccountSecurityViewModel.KeyVersionInfo info) {
        if (binding == null) return;

        if (info.version != null) {
            String versionText;
            String summaryText;
            int statusColor;

            if ("v3".equals(info.version)) {
                versionText = "v3.0 (X25519)";
                summaryText = info.hasMigratedToV3
                        ? "已从 RSA 迁移到 X25519"
                        : "已使用最新加密算法";
                statusColor = android.graphics.Color.parseColor("#4CAF50");
            } else {
                versionText = "v2.0 (RSA)";
                summaryText = "建议迁移到 X25519 以获得更好的性能和安全性";
                statusColor = android.graphics.Color.parseColor("#FF9800");
            }

            binding.tvKeyVersionValue.setText(versionText);
            binding.tvKeyVersionValue.setTextColor(statusColor);
            binding.tvKeyVersionSummary.setText(summaryText);
        } else {
            binding.tvKeyVersionValue.setText("未知");
            binding.tvKeyVersionSummary.setText("密钥信息未找到");
        }
    }

    // ========== Key Migration ==========

    private void startKeyMigration() {
        try {
            if (!viewModel.isSessionReadyForMigration()) {
                showPasswordVerificationForMigration();
                return;
            }

            android.content.Intent intent = new android.content.Intent(
                    requireContext(), KeyMigrationActivity.class);
            requireContext().startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "启动密钥迁移失败", e);
            Toast.makeText(requireContext(), "启动迁移失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showPasswordVerificationForMigration() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_input, null);
        com.google.android.material.textfield.TextInputEditText passwordInput =
                dialogView.findViewById(R.id.passwordInput);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("验证身份")
                .setMessage("迁移加密算法需要验证您的身份，请输入主密码。")
                .setView(dialogView)
                .setPositiveButton("验证", (dialog, which) -> {
                    String password = passwordInput.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(requireContext(), "密码不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean authenticated = viewModel.verifyPassword(password);
                    if (authenticated) {
                        android.content.Intent intent = new android.content.Intent(
                                requireContext(), KeyMigrationActivity.class);
                        requireContext().startActivity(intent);
                    } else {
                        Toast.makeText(requireContext(), "密码错误，验证失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ========== Lifecycle ==========

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
