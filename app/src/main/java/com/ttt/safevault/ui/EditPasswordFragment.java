package com.ttt.safevault.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ttt.safevault.R;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.PasswordStrength;
import com.ttt.safevault.utils.AnimationUtils;
import com.ttt.safevault.viewmodel.EditPasswordViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑密码Fragment
 * 用于创建和编辑密码条目
 */
public class EditPasswordFragment extends Fragment {

    private EditPasswordViewModel viewModel;
    private TextInputLayout titleLayout;
    private TextInputEditText titleText;
    private TextInputLayout usernameLayout;
    private TextInputEditText usernameText;
    private TextInputLayout passwordLayout;
    private TextInputEditText passwordText;
    private TextInputLayout urlLayout;
    private TextInputEditText urlText;
    private TextInputLayout notesLayout;
    private TextInputEditText notesText;
    private MaterialButton generatePasswordButton;
    private View passwordStrengthContainer;
    private View strengthBar1;
    private View strengthBar2;
    private View strengthBar3;
    private View strengthBar4;
    private TextView passwordStrengthText;
    private View loadingOverlay;
    private LinearProgressIndicator progressIndicator;
    private MaterialButton saveButton;
    private BackendService backendService;
    private int passwordId = -1;
    private boolean isPasswordVisible = false;

    // 标签相关
    private ChipGroup tagsChipGroup;
    private TextInputLayout tagInputLayout;
    private TextInputEditText tagInputText;
    private MaterialButton addTagButton;
    private final List<String> currentTags = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 获取传递的密码ID
        if (getArguments() != null) {
            passwordId = getArguments().getInt("passwordId", -1);
        }

        // 获取BackendService实例
        backendService = com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        initViewModel();
        setupTextWatchers();
        setupClickListeners();
        setupObservers();
    }

    private void initViews(View view) {
        titleLayout = view.findViewById(R.id.title_layout);
        titleText = view.findViewById(R.id.title_text);
        usernameLayout = view.findViewById(R.id.username_layout);
        usernameText = view.findViewById(R.id.username_text);
        passwordLayout = view.findViewById(R.id.password_layout);
        passwordText = view.findViewById(R.id.password_text);
        urlLayout = view.findViewById(R.id.url_layout);
        urlText = view.findViewById(R.id.url_text);
        notesLayout = view.findViewById(R.id.notes_layout);
        notesText = view.findViewById(R.id.notes_text);
        generatePasswordButton = view.findViewById(R.id.btn_generate_password);
        passwordStrengthContainer = view.findViewById(R.id.password_strength_container);
        strengthBar1 = view.findViewById(R.id.strength_bar_1);
        strengthBar2 = view.findViewById(R.id.strength_bar_2);
        strengthBar3 = view.findViewById(R.id.strength_bar_3);
        strengthBar4 = view.findViewById(R.id.strength_bar_4);
        passwordStrengthText = view.findViewById(R.id.password_strength_text);
        loadingOverlay = view.findViewById(R.id.loading_overlay);
        progressIndicator = view.findViewById(R.id.progress_indicator);
        saveButton = view.findViewById(R.id.btn_save);

        // 初始化标签相关视图
        tagsChipGroup = view.findViewById(R.id.tags_chip_group);
        tagInputLayout = view.findViewById(R.id.tag_input_layout);
        tagInputText = view.findViewById(R.id.tag_input_text);
        addTagButton = view.findViewById(R.id.btn_add_tag);

        // 设置密码输入框默认图标为闭眼
        if (passwordLayout != null) {
            passwordLayout.setEndIconDrawable(R.drawable.ic_visibility);
        }
    }

    private void initViewModel() {
        // 通过ViewModelFactory创建ViewModel
        ViewModelProvider.Factory factory = new com.ttt.safevault.viewmodel.ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(this, factory).get(EditPasswordViewModel.class);

        viewModel.loadPasswordItem(passwordId);
    }

    private void setupTextWatchers() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.markChanges();
                updatePasswordStrength();
                updateSaveButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        if (titleText != null) titleText.addTextChangedListener(textWatcher);
        if (usernameText != null) usernameText.addTextChangedListener(textWatcher);
        if (passwordText != null) passwordText.addTextChangedListener(textWatcher);
        if (urlText != null) urlText.addTextChangedListener(textWatcher);
        if (notesText != null) notesText.addTextChangedListener(textWatcher);
    }

    private void setupClickListeners() {
        // 生成密码按钮
        if (generatePasswordButton != null) {
            generatePasswordButton.setOnClickListener(v -> {
                showPasswordGeneratorDialog();
            });
        }

        // 保存按钮
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> {
                savePassword();
            });
        }

        // 密码输入框的显示/隐藏按钮
        if (passwordLayout != null) {
            passwordLayout.setEndIconOnClickListener(v -> {
                togglePasswordVisibility();
            });
        }

        // 添加标签按钮
        if (addTagButton != null) {
            addTagButton.setOnClickListener(v -> {
                addTag();
            });
        }

        // 标签输入框的回车键
        if (tagInputText != null) {
            tagInputText.setOnEditorActionListener((v, actionId, event) -> {
                addTag();
                return true;
            });
        }
    }

    private void setupObservers() {
        // 观察密码条目
        viewModel.passwordItem.observe(getViewLifecycleOwner(), this::updatePasswordItem);

        // 观察加载状态
        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            updateLoadingState(isLoading != null && isLoading);
        });

        // 观察错误信息
        viewModel.errorMessage.observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                showError(error);
                viewModel.clearError();
            }
        });

        // 观察保存状态
        viewModel.isSaved.observe(getViewLifecycleOwner(), isSaved -> {
            if (isSaved != null && isSaved) {
                Toast.makeText(requireContext(), "密码已保存", Toast.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).navigateUp();
            }
        });

        // 观察生成的密码
        viewModel.generatedPassword.observe(getViewLifecycleOwner(), password -> {
            if (password != null && passwordText != null) {
                passwordText.setText(password);
                updatePasswordStrength();
                Toast.makeText(requireContext(), "密码已生成", Toast.LENGTH_SHORT).show();
                viewModel.clearGeneratedPassword();
            }
        });
    }

    private void updatePasswordItem(PasswordItem item) {
        if (item == null) return;

        if (titleText != null) titleText.setText(item.getTitle());
        if (usernameText != null) usernameText.setText(item.getUsername());
        if (passwordText != null) passwordText.setText(item.getPassword());
        if (urlText != null) urlText.setText(item.getUrl());
        if (notesText != null) notesText.setText(item.getNotes());

        // 加载标签
        currentTags.clear();
        if (item.getTags() != null) {
            currentTags.addAll(item.getTags());
        }
        updateTagsChips();

        updatePasswordStrength();
    }

    private void updateLoadingState(boolean isLoading) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }

        if (progressIndicator != null) {
            progressIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }

        // 禁用/启用输入框和按钮
        boolean enabled = !isLoading;
        if (titleText != null) titleText.setEnabled(enabled);
        if (usernameText != null) usernameText.setEnabled(enabled);
        if (passwordText != null) passwordText.setEnabled(enabled);
        if (urlText != null) urlText.setEnabled(enabled);
        if (notesText != null) notesText.setEnabled(enabled);
        if (generatePasswordButton != null) generatePasswordButton.setEnabled(enabled);
        if (saveButton != null) saveButton.setEnabled(enabled);
    }

    private void updatePasswordStrength() {
        if (passwordText == null || passwordStrengthText == null) {
            return;
        }

        String password = passwordText.getText().toString();
        if (password.isEmpty()) {
            if (passwordStrengthContainer != null) {
                passwordStrengthContainer.setVisibility(View.GONE);
            }
            return;
        }

        if (passwordStrengthContainer != null) {
            passwordStrengthContainer.setVisibility(View.VISIBLE);
        }

        var strength = viewModel.checkPasswordStrength(password);
        var description = viewModel.getPasswordStrengthDescription(strength);

        // 重置所有强度条
        if (strengthBar1 != null) strengthBar1.setAlpha(0.3f);
        if (strengthBar2 != null) strengthBar2.setAlpha(0.3f);
        if (strengthBar3 != null) strengthBar3.setAlpha(0.3f);
        if (strengthBar4 != null) strengthBar4.setAlpha(0.3f);

        // 根据强度点亮相应的段
        int strengthColor;
        switch (strength.level()) {
            case WEAK:
                if (strengthBar1 != null) strengthBar1.setAlpha(1.0f);
                strengthColor = getResources().getColor(R.color.strength_weak, null);
                break;
            case MEDIUM:
                if (strengthBar1 != null) strengthBar1.setAlpha(1.0f);
                if (strengthBar2 != null) strengthBar2.setAlpha(1.0f);
                strengthColor = getResources().getColor(R.color.strength_medium, null);
                break;
            case STRONG:
                if (strengthBar1 != null) strengthBar1.setAlpha(1.0f);
                if (strengthBar2 != null) strengthBar2.setAlpha(1.0f);
                if (strengthBar3 != null) strengthBar3.setAlpha(1.0f);
                strengthColor = getResources().getColor(R.color.strength_strong, null);
                break;
            default:
                if (strengthBar1 != null) strengthBar1.setAlpha(1.0f);
                if (strengthBar2 != null) strengthBar2.setAlpha(1.0f);
                if (strengthBar3 != null) strengthBar3.setAlpha(1.0f);
                if (strengthBar4 != null) strengthBar4.setAlpha(1.0f);
                strengthColor = getResources().getColor(R.color.strength_very_strong, null);
                break;
        }

        if (passwordStrengthText != null) {
            passwordStrengthText.setText(description);
            passwordStrengthText.setTextColor(strengthColor);
        }
    }

    private void updateSaveButtonState() {
        if (saveButton == null) return;

        boolean hasTitle = titleText != null && !titleText.getText().toString().trim().isEmpty();
        boolean hasUsername = usernameText != null && !usernameText.getText().toString().trim().isEmpty();
        boolean hasPassword = passwordText != null && !passwordText.getText().toString().trim().isEmpty();

        saveButton.setEnabled(hasTitle && hasUsername && hasPassword);
    }

    private void savePassword() {
        String title = titleText != null ? titleText.getText().toString().trim() : "";
        String username = usernameText != null ? usernameText.getText().toString().trim() : "";
        String password = passwordText != null ? passwordText.getText().toString().trim() : "";
        String url = urlText != null ? urlText.getText().toString().trim() : "";
        String notes = notesText != null ? notesText.getText().toString().trim() : "";

        viewModel.savePassword(title, username, password, url, notes, new ArrayList<>(currentTags));
    }

    private void showPasswordGeneratorDialog() {
        GeneratePasswordDialog dialog = new GeneratePasswordDialog();
        dialog.setOnPasswordGeneratedListener((password, length, uppercase, lowercase, numbers, symbols) -> {
            // 设置生成的密码
            if (passwordText != null) {
                passwordText.setText(password);
                updatePasswordStrength();
            }
            // 保存生成参数
            viewModel.setPasswordGenerationParams(length, uppercase, lowercase, numbers, symbols);
        });
        dialog.show(getChildFragmentManager(), "PasswordGenerator");
    }

    private void handleBackNavigation() {
        if (viewModel.hasUnsavedChanges()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("未保存的更改")
                    .setMessage("您有未保存的更改，确定要退出吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("退出", (dialog, which) -> {
                        Navigation.findNavController(requireView()).navigateUp();
                    })
                    .show();
        } else {
            Navigation.findNavController(requireView()).navigateUp();
        }
    }

    private void togglePasswordVisibility() {
        if (passwordText == null || passwordLayout == null) return;

        isPasswordVisible = !isPasswordVisible;

        // 切换密码输入类型
        if (isPasswordVisible) {
            // 显示密码
            passwordText.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            passwordLayout.setEndIconDrawable(R.drawable.ic_visibility_off);
            passwordLayout.setEndIconContentDescription(getString(R.string.hide_password));
        } else {
            // 隐藏密码
            passwordText.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordLayout.setEndIconDrawable(R.drawable.ic_visibility);
            passwordLayout.setEndIconContentDescription(getString(R.string.show_password));
        }

        // 将光标移到末尾
        passwordText.setSelection(passwordText.getText().length());

        // 添加旋转动画反馈
        // 注意：getEndIconView() 不是公开 API，这里简化处理
    }

    /**
     * 添加标签
     */
    private void addTag() {
        if (tagInputText == null) return;

        String tag = tagInputText.getText().toString().trim();
        if (tag.isEmpty()) {
            return;
        }

        // 检查是否已存在
        if (currentTags.contains(tag)) {
            if (tagInputLayout != null) {
                tagInputLayout.setError("标签已存在");
            }
            return;
        }

        // 添加标签
        currentTags.add(tag);
        updateTagsChips();

        // 清空输入框
        tagInputText.setText("");
        if (tagInputLayout != null) {
            tagInputLayout.setError(null);
        }
    }

    /**
     * 移除标签
     */
    private void removeTag(String tag) {
        currentTags.remove(tag);
        updateTagsChips();
    }

    /**
     * 更新标签 Chip 显示
     */
    private void updateTagsChips() {
        if (tagsChipGroup == null) return;

        // 清除所有 Chip
        tagsChipGroup.removeAllViews();

        // 为每个标签创建 Chip
        for (String tag : currentTags) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag);
            chip.setCloseIconVisible(true);
            chip.setCheckable(false);
            chip.setClickable(true);

            // 设置关闭按钮点击事件
            chip.setOnCloseIconClickListener(v -> {
                removeTag(tag);
            });

            tagsChipGroup.addView(chip);
        }
    }

    private void showError(String error) {
        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
    }
}