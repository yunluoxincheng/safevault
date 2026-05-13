package com.ttt.safevault.ui;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.ttt.safevault.R;
import com.ttt.safevault.databinding.FragmentAppearanceSettingsBinding;
import com.ttt.safevault.security.SecurityConfig;

/**
 * 外观设置 Fragment
 * 管理主题和动态颜色配置
 */
public class AppearanceSettingsFragment extends BaseFragment {

    private FragmentAppearanceSettingsBinding binding;
    private SecurityConfig securityConfig;
    private boolean isLoadingSettings = false; // 防止加载设置时触发监听器

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAppearanceSettingsBinding.inflate(inflater, container, false);
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
        isLoadingSettings = true;

        // 先移除动态颜色监听器，避免加载时触发
        binding.switchDynamicColor.setOnCheckedChangeListener(null);

        // 加载主题模式
        SecurityConfig.ThemeMode themeMode = securityConfig.getThemeMode();
        switch (themeMode) {
            case SYSTEM:
                binding.rbThemeSystem.setChecked(true);
                break;
            case LIGHT:
                binding.rbThemeLight.setChecked(true);
                break;
            case DARK:
                binding.rbThemeDark.setChecked(true);
                break;
        }

        // 动态颜色（仅 Android 12+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.layoutDynamicColor.setVisibility(View.VISIBLE);
            binding.switchDynamicColor.setChecked(securityConfig.isDynamicColorEnabled());
        } else {
            binding.layoutDynamicColor.setVisibility(View.GONE);
        }

        // 重新设置动态颜色监听器
        setupDynamicColorListener();

        isLoadingSettings = false;
    }

    private void setupDynamicColorListener() {
        binding.switchDynamicColor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isLoadingSettings) { // 只在用户操作时处理
                boolean oldValue = securityConfig.isDynamicColorEnabled();
                if (oldValue != isChecked) {
                    securityConfig.setDynamicColorEnabled(isChecked);
                    // 动态颜色需要重建 Activity 才能生效
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        requireActivity().recreate();
                    }
                }
            }
        });
    }

    private void setupClickListeners() {
        // 使用 RadioGroup.OnCheckedChangeListener 来处理主题选择
        binding.rgThemeMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (isLoadingSettings) return; // 加载设置时不处理

            SecurityConfig.ThemeMode newThemeMode;
            int nightMode;

            if (checkedId == R.id.rb_theme_system) {
                newThemeMode = SecurityConfig.ThemeMode.SYSTEM;
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            } else if (checkedId == R.id.rb_theme_light) {
                newThemeMode = SecurityConfig.ThemeMode.LIGHT;
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.rb_theme_dark) {
                newThemeMode = SecurityConfig.ThemeMode.DARK;
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
            } else {
                return;
            }

            // 只在主题真正改变时才应用
            if (securityConfig.getThemeMode() != newThemeMode) {
                securityConfig.setThemeMode(newThemeMode);
                AppCompatDelegate.setDefaultNightMode(nightMode);
            }
        });

        // 语言选项（预留）
        binding.cardLanguage.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "多语言支持即将推出", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
