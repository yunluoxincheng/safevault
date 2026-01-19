package com.ttt.safevault.ui.share;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.ttt.safevault.R;
import com.ttt.safevault.service.manager.ContactManager;

/**
 * 扫描添加联系人界面
 * 通过扫描QR码或手动输入添加联系人
 */
public class ScanContactActivity extends AppCompatActivity {
    private static final String TAG = "ScanContactActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private MaterialToolbar toolbar;
    private MaterialButton btnScan;
    private MaterialButton btnManualInput;
    private CircularProgressIndicator progressBar;

    private ContactManager contactManager;

    // 相机权限请求 launcher
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startQRScanner();
                } else {
                    Toast.makeText(this, "需要相机权限才能扫描QR码", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_contact);

        contactManager = new ContactManager(this);

        initViews();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        btnScan = findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(v -> checkCameraPermission());

        btnManualInput = findViewById(R.id.btn_manual_input);
        btnManualInput.setOnClickListener(v -> showManualInputDialog());

        progressBar = findViewById(R.id.progress_bar);
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            // 权限已授予，启动扫描
            startQRScanner();
        } else {
            // 请求权限
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startQRScanner() {
        // 使用 ZXing IntentIntegrator 启动扫描
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("将QR码放入扫描框内");
        integrator.setCameraId(0);  // 使用后置摄像头
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 处理QR码扫描结果
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                // 扫描成功，处理QR码内容
                handleScannedData(result.getContents());
            } else {
                // 扫描取消
                Toast.makeText(this, "扫描已取消", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleScannedData(String qrContent) {
        // 验证是否是 SafeVault 身份码
        if (!qrContent.startsWith("safevault://identity/")) {
            Toast.makeText(this, "这不是有效的 SafeVault 身份码", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示添加对话框，输入备注
        showAddContactDialog(qrContent);
    }

    private void showManualInputDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_input_qr, null);
        TextInputEditText editQRCode = dialogView.findViewById(R.id.edit_qr_code);
        editQRCode.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        new AlertDialog.Builder(this)
                .setTitle("手动输入身份码")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    String qrContent = editQRCode.getText().toString().trim();
                    if (qrContent.isEmpty()) {
                        Toast.makeText(this, "请输入身份码", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    handleScannedData(qrContent);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddContactDialog(String qrContent) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_note, null);
        TextInputEditText editNote = dialogView.findViewById(R.id.edit_note);
        editNote.setHint("输入备注（如：妈妈、同事等）");

        new AlertDialog.Builder(this)
                .setTitle("添加联系人")
                .setMessage("请为此联系人添加备注")
                .setView(dialogView)
                .setPositiveButton("添加", (dialog, which) -> {
                    String note = editNote.getText().toString().trim();
                    addContact(qrContent, note);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addContact(String qrContent, String note) {
        // 显示加载指示器
        progressBar.setVisibility(View.VISIBLE);

        // 在后台线程添加联系人
        new Thread(() -> {
            boolean success = contactManager.addContactFromQR(qrContent, note);

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);

                if (success) {
                    Toast.makeText(this, "联系人添加成功", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("添加失败")
                            .setMessage("无法添加联系人，可能原因：\n1. 身份码格式错误\n2. 该联系人已存在")
                            .setPositiveButton("确定", null)
                            .show();
                }
            });
        }).start();
    }
}
