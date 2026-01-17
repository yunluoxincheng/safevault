package com.ttt.safevault.ui.share;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;
import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.databinding.ActivityShareBinding;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.security.BiometricAuthHelper;
import com.ttt.safevault.viewmodel.ShareViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 密码分享配置界面
 * 支持离线分享和云端分享
 * 分享前需要验证用户身份（生物识别或主密码）
 */
public class ShareActivity extends AppCompatActivity {

    private ActivityShareBinding binding;
    private ShareViewModel viewModel;
    private TokenManager tokenManager;
    private BackendService backendService;
    private BiometricAuthHelper biometricAuthHelper;
    private int passwordId;

    // 传输方式
    private enum TransmissionMethod {
        QR_CODE, BLUETOOTH, NFC, CLOUD_DIRECT, CLOUD_USER, CLOUD_NEARBY
    }
    private TransmissionMethod selectedTransmissionMethod = TransmissionMethod.QR_CODE;

    // 分享类型
    private static final String SHARE_TYPE_DIRECT = "DIRECT";
    private static final String SHARE_TYPE_USER_TO_USER = "USER_TO_USER";
    private static final String SHARE_TYPE_NEARBY = "NEARBY";

    // 过期时间选项（分钟）
    private final String[] expireTimeOptions = {
        "1小时", "1天", "3天", "7天", "永久"
    };
    private final int[] expireTimeValues = {
        60, 1440, 4320, 10080, 0
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 初始化服务
        tokenManager = TokenManager.getInstance(this);
        backendService = ServiceLocator.getInstance().getBackendService();
        biometricAuthHelper = new BiometricAuthHelper(this);

        // 获取要分享的密码ID
        passwordId = getIntent().getIntExtra("PASSWORD_ID", -1);
        if (passwordId == -1) {
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupViewModel();
        setupToolbar();
        setupViews();
        observeViewModel();
    }

    private void setupViewModel() {
        ViewModelFactory factory = new ViewModelFactory(
            getApplication(),
            backendService
        );
        viewModel = new ViewModelProvider(this, factory).get(ShareViewModel.class);

        // 加载密码信息
        viewModel.loadPasswordItem(passwordId);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupViews() {
        // 设置过期时间下拉菜单
        ArrayAdapter<String> expireAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            expireTimeOptions
        );
        binding.autoCompleteExpireTime.setAdapter(expireAdapter);
        binding.autoCompleteExpireTime.setText(expireTimeOptions[1], false); // 默认1天

        // 按钮点击事件
        binding.btnCancel.setOnClickListener(v -> finish());
        binding.btnShare.setOnClickListener(v -> createShare());
        
        // 传输方式切换
        binding.chipGroupTransmissionMethod.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chipQrCode) {
                    selectedTransmissionMethod = TransmissionMethod.QR_CODE;
                    binding.textBluetoothHint.setVisibility(View.GONE);
                    binding.cloudOptionsSection.setVisibility(View.GONE);
                } else if (checkedId == R.id.chipBluetooth) {
                    selectedTransmissionMethod = TransmissionMethod.BLUETOOTH;
                    binding.textBluetoothHint.setVisibility(View.VISIBLE);
                    binding.textBluetoothHint.setText(R.string.bluetooth_share_hint);
                    binding.cloudOptionsSection.setVisibility(View.GONE);
                } else if (checkedId == R.id.chipNfc) {
                    selectedTransmissionMethod = TransmissionMethod.NFC;
                    binding.textBluetoothHint.setVisibility(View.VISIBLE);
                    binding.textBluetoothHint.setText(R.string.nfc_hint);
                    binding.cloudOptionsSection.setVisibility(View.GONE);
                } else if (checkedId == R.id.chipCloudDirect) {
                    selectedTransmissionMethod = TransmissionMethod.CLOUD_DIRECT;
                    binding.textBluetoothHint.setVisibility(View.VISIBLE);
                    binding.textBluetoothHint.setText(R.string.cloud_direct_hint);
                    binding.cloudOptionsSection.setVisibility(View.VISIBLE);
                    binding.userIdInputLayout.setVisibility(View.GONE);
                } else if (checkedId == R.id.chipCloudUser) {
                    selectedTransmissionMethod = TransmissionMethod.CLOUD_USER;
                    binding.textBluetoothHint.setVisibility(View.VISIBLE);
                    binding.textBluetoothHint.setText(R.string.cloud_user_hint);
                    binding.cloudOptionsSection.setVisibility(View.VISIBLE);
                    binding.userIdInputLayout.setVisibility(View.VISIBLE);
                } else if (checkedId == R.id.chipCloudNearby) {
                    selectedTransmissionMethod = TransmissionMethod.CLOUD_NEARBY;
                    binding.textBluetoothHint.setVisibility(View.VISIBLE);
                    binding.textBluetoothHint.setText(R.string.cloud_nearby_hint);
                    binding.cloudOptionsSection.setVisibility(View.VISIBLE);
                    binding.userIdInputLayout.setVisibility(View.GONE);
                    // 打开附近用户选择界面
                    Intent intent = new Intent(ShareActivity.this, NearbyUsersActivity.class);
                    startActivityForResult(intent, REQUEST_CODE_NEARBY_USER);
                }
            }
        });
    }

    private void observeViewModel() {
        // 观察加载状态
        viewModel.isLoading.observe(this, isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnShare.setEnabled(!isLoading);
        });

        // 观察错误信息
        viewModel.errorMessage.observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
            }
        });

        // 观察分享成功
        viewModel.shareSuccess.observe(this, success -> {
            if (success) {
                // 用户对用户分享（直接通知接收方）或附近用户分享，直接返回
                if (selectedTransmissionMethod == TransmissionMethod.CLOUD_USER
                        || selectedTransmissionMethod == TransmissionMethod.CLOUD_NEARBY) {
                    Toast.makeText(this, "分享成功，对方将收到通知", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // 云端直接分享或离线分享，跳转到分享结果页面
                viewModel.cloudShareResponse.observe(this, shareResponse -> {
                    if (shareResponse != null) {
                        Intent intent = new Intent(this, ShareResultActivity.class);
                        intent.putExtra("SHARE_TOKEN", shareResponse.getShareToken());
                        intent.putExtra("SHARE_ID", shareResponse.getShareId());
                        intent.putExtra("PASSWORD_ID", passwordId);
                        intent.putExtra("TRANSMISSION_METHOD", selectedTransmissionMethod.name());

                        boolean isOfflineShare = selectedTransmissionMethod == TransmissionMethod.QR_CODE
                                || selectedTransmissionMethod == TransmissionMethod.BLUETOOTH
                                || selectedTransmissionMethod == TransmissionMethod.NFC;
                        if (isOfflineShare) {
                            intent.putExtra("IS_OFFLINE_SHARE", true);
                        } else {
                            intent.putExtra("IS_CLOUD_SHARE", true);
                        }

                        startActivity(intent);
                        finish();
                    }
                });
            }
        });
    }

    private void createShare() {
        // 分享前需要验证用户身份
        authenticateAndShare();
    }

    /**
     * 验证用户身份后执行分享
     */
    private void authenticateAndShare() {
        // 检查是否可以使用生物识别
        boolean canUseBiometric = BiometricAuthHelper.isBiometricSupported(this)
                && backendService.canUseBiometricAuthentication();

        if (canUseBiometric) {
            // 使用生物识别验证
            showBiometricAuthAndShare();
        } else {
            // 使用主密码验证
            showPasswordAuthAndShare();
        }
    }

    /**
     * 使用生物识别验证
     */
    private void showBiometricAuthAndShare() {
        biometricAuthHelper.authenticate(new BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                // 生物识别验证成功，执行分享
                runOnUiThread(() -> performShare());
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ShareActivity.this,
                        "生物识别验证失败: " + error, Toast.LENGTH_SHORT).show();
                    // 验证失败，尝试使用主密码
                    showPasswordAuthAndShare();
                });
            }

            @Override
            public void onCancel() {
                // 用户取消生物识别，不执行任何操作
                runOnUiThread(() ->
                    Toast.makeText(ShareActivity.this, "已取消分享", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    /**
     * 使用主密码验证
     */
    private void showPasswordAuthAndShare() {
        // 使用自定义布局，包含密码可见性切换
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_input, null);
        com.google.android.material.textfield.TextInputLayout passwordLayout =
            dialogView.findViewById(R.id.passwordLayout);
        com.google.android.material.textfield.TextInputEditText passwordInput =
            dialogView.findViewById(R.id.passwordInput);

        // 确保初始状态正确：密码隐藏，闭眼图标
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordLayout.setEndIconDrawable(getDrawable(R.drawable.ic_visibility));

        // 设置密码可见性切换
        passwordLayout.setEndIconOnClickListener(v -> {
            // 切换密码可见性
            int selection = passwordInput.getSelectionEnd();
            int currentInputType = passwordInput.getInputType();
            int variation = currentInputType & android.text.InputType.TYPE_MASK_VARIATION;

            if (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                // 当前是密码状态，切换为可见
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                passwordLayout.setEndIconDrawable(getDrawable(R.drawable.ic_visibility_off));
            } else {
                // 当前是可见状态，切换为密码
                passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordLayout.setEndIconDrawable(getDrawable(R.drawable.ic_visibility));
            }
            // 保持光标位置
            passwordInput.setSelection(selection);
        });

        new MaterialAlertDialogBuilder(this)
            .setTitle("验证身份")
            .setMessage("请输入主密码以验证身份")
            .setView(dialogView)
            .setPositiveButton("确认", (dialog, which) -> {
                String password = passwordInput.getText().toString();
                if (password.isEmpty()) {
                    Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 在后台线程验证密码
                new Thread(() -> {
                    boolean verified = backendService.unlock(password);
                    runOnUiThread(() -> {
                        if (verified) {
                            performShare();
                        } else {
                            Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start();
            })
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show();
    }

    /**
     * 执行实际的分享操作
     */
    private void performShare() {
        // 获取选择的过期时间
        String selectedExpireTime = binding.autoCompleteExpireTime.getText().toString();
        int expireInMinutes = getExpireTimeValue(selectedExpireTime);

        // 创建分享权限
        SharePermission permission = new SharePermission();
        permission.setCanView(true);
        permission.setCanSave(binding.switchAllowSave.isChecked());
        permission.setRevocable(binding.switchRevocable.isChecked());

        // 根据传输方式决定分享类型
        switch (selectedTransmissionMethod) {
            case CLOUD_DIRECT:
                // 云端直接分享
                if (!tokenManager.isLoggedIn()) {
                    Toast.makeText(this, R.string.please_login_cloud_first, Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.createCloudShare(passwordId, null, expireInMinutes, permission, SHARE_TYPE_DIRECT);
                break;

            case CLOUD_USER:
                // 云端用户对用户分享
                if (!tokenManager.isLoggedIn()) {
                    Toast.makeText(this, R.string.please_login_cloud_first, Toast.LENGTH_SHORT).show();
                    return;
                }
                String toUserId = binding.userIdInput.getText().toString().trim();
                if (toUserId.isEmpty()) {
                    Toast.makeText(this, R.string.please_enter_user_id, Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.createCloudShare(passwordId, toUserId, expireInMinutes, permission, SHARE_TYPE_USER_TO_USER);
                break;

            case CLOUD_NEARBY:
                // 云端附近用户分享
                if (!tokenManager.isLoggedIn()) {
                    Toast.makeText(this, R.string.please_login_cloud_first, Toast.LENGTH_SHORT).show();
                    return;
                }
                String nearbyUserId = getIntent().getStringExtra("SELECTED_USER_ID");
                if (nearbyUserId == null || nearbyUserId.isEmpty()) {
                    Toast.makeText(this, R.string.please_select_nearby_user, Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.createCloudShare(passwordId, nearbyUserId, expireInMinutes, permission, SHARE_TYPE_NEARBY);
                break;

            default:
                // 离线分享（使用二维码/蓝牙/NFC）
                viewModel.createOfflineShare(passwordId, expireInMinutes, permission);
                break;
        }
    }

    private int getExpireTimeValue(String expireTimeText) {
        for (int i = 0; i < expireTimeOptions.length; i++) {
            if (expireTimeOptions[i].equals(expireTimeText)) {
                return expireTimeValues[i];
            }
        }
        return expireTimeValues[1]; // 默认1天
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    private static final int REQUEST_CODE_NEARBY_USER = 1001;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_NEARBY_USER && resultCode == RESULT_OK && data != null) {
            String selectedUserId = data.getStringExtra("SELECTED_USER_ID");
            String selectedUserName = data.getStringExtra("SELECTED_USER_NAME");
            if (selectedUserId != null) {
                getIntent().putExtra("SELECTED_USER_ID", selectedUserId);
                if (binding.userIdInputLayout != null) {
                    binding.userIdInputLayout.setVisibility(View.VISIBLE);
                    binding.userIdInput.setText(selectedUserName != null ? selectedUserName : selectedUserId);
                    binding.userIdInput.setEnabled(false);
                }
            }
        }
    }
}
