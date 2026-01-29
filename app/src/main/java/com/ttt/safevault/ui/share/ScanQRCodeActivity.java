package com.ttt.safevault.ui.share;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.BeepManager;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.google.zxing.BarcodeFormat;
import com.ttt.safevault.R;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 通用二维码扫描Activity
 * 支持扫描：
 * 1. safevault://identity/{base64} - 身份码
 * 2. safevault://share/{shareId} - 分享码
 * 3. safevault://user/{userId} - 好友码（兼容旧版）
 */
public class ScanQRCodeActivity extends AppCompatActivity {

    private static final String TAG = "ScanQRCodeActivity";
    private static final String IDENTITY_QR_PREFIX = "safevault://identity/";
    private static final String SHARE_QR_PREFIX = "safevault://share/";
    private static final String USER_QR_PREFIX = "safevault://user/";

    private DecoratedBarcodeView barcodeView;
    private BeepManager beepManager;
    private String scanType; // "universal", "share", "friend"

    private final ActivityResultLauncher<String> requestCameraPermission =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                startScanning();
            } else {
                Toast.makeText(this, "需要相机权限才能扫描二维码", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() == null) {
                return;
            }

            // 播放提示音
            beepManager.playBeepSoundAndVibrate();

            // 处理扫描结果
            handleScanResult(result.getText());
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
            // 可选：绘制可能的结果点
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_qr_code);

        // 获取扫描类型
        scanType = getIntent().getStringExtra("scan_type");
        if (scanType == null) {
            scanType = "share"; // 默认为分享扫描
        }

        // 初始化扫描视图
        barcodeView = findViewById(R.id.barcode_scanner);

        // 配置扫描格式（只扫描二维码）
        Collection<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.QR_CODE);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));
        barcodeView.initializeFromIntent(getIntent());
        barcodeView.decodeContinuous(callback);

        // 初始化提示音管理器
        beepManager = new BeepManager(this);

        // 设置toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            String title;
            switch (scanType) {
                case "universal":
                    title = "扫一扫";
                    break;
                case "friend":
                    title = "扫描身份码";
                    break;
                default:
                    title = "扫描分享二维码";
                    break;
            }
            getSupportActionBar().setTitle(title);
        }

        // 检查权限
        if (checkCameraPermission()) {
            startScanning();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startScanning() {
        barcodeView.resume();
    }

    private void handleScanResult(String result) {
        android.util.Log.d(TAG, "扫描到二维码: " + result);

        // 暂停扫描
        barcodeView.pause();

        // 通用扫描模式：识别所有类型的二维码
        if ("universal".equals(scanType)) {
            handleUniversalScan(result);
            return;
        }

        // 好友扫描模式
        if ("friend".equals(scanType)) {
            handleFriendScan(result);
            return;
        }

        // 分享扫描模式（默认）
        handleShareScan(result);
    }

    /**
     * 通用扫描模式：识别身份码和分享码
     */
    private void handleUniversalScan(String result) {
        // 识别身份码
        if (result.startsWith(IDENTITY_QR_PREFIX)) {
            android.util.Log.d(TAG, "识别为身份码");
            // 跳转到添加联系人界面
            Intent intent = new Intent(this, ScanContactActivity.class);
            intent.putExtra("scanned_qr_content", result);
            startActivity(intent);
            finish();
            return;
        }

        // 识别分享码
        if (result.startsWith(SHARE_QR_PREFIX)) {
            android.util.Log.d(TAG, "识别为分享码");
            // 跳转到接收分享界面
            Intent intent = new Intent(this, ReceiveShareActivity.class);
            intent.setData(android.net.Uri.parse(result));
            startActivity(intent);
            finish();
            return;
        }

        // 无法识别的二维码
        Toast.makeText(this, "无法识别此二维码\n仅支持身份码和分享码", Toast.LENGTH_LONG).show();
        barcodeView.resume();
    }

    /**
     * 好友扫描模式（也识别身份码）
     */
    private void handleFriendScan(String result) {
        // 支持身份码和用户码
        if (result.startsWith(IDENTITY_QR_PREFIX) || result.startsWith(USER_QR_PREFIX)) {
            returnResult(result);
        } else {
            Toast.makeText(this, "无效的身份码或好友二维码", Toast.LENGTH_SHORT).show();
            barcodeView.resume();
        }
    }

    /**
     * 分享扫描模式
     */
    private void handleShareScan(String result) {
        if (result.startsWith(SHARE_QR_PREFIX)) {
            // 启动接收分享Activity
            Intent intent = new Intent(this, ReceiveShareActivity.class);
            intent.setData(android.net.Uri.parse(result));
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "无效的分享二维码", Toast.LENGTH_SHORT).show();
            barcodeView.resume();
        }
    }

    /**
     * 返回扫描结果
     */
    private void returnResult(String qrData) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("qr_data", qrData);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkCameraPermission()) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        barcodeView.pause();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
