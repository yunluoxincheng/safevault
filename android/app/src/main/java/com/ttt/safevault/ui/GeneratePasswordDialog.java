package com.ttt.safevault.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.ttt.safevault.R;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 密码生成器对话框
 */
public class GeneratePasswordDialog extends DialogFragment {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 32;

    private Slider lengthSlider;
    private TextView lengthText;
    private TextInputEditText passwordText;
    private MaterialSwitch uppercaseSwitch;
    private MaterialSwitch lowercaseSwitch;
    private MaterialSwitch numbersSwitch;
    private MaterialSwitch symbolsSwitch;
    private Button regenerateButton;
    private Button useButton;
    private Button cancelButton;

    private OnPasswordGeneratedListener listener;

    public interface OnPasswordGeneratedListener {
        void onPasswordGenerated(String password, int length, boolean uppercase,
                                 boolean lowercase, boolean numbers, boolean symbols);
    }

    public void setOnPasswordGeneratedListener(OnPasswordGeneratedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_generate_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupSlider();
        setupClickListeners();
        generateInitialPassword();
    }

    @Override
    public void onStart() {
        super.onStart();
        // 设置对话框宽度和圆角
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);

            // 设置圆角背景
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void initViews(View view) {
        lengthSlider = view.findViewById(R.id.length_slider);
        lengthText = view.findViewById(R.id.length_text);
        passwordText = view.findViewById(R.id.password_text);
        uppercaseSwitch = view.findViewById(R.id.switch_uppercase);
        lowercaseSwitch = view.findViewById(R.id.switch_lowercase);
        numbersSwitch = view.findViewById(R.id.switch_numbers);
        symbolsSwitch = view.findViewById(R.id.switch_symbols);
        regenerateButton = view.findViewById(R.id.btn_regenerate);
        useButton = view.findViewById(R.id.btn_use);
        cancelButton = view.findViewById(R.id.btn_cancel);

        // 设置默认值
        lengthSlider.setValue(16);
        uppercaseSwitch.setChecked(true);
        lowercaseSwitch.setChecked(true);
        numbersSwitch.setChecked(true);
        symbolsSwitch.setChecked(false);
    }

    private void setupSlider() {
        lengthSlider.setValueFrom(MIN_PASSWORD_LENGTH);
        lengthSlider.setValueTo(MAX_PASSWORD_LENGTH);
        lengthSlider.setStepSize(1);

        lengthSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                lengthText.setText(String.valueOf((int) value));
                generatePassword();
            }
        });

        // 初始化显示
        lengthText.setText(String.valueOf((int) lengthSlider.getValue()));
    }

    private void setupClickListeners() {
        regenerateButton.setOnClickListener(v -> {
            generatePassword();
        });

        useButton.setOnClickListener(v -> {
            String password = passwordText.getText().toString();
            if (listener != null && !password.isEmpty()) {
                listener.onPasswordGenerated(
                        password,
                        (int) lengthSlider.getValue(),
                        uppercaseSwitch.isChecked(),
                        lowercaseSwitch.isChecked(),
                        numbersSwitch.isChecked(),
                        symbolsSwitch.isChecked()
                );
            }
            dismiss();
        });

        cancelButton.setOnClickListener(v -> {
            dismiss();
        });

        // 监听开关变化
        uppercaseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> generatePassword());
        lowercaseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> generatePassword());
        numbersSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> generatePassword());
        symbolsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> generatePassword());
    }

    private void generateInitialPassword() {
        generatePassword();
    }

    private void generatePassword() {
        int length = (int) lengthSlider.getValue();
        boolean includeUppercase = uppercaseSwitch.isChecked();
        boolean includeLowercase = lowercaseSwitch.isChecked();
        boolean includeNumbers = numbersSwitch.isChecked();
        boolean includeSymbols = symbolsSwitch.isChecked();

        // 验证至少选择一种字符类型
        if (!includeUppercase && !includeLowercase && !includeNumbers && !includeSymbols) {
            showError("请至少选择一种字符类型");
            return;
        }

        String password = generateSecurePassword(length, includeUppercase, includeLowercase,
                includeNumbers, includeSymbols);
        passwordText.setText(password);
    }

    private String generateSecurePassword(int length, boolean uppercase, boolean lowercase,
                                          boolean numbers, boolean symbols) {
        List<Character> allChars = new ArrayList<>();
        List<Character> requiredChars = new ArrayList<>();

        if (uppercase) {
            addChars(allChars, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            addChar(requiredChars, getRandomChar("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
        }
        if (lowercase) {
            addChars(allChars, "abcdefghijklmnopqrstuvwxyz");
            addChar(requiredChars, getRandomChar("abcdefghijklmnopqrstuvwxyz"));
        }
        if (numbers) {
            addChars(allChars, "0123456789");
            addChar(requiredChars, getRandomChar("0123456789"));
        }
        if (symbols) {
            addChars(allChars, "!@#$%^&*()_+-=[]{}|;:,.<>?");
            addChar(requiredChars, getRandomChar("!@#$%^&*()_+-=[]{}|;:,.<>?"));
        }

        // 确保包含至少一个每种选中的字符类型
        List<Character> passwordChars = new ArrayList<>(requiredChars);

        // 填充剩余位置
        SecureRandom random = new SecureRandom();
        for (int i = requiredChars.size(); i < length; i++) {
            passwordChars.add(allChars.get(random.nextInt(allChars.size())));
        }

        // 打乱顺序
        Collections.shuffle(passwordChars, random);

        // 转换为字符串
        StringBuilder password = new StringBuilder();
        for (char c : passwordChars) {
            password.append(c);
        }

        return password.toString();
    }

    private void addChars(List<Character> list, String chars) {
        for (char c : chars.toCharArray()) {
            list.add(c);
        }
    }

    private void addChar(List<Character> list, char c) {
        list.add(c);
    }

    private char getRandomChar(String chars) {
        SecureRandom random = new SecureRandom();
        return chars.charAt(random.nextInt(chars.length()));
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("错误")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }
}