package com.ttt.safevault.ui.autofill;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ttt.safevault.R;
import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.databinding.ActivityAutofillSaveBinding;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.utils.AutofillUtils;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自动填充保存凭据Activity
 * 当用户在第三方应用输入新凭据时，弹出此界面让用户确认保存
 */
public class AutofillSaveActivity extends AppCompatActivity {
    private static final String TAG = "AutofillSaveActivity";

    private ActivityAutofillSaveBinding binding;
    private BackendService backendService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 标签相关
    private ChipGroup tagsChipGroup;
    private TextInputLayout tagInputLayout;
    private TextInputEditText tagInputText;
    private com.google.android.material.button.MaterialButton addTagButton;
    private final List<String> currentTags = new ArrayList<>();

    // Intent 数据
    private String username;
    private String password;
    private String domain;
    private String packageName;
    private String title;
    private boolean isWeb;

    // 去重检查结果
    private boolean isDuplicate = false;
    private PasswordItem existingItem = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置FLAG_SECURE防止截屏 - 根据 SecurityConfig 设置决定
        com.ttt.safevault.security.SecurityConfig securityConfig =
            new com.ttt.safevault.security.SecurityConfig(this);
        if (securityConfig.isScreenshotProtectionEnabled()) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
            );
        }

        binding = ActivityAutofillSaveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 获取BackendService
        backendService = ServiceLocator.getInstance().getBackendService();

        // 设置Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.autofill_save_credential);
        }

        // 获取Intent数据
        username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");
        domain = getIntent().getStringExtra("domain");
        packageName = getIntent().getStringExtra("packageName");
        title = getIntent().getStringExtra("title");
        isWeb = getIntent().getBooleanExtra("isWeb", false);

        // 填充表单
        if (username != null && !username.isEmpty()) {
            binding.usernameInput.setText(username);
        }

        if (password != null && !password.isEmpty()) {
            binding.passwordInput.setText(password);
        }

        // 设置标题和网站/应用
        if (title != null && !title.isEmpty()) {
            binding.titleInput.setText(title);
        }

        if (isWeb && domain != null) {
            binding.websiteInput.setText(domain);
        } else if (packageName != null) {
            binding.websiteInput.setText("android://" + packageName);
        }

        // 保存按钮
        binding.saveButton.setOnClickListener(v -> saveCredential());

        // 取消按钮
        binding.cancelButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        // 初始化标签输入
        initTagsInput();

        // 启动去重检查
        checkDuplicateCredential();
    }

    /**
     * 检查凭据是否重复
     */
    private void checkDuplicateCredential() {
        // 显示加载状态
        runOnUiThread(() -> {
            binding.saveButton.setEnabled(false);
            binding.saveButton.setText(R.string.checking);
        });

        executor.execute(() -> {
            try {
                // 检查是否已解锁
                if (backendService == null || !backendService.isUnlocked()) {
                    runOnUiThread(() -> {
                        binding.saveButton.setEnabled(true);
                        binding.saveButton.setText(R.string.button_save);
                    });
                    return;
                }

                // 构造搜索关键词
                String searchKeyword = null;
                if (isWeb && domain != null) {
                    searchKeyword = domain;
                } else if (packageName != null) {
                    searchKeyword = packageName;
                }

                if (searchKeyword == null || searchKeyword.isEmpty()) {
                    runOnUiThread(() -> {
                        binding.saveButton.setEnabled(true);
                        binding.saveButton.setText(R.string.button_save);
                    });
                    return;
                }

                // 搜索匹配的凭据
                List<PasswordItem> items = backendService.search(searchKeyword);

                if (items != null && !items.isEmpty()) {
                    // 检查是否有相同的用户名
                    String currentUsername = username != null ? username.trim() : "";

                    for (PasswordItem item : items) {
                        String itemDomain = AutofillUtils.extractDomainFromUrl(item.getUrl());
                        String currentDomain = isWeb ? domain : packageName;

                        // 检查域名和用户名是否匹配
                        boolean domainMatches = false;
                        if (itemDomain != null && currentDomain != null) {
                            if (isWeb) {
                                domainMatches = AutofillUtils.domainsMatch(itemDomain, currentDomain);
                            } else {
                                // 包名精确匹配
                                domainMatches = itemDomain.equals(currentDomain);
                            }
                        }

                        if (domainMatches) {
                            String itemUsername = item.getUsername() != null ? item.getUsername().trim() : "";
                            if (itemUsername.equalsIgnoreCase(currentUsername)) {
                                // 找到重复凭据
                                isDuplicate = true;
                                existingItem = item;
                                break;
                            }
                        }
                    }
                }

                // 更新UI
                runOnUiThread(() -> {
                    binding.saveButton.setEnabled(true);
                    binding.saveButton.setText(R.string.button_save);

                    if (isDuplicate) {
                        showDuplicateWarning();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    binding.saveButton.setEnabled(true);
                    binding.saveButton.setText(R.string.button_save);
                });
            }
        });
    }

    /**
     * 显示重复凭据警告（Material Design 3 对话框）
     */
    private void showDuplicateWarning() {
        if (existingItem == null) {
            return;
        }

        String existingTitle = existingItem.getTitle() != null ? existingItem.getTitle() : "";
        String existingUsername = existingItem.getUsername() != null ? existingItem.getUsername() : "";

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.duplicate_credential_title)
                .setMessage(getString(R.string.duplicate_credential_message,
                        existingTitle, existingUsername))
                .setPositiveButton(R.string.button_overwrite, (d, which) -> {
                    // 覆盖现有凭据
                    overwriteExistingCredential();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();

        // 设置 Material 3 样式
        dialog.setOnShowListener(listener -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(getColor(android.R.color.holo_red_dark));
        });

        dialog.show();
    }

    /**
     * 覆盖现有凭据
     */
    private void overwriteExistingCredential() {
        if (existingItem == null) {
            return;
        }

        // 更新现有凭据的数据
        String titleText = binding.titleInput.getText().toString().trim();
        String usernameText = binding.usernameInput.getText().toString().trim();
        String passwordText = binding.passwordInput.getText().toString().trim();
        String websiteText = binding.websiteInput.getText().toString().trim();
        String notesText = binding.notesInput.getText().toString().trim();

        existingItem.setTitle(titleText.isEmpty() ? usernameText : titleText);
        existingItem.setUsername(usernameText);
        existingItem.setPassword(passwordText);
        existingItem.setUrl(websiteText);
        existingItem.setNotes(notesText);

        // 异步保存
        executor.execute(() -> {
            try {
                int updatedId = backendService.saveItem(existingItem);

                runOnUiThread(() -> {
                    if (updatedId > 0) {
                        Toast.makeText(this, R.string.credential_overwritten, Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        // 保存成功后启动 MainActivity
                        Intent intent = new Intent(this, com.ttt.safevault.ui.MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, R.string.autofill_save_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.autofill_save_failed, Toast.LENGTH_SHORT).show();
                    binding.saveButton.setEnabled(true);
                    binding.cancelButton.setEnabled(true);
                    binding.saveButton.setText(R.string.button_save);
                });
            }
        });
    }

    /**
     * 保存凭据
     */
    private void saveCredential() {
        String titleText = binding.titleInput.getText().toString().trim();
        String usernameText = binding.usernameInput.getText().toString().trim();
        String passwordText = binding.passwordInput.getText().toString().trim();
        String websiteText = binding.websiteInput.getText().toString().trim();
        String notesText = binding.notesInput.getText().toString().trim();

        // 验证必填字段
        if (usernameText.isEmpty()) {
            binding.usernameInputLayout.setError(getString(R.string.error_username_required));
            return;
        }

        if (passwordText.isEmpty()) {
            binding.passwordInputLayout.setError(getString(R.string.error_password_required));
            return;
        }

        // 清除错误提示
        binding.usernameInputLayout.setError(null);
        binding.passwordInputLayout.setError(null);

        // 禁用按钮防止重复点击
        binding.saveButton.setEnabled(false);
        binding.cancelButton.setEnabled(false);
        binding.saveButton.setText(R.string.saving);

        // 异步保存
        executor.execute(() -> {
            try {
                // 创建PasswordItem
                PasswordItem item = new PasswordItem();
                item.setTitle(titleText.isEmpty() ? usernameText : titleText);
                item.setUsername(usernameText);
                item.setPassword(passwordText);
                item.setUrl(websiteText);
                item.setNotes(notesText);
                item.setTags(new ArrayList<>(currentTags));  // 添加标签

                // 保存
                int savedId = backendService.saveItem(item);

                runOnUiThread(() -> {
                    if (savedId > 0) {
                        Toast.makeText(this, R.string.autofill_save_success, Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        // 保存成功后启动 MainActivity
                        Intent intent = new Intent(this, com.ttt.safevault.ui.MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, R.string.autofill_save_failed, Toast.LENGTH_SHORT).show();
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.autofill_save_failed, Toast.LENGTH_SHORT).show();
                    binding.saveButton.setEnabled(true);
                    binding.cancelButton.setEnabled(true);
                    binding.saveButton.setText(R.string.button_save);
                });
            }
        });
    }

    /**
     * 初始化标签输入
     */
    private void initTagsInput() {
        tagsChipGroup = binding.getRoot().findViewById(R.id.tags_chip_group);
        tagInputLayout = binding.getRoot().findViewById(R.id.tag_input_layout);
        tagInputText = binding.getRoot().findViewById(R.id.tag_input_text);
        addTagButton = binding.getRoot().findViewById(R.id.btn_add_tag);

        // 添加标签按钮点击事件
        if (addTagButton != null) {
            addTagButton.setOnClickListener(v -> {
                addTag();
            });
        }

        // 标签输入框回车键事件
        if (tagInputText != null) {
            tagInputText.setOnEditorActionListener((v, actionId, event) -> {
                addTag();
                return true;
            });
        }
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
            Chip chip = new Chip(this);
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

    @Override
    public boolean onSupportNavigateUp() {
        setResult(RESULT_CANCELED);
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 清除敏感数据，防止内存泄漏
        if (password != null) {
            password = null;
        }
        if (username != null) {
            username = null;
        }
        if (existingItem != null) {
            existingItem = null;
        }

        // 正确关闭ExecutorService，使用shutdownNow中断正在执行的任务
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }

        // 清理binding引用，防止内存泄漏
        binding = null;
        backendService = null;
    }
}
