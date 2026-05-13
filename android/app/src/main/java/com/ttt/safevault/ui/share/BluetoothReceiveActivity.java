package com.ttt.safevault.ui.share;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ttt.safevault.R;
import com.ttt.safevault.databinding.ActivityBluetoothReceiveBinding;
import com.ttt.safevault.utils.BluetoothTransferManager;

/**
 * 蓝牙接收界面
 * 监听蓝牙连接并接收分享数据
 */
public class BluetoothReceiveActivity extends AppCompatActivity {

    private ActivityBluetoothReceiveBinding binding;
    private BluetoothTransferManager bluetoothManager;
    private ActivityResultLauncher<String[]> bluetoothPermissionLauncher;
    private boolean isListening = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置FLAG_SECURE防止截屏 - 根据 SecurityConfig 设置决定
        com.ttt.safevault.security.SecurityConfig securityConfig =
            new com.ttt.safevault.security.SecurityConfig(this);
        if (securityConfig.isScreenshotProtectionEnabled()) {
            getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            );
        }

        binding = ActivityBluetoothReceiveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 初始化蓝牙管理器
        bluetoothManager = new BluetoothTransferManager(this);
        
        // 初始化权限启动器
        initPermissionLaunchers();

        setupToolbar();
        setupViews();
        
        // 自动开始监听
        startListening();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupViews() {
        binding.btnStartListening.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });
    }

    private void initPermissionLaunchers() {
        // 蓝牙权限启动器
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
                if (allGranted) {
                    startListeningInternal();
                } else {
                    Toast.makeText(this, R.string.bluetooth_permission_required, Toast.LENGTH_SHORT).show();
                }
            }
        );
    }

    private void startListening() {
        // 检查蓝牙是否可用
        if (!bluetoothManager.isBluetoothAvailable()) {
            Toast.makeText(this, R.string.bluetooth_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查蓝牙是否已开启
        if (!bluetoothManager.isBluetoothEnabled()) {
            Toast.makeText(this, R.string.bluetooth_not_enabled, Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查权限
        if (!bluetoothManager.hasBluetoothPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        startListeningInternal();
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            bluetoothPermissionLauncher.launch(new String[]{
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            });
        } else {
            // Android 11 及以下
            bluetoothPermissionLauncher.launch(new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            });
        }
    }

    private void startListeningInternal() {
        // 设置传输回调
        bluetoothManager.setTransferCallback(new BluetoothTransferManager.TransferCallback() {
            @Override
            public void onTransferStarted() {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.textStatus.setText(R.string.bluetooth_receiving);
                });
            }

            @Override
            public void onTransferProgress(int progress) {
                // 可以添加进度更新
            }

            @Override
            public void onTransferSuccess(String data) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(BluetoothReceiveActivity.this, 
                        R.string.bluetooth_receive_success, Toast.LENGTH_SHORT).show();
                    
                    // 跳转到接收分享界面
                    Intent intent = new Intent(BluetoothReceiveActivity.this, ReceiveShareActivity.class);
                    intent.putExtra("SHARE_TOKEN", data);
                    intent.putExtra("FROM_BLUETOOTH", true);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onTransferFailed(String error) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.textStatus.setText(R.string.waiting_for_receiver);
                    Toast.makeText(BluetoothReceiveActivity.this, 
                        getString(R.string.bluetooth_receive_failed) + ": " + error, 
                        Toast.LENGTH_LONG).show();
                    
                    // 继续监听
                    bluetoothManager.startListening();
                });
            }
        });

        // 开始监听
        bluetoothManager.startListening();
        isListening = true;
        
        binding.btnStartListening.setText(R.string.stop_listening);
        binding.textStatus.setText(R.string.waiting_for_receiver);
        binding.iconBluetooth.setVisibility(View.VISIBLE);
    }

    private void stopListening() {
        bluetoothManager.stopListening();
        isListening = false;
        
        binding.btnStartListening.setText(R.string.start_listening);
        binding.textStatus.setText(R.string.bluetooth_transfer);
        binding.iconBluetooth.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothManager != null) {
            bluetoothManager.close();
        }
        binding = null;
    }
}
