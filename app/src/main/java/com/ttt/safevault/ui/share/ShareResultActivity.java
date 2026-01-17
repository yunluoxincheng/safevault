package com.ttt.safevault.ui.share;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.ttt.safevault.R;
import com.ttt.safevault.databinding.ActivityShareResultBinding;
import com.ttt.safevault.utils.BluetoothTransferManager;
import com.ttt.safevault.utils.NFCTransferManager;
import com.ttt.safevault.utils.QRCodeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 分享结果显示界面
 * 显示分享二维码和分享码
 */
public class ShareResultActivity extends AppCompatActivity {

    private ActivityShareResultBinding binding;
    private String shareToken;
    private String shareId;
    private boolean isOfflineShare;
    private int passwordId;
    private String transmissionMethod;

    private BluetoothTransferManager bluetoothManager;
    private NFCTransferManager nfcManager;
    private NfcAdapter nfcAdapter;
    private ActivityResultLauncher<String[]> bluetoothPermissionLauncher;
    private ActivityResultLauncher<Intent> enableBluetoothLauncher;

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

        binding = ActivityShareResultBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 初始化蓝牙管理器
        bluetoothManager = new BluetoothTransferManager(this);

        // 初始化NFC管理器
        nfcManager = new NFCTransferManager(this);
        nfcAdapter = nfcManager.getNfcAdapter();

        // 初始化权限启动器
        initPermissionLaunchers();

        // 获取分享Token和ShareId
        shareToken = getIntent().getStringExtra("SHARE_TOKEN");
        shareId = getIntent().getStringExtra("SHARE_ID");
        passwordId = getIntent().getIntExtra("PASSWORD_ID", -1);
        isOfflineShare = getIntent().getBooleanExtra("IS_OFFLINE_SHARE", false);
        transmissionMethod = getIntent().getStringExtra("TRANSMISSION_METHOD");

        if (shareToken == null || shareToken.isEmpty()) {
            Toast.makeText(this, "分享数据无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupViews();

        // 根据传输方式显示不同界面
        if ("BLUETOOTH".equals(transmissionMethod)) {
            showBluetoothTransferUI();
        } else if ("NFC".equals(transmissionMethod)) {
            showNFCTransferUI();
        } else if ("CLOUD".equals(transmissionMethod)) {
            showCloudTransferUI();
        } else {
            generateQRCode();
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // 根据传输方式设置标题
        updateToolbarTitle();
    }

    private void updateToolbarTitle() {
        int titleResId;
        switch (transmissionMethod) {
            case "BLUETOOTH":
                titleResId = R.string.bluetooth_share;
                break;
            case "NFC":
                titleResId = R.string.nfc_share;
                break;
            case "CLOUD":
                titleResId = R.string.cloud_share;
                break;
            case "QR_CODE":
            default:
                titleResId = R.string.qr_code_share;
                break;
        }
        binding.toolbar.setTitle(titleResId);
    }

    private void setupViews() {
        // 显示分享Token
        binding.textShareToken.setText(shareToken);

        // 复制按钮
        binding.btnCopyToken.setOnClickListener(v -> copyTokenToClipboard());

        // 完成按钮
        binding.btnDone.setOnClickListener(v -> finish());
    }

    private void generateQRCode() {
        // 使用QRCodeUtils生成二维码
        Bitmap qrBitmap = QRCodeUtils.generatePasswordShareQRCode(shareToken);

        if (qrBitmap != null) {
            binding.imageQRCode.setImageBitmap(qrBitmap);
        } else {
            Toast.makeText(this, "生成二维码失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyTokenToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("分享码", shareToken);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "分享码已复制", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothManager != null) {
            bluetoothManager.close();
        }
        binding = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 如果是NFC模式，启用NFC前台调度
        if ("NFC".equals(transmissionMethod) && nfcAdapter != null) {
            enableNfcForegroundDispatch();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 禁用NFC前台调度
        if (nfcAdapter != null) {
            disableNfcForegroundDispatch();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 处理NFC标签
        if ("NFC".equals(transmissionMethod) && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                writeNfcTag(tag);
            }
        }
    }

    // ========== 蓝牙传输相关 ==========

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
                    showBluetoothDeviceSelector();
                } else {
                    Toast.makeText(this, R.string.bluetooth_permission_required, Toast.LENGTH_SHORT).show();
                }
            }
        );

        // 开启蓝牙启动器
        enableBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    showBluetoothDeviceSelector();
                } else {
                    Toast.makeText(this, R.string.bluetooth_not_enabled, Toast.LENGTH_SHORT).show();
                }
            }
        );
    }

    private void showBluetoothTransferUI() {
        // 隐藏二维码和分享码相关UI
        binding.imageQRCode.setVisibility(View.GONE);
        binding.cardShareToken.setVisibility(View.GONE);
        binding.btnCopyToken.setVisibility(View.GONE);

        // 显示蓝牙传输按钮
        binding.btnDone.setText(R.string.select_bluetooth_device);
        binding.btnDone.setOnClickListener(v -> startBluetoothTransfer());
    }

    private void startBluetoothTransfer() {
        // 检查蓝牙是否可用
        if (!bluetoothManager.isBluetoothAvailable()) {
            Toast.makeText(this, R.string.bluetooth_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查蓝牙是否已开启
        if (!bluetoothManager.isBluetoothEnabled()) {
            // 请求开启蓝牙
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(enableBtIntent);
            return;
        }

        // 检查权限
        if (!bluetoothManager.hasBluetoothPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        // 显示设备选择器
        showBluetoothDeviceSelector();
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

    private void showBluetoothDeviceSelector() {
        Set<BluetoothDevice> pairedDevices = bluetoothManager.getPairedDevices();

        if (pairedDevices == null || pairedDevices.isEmpty()) {
            Toast.makeText(this, R.string.no_paired_devices, Toast.LENGTH_SHORT).show();
            return;
        }

        // 转换为列表
        List<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
        String[] deviceNames = new String[deviceList.size()];

        for (int i = 0; i < deviceList.size(); i++) {
            BluetoothDevice device = deviceList.get(i);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                deviceNames[i] = device.getName() != null ? device.getName() : device.getAddress();
            } else {
                deviceNames[i] = device.getAddress();
            }
        }

        // 显示选择对话框
        new AlertDialog.Builder(this)
            .setTitle(R.string.select_bluetooth_device)
            .setItems(deviceNames, (dialog, which) -> {
                BluetoothDevice selectedDevice = deviceList.get(which);
                sendViaBluetooth(selectedDevice);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void sendViaBluetooth(BluetoothDevice device) {
        // 设置传输回调
        bluetoothManager.setTransferCallback(new BluetoothTransferManager.TransferCallback() {
            @Override
            public void onTransferStarted() {
                runOnUiThread(() -> {
                    // 显示进度UI
                    binding.layoutTransferProgress.setVisibility(View.VISIBLE);
                    binding.textProgressLabel.setText(R.string.sending);
                    binding.progressIndicator.setProgress(0);
                    binding.textProgressPercent.setText("0%");
                    binding.btnDone.setEnabled(false);
                    binding.btnDone.setText(R.string.sending);
                });
            }

            @Override
            public void onTransferProgress(int progress) {
                runOnUiThread(() -> {
                    // 更新进度条
                    binding.progressIndicator.setProgress(progress);
                    binding.textProgressPercent.setText(progress + "%");
                    binding.btnDone.setText(getString(R.string.transfer_progress, progress));
                });
            }

            @Override
            public void onTransferSuccess(String data) {
                runOnUiThread(() -> {
                    // 隐藏进度UI
                    binding.layoutTransferProgress.setVisibility(View.GONE);
                    Toast.makeText(ShareResultActivity.this,
                        R.string.bluetooth_send_success, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onTransferFailed(String error) {
                runOnUiThread(() -> {
                    // 隐藏进度UI
                    binding.layoutTransferProgress.setVisibility(View.GONE);
                    binding.btnDone.setEnabled(true);
                    binding.btnDone.setText(R.string.select_bluetooth_device);
                    Toast.makeText(ShareResultActivity.this,
                        getString(R.string.bluetooth_send_failed) + ": " + error,
                        Toast.LENGTH_LONG).show();
                });
            }
        });

        // 发送数据
        bluetoothManager.sendData(device, shareToken);
    }

    // ========== NFC传输相关 ==========

    private void showNFCTransferUI() {
        // 隐藏二维码和分享码相关UI
        binding.imageQRCode.setVisibility(View.GONE);
        binding.cardShareToken.setVisibility(View.GONE);
        binding.btnCopyToken.setVisibility(View.GONE);

        // 显示NFC提示
        binding.btnDone.setText(R.string.nfc_ready);
        binding.btnDone.setOnClickListener(null);
        binding.btnDone.setEnabled(false);

        // 检查NFC是否可用
        if (!nfcManager.isNfcAvailable()) {
            Toast.makeText(this, R.string.nfc_not_available, Toast.LENGTH_SHORT).show();
            binding.btnDone.setText(R.string.nfc_not_available);
            return;
        }

        // 检查NFC是否已开启
        if (!nfcManager.isNfcEnabled()) {
            Toast.makeText(this, R.string.nfc_not_enabled, Toast.LENGTH_SHORT).show();
            binding.btnDone.setText(R.string.enable_nfc);
            binding.btnDone.setEnabled(true);
            binding.btnDone.setOnClickListener(v -> {
                // 引导用户到NFC设置
                startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));
            });
            return;
        }

        // NFC已就绪
        Toast.makeText(this, R.string.nfc_tap_hint, Toast.LENGTH_LONG).show();
    }

    // ========== 云端传输相关 ==========

    private void showCloudTransferUI() {
        // 隐藏二维码
        binding.imageQRCode.setVisibility(View.GONE);

        // 生成云端分享链接（占位实现）
        String cloudLink = generateCloudShareLink(shareToken);

        // 显示分享链接
        binding.textShareToken.setVisibility(View.VISIBLE);
        binding.textShareToken.setText(cloudLink);

        // 复制按钮改为复制链接
        binding.btnCopyToken.setVisibility(View.VISIBLE);
        binding.btnCopyToken.setText(R.string.copy_link);
        binding.btnCopyToken.setOnClickListener(v -> copyCloudLinkToClipboard(cloudLink));

        // 完成按钮改为分享链接
        binding.btnDone.setText(R.string.share_link);
        binding.btnDone.setOnClickListener(v -> shareCloudLink(cloudLink));
    }

    /**
     * 生成云端分享链接（使用后端返回的 shareId）
     */
    private String generateCloudShareLink(String shareToken) {
        // 使用后端返回的 shareId 生成真正的分享链接
        if (shareId != null && !shareId.isEmpty()) {
            return "https://safevault.app/share/" + shareId;
        }
        // 如果没有 shareId，回退到占位实现（理论上不应该发生）
        return "https://safevault.app/share/" + shareToken.substring(0, Math.min(8, shareToken.length()));
    }

    /**
     * 复制云端链接到剪贴板
     */
    private void copyCloudLinkToClipboard(String link) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("分享链接", link);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show();
    }

    /**
     * 通过系统分享菜单分享链接
     */
    private void shareCloudLink(String link) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, link);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_password));
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
    }

    private void enableNfcForegroundDispatch() {
        if (nfcAdapter == null) return;

        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        IntentFilter[] filters = new IntentFilter[]{
            new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        };

        nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, null);
    }

    private void disableNfcForegroundDispatch() {
        if (nfcAdapter == null) return;
        nfcAdapter.disableForegroundDispatch(this);
    }

    private void writeNfcTag(Tag tag) {
        // 设置回调
        nfcManager.setTransferCallback(new NFCTransferManager.TransferCallback() {
            @Override
            public void onTransferStarted() {
                runOnUiThread(() -> {
                    binding.btnDone.setText(R.string.nfc_writing);
                });
            }

            @Override
            public void onTransferSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ShareResultActivity.this,
                        R.string.nfc_write_success, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onTransferFailed(String error) {
                runOnUiThread(() -> {
                    binding.btnDone.setText(R.string.nfc_ready);
                    Toast.makeText(ShareResultActivity.this,
                        getString(R.string.nfc_write_failed) + ": " + error,
                        Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onDataReceived(String data) {
                // 发送端不需要处理接收
            }
        });

        // 写入标签
        nfcManager.writeToTag(tag, shareToken);
    }
}
