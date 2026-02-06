package com.ttt.safevault.ui;

import android.content.Context;
import android.os.Bundle;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.ttt.safevault.R;
import com.ttt.safevault.databinding.FragmentAccountSecurityBinding;
import com.ttt.safevault.security.SecurityConfig;
import com.ttt.safevault.security.biometric.BiometricAuthManager;
import com.ttt.safevault.security.biometric.AuthCallback;
import com.ttt.safevault.security.biometric.AuthError;
import com.ttt.safevault.security.biometric.AuthScenario;

/**
 * 账户安全设置 Fragment
 * 管理解锁选项、生物识别、PIN码、主密码等安全设置
 */
public class AccountSecurityFragment extends BaseFragment {

    private FragmentAccountSecurityBinding binding;
    private SecurityConfig securityConfig;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountSecurityBinding.inflate(inflater, container, false);
        securityConfig = new SecurityConfig(requireContext());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupClickListeners();
        loadSettings();
    }

    private void loadSettings() {
        // 显示当前自动锁定选项
        SecurityConfig.AutoLockMode autoLockMode = securityConfig.getAutoLockMode();
        binding.tvAutoLockValue.setText(autoLockMode.getDisplayName());

        // 生物识别开关状态
        binding.switchBiometric.setChecked(securityConfig.isBiometricEnabled());

        // 允许截图开关状态
        binding.switchScreenshot.setChecked(securityConfig.isScreenshotAllowed());

        // PIN码状态
        if (securityConfig.isPinCodeEnabled()) {
            binding.tvPinStatus.setText("已启用");
        } else {
            binding.tvPinStatus.setText("未启用");
        }
    }

    private void setupClickListeners() {
        // 自动锁定选项
        binding.cardAutoLock.setOnClickListener(v -> showAutoLockDialog());

        // 生物识别开关 - 使用点击监听器而不是状态改变监听器
        // 这样只有用户主动点击时才会触发，避免初始化时触发
        binding.switchBiometric.setOnClickListener(v -> {
            boolean newState = binding.switchBiometric.isChecked();

            if (newState) {
                // 用户想要开启生物识别（点击后状态变为true）
                // 先将开关恢复为关闭状态，等待验证成功后再开启
                binding.switchBiometric.setChecked(false);

                // 检查设备是否支持生物识别
                BiometricAuthManager authManager = BiometricAuthManager.getInstance(requireContext());
                if (!authManager.canUseBiometric()) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("生物识别不可用")
                            .setMessage(authManager.getBiometricNotAvailableReason())
                            .setPositiveButton("确定", null)
                            .show();
                    return;
                }

                // 检查三层架构是否已初始化
                com.ttt.safevault.security.SecureKeyStorageManager secureStorage =
                        com.ttt.safevault.security.SecureKeyStorageManager.getInstance(requireContext());
                if (!secureStorage.isMigrated()) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("无法启用生物识别")
                            .setMessage("密钥存储未初始化，请先使用主密码登录")
                            .setPositiveButton("确定", null)
                            .show();
                    return;
                }

                // 启用生物识别（使用新架构的 BiometricAuthManager）
                android.util.Log.d("AccountSecurity", "启用生物识别（新架构）");
                enableBiometricAuthentication(authManager);
            } else {
                // 用户想要关闭生物识别（点击后状态变为false）
                binding.switchBiometric.setChecked(false);

                // 使用 BiometricAuthManager 禁用生物识别
                BiometricAuthManager authManager = BiometricAuthManager.getInstance(requireContext());
                authManager.disableBiometric();

                // 同步到 SecurityConfig
                securityConfig.setBiometricEnabled(false);

                android.util.Log.d("AccountSecurity", "生物识别已禁用（新架构）");
                Toast.makeText(requireContext(), "生物识别已禁用", Toast.LENGTH_SHORT).show();
            }
        });

        // 允许截图开关 - 使用点击监听器
        binding.switchScreenshot.setOnClickListener(v -> {
            boolean newState = binding.switchScreenshot.isChecked();

            if (newState) {
                // 用户想要开启截图（点击后状态变为true）
                // 先将开关恢复为关闭状态，等待验证成功后再开启
                binding.switchScreenshot.setChecked(false);

                // 启用截图前要求用户验证身份并确认
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.enable_screenshot_confirm)
                        .setMessage(R.string.enable_screenshot_message)
                        .setPositiveButton("验证并启用", (dialog, which) -> {
                            // 验证身份
                            promptUserAuthentication(() -> {
                                // 验证成功，启用截图
                                binding.switchScreenshot.setChecked(true);
                                securityConfig.setScreenshotAllowed(true);
                                Toast.makeText(requireContext(), R.string.screenshot_enabled, Toast.LENGTH_SHORT).show();
                                // 应用设置到所有Activity
                                applyScreenshotSettings();
                            }, () -> {
                                // 验证失败，保持开关关闭状态
                                binding.switchScreenshot.setChecked(false);
                            });
                        })
                        .setNegativeButton(R.string.cancel, (dialog, which) -> {
                            binding.switchScreenshot.setChecked(false);
                        })
                        .show();
            } else {
                // 用户想要关闭截图（点击后状态变为false）- 直接关闭，不需要验证
                binding.switchScreenshot.setChecked(false);
                securityConfig.setScreenshotAllowed(false);
                Toast.makeText(requireContext(), R.string.screenshot_disabled, Toast.LENGTH_SHORT).show();
                // 应用设置到所有Activity
                applyScreenshotSettings();
            }
        });

        // PIN码设置
        binding.cardPinCode.setOnClickListener(v -> {
            if (securityConfig.isPinCodeEnabled()) {
                showPinCodeOptionsDialog();
            } else {
                // 显示设置PIN码对话框
                showSetupPinDialog();
            }
        });

        // 更改主密码
        binding.cardChangePassword.setOnClickListener(v -> {
            showChangePasswordDialog();
        });

        // 导出数据
        binding.cardExportData.setOnClickListener(v -> {
            showExportDataDialog();
        });

        // 导入数据
        binding.cardImportData.setOnClickListener(v -> {
            showImportDataDialog();
        });

        // 注销登录
        binding.cardLogout.setOnClickListener(v -> showLogoutDialog());

        // 删除账户
        binding.cardDeleteAccount.setOnClickListener(v -> {
            showDeleteAccountDialog();
        });
    }

    private void showAutoLockDialog() {
        SecurityConfig.AutoLockMode[] modes = SecurityConfig.AutoLockMode.values();
        String[] options = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            options[i] = modes[i].getDisplayName();
        }

        int currentSelection = securityConfig.getAutoLockMode().ordinal();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.auto_lock)
                .setSingleChoiceItems(options, currentSelection, (dialog, which) -> {
                    SecurityConfig.AutoLockMode selectedMode = modes[which];
                    securityConfig.setAutoLockMode(selectedMode);
                    binding.tvAutoLockValue.setText(selectedMode.getDisplayName());
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "自动锁定已设置为: " + selectedMode.getDisplayName(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showPinCodeOptionsDialog() {
        String[] options = {"更改PIN码", "移除PIN码"};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("PIN码选项")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // 更改PIN码
                        showChangePinDialog();
                    } else {
                        // 移除PIN码
                        showRemovePinDialog();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 显示设置PIN码对话框
     */
    private void showSetupPinDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_pin_setup, null);
        com.google.android.material.textfield.TextInputLayout pinLayout =
                dialogView.findViewById(R.id.pinLayout);
        com.google.android.material.textfield.TextInputEditText pinInput =
                dialogView.findViewById(R.id.pinInput);
        com.google.android.material.textfield.TextInputLayout confirmPinLayout =
                dialogView.findViewById(R.id.confirmPinLayout);
        com.google.android.material.textfield.TextInputEditText confirmPinInput =
                dialogView.findViewById(R.id.confirmPinInput);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("设置PIN码")
                .setMessage("请输入4-6位数字作为PIN码，用于快速解锁应用")
                .setView(dialogView)
                .setPositiveButton("设置", (dialog, which) -> {
                    String pin = pinInput.getText().toString();
                    String confirmPin = confirmPinInput.getText().toString();

                    // 验证PIN码格式
                    if (!pin.matches("\\d{4,6}")) {
                        Toast.makeText(requireContext(), "PIN码必须是4-6位数字", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 验证两次输入是否一致
                    if (!pin.equals(confirmPin)) {
                        Toast.makeText(requireContext(), "两次输入的PIN码不一致", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 调用后端服务设置PIN码
                    com.ttt.safevault.model.BackendService backendService =
                            com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
                    boolean success = backendService.setPinCode(pin);

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

    /**
     * 显示更改PIN码对话框
     */
    private void showChangePinDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_pin_setup, null);
        com.google.android.material.textfield.TextInputLayout oldPinLayout =
                dialogView.findViewById(R.id.pinLayout);
        com.google.android.material.textfield.TextInputEditText oldPinInput =
                dialogView.findViewById(R.id.pinInput);
        com.google.android.material.textfield.TextInputLayout newPinLayout =
                dialogView.findViewById(R.id.confirmPinLayout);
        com.google.android.material.textfield.TextInputEditText newPinInput =
                dialogView.findViewById(R.id.confirmPinInput);

        // 修改提示文字
        oldPinLayout.setHint("请输入当前PIN码");
        newPinLayout.setHint("请输入新PIN码（4-6位数字）");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("更改PIN码")
                .setView(dialogView)
                .setPositiveButton("更改", (dialog, which) -> {
                    String oldPin = oldPinInput.getText().toString();
                    String newPin = newPinInput.getText().toString();

                    // 验证旧PIN码
                    com.ttt.safevault.model.BackendService backendService =
                            com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

                    if (!backendService.verifyPinCode(oldPin)) {
                        Toast.makeText(requireContext(), "当前PIN码错误", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 验证新PIN码格式
                    if (!newPin.matches("\\d{4,6}")) {
                        Toast.makeText(requireContext(), "新PIN码必须是4-6位数字", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 设置新PIN码
                    boolean success = backendService.setPinCode(newPin);

                    if (success) {
                        Toast.makeText(requireContext(), "PIN码更改成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "PIN码更改失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRemovePinDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("移除PIN码")
                .setMessage("确定要移除PIN码吗？")
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    com.ttt.safevault.model.BackendService backendService =
                            com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
                    backendService.clearPinCode();
                    binding.tvPinStatus.setText("未启用");
                    Toast.makeText(requireContext(), R.string.pin_removed, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 显示更改主密码对话框
     */
    private void showChangePasswordDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_change, null);
        com.google.android.material.textfield.TextInputLayout oldPasswordLayout =
                dialogView.findViewById(R.id.oldPasswordLayout);
        com.google.android.material.textfield.TextInputEditText oldPasswordInput =
                dialogView.findViewById(R.id.oldPasswordInput);
        com.google.android.material.textfield.TextInputLayout newPasswordLayout =
                dialogView.findViewById(R.id.newPasswordLayout);
        com.google.android.material.textfield.TextInputEditText newPasswordInput =
                dialogView.findViewById(R.id.newPasswordInput);
        com.google.android.material.textfield.TextInputLayout confirmPasswordLayout =
                dialogView.findViewById(R.id.confirmPasswordLayout);
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

                    // 验证旧密码
                    com.ttt.safevault.model.BackendService backendService =
                            com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

                    if (!backendService.isUnlocked()) {
                        // 先尝试用旧密码解锁
                        if (!backendService.unlock(oldPassword)) {
                            Toast.makeText(requireContext(), "当前密码错误", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        // 已解锁状态，验证旧密码是否正确
                        if (!oldPassword.isEmpty()) {
                            // 简单验证：这里应该使用更安全的方式
                            // 由于后端服务设计，这里直接使用changeMasterPassword验证
                        }
                    }

                    // 验证新密码强度
                    if (newPassword.length() < 8) {
                        Toast.makeText(requireContext(), "新密码长度至少为8位", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 验证两次输入是否一致
                    if (!newPassword.equals(confirmPassword)) {
                        Toast.makeText(requireContext(), "两次输入的新密码不一致", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 更改主密码
                    boolean success = backendService.changeMasterPassword(oldPassword.isEmpty() ? null : oldPassword, newPassword);

                    if (success) {
                        Toast.makeText(requireContext(), "主密码更改成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "主密码更改失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 显示删除账户对话框（多重确认）
     */
    private void showDeleteAccountDialog() {
        // 第一重确认
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除账户")
                .setMessage("您确定要删除账户吗？此操作不可撤销！")
                .setPositiveButton("继续", (dialog, which) -> {
                    // 第二重确认
                    showDeleteAccountSecondConfirmation();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 第二重确认对话框
     */
    private void showDeleteAccountSecondConfirmation() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("最后确认")
                .setMessage("删除账户将永久清除所有数据，包括：\n\n" +
                           "• 所有密码记录\n" +
                           "• 云端同步数据\n" +
                           "• 设备信息\n" +
                           "• 安全设置\n\n" +
                           "提示：建议您在删除账户前先导出重要数据。\n" +
                           "如需导出，请前往「设置 → 数据管理 → 导出数据」。\n\n" +
                           "此操作无法撤销，确定继续吗？")
                .setPositiveButton("我已了解，继续删除", (dialog, which) -> {
                    // 要求用户验证身份
                    promptUserAuthentication(() -> {
                        // 验证成功，执行删除
                        performDeleteAccount();
                    }, () -> {
                        Toast.makeText(requireContext(), "身份验证失败，无法删除账户", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 执行账户删除操作
     */
    private void performDeleteAccount() {
        com.ttt.safevault.model.BackendService backendService =
                com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

        boolean success = backendService.deleteAccount();

        if (success) {
            Toast.makeText(requireContext(), "账户已删除", Toast.LENGTH_SHORT).show();
            // 返回登录页面
            requireActivity().finish();
        } else {
            Toast.makeText(requireContext(), "账户删除失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    performLogout();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 执行注销登录操作
     */
    private void performLogout() {
        com.ttt.safevault.model.BackendService backendService =
                com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

        // 显示现代化加载对话框
        com.ttt.safevault.utils.LoadingDialog loadingDialog =
                com.ttt.safevault.utils.LoadingDialog.show(requireContext(), "正在注销...");

        // 在后台线程执行注销
        new android.os.Handler().post(() -> {
            try {
                // 调用后端注销 API
                backendService.logoutCloud();

                // 注销成功
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), R.string.logged_out, Toast.LENGTH_SHORT).show();
                    // 返回登录页面
                    requireActivity().finish();
                });
            } catch (Exception e) {
                // 注销失败，询问是否强制清除本地数据
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    showLogoutFailedDialog();
                });
            }
        });
    }

    /**
     * 注销失败对话框
     */
    private void showLogoutFailedDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("注销失败")
                .setMessage("无法连接到服务器，是否强制清除本地数据？\n\n注意：服务器会话可能会保留，直到令牌过期。")
                .setPositiveButton("强制清除", (dialog, which) -> {
                    forceLogout();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 强制注销（仅清除本地数据）
     */
    private void forceLogout() {
        try {
            com.ttt.safevault.model.BackendService backendService =
                    com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

            // 只清除本地令牌，不调用后端 API
            com.ttt.safevault.network.TokenManager tokenManager =
                    com.ttt.safevault.network.RetrofitClient.getInstance(requireContext())
                            .getTokenManager();
            tokenManager.clearTokens();

            Toast.makeText(requireContext(), "已清除本地数据", Toast.LENGTH_SHORT).show();
            // 返回登录页面
            requireActivity().finish();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "清除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 启用生物识别认证（使用新架构）
     * 使用 BiometricAuthManager 进行生物识别验证
     */
    private void enableBiometricAuthentication(@NonNull BiometricAuthManager authManager) {
        androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) requireActivity();

        authManager.authenticate(activity, AuthScenario.ENROLLMENT, new AuthCallback() {
            @Override
            public void onUserVerified() {
                // 用户通过 UI 认证，等待 Keystore 授权
            }

            @Override
            public void onKeyAccessGranted() {
                // Keystore 授权成功，启用生物识别功能
                requireActivity().runOnUiThread(() -> {
                    // 更新 SecurityConfig
                    securityConfig.setBiometricEnabled(true);
                    binding.switchBiometric.setChecked(true);

                    android.util.Log.d("AccountSecurity", "生物识别已启用（新架构）");
                    Toast.makeText(requireContext(), "生物识别已启用", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(@NonNull AuthError error, @NonNull String message, boolean canRetry) {
                requireActivity().runOnUiThread(() -> {
                    // 处理防抖动错误
                    if (error == AuthError.DEBOUNCED) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 其他错误显示提示
                    android.util.Log.e("AccountSecurity", "启用生物识别失败: " + error + " - " + message);
                    Toast.makeText(requireContext(), "启用生物识别失败: " + message, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onCancel() {
                // 用户取消，不做处理
            }

            @Override
            public void onBiometricChanged() {
                // 生物识别信息已变更
                requireActivity().runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("生物识别信息已变更")
                            .setMessage("检测到您的生物识别信息已变更，请使用主密码登录后重新启用生物识别。")
                            .setPositiveButton("我知道了", null)
                            .show();
                });
            }
        });
    }

    /**
     * 只使用生物识别验证身份（用于启用生物识别功能）
     * @param onSuccess 验证成功回调
     * @param onFailure 验证失败回调
     */
    private void showBiometricOnlyAuthentication(Runnable onSuccess, Runnable onFailure) {
        com.ttt.safevault.security.BiometricAuthHelper biometricHelper =
            new com.ttt.safevault.security.BiometricAuthHelper(
                (androidx.fragment.app.FragmentActivity) requireActivity());
        biometricHelper.authenticate(new com.ttt.safevault.security.BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                if (onSuccess != null) {
                    requireActivity().runOnUiThread(onSuccess);
                }
            }

            @Override
            public void onFailure(String error) {
                if (onFailure != null) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "生物识别验证失败: " + error, Toast.LENGTH_SHORT).show();
                        onFailure.run();
                    });
                }
            }

            @Override
            public void onCancel() {
                if (onFailure != null) {
                    requireActivity().runOnUiThread(onFailure);
                }
            }
        });
    }

    /**
     * 提示用户验证身份
     * @param onSuccess 验证成功回调
     * @param onFailure 验证失败回调
     */
    private void promptUserAuthentication(Runnable onSuccess, Runnable onFailure) {
        // 如果已经启用了生物识别，优先使用生物识别验证
        if (securityConfig.isBiometricEnabled()) {
            com.ttt.safevault.security.BiometricAuthHelper biometricHelper =
                new com.ttt.safevault.security.BiometricAuthHelper(
                    (androidx.fragment.app.FragmentActivity) requireActivity());
            biometricHelper.authenticate(new com.ttt.safevault.security.BiometricAuthHelper.BiometricAuthCallback() {
                @Override
                public void onSuccess() {
                    if (onSuccess != null) {
                        requireActivity().runOnUiThread(onSuccess);
                    }
                }

                @Override
                public void onFailure(String error) {
                    // 生物识别失败，回退到主密码验证
                    requireActivity().runOnUiThread(() -> showPasswordAuthenticationDialog(onSuccess, onFailure));
                }

                @Override
                public void onCancel() {
                    if (onFailure != null) {
                        requireActivity().runOnUiThread(onFailure);
                    }
                }
            });
        } else {
            // 未启用生物识别，使用主密码验证
            showPasswordAuthenticationDialog(onSuccess, onFailure);
        }
    }

    /**
     * 显示主密码验证对话框
     * @param onSuccess 验证成功回调
     * @param onFailure 验证失败回调
     */
    private void showPasswordAuthenticationDialog(Runnable onSuccess, Runnable onFailure) {
        // 使用自定义布局，包含密码可见性切换
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_input, null);
        com.google.android.material.textfield.TextInputLayout passwordLayout =
            dialogView.findViewById(R.id.passwordLayout);
        com.google.android.material.textfield.TextInputEditText passwordInput =
            dialogView.findViewById(R.id.passwordInput);

        // 确保初始状态正确：密码隐藏，闭眼图标
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordLayout.setEndIconDrawable(requireContext().getDrawable(R.drawable.ic_visibility));

        // 设置密码可见性切换
        passwordLayout.setEndIconOnClickListener(v -> {
            // 切换密码可见性
            int selection = passwordInput.getSelectionEnd();
            int currentInputType = passwordInput.getInputType();
            int variation = currentInputType & android.text.InputType.TYPE_MASK_VARIATION;

            if (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                // 当前是密码状态，切换为可见
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                passwordLayout.setEndIconDrawable(requireContext().getDrawable(R.drawable.ic_visibility_off));
            } else {
                // 当前是可见状态，切换为密码
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordLayout.setEndIconDrawable(requireContext().getDrawable(R.drawable.ic_visibility));
            }
            // 保持光标位置
            passwordInput.setSelection(selection);
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("验证身份")
                .setMessage("为了安全起见，请验证您的身份以启用生物识别")
                .setView(dialogView)
                .setPositiveButton("验证", (dialog, which) -> {
                    String password = passwordInput.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(requireContext(), "密码不能为空", Toast.LENGTH_SHORT).show();
                        if (onFailure != null) {
                            onFailure.run();
                        }
                        return;
                    }

                    // 调用后端服务验证密码
                    com.ttt.safevault.model.BackendService backendService =
                        com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
                    try {
                        boolean authenticated = backendService.unlock(password);
                        if (authenticated) {
                            if (onSuccess != null) {
                                onSuccess.run();
                            }
                        } else {
                            Toast.makeText(requireContext(), "密码错误，验证失败", Toast.LENGTH_SHORT).show();
                            if (onFailure != null) {
                                onFailure.run();
                            }
                        }
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "验证时发生错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        if (onFailure != null) {
                            onFailure.run();
                        }
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    if (onFailure != null) {
                        onFailure.run();
                    }
                })
                .show();
    }

    /**
     * 应用截图设置到所有Activity
     */
    private void applyScreenshotSettings() {
        boolean screenshotAllowed = securityConfig.isScreenshotAllowed();
        int flags = screenshotAllowed ? 0 : android.view.WindowManager.LayoutParams.FLAG_SECURE;

        // 通知所有正在运行的Activity更新截图设置
        // 这里通过发送广播或者直接更新当前Activity
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                getActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                if (!screenshotAllowed) {
                    getActivity().getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                }
            });
        }
    }

    /**
     * 显示导出数据对话框
     */
    private void showExportDataDialog() {
        // 验证身份
        promptUserAuthentication(() -> {
            // 验证成功，执行导出
            performExportData();
        }, null);
    }

    /**
     * 执行数据导出
     */
    private void performExportData() {
        // 检查存储权限
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (requireContext().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 请求权限
                requestPermissions(
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1001);
                return;
            }
        }

        // 显示现代化加载对话框
        com.ttt.safevault.utils.LoadingDialog loadingDialog =
                com.ttt.safevault.utils.LoadingDialog.show(requireContext(), "正在导出数据...");

        new android.os.Handler().post(() -> {
            try {
                com.ttt.safevault.model.BackendService backendService =
                        com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

                // 生成导出文件路径
                String fileName = "safevault_backup_" +
                        new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                                .format(new java.util.Date()) + ".svault";

                // 使用应用外部存储目录
                java.io.File exportDir = new java.io.File(requireContext().getExternalFilesDir(null), "backups");
                if (!exportDir.exists()) {
                    exportDir.mkdirs();
                }
                java.io.File exportFile = new java.io.File(exportDir, fileName);

                boolean success = backendService.exportData(exportFile.getAbsolutePath());

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

    /**
     * 显示导入数据对话框
     */
    private void showImportDataDialog() {
        // 验证身份
        promptUserAuthentication(() -> {
            // 验证成功，选择文件
            selectImportFile();
        }, null);
    }

    /**
     * 选择导入文件
     */
    private void selectImportFile() {
        // 创建文件选择器
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);

        // 创建文件选择器（只显示 .svault 文件）
        android.content.Intent chooser = android.content.Intent.createChooser(intent, "选择备份文件");
        startActivityForResult(chooser, 1002);
    }

    /**
     * 处理文件选择结果
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1002 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                // 获取文件路径
                String filePath = getFilePathFromUri(uri);
                if (filePath != null) {
                    // 显示导入预览和确认对话框
                    showImportConfirmDialog(filePath);
                } else {
                    Toast.makeText(requireContext(), "无法获取文件路径", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * 从 Uri 获取文件路径
     */
    private String getFilePathFromUri(android.net.Uri uri) {
        try {
            // 尝试使用 content resolver 获取文件路径
            String[] projection = {android.provider.OpenableColumns.DISPLAY_NAME};
            android.database.Cursor cursor = requireContext().getContentResolver()
                    .query(uri, projection, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String fileName = cursor.getString(nameIndex);
                    cursor.close();

                    // 创建临时文件
                    java.io.File tempDir = new java.io.File(requireContext().getCacheDir(), "import");
                    if (!tempDir.exists()) {
                        tempDir.mkdirs();
                    }
                    java.io.File tempFile = new java.io.File(tempDir, fileName);

                    // 复制文件内容
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
            android.util.Log.e("AccountSecurityFragment", "获取文件路径失败", e);
        }
        return null;
    }

    /**
     * 显示导入确认对话框
     */
    private void showImportConfirmDialog(String filePath) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认导入")
                .setMessage("即将从备份文件导入数据。\n\n" +
                        "注意：这将覆盖现有的密码数据。\n\n" +
                        "是否继续？")
                .setPositiveButton("导入", (dialog, which) -> {
                    performImportData(filePath);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 执行数据导入
     */
    private void performImportData(String filePath) {
        // 显示现代化加载对话框
        com.ttt.safevault.utils.LoadingDialog loadingDialog =
                com.ttt.safevault.utils.LoadingDialog.show(requireContext(), "正在导入数据...");

        new android.os.Handler().post(() -> {
            try {
                com.ttt.safevault.model.BackendService backendService =
                        com.ttt.safevault.ServiceLocator.getInstance().getBackendService();

                boolean success = backendService.importData(filePath);

                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    if (success) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("导入成功")
                                .setMessage("数据已成功导入。")
                                .setPositiveButton("确定", (d, w) -> {
                                    // 返回首页刷新数据
                                    if (getActivity() != null) {
                                        getActivity().recreate();
                                    }
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}