package com.ttt.safevault.ui.share;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.Gson;
import com.ttt.safevault.R;
import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.security.BiometricAuthHelper;
import com.ttt.safevault.service.manager.ShareRecordManager;
import com.ttt.safevault.utils.BluetoothTransferManager;
import com.ttt.safevault.viewmodel.ShareViewModel;
import com.ttt.safevault.viewmodel.ViewModelFactory;

import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 蓝牙分享界面
 * 选择设备并通过蓝牙发送加密的密码数据
 */
public class BluetoothShareActivity extends AppCompatActivity {

    private static final String TAG = "BluetoothShareActivity";
    public static final String EXTRA_PASSWORD_ID = "password_id";

    private ShareViewModel viewModel;
    private BackendService backendService;
    private BiometricAuthHelper biometricAuthHelper;
    private BluetoothTransferManager bluetoothManager;
    private ShareRecordManager recordManager;

    private TextView textUsername;
    private TextView textSelectedDevice;
    private Button btnSelectDevice;
    private Button btnSend;
    private LinearProgressIndicator progressIndicator;
    private TextView textStatus;

    private int passwordId;
    private PasswordItem passwordToShare;
    private BluetoothDevice selectedDevice;
    private String shareData;

    private ActivityResultLauncher<String[]> bluetoothPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_share);

        // 设置FLAG_SECURE防止截屏
        com.ttt.safevault.security.SecurityConfig securityConfig =
            new com.ttt.safevault.security.SecurityConfig(this);
        if (securityConfig.isScreenshotProtectionEnabled()) {
            getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            );
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("蓝牙分享");
        }

        passwordId = getIntent().getIntExtra(EXTRA_PASSWORD_ID, -1);

        if (passwordId == -1) {
            Toast.makeText(this, "无效的密码ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initManagers();
        initPermissionLaunchers();
        initViews();
        loadPassword();
    }

    private void initManagers() {
        backendService = ServiceLocator.getInstance().getBackendService();
        biometricAuthHelper = new BiometricAuthHelper(this);
        ViewModelFactory factory = new ViewModelFactory(getApplication(), backendService);
        viewModel = new ViewModelProvider(this, factory).get(ShareViewModel.class);
        bluetoothManager = new BluetoothTransferManager(this);
        recordManager = new ShareRecordManager(this);
    }

    private void initPermissionLaunchers() {
        bluetoothPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (!allGranted) {
                    Toast.makeText(this, R.string.bluetooth_permission_required, Toast.LENGTH_SHORT).show();
                }
            }
        );
    }

    private void initViews() {
        textUsername = findViewById(R.id.textUsername);
        textSelectedDevice = findViewById(R.id.textSelectedDevice);
        btnSelectDevice = findViewById(R.id.btnSelectDevice);
        btnSend = findViewById(R.id.btnSend);
        progressIndicator = findViewById(R.id.progressIndicator);
        textStatus = findViewById(R.id.textStatus);

        btnSelectDevice.setOnClickListener(v -> showDeviceListDialog());
        btnSend.setOnClickListener(v -> authenticateAndSend());
    }

    private void loadPassword() {
        viewModel.loadPasswordItem(passwordId);
        viewModel.getPasswordById(passwordId).observe(this, password -> {
            if (password != null) {
                passwordToShare = password;
                textUsername.setText(password.getUsername());
            } else {
                Toast.makeText(this, "密码不存在", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void showDeviceListDialog() {
        // 检查蓝牙权限
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        // 检查蓝牙是否开启
        if (!bluetoothManager.isBluetoothEnabled()) {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bluetooth_not_enabled)
                .setMessage("请先开启蓝牙")
                .setPositiveButton("去开启", (dialog, which) -> {
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
            return;
        }

        // 显示设备列表对话框
        BluetoothDeviceListDialog dialog = BluetoothDeviceListDialog.newInstance(passwordId);
        dialog.setOnDeviceSelectedListener(this::onDeviceSelected);
        dialog.show(getSupportFragmentManager(), BluetoothDeviceListDialog.TAG);
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(new String[]{
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            });
        }
    }

    private void onDeviceSelected(BluetoothDevice device) {
        selectedDevice = device;
        String name = device.getName();
        if (name == null || name.isEmpty()) {
            name = "未知设备";
        }
        textSelectedDevice.setText(name);
        textSelectedDevice.setTextColor(getColor(android.R.color.white));
    }

    private void authenticateAndSend() {
        if (selectedDevice == null) {
            Toast.makeText(this, "请先选择设备", Toast.LENGTH_SHORT).show();
            return;
        }

        if (passwordToShare == null) {
            Toast.makeText(this, "密码数据未加载", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean canUseBiometric = BiometricAuthHelper.isBiometricSupported(this)
                && backendService.canUseBiometricAuthentication();

        if (canUseBiometric) {
            showBiometricAuthAndSend();
        } else {
            showPasswordAuthAndSend();
        }
    }

    private void showBiometricAuthAndSend() {
        biometricAuthHelper.authenticate(new BiometricAuthHelper.BiometricAuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> prepareAndSend());
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(BluetoothShareActivity.this,
                        "生物识别验证失败: " + error, Toast.LENGTH_SHORT).show();
                    showPasswordAuthAndSend();
                });
            }

            @Override
            public void onCancel() {
                runOnUiThread(() ->
                    Toast.makeText(BluetoothShareActivity.this, "已取消分享", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showPasswordAuthAndSend() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_input, null);
        com.google.android.material.textfield.TextInputLayout passwordLayout =
            dialogView.findViewById(R.id.passwordLayout);
        com.google.android.material.textfield.TextInputEditText passwordInput =
            dialogView.findViewById(R.id.passwordInput);

        if (passwordInput != null) {
            passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        if (passwordLayout != null) {
            passwordLayout.setEndIconDrawable(getDrawable(R.drawable.ic_visibility));

            passwordLayout.setEndIconOnClickListener(v -> {
                if (passwordInput == null) return;

                int selection = passwordInput.getSelectionEnd();
                int currentInputType = passwordInput.getInputType();
                int variation = currentInputType & android.text.InputType.TYPE_MASK_VARIATION;

                if (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                    passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    passwordLayout.setEndIconDrawable(getDrawable(R.drawable.ic_visibility_off));
                } else {
                    passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    passwordLayout.setEndIconDrawable(getDrawable(R.drawable.ic_visibility));
                }
                if (selection >= 0) {
                    passwordInput.setSelection(selection);
                }
            });
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("验证身份")
            .setMessage("请输入主密码以验证身份")
            .setView(dialogView)
            .setPositiveButton("确认", (dialog, which) -> {
                if (passwordInput == null) return;

                String password = passwordInput.getText().toString();
                if (password.isEmpty()) {
                    Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
                    return;
                }

                new Thread(() -> {
                    boolean verified = backendService.unlock(password);
                    runOnUiThread(() -> {
                        if (verified) {
                            prepareAndSend();
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

    private void prepareAndSend() {
        new Thread(() -> {
            try {
                // 生成加密数据包
                shareData = generateSharePacket();

                runOnUiThread(() -> {
                    sendViaBluetooth();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "数据准备失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private String generateSharePacket() throws Exception {
        // 1. 生成临时AES密钥
        byte[] keyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(keyBytes);
        String tempKeyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP);

        // 2. 将密码数据转换为JSON
        Map<String, Object> passwordData = new HashMap<>();
        passwordData.put("title", passwordToShare.getTitle());
        passwordData.put("username", passwordToShare.getUsername());
        passwordData.put("password", passwordToShare.getPassword());
        passwordData.put("url", passwordToShare.getUrl());
        passwordData.put("notes", passwordToShare.getNotes());
        String passwordJson = new Gson().toJson(passwordData);

        // 3. 用临时密钥加密密码数据
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);

        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

        byte[] encryptedData = cipher.doFinal(passwordJson.getBytes("UTF-8"));
        String encryptedDataBase64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP);
        String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP);

        // 4. 构建分享数据包（离线分享无法强制过期）
        Map<String, Object> sharePacket = new HashMap<>();
        sharePacket.put("v", "2.0");
        sharePacket.put("data", encryptedDataBase64);
        sharePacket.put("key", tempKeyBase64);
        sharePacket.put("iv", ivBase64);
        sharePacket.put("meta", Map.of(
            "created", System.currentTimeMillis(),
            "expire", 0,  // 离线分享无法强制过期
            "perm", Map.of("view", true, "save", true)
        ));

        return new Gson().toJson(sharePacket);
    }

    private void sendViaBluetooth() {
        // 设置传输回调
        bluetoothManager.setTransferCallback(new BluetoothTransferManager.TransferCallback() {
            @Override
            public void onTransferStarted() {
                runOnUiThread(() -> {
                    progressIndicator.setVisibility(View.VISIBLE);
                    textStatus.setText("正在发送...");
                    btnSend.setEnabled(false);
                });
            }

            @Override
            public void onTransferProgress(int progress) {
                runOnUiThread(() -> {
                    progressIndicator.setProgress(progress);
                });
            }

            @Override
            public void onTransferSuccess(String data) {
                runOnUiThread(() -> {
                    progressIndicator.setVisibility(View.GONE);
                    textStatus.setText("发送成功");
                    Toast.makeText(BluetoothShareActivity.this, "密码已发送", Toast.LENGTH_SHORT).show();

                    // 保存分享记录
                    recordManager.createShareRecord(
                        passwordId,
                        "sent_bluetooth",
                        selectedDevice.getAddress(),
                        shareData,
                        new SharePermission(true, true, true),
                        0  // 离线分享无法强制过期
                    );

                    finish();
                });
            }

            @Override
            public void onTransferFailed(String error) {
                runOnUiThread(() -> {
                    progressIndicator.setVisibility(View.GONE);
                    textStatus.setText("发送失败");
                    btnSend.setEnabled(true);
                    Toast.makeText(BluetoothShareActivity.this,
                        "发送失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });

        // 发送数据
        bluetoothManager.sendData(selectedDevice, shareData);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothManager != null) {
            bluetoothManager.close();
        }
    }
}
