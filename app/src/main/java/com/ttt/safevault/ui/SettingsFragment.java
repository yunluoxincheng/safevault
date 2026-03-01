package com.ttt.safevault.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.autofill.AutofillManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;
import com.ttt.safevault.databinding.FragmentSettingsBinding;

/**
 * 设置主页面 Fragment
 * 显示四个主要设置分类的入口
 */
public class SettingsFragment extends BaseFragment {
    private static final String TAG = "SettingsFragment";

    private FragmentSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupClickListeners();
        updateAutofillStatus();
    }

    private void setupClickListeners() {
        // 账户安全
        binding.cardAccountSecurity.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_settings_to_accountSecurity));

        // 分享历史
        binding.cardShareHistory.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), com.ttt.safevault.ui.share.ShareHistoryActivity.class);
            startActivity(intent);
        });

        // 自动填充服务
        binding.cardAutofillService.setOnClickListener(v -> openAutofillSettings());

        // 外观设置
        binding.cardAppearance.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_settings_to_appearanceSettings));

        // 云端同步
        binding.cardCloudSync.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_settingsFragment_to_syncSettingsFragment));

        // 关于
        binding.cardAbout.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_settings_to_about));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次返回时更新自动填充状态
        // 使用多次重试机制，因为 getAutofillServiceComponentName() 可能需要时间准备
        binding.autofillStatus.postDelayed(() -> updateAutofillStatus(), 100);
        binding.autofillStatus.postDelayed(() -> updateAutofillStatus(), 500);
        binding.autofillStatus.postDelayed(() -> updateAutofillStatus(), 1000);
    }

    /**
     * 更新自动填充服务状态
     */
    private void updateAutofillStatus() {
        if (binding == null) {
            Log.w(TAG, "updateAutofillStatus: binding is null");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofillManager = requireContext().getSystemService(AutofillManager.class);
            Log.d(TAG, "updateAutofillStatus: AutofillManager = " + (autofillManager != null));
            Log.d(TAG, "updateAutofillStatus: SDK_INT = " + Build.VERSION.SDK_INT);

            if (autofillManager != null) {
                boolean isOurServiceEnabled = isOurAutofillServiceEnabled(autofillManager);
                Log.d(TAG, "updateAutofillStatus: isOurServiceEnabled = " + isOurServiceEnabled);

                // 在主线程更新 UI
                binding.autofillStatus.post(() -> {
                    if (isOurServiceEnabled) {
                        Log.d(TAG, "设置状态为：已启用");
                        binding.autofillStatus.setText(R.string.autofill_service_enabled);
                        binding.autofillStatus.setTextColor(getResources().getColor(R.color.strength_strong, null));
                    } else {
                        Log.d(TAG, "设置状态为：未启用");
                        binding.autofillStatus.setText(R.string.autofill_service_disabled);
                        binding.autofillStatus.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
                    }
                    Log.d(TAG, "当前文字: " + binding.autofillStatus.getText());
                });

                // 如果是 Android 11+ 且第一次检查返回未启用，稍后重试一次
                // 因为 getAutofillServiceComponentName() 可能需要时间准备
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isOurServiceEnabled) {
                    final boolean firstResult = isOurServiceEnabled;
                    binding.autofillStatus.postDelayed(() -> {
                        boolean retryResult = isOurAutofillServiceEnabled(autofillManager);
                        Log.d(TAG, "重试检查: isOurServiceEnabled = " + retryResult);
                        if (retryResult != firstResult) {
                            Log.d(TAG, "重试结果不同，更新UI");
                            if (retryResult) {
                                binding.autofillStatus.setText(R.string.autofill_service_enabled);
                                binding.autofillStatus.setTextColor(getResources().getColor(R.color.strength_strong, null));
                            } else {
                                binding.autofillStatus.setText(R.string.autofill_service_disabled);
                                binding.autofillStatus.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
                            }
                        }
                    }, 300);
                }
            } else {
                // AutofillManager 为空，视为未启用
                Log.d(TAG, "AutofillManager 为 null，设置为未启用");
                binding.autofillStatus.setText(R.string.autofill_service_disabled);
                binding.autofillStatus.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
            }
        } else {
            // Android 8.0 以下不支持自动填充
            binding.cardAutofillService.setVisibility(View.GONE);
        }
    }

    /**
     * 检查是否启用了当前应用的自动填充服务
     *
     * @param autofillManager AutofillManager 实例
     * @return 是否启用了 SafeVault 的自动填充服务
     */
    private boolean isOurAutofillServiceEnabled(@NonNull AutofillManager autofillManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }

        // 方法1: 使用 Settings.Secure 获取当前启用的自动填充服务
        String enabledService = null;
        try {
            android.content.ContentResolver resolver = requireContext().getContentResolver();
            enabledService = android.provider.Settings.Secure.getString(resolver, "autofill_service");
        } catch (Exception e) {
            // 忽略异常，尝试其他方法
        }

        // 如果获取到了服务信息
        if (enabledService != null && !enabledService.isEmpty()) {
            try {
                android.content.ComponentName componentName = android.content.ComponentName.unflattenFromString(enabledService);
                if (componentName != null) {
                    String ourPackageName = requireContext().getPackageName();
                    String ourServiceClassName = ourPackageName + ".autofill.SafeVaultAutofillService";

                    boolean packageNameMatches = componentName.getPackageName().equals(ourPackageName);
                    boolean classNameMatches = componentName.getClassName().equals(ourServiceClassName);

                    return packageNameMatches && classNameMatches;
                }
            } catch (Exception e) {
                // 忽略异常，尝试其他方法
            }
        }

        // 方法2: 使用 getAutofillServiceComponentName() (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                android.content.ComponentName enabledServiceComponent = autofillManager.getAutofillServiceComponentName();
                if (enabledServiceComponent != null) {
                    String ourPackageName = requireContext().getPackageName();
                    String ourServiceClassName = ourPackageName + ".autofill.SafeVaultAutofillService";

                    boolean packageNameMatches = enabledServiceComponent.getPackageName().equals(ourPackageName);
                    boolean classNameMatches = enabledServiceComponent.getClassName().equals(ourServiceClassName);

                    return packageNameMatches && classNameMatches;
                }
            } catch (Exception e) {
                // 忽略异常，尝试其他方法
            }
        }

        // 方法3: 使用 hasEnabledAutofillServices() 作为最后备选
        return autofillManager.hasEnabledAutofillServices();
    }

    /**
     * 打开自动填充设置
     */
    private void openAutofillSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofillManager = requireContext().getSystemService(AutofillManager.class);

            if (autofillManager != null && isOurAutofillServiceEnabled(autofillManager)) {
                // 已启用，显示信息对话框
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.autofill_service)
                    .setMessage("SafeVault 自动填充服务已启用。您可以在系统设置中更改或禁用此服务。")
                    .setPositiveButton(R.string.autofill_go_to_settings, (dialog, which) -> {
                        openSystemAutofillSettings();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            } else {
                // 未启用，引导用户启用
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("启用自动填充")
                    .setMessage("自动填充功能可以让 SafeVault 在其他应用中自动填充您的密码。\n\n请点击下方按钮前往系统设置，选择 SafeVault 作为自动填充服务。")
                    .setPositiveButton(R.string.autofill_go_to_settings, (dialog, which) -> {
                        openSystemAutofillSettings();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            }
        }
    }

    /**
     * 打开系统自动填充设置页面
     */
    private void openSystemAutofillSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE);
                intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                // 如果无法打开特定页面，打开通用设置
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ex) {
                    Log.e(TAG, "打开设置页面失败", ex);
                }
            }
        }
    }
}
