package com.ttt.safevault.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.ttt.safevault.R;
import com.ttt.safevault.databinding.FragmentGeneratorBinding;
import com.ttt.safevault.viewmodel.GeneratorViewModel;
import com.ttt.safevault.utils.ClipboardManager;

/**
 * 密码生成器页面 Fragment
 * 提供独立的密码生成功能，包括实时预览
 */
public class GeneratorFragment extends BaseFragment {

    private FragmentGeneratorBinding binding;
    private GeneratorViewModel viewModel;
    private ClipboardManager clipboardManager;

    // UI 组件
    private TextInputEditText generatedPasswordText;
    private Slider lengthSlider;
    private MaterialSwitch uppercaseSwitch;
    private MaterialSwitch lowercaseSwitch;
    private MaterialSwitch numbersSwitch;
    private MaterialSwitch symbolsSwitch;
    private Button generateButton;
    private MaterialCardView pinPresetCard;
    private MaterialCardView strongPresetCard;
    private MaterialCardView memorablePresetCard;
    private View strengthBar1;
    private View strengthBar2;
    private View strengthBar3;
    private View strengthBar4;
    private TextView strengthText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGeneratorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews();
        initViewModel();
        setupClickListeners();
        initClipboardManager();
        generateInitialPassword();
    }

    private void initViews() {
        generatedPasswordText = binding.generatedPasswordText;
        lengthSlider = binding.lengthSlider;
        uppercaseSwitch = binding.uppercaseSwitch;
        lowercaseSwitch = binding.lowercaseSwitch;
        numbersSwitch = binding.numbersSwitch;
        symbolsSwitch = binding.symbolsSwitch;
        generateButton = binding.generateButton;
        pinPresetCard = binding.pinPresetCard;
        strongPresetCard = binding.strongPresetCard;
        memorablePresetCard = binding.memorablePresetCard;
        strengthBar1 = binding.strengthBar1;
        strengthBar2 = binding.strengthBar2;
        strengthBar3 = binding.strengthBar3;
        strengthBar4 = binding.strengthBar4;
        strengthText = binding.strengthText;

        // 设置默认值
        lengthSlider.setValue(16);
        uppercaseSwitch.setChecked(true);
        lowercaseSwitch.setChecked(true);
        numbersSwitch.setChecked(true);
        symbolsSwitch.setChecked(false);
    }

    private void initClipboardManager() {
        clipboardManager = new ClipboardManager(requireContext());
    }

    private void initViewModel() {
        ViewModelProvider.Factory factory = new com.ttt.safevault.viewmodel.ViewModelFactory(
                requireActivity().getApplication());
        viewModel = new ViewModelProvider(this, factory).get(GeneratorViewModel.class);

        // 观察生成的密码
        viewModel.getGeneratedPassword().observe(getViewLifecycleOwner(), password -> {
            generatedPasswordText.setText(password);
        });

        // 观察密码强度
        viewModel.getPasswordStrength().observe(getViewLifecycleOwner(), strength -> {
            updateStrengthIndicator(strength);
        });
    }

    private void setupClickListeners() {
        // 滑块变化时重新生成
        lengthSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                generatePassword();
            }
        });

        // 开关变化时重新生成
        uppercaseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> generatePassword());
        lowercaseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> generatePassword());
        numbersSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> generatePassword());
        symbolsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> generatePassword());

        // 生成按钮
        generateButton.setOnClickListener(v -> generatePassword());

        // 点击生成的密码文本复制
        generatedPasswordText.setOnClickListener(v -> copyPasswordToClipboard());

        // 预设配置
        pinPresetCard.setOnClickListener(v -> applyPreset(GeneratorViewModel.Preset.PIN));
        strongPresetCard.setOnClickListener(v -> applyPreset(GeneratorViewModel.Preset.STRONG));
        memorablePresetCard.setOnClickListener(v -> applyPreset(GeneratorViewModel.Preset.MEMORABLE));
    }

    private void generateInitialPassword() {
        generatePassword();
    }

    private void generatePassword() {
        int length = (int) lengthSlider.getValue();
        boolean uppercase = uppercaseSwitch.isChecked();
        boolean lowercase = lowercaseSwitch.isChecked();
        boolean numbers = numbersSwitch.isChecked();
        boolean symbols = symbolsSwitch.isChecked();

        viewModel.generatePassword(length, uppercase, lowercase, numbers, symbols);
    }

    private void copyPasswordToClipboard() {
        String password = generatedPasswordText.getText() != null
                ? generatedPasswordText.getText().toString()
                : "";
        if (!password.isEmpty()) {
            copyToClipboard(password);
        }
    }

    private void copyToClipboard(String password) {
        // 使用自定义 ClipboardManager，支持 30 秒自动清除
        clipboardManager.copySensitiveText(password, "password");

        // 使用 Snackbar 显示复制成功提示
        Snackbar.make(binding.getRoot(), R.string.copied, Snackbar.LENGTH_SHORT).show();
    }

    private void applyPreset(GeneratorViewModel.Preset preset) {
        viewModel.applyPreset(preset);

        // 更新 UI
        switch (preset) {
            case PIN:
                lengthSlider.setValue(4);
                uppercaseSwitch.setChecked(false);
                lowercaseSwitch.setChecked(false);
                numbersSwitch.setChecked(true);
                symbolsSwitch.setChecked(false);
                break;
            case STRONG:
                lengthSlider.setValue(16);
                uppercaseSwitch.setChecked(true);
                lowercaseSwitch.setChecked(true);
                numbersSwitch.setChecked(true);
                symbolsSwitch.setChecked(true);
                break;
            case MEMORABLE:
                lengthSlider.setValue(12);
                uppercaseSwitch.setChecked(true);
                lowercaseSwitch.setChecked(true);
                numbersSwitch.setChecked(true);
                symbolsSwitch.setChecked(false);
                break;
        }
    }

    private void updateStrengthIndicator(int strength) {
        if (strengthBar1 == null || strengthBar2 == null ||
            strengthBar3 == null || strengthBar4 == null || strengthText == null) return;

        // strength 范围 0-100
        // 重置所有条
        strengthBar1.setAlpha(0.3f);
        strengthBar2.setAlpha(0.3f);
        strengthBar3.setAlpha(0.3f);
        strengthBar4.setAlpha(0.3f);

        // 根据强度点亮相应的段
        String strengthLabel;
        int strengthColor;

        if (strength < 25) {
            // 弱 - 只点亮第1段
            strengthBar1.setAlpha(1.0f);
            strengthLabel = "弱";
            strengthColor = getResources().getColor(R.color.strength_weak, null);
        } else if (strength < 50) {
            // 中等 - 点亮第1-2段
            strengthBar1.setAlpha(1.0f);
            strengthBar2.setAlpha(1.0f);
            strengthLabel = "中等";
            strengthColor = getResources().getColor(R.color.strength_medium, null);
        } else if (strength < 75) {
            // 强 - 点亮第1-3段
            strengthBar1.setAlpha(1.0f);
            strengthBar2.setAlpha(1.0f);
            strengthBar3.setAlpha(1.0f);
            strengthLabel = "强";
            strengthColor = getResources().getColor(R.color.strength_strong, null);
        } else {
            // 很强 - 点亮所有段
            strengthBar1.setAlpha(1.0f);
            strengthBar2.setAlpha(1.0f);
            strengthBar3.setAlpha(1.0f);
            strengthBar4.setAlpha(1.0f);
            strengthLabel = "很强";
            strengthColor = getResources().getColor(R.color.strength_very_strong, null);
        }

        strengthText.setText(strengthLabel);
        strengthText.setTextColor(strengthColor);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
