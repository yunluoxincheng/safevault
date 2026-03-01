package com.ttt.safevault.ui.autofill;

import android.content.Intent;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.ttt.safevault.R;
import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.autofill.matcher.AutofillMatcher;
import com.ttt.safevault.autofill.model.AutofillRequest;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.security.biometric.BiometricAuthManager;
import com.ttt.safevault.security.biometric.AuthCallback;
import com.ttt.safevault.security.biometric.AuthScenario;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自动填充凭据选择Activity
 * 显示匹配当前网站/应用的账号密码列表
 * 作为认证Activity使用，返回EXTRA_AUTHENTICATION_RESULT
 */
public class AutofillCredentialSelectorActivity extends AppCompatActivity {
    private static final String TAG = "AutofillCredentialSelector";
    private static final int REQUEST_CODE_ADD_CREDENTIAL = 1001;

    private MaterialToolbar toolbar;
    private RecyclerView recyclerView;
    private ExtendedFloatingActionButton addButton;
    private View emptyView;

    private BackendService backendService;
    private AutofillCredentialAdapter adapter;
    private List<PasswordItem> credentials;
    private AutofillRequest autofillRequest;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 自动填充相关数据
    private List<AutofillId> usernameIds;
    private List<AutofillId> passwordIds;

    // Intent 数据
    private String domain;
    private String packageName;
    private String title;
    private boolean isWeb;
    private boolean isFromAutofillUnlock = false;
    private boolean needsAuth = false;  // 是否需要身份验证
    private boolean isAuthenticated = false;  // 是否已通过验证

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置FLAG_SECURE防止截屏
        com.ttt.safevault.security.SecurityConfig securityConfig =
            new com.ttt.safevault.security.SecurityConfig(this);
        if (securityConfig.isScreenshotProtectionEnabled()) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
            );
        }

        setContentView(R.layout.activity_autofill_credential_selector);

        // 初始化BackendService
        backendService = ServiceLocator.getInstance().getBackendService();

        // 初始化视图
        initViews();

        // 获取Intent数据
        parseIntentData();

        // 检查是否来自自动填充解锁
        isFromAutofillUnlock = getIntent().getBooleanExtra("from_autofill_unlock", false);

        // 检查是否需要身份验证
        if (needsAuth) {
            // 先验证身份，验证成功后再加载凭据
            android.util.Log.d(TAG, "需要身份验证，显示验证对话框");
            showAuthenticationDialog();
        } else {
            // 不需要验证，直接加载凭据
            loadCredentials();
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.credentials_recycler_view);
        addButton = findViewById(R.id.add_credential_button);
        emptyView = findViewById(R.id.empty_view);

        // 设置Toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.autofill_select_credential);
        }

        toolbar.setNavigationOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        // 设置RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        credentials = new ArrayList<>();
        adapter = new AutofillCredentialAdapter(credentials, this::onCredentialSelected);
        recyclerView.setAdapter(adapter);

        // 添加按钮
        addButton.setOnClickListener(v -> addNewCredential());


    }

    private void parseIntentData() {
        Intent intent = getIntent();
        domain = intent.getStringExtra("domain");
        packageName = intent.getStringExtra("packageName");
        title = intent.getStringExtra("title");
        isWeb = intent.getBooleanExtra("isWeb", false);
        needsAuth = intent.getBooleanExtra("needs_auth", false);

        // 获取AutofillId列表
        usernameIds = intent.getParcelableArrayListExtra("username_ids");
        passwordIds = intent.getParcelableArrayListExtra("password_ids");

        android.util.Log.d(TAG, "收到AutofillId: usernameIds=" + (usernameIds != null ? usernameIds.size() : 0) +
                ", passwordIds=" + (passwordIds != null ? passwordIds.size() : 0));
        android.util.Log.d(TAG, "needsAuth=" + needsAuth);

        // 构建AutofillRequest（用于匹配凭据）
        autofillRequest = buildAutofillRequest();
    }

    private AutofillRequest buildAutofillRequest() {
        AutofillRequest.Builder builder = new AutofillRequest.Builder();

        // 设置元数据
        if (domain != null) {
            builder.setDomain(domain);
        }
        if (packageName != null) {
            builder.setPackageName(packageName);
        }
        builder.setIsWeb(isWeb);

        return builder.build();
    }

    private void loadCredentials() {
        if (backendService == null || !backendService.isUnlocked()) {
            runOnUiThread(this::showEmptyState);
            return;
        }

        // 在后台线程执行数据库操作
        executor.execute(() -> {
            try {
                // 使用AutofillMatcher匹配凭据
                AutofillMatcher matcher = new AutofillMatcher(backendService);
                List<PasswordItem> matchedCredentials = matcher.matchCredentials(autofillRequest);

                // 切换到主线程更新UI
                final List<PasswordItem> result = matchedCredentials;
                runOnUiThread(() -> {
                    if (result == null || result.isEmpty()) {
                        showEmptyState();
                    } else {
                        credentials.clear();
                        credentials.addAll(result);
                        adapter.notifyDataSetChanged();
                        recyclerView.setVisibility(View.VISIBLE);
                        emptyView.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    android.util.Log.e(TAG, "加载凭据失败: " + e.getMessage(), e);
                    showEmptyState();
                });
            }
        });
    }

    private void showEmptyState() {
        credentials.clear();
        adapter.notifyDataSetChanged();
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);

        // 设置空状态标题
        String emptyTitle;
        if (isWeb && domain != null) {
            emptyTitle = domain;
        } else if (title != null) {
            emptyTitle = title;
        } else {
            emptyTitle = getString(R.string.app_name);
        }

        android.widget.TextView emptyTitleView = emptyView.findViewById(R.id.empty_title);
        if (emptyTitleView != null) {
            emptyTitleView.setText(emptyTitle);
        }
    }

    private void onCredentialSelected(PasswordItem credential) {
        android.util.Log.d(TAG, "用户选择凭据: " + credential.getTitle() + ", username=" + credential.getUsername());
        android.util.Log.d(TAG, "AutofillId状态: usernameIds=" + (usernameIds != null ? usernameIds.size() : "null") +
                ", passwordIds=" + (passwordIds != null ? passwordIds.size() : "null"));

        // 构建Dataset包含选中的凭据（返回Dataset会立即填充，不需要再次点击）
        Dataset dataset = buildDatasetForCredential(credential);

        if (dataset != null) {
            // 使用EXTRA_AUTHENTICATION_RESULT返回Dataset（而非FillResponse）
            // 返回Dataset会立即自动填充到视图，无需用户再次点击
            Intent resultIntent = new Intent();
            resultIntent.putExtra(android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset);
            setResult(RESULT_OK, resultIntent);

            android.util.Log.d(TAG, "已返回EXTRA_AUTHENTICATION_RESULT (Dataset)，系统将立即自动填充");
        } else {
            android.util.Log.e(TAG, "构建Dataset失败");
            setResult(RESULT_CANCELED);
        }

        finish();
    }

    /**
     * 为选中的凭据构建Dataset
     * 返回Dataset而非FillResponse，这样系统会立即填充到视图，无需用户再次点击
     */
    private Dataset buildDatasetForCredential(PasswordItem credential) {
        try {
            // 使用凭据信息的presentation，显示标题和用户名
            RemoteViews presentation = createCredentialPresentation(credential);

            // 创建Dataset.Builder，传入presentation
            Dataset.Builder datasetBuilder = new Dataset.Builder(presentation);

            // 设置用户名
            String username = credential.getUsername();
            if (username != null && !username.isEmpty() && usernameIds != null && !usernameIds.isEmpty()) {
                for (AutofillId id : usernameIds) {
                    datasetBuilder.setValue(id, AutofillValue.forText(username), presentation);
                }
                android.util.Log.d(TAG, "设置用户名: " + username + " 到 " + usernameIds.size() + " 个字段");
            }

            // 设置密码
            String password = credential.getPassword();
            if (password != null && !password.isEmpty() && passwordIds != null && !passwordIds.isEmpty()) {
                for (AutofillId id : passwordIds) {
                    datasetBuilder.setValue(id, AutofillValue.forText(password), presentation);
                }
                android.util.Log.d(TAG, "设置密码到 " + passwordIds.size() + " 个字段");
            }

            // 构建并返回Dataset
            return datasetBuilder.build();
        } catch (Exception e) {
            android.util.Log.e(TAG, "构建Dataset失败: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建"转到我的密码库"的presentation（保持显示不变）
     */
    private RemoteViews createVaultPresentation() {
        RemoteViews presentation = new RemoteViews(getPackageName(), R.layout.autofill_auth_item);

        // 保持与"转到我的密码库"相同的显示
        presentation.setTextViewText(R.id.autofill_auth_title, getString(R.string.app_name));

        // 根据是否需要认证（锁定状态），显示不同的文字
        // needsAuth=true 表示锁定，显示"密码库已锁定"
        // needsAuth=false 表示未锁定，显示"转到我的密码库"
        String statusText = needsAuth
                ? getString(R.string.autofill_vault_locked)
                : getString(R.string.autofill_open_vault);
        presentation.setTextViewText(R.id.autofill_auth_text, statusText);

        return presentation;
    }

    /**
     * 创建凭据信息的presentation，显示标题和用户名
     */
    private RemoteViews createCredentialPresentation(PasswordItem credential) {
        RemoteViews presentation = new RemoteViews(getPackageName(), R.layout.autofill_auth_item);

        // 设置标题为凭据标题
        String title = credential.getTitle();
        if (title == null || title.isEmpty()) {
            title = credential.getUsername();
        }
        if (title == null || title.isEmpty()) {
            title = getString(R.string.app_name);
        }
        presentation.setTextViewText(R.id.autofill_auth_title, title);

        // 设置用户名（部分隐藏）
        String username = credential.getUsername();
        if (username != null && !username.isEmpty()) {
            String maskedUsername = maskUsername(username);
            presentation.setTextViewText(R.id.autofill_auth_text, maskedUsername);
        } else {
            presentation.setTextViewText(R.id.autofill_auth_text, "");
        }

        return presentation;
    }

    /**
     * 部分隐藏用户名（显示前2后2个字符）
     */
    private String maskUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "";
        }

        if (username.length() <= 4) {
            return username;
        }

        // 显示前2个和后2个字符，中间用*替代
        int visibleChars = 2;
        String start = username.substring(0, visibleChars);
        String end = username.substring(username.length() - visibleChars);
        int maskedLength = username.length() - (visibleChars * 2);

        StringBuilder masked = new StringBuilder(start);
        for (int i = 0; i < maskedLength; i++) {
            masked.append("*");
        }
        masked.append(end);

        return masked.toString();
    }

    private void addNewCredential() {
        // 跳转到添加密码页面
        Intent intent = new Intent(this, AutofillSaveActivity.class);
        intent.putExtra("domain", domain);
        intent.putExtra("packageName", packageName);
        intent.putExtra("title", title);
        intent.putExtra("isWeb", isWeb);
        intent.putExtra("from_credential_selector", true);

        startActivityForResult(intent, REQUEST_CODE_ADD_CREDENTIAL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_ADD_CREDENTIAL) {
            if (resultCode == RESULT_OK) {
                // 添加成功，重新加载凭据列表
                loadCredentials();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 显示身份验证对话框
     * 在自动填充前要求用户验证身份
     * 支持主密码验证和生物识别验证
     */
    private void showAuthenticationDialog() {
        // 检查是否支持且开启了生物识别
        boolean biometricAvailable = com.ttt.safevault.security.BiometricAuthHelper.isBiometricSupported(this);
        com.ttt.safevault.security.SecurityConfig securityConfig = new com.ttt.safevault.security.SecurityConfig(this);
        boolean biometricEnabled = securityConfig.isBiometricEnabled();

        android.util.Log.d(TAG, "生物识别支持: " + biometricAvailable + ", 已开启: " + biometricEnabled);

        // 如果支持且开启了生物识别，优先使用生物识别
        if (biometricAvailable && biometricEnabled) {
            showBiometricAuthentication();
        } else {
            // 否则显示密码验证对话框
            showPasswordAuthenticationDialog();
        }
    }

    /**
     * 显示生物识别验证
     */
    private void showBiometricAuthentication() {
        BiometricAuthManager biometricAuthManager = BiometricAuthManager.getInstance(this);

        biometricAuthManager.authenticate(this, AuthScenario.LOGIN, new AuthCallback() {
            @Override
            public void onUserVerified() {
                // 用户通过 UI 认证，等待 Keystore 授权
                android.util.Log.d(TAG, "生物识别 UI 验证成功");
            }

            @Override
            public void onKeyAccessGranted() {
                // Keystore 授权成功，可以安全访问数据
                android.util.Log.d(TAG, "生物识别 Keystore 授权成功");

                // 需要在后台线程执行密钥操作
                executor.execute(() -> {
                    try {
                        // 获取 DataKey 并设置到 CryptoSession（用于会话恢复）
                        com.ttt.safevault.security.SecureKeyStorageManager secureStorage =
                                com.ttt.safevault.security.SecureKeyStorageManager.getInstance(AutofillCredentialSelectorActivity.this);
                        javax.crypto.SecretKey dataKey = secureStorage.unlockDataKeyWithBiometric();

                        if (dataKey == null) {
                            runOnUiThread(() -> {
                                android.widget.Toast.makeText(AutofillCredentialSelectorActivity.this,
                                        "生物识别失败，请使用主密码", android.widget.Toast.LENGTH_SHORT).show();
                                showPasswordAuthenticationDialog();
                            });
                            return;
                        }

                        // 设置 DataKey 到 CryptoSession
                        com.ttt.safevault.security.CryptoSession cryptoSession =
                                com.ttt.safevault.security.CryptoSession.getInstance();
                        cryptoSession.unlockWithDataKey(dataKey);

                        android.util.Log.d(TAG, "生物识别验证成功，会话已设置");

                        runOnUiThread(() -> {
                            isAuthenticated = true;
                            loadCredentials();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            android.util.Log.e(TAG, "生物识别解锁异常", e);
                            android.widget.Toast.makeText(AutofillCredentialSelectorActivity.this,
                                    "生物识别失败，请使用主密码", android.widget.Toast.LENGTH_SHORT).show();
                            showPasswordAuthenticationDialog();
                        });
                    }
                });
            }

            @Override
            public void onFailure(com.ttt.safevault.security.biometric.AuthError error, String message, boolean canRetry) {
                android.util.Log.d(TAG, "生物识别验证失败: " + error.name() + ", message: " + message);
                runOnUiThread(() -> {
                    // 使用错误消息或默认消息
                    String displayMessage = (message != null && !message.isEmpty()) ? message : "生物识别失败";
                    android.widget.Toast.makeText(AutofillCredentialSelectorActivity.this,
                            displayMessage, android.widget.Toast.LENGTH_SHORT).show();
                    // 失败后显示密码验证对话框
                    showPasswordAuthenticationDialog();
                });
            }

            @Override
            public void onCancel() {
                android.util.Log.d(TAG, "生物识别验证取消");
                runOnUiThread(() -> {
                    // 用户取消，显示密码验证对话框
                    showPasswordAuthenticationDialog();
                });
            }

            @Override
            public void onBiometricChanged() {
                android.util.Log.d(TAG, "生物识别信息已变更");
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(AutofillCredentialSelectorActivity.this,
                            "生物识别信息已变更，请使用主密码", android.widget.Toast.LENGTH_SHORT).show();
                    showPasswordAuthenticationDialog();
                });
            }
        });
    }

    /**
     * 显示密码验证对话框
     */
    private void showPasswordAuthenticationDialog() {
        // 创建认证对话框
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);

        // 设置标题和消息
        builder.setTitle("验证身份");
        builder.setMessage("请输入主密码以继续自动填充");

        // 创建输入框
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_input, null);
        com.google.android.material.textfield.TextInputLayout passwordLayout =
                dialogView.findViewById(R.id.passwordLayout);
        com.google.android.material.textfield.TextInputEditText passwordInput =
                dialogView.findViewById(R.id.passwordInput);

        // 确保初始状态正确：密码隐藏，睁眼图标
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordLayout.setEndIconDrawable(getResources().getDrawable(R.drawable.ic_visibility));

        // 设置密码可见性切换
        passwordLayout.setEndIconOnClickListener(v -> {
            // 切换密码可见性
            int selection = passwordInput.getSelectionEnd();
            int currentInputType = passwordInput.getInputType();
            int variation = currentInputType & android.text.InputType.TYPE_MASK_VARIATION;

            if (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                // 当前是密码状态，切换为可见
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                passwordLayout.setEndIconDrawable(getResources().getDrawable(R.drawable.ic_visibility_off));
            } else {
                // 当前是可见状态，切换为密码
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordLayout.setEndIconDrawable(getResources().getDrawable(R.drawable.ic_visibility));
            }
            // 保持光标位置
            passwordInput.setSelection(selection);
        });

        builder.setView(dialogView);

        // 设置按钮
        builder.setPositiveButton("验证", (dialog, which) -> {
            String password = passwordInput.getText().toString();
            if (password.isEmpty()) {
                android.widget.Toast.makeText(this, "密码不能为空", android.widget.Toast.LENGTH_SHORT).show();
                cancelAuthentication();
                return;
            }

            // 验证密码
            verifyPasswordAndLoadCredentials(password);
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            cancelAuthentication();
        });

        // 设置取消监听
        builder.setOnCancelListener(dialog -> {
            cancelAuthentication();
        });

        // 显示对话框
        builder.show();
    }

    /**
     * 验证密码并加载凭据
     */
    private void verifyPasswordAndLoadCredentials(String password) {
        executor.execute(() -> {
            try {
                // 调用后端服务验证密码
                boolean authenticated = backendService.unlock(password);

                runOnUiThread(() -> {
                    if (authenticated) {
                        android.util.Log.d(TAG, "身份验证成功，加载凭据");
                        isAuthenticated = true;
                        loadCredentials();
                    } else {
                        android.util.Log.d(TAG, "身份验证失败");
                        android.widget.Toast.makeText(this, "密码错误，验证失败", android.widget.Toast.LENGTH_SHORT).show();
                        cancelAuthentication();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    android.util.Log.e(TAG, "验证时发生错误: " + e.getMessage(), e);
                    android.widget.Toast.makeText(this, "验证时发生错误: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                    cancelAuthentication();
                });
            }
        });
    }

    /**
     * 取消认证（用户取消或验证失败）
     */
    private void cancelAuthentication() {
        android.util.Log.d(TAG, "取消认证，返回RESULT_CANCELED");
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 清除敏感引用
        if (credentials != null) {
            credentials.clear();
            credentials = null;
        }
        if (adapter != null) {
            adapter = null;
        }
        backendService = null;
        autofillRequest = null;

        // 关闭ExecutorService
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
}
