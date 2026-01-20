package com.ttt.safevault.ui.share;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;

/**
 * 分享方式选择对话框
 * 提供：联系人分享（QR码）、蓝牙分享、NFC分享三种方式
 */
public class ShareEntryDialog extends DialogFragment {

    public static final String TAG = "ShareEntryDialog";
    private static final String ARG_PASSWORD_ID = "password_id";

    private int passwordId;
    private FragmentActivity activity;

    /**
     * 创建对话框实例
     * @param passwordId 密码ID
     */
    public static ShareEntryDialog newInstance(int passwordId) {
        ShareEntryDialog dialog = new ShareEntryDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_PASSWORD_ID, passwordId);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            passwordId = getArguments().getInt(ARG_PASSWORD_ID, -1);
        }
        activity = getActivity();
        // 不设置特定样式，让系统使用默认的 Material3 主题
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // 使用 MaterialAlertDialogBuilder 创建对话框，自动跟随主题
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

        // 自定义视图
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_share_entry, null);
        initViews(view);
        builder.setView(view);

        return builder.create();
    }

    private void initViews(View view) {
        // 联系人分享
        MaterialCardView cardContact = view.findViewById(R.id.cardContactShare);
        cardContact.setOnClickListener(v -> openContactShare());

        // QR码分享
        MaterialCardView cardQR = view.findViewById(R.id.cardQRShare);
        cardQR.setOnClickListener(v -> openQRShare());

        // 蓝牙分享
        MaterialCardView cardBluetooth = view.findViewById(R.id.cardBluetoothShare);
        cardBluetooth.setOnClickListener(v -> openBluetoothShare());

        // NFC分享
        MaterialCardView cardNfc = view.findViewById(R.id.cardNfcShare);
        cardNfc.setOnClickListener(v -> openNfcShare());

        // 检查NFC是否可用
        if (!isNfcAvailable()) {
            cardNfc.setVisibility(View.GONE);
        }

        // 取消按钮
        Button btnCancel = view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> dismiss());
    }

    /**
     * 打开联系人分享（现有的ShareActivity）
     */
    private void openContactShare() {
        if (activity == null) return;

        Intent intent = new Intent(activity, com.ttt.safevault.ui.share.ShareActivity.class);
        intent.putExtra(com.ttt.safevault.ui.share.ShareActivity.EXTRA_PASSWORD_ID, passwordId);
        startActivity(intent);
        dismiss();
    }

    /**
     * 打开QR码分享（简化版）
     */
    private void openQRShare() {
        if (activity == null) return;

        Intent intent = new Intent(activity, QRShareActivity.class);
        intent.putExtra(QRShareActivity.EXTRA_PASSWORD_ID, passwordId);
        startActivity(intent);
        dismiss();
    }

    /**
     * 打开蓝牙分享
     */
    private void openBluetoothShare() {
        if (activity == null) return;

        Intent intent = new Intent(activity, BluetoothShareActivity.class);
        intent.putExtra(BluetoothShareActivity.EXTRA_PASSWORD_ID, passwordId);
        startActivity(intent);
        dismiss();
    }

    /**
     * 打开NFC分享
     */
    private void openNfcShare() {
        if (activity == null) return;

        Intent intent = new Intent(activity, NFCSendActivity.class);
        intent.putExtra(NFCSendActivity.EXTRA_PASSWORD_ID, passwordId);
        startActivity(intent);
        dismiss();
    }

    /**
     * 检查NFC是否可用
     */
    private boolean isNfcAvailable() {
        if (activity == null) return false;

        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
        return nfcAdapter != null;
    }
}
