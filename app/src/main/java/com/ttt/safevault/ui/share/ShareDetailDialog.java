package com.ttt.safevault.ui.share;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;
import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.data.ShareRecord;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.service.manager.ShareRecordManager;
import com.ttt.safevault.utils.ClipboardManager;
import com.ttt.safevault.utils.UIUtils;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 分享详情对话框
 * 显示密码分享的详细信息，包括密码内容、时间、状态、权限等
 */
public class ShareDetailDialog extends DialogFragment {

    private static final String TAG = "ShareDetailDialog";
    private static final String ARG_SHARE_RECORD = "share_record";
    private static final String ARG_CONTACT_ID = "contact_id";

    private ShareRecord shareRecord;
    private String contactId;
    private PasswordItem passwordItem;
    private SharePermission permission;

    private BackendService backendService;
    private ShareRecordManager recordManager;

    private TextView textTitle;
    private TextView textUsername;
    private TextView textPassword;
    private TextView textShareTime;
    private TextView textExpireTime;
    private TextView textStatus;
    private TextView textPermissions;

    private MaterialButton btnCopyPassword;
    private MaterialButton btnRevoke;
    private MaterialButton btnSave;
    private MaterialButton btnClose;

    public static ShareDetailDialog newInstance(ShareRecord record, String contactId) {
        ShareDetailDialog dialog = new ShareDetailDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_SHARE_RECORD, record);
        args.putString(ARG_CONTACT_ID, contactId);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_SafeVault_Dialog);

        if (getArguments() != null) {
            shareRecord = (ShareRecord) getArguments().getSerializable(ARG_SHARE_RECORD);
            contactId = getArguments().getString(ARG_CONTACT_ID);
        }

        backendService = ServiceLocator.getInstance().getBackendService();
        recordManager = new ShareRecordManager(requireContext());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_share_detail, null);
        initViews(view);
        loadData();

        builder.setView(view);
        Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private void initViews(View view) {
        textTitle = view.findViewById(R.id.text_title);
        textUsername = view.findViewById(R.id.text_username);
        textPassword = view.findViewById(R.id.text_password);
        textShareTime = view.findViewById(R.id.text_share_time);
        textExpireTime = view.findViewById(R.id.text_expire_time);
        textStatus = view.findViewById(R.id.text_status);
        textPermissions = view.findViewById(R.id.text_permissions);

        btnCopyPassword = view.findViewById(R.id.btn_copy_password);
        btnRevoke = view.findViewById(R.id.btn_revoke);
        btnSave = view.findViewById(R.id.btn_save);
        btnClose = view.findViewById(R.id.btn_close);

        // 复制密码
        if (btnCopyPassword != null) {
            btnCopyPassword.setOnClickListener(v -> copyPasswordToClipboard());
        }

        // 撤销分享
        if (btnRevoke != null) {
            btnRevoke.setOnClickListener(v -> confirmRevokeShare());
        }

        // 保存到本地
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> savePasswordToLocal());
        }

        // 关闭对话框
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
    }

    private void loadData() {
        if (shareRecord == null) {
            dismiss();
            return;
        }

        // 加载密码信息
        new Thread(() -> {
            if (backendService != null) {
                passwordItem = backendService.getPasswordById(shareRecord.passwordId);
            }

            // 解析权限
            if (shareRecord.permission != null) {
                try {
                    permission = new Gson().fromJson(shareRecord.permission, SharePermission.class);
                } catch (Exception e) {
                    permission = new SharePermission(true, true, true);
                }
            } else {
                permission = new SharePermission(true, true, true);
            }

            // 更新UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> updateUI());
            }
        }).start();
    }

    private void updateUI() {
        if (passwordItem == null) {
            if (textTitle != null) {
                textTitle.setText("密码不存在");
            }
            return;
        }

        Context context = requireContext();

        // 密码信息
        if (textTitle != null) {
            textTitle.setText(passwordItem.getTitle());
        }
        if (textUsername != null) {
            textUsername.setText(passwordItem.getUsername());
        }
        if (textPassword != null) {
            String password = passwordItem.getPassword();
            if (password != null && password.length() > 4) {
                String masked = password.substring(0, 2) + "****" + password.substring(password.length() - 2);
                textPassword.setText(masked);
            } else {
                textPassword.setText("****");
            }
        }

        // 分享时间
        if (textShareTime != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            textShareTime.setText(sdf.format(new Date(shareRecord.createdAt)));
        }

        // 过期时间
        if (textExpireTime != null) {
            if (shareRecord.expireAt == 0) {
                textExpireTime.setText(R.string.never_expires);
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                String expireText = sdf.format(new Date(shareRecord.expireAt));

                // 检查是否已过期
                boolean isExpired = System.currentTimeMillis() > shareRecord.expireAt;
                if (isExpired) {
                    textExpireTime.setText(context.getString(R.string.expired) + " (" + expireText + ")");
                    textExpireTime.setTextColor(context.getColor(R.color.error_red));
                } else {
                    long remaining = shareRecord.expireAt - System.currentTimeMillis();
                    String timeRemaining = DateUtils.getRelativeTimeSpanString(
                            shareRecord.expireAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                    ).toString();
                    textExpireTime.setText(timeRemaining + " (" + expireText + ")");
                }
            }
        }

        // 状态
        if (textStatus != null) {
            String statusText = formatStatus(shareRecord.status);
            textStatus.setText(statusText);

            // 设置状态颜色
            int statusColor = getStatusColor(shareRecord.status);
            textStatus.setTextColor(context.getColor(statusColor));
        }

        // 权限
        if (textPermissions != null) {
            StringBuilder permText = new StringBuilder();
            if (permission != null) {
                if (permission.canView) {
                    permText.append(context.getString(R.string.can_view)).append(" ");
                }
                if (permission.canSave) {
                    permText.append(context.getString(R.string.can_save)).append(" ");
                }
                if (permission.revocable) {
                    permText.append(context.getString(R.string.is_revocable)).append(" ");
                }
            }
            textPermissions.setText(permText.toString().trim());
        }

        // 按钮状态
        updateButtonsState();
    }

    private void updateButtonsState() {
        boolean isSent = "sent".equals(shareRecord.type);
        boolean isActive = "active".equals(shareRecord.status);

        // 只有发送的活跃分享可以撤销
        if (btnRevoke != null) {
            btnRevoke.setVisibility(isSent && isActive && permission != null && permission.revocable
                    ? View.VISIBLE : View.GONE);
        }

        // 只有接收的分享可以保存
        if (btnSave != null) {
            btnSave.setVisibility(!isSent ? View.VISIBLE : View.GONE);
        }

        // 密码复制按钮（如果有的话）
        if (btnCopyPassword != null) {
            btnCopyPassword.setVisibility(passwordItem != null && passwordItem.getPassword() != null
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void copyPasswordToClipboard() {
        if (passwordItem == null || passwordItem.getPassword() == null) {
            UIUtils.showError(requireContext(), "密码不可用");
            return;
        }

        ClipboardManager clipboardManager = new ClipboardManager(requireContext());
        clipboardManager.copySensitiveText(passwordItem.getPassword(), "SafeVault Password");
        UIUtils.showSuccess(requireContext(), R.string.password_copied);
    }

    private void confirmRevokeShare() {
        UIUtils.showConfirmDialog(
                requireContext(),
                getString(R.string.confirm_revoke_share),
                getString(R.string.confirm_revoke_share_message),
                () -> revokeShare()
        );
    }

    private void revokeShare() {
        if (shareRecord == null) return;

        new Thread(() -> {
            boolean success = recordManager.revokeShare(shareRecord.shareId);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (success) {
                        UIUtils.showSuccess(requireContext(), R.string.share_revoked_success);
                        shareRecord.status = "revoked";
                        updateUI();
                        // 通知监听器
                        if (getListener() != null) {
                            getListener().onShareRevoked(shareRecord);
                        }
                    } else {
                        UIUtils.showError(requireContext(), R.string.share_revoked_failed);
                    }
                });
            }
        }).start();
    }

    private void savePasswordToLocal() {
        if (passwordItem == null) {
            UIUtils.showError(requireContext(), "密码不可用");
            return;
        }

        new Thread(() -> {
            try {
                if (backendService != null) {
                    // 创建新的密码条目（新ID）
                    PasswordItem newPassword = new PasswordItem();
                    newPassword.setTitle(passwordItem.getTitle());
                    newPassword.setUsername(passwordItem.getUsername());
                    newPassword.setPassword(passwordItem.getPassword());
                    newPassword.setUrl(passwordItem.getUrl());
                    newPassword.setNotes(passwordItem.getNotes());
                    newPassword.setTags(passwordItem.getTags());

                    boolean success = backendService.addPassword(newPassword);

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (success) {
                                UIUtils.showSuccess(requireContext(), "密码已保存到本地");
                                if (getListener() != null) {
                                    getListener().onPasswordSaved(shareRecord);
                                }
                                dismiss();
                            } else {
                                UIUtils.showError(requireContext(), "保存密码失败");
                            }
                        });
                    }
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        UIUtils.showError(requireContext(), "保存失败: " + e.getMessage());
                    });
                }
            }
        }).start();
    }

    private String formatStatus(String status) {
        if (status == null) return "";

        switch (status) {
            case "active":
                return getString(R.string.share_status_active);
            case "expired":
                return getString(R.string.share_status_expired);
            case "revoked":
                return getString(R.string.share_status_revoked);
            case "accepted":
                return getString(R.string.share_status_accepted);
            case "pending":
                return getString(R.string.share_status_pending);
            default:
                return status;
        }
    }

    private int getStatusColor(String status) {
        if (status == null) return R.color.md_theme_light_outline;

        switch (status) {
            case "active":
                return R.color.success_green;
            case "expired":
            case "pending":
                return R.color.strength_medium;
            case "revoked":
                return R.color.error_red;
            case "accepted":
                return R.color.primary_blue;
            default:
                return R.color.md_theme_light_outline;
        }
    }

    private ShareDetailListener getListener() {
        if (getParentFragment() instanceof ShareDetailListener) {
            return (ShareDetailListener) getParentFragment();
        }
        if (getActivity() instanceof ShareDetailListener) {
            return (ShareDetailListener) getActivity();
        }
        return null;
    }

    public interface ShareDetailListener {
        void onShareRevoked(ShareRecord record);
        void onPasswordSaved(ShareRecord record);
    }
}
