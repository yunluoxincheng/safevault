package com.ttt.safevault.ui.friend;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.ttt.safevault.R;
import com.ttt.safevault.dto.response.UserSearchResult;

/**
 * 发送好友请求对话框
 * 显示目标用户信息，允许用户输入可选的留言
 */
public class SendFriendRequestDialog extends DialogFragment {

    private static final String ARG_USER_RESULT = "user_result";

    private UserSearchResult userResult;
    private OnSendRequestListener listener;

    /**
     * 创建对话框实例
     * @param userResult 搜索结果用户信息
     */
    public static SendFriendRequestDialog newInstance(UserSearchResult userResult) {
        SendFriendRequestDialog dialog = new SendFriendRequestDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_USER_RESULT, userResult);
        dialog.setArguments(args);
        return dialog;
    }

    public interface OnSendRequestListener {
        /**
         * 发送好友请求回调
         * @param toUserId 目标用户ID
         * @param message 可选的留言
         */
        void onSendRequest(String toUserId, String message);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            userResult = (UserSearchResult) getArguments().getSerializable(ARG_USER_RESULT);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

        // 自定义视图
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_send_friend_request, null);
        initViews(view);
        builder.setView(view);

        return builder.create();
    }

    private void initViews(View view) {
        // 显示目标用户信息
        TextView textTargetUser = view.findViewById(R.id.text_target_user);
        String displayText = userResult.getDisplayName() != null && !userResult.getDisplayName().isEmpty()
                ? userResult.getDisplayName()
                : userResult.getUsername();
        textTargetUser.setText(displayText);

        // 用户名
        TextView textUsername = view.findViewById(R.id.text_username);
        textUsername.setText("@" + userResult.getUsername());

        // 留言输入框
        TextInputEditText editMessage = view.findViewById(R.id.edit_message);

        // 取消按钮
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        btnCancel.setOnClickListener(v -> dismiss());

        // 发送按钮
        Button btnSend = view.findViewById(R.id.btn_send);
        btnSend.setOnClickListener(v -> {
            String message = editMessage.getText() != null ? editMessage.getText().toString().trim() : "";
            if (listener != null) {
                listener.onSendRequest(userResult.getUserId(), message);
            }
            dismiss();
        });
    }

    /**
     * 设置发送请求监听器
     */
    public void setOnSendRequestListener(OnSendRequestListener listener) {
        this.listener = listener;
    }
}
