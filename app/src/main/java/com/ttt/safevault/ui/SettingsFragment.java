package com.ttt.safevault.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
        updateAutofillStatus();
    }

    /**
     * 更新自动填充服务状态
     */
    private void updateAutofillStatus() {
        if (binding == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofillManager = requireContext().getSystemService(AutofillManager.class);
            if (autofillManager != null && autofillManager.hasEnabledAutofillServices()) {
                binding.autofillStatus.setText(R.string.autofill_service_enabled);
                binding.autofillStatus.setTextColor(getResources().getColor(R.color.strength_strong, null));
            } else {
                binding.autofillStatus.setText(R.string.autofill_service_disabled);
                binding.autofillStatus.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
            }
        } else {
            // Android 8.0 以下不支持自动填充
            binding.cardAutofillService.setVisibility(View.GONE);
        }
    }

    /**
     * 打开自动填充设置
     */
    private void openAutofillSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofillManager = requireContext().getSystemService(AutofillManager.class);

            if (autofillManager != null && autofillManager.hasEnabledAutofillServices()) {
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
                    ex.printStackTrace();
                }
            }
        }
    }
}
