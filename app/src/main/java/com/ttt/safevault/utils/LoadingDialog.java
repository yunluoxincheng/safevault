package com.ttt.safevault.utils;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ttt.safevault.R;
import com.ttt.safevault.databinding.DialogLoadingBinding;

/**
 * 现代化 Material Design 3 加载对话框
 * 使用 CircularProgressIndicator 和 MaterialCardView
 */
public class LoadingDialog extends Dialog {

    private final DialogLoadingBinding binding;

    private LoadingDialog(@NonNull Context context, String message) {
        super(context, R.style.Theme_SafeVault);
        binding = DialogLoadingBinding.inflate(LayoutInflater.from(context));

        setContentView(binding.getRoot());
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        // 设置对话框窗口属性
        getWindow().setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        setMessage(message);
    }

    /**
     * 显示加载对话框
     * @param context 上下文
     * @param message 加载提示消息
     * @return LoadingDialog 实例
     */
    public static LoadingDialog show(@NonNull Context context, String message) {
        LoadingDialog dialog = new LoadingDialog(context, message);
        dialog.show();
        return dialog;
    }

    /**
     * 显示加载对话框（字符串资源）
     * @param context 上下文
     * @param messageRes 加载提示消息字符串资源 ID
     * @return LoadingDialog 实例
     */
    public static LoadingDialog show(@NonNull Context context, @StringRes int messageRes) {
        String message = context.getString(messageRes);
        return show(context, message);
    }

    /**
     * 更新加载提示消息
     * @param message 新消息
     */
    public void setMessage(String message) {
        binding.tvMessage.setText(message);
    }

    /**
     * 更新加载提示消息（字符串资源）
     * @param messageRes 新消息字符串资源 ID
     */
    public void setMessage(@StringRes int messageRes) {
        binding.tvMessage.setText(messageRes);
    }
}
