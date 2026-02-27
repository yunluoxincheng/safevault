package com.ttt.safevault.ui.share;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ttt.safevault.R;
import com.ttt.safevault.data.Contact;
import com.ttt.safevault.security.SafetyNumberManager;

import java.security.PublicKey;

/**
 * 安全码验证对话框
 * 显示双方的安全码，供用户线下验证
 */
public class SafetyNumberVerificationDialog {

    public interface Callback {
        void onVerified();
        void onNotMatch();
        void onSkip();
    }

    /**
     * 显示安全码验证对话框
     *
     * @param context 上下文
     * @param contact 联系人
     * @param receiverPublicKey 接收方公钥
     * @param senderPublicKey 发送方公钥（自己的公钥）
     * @param callback 回调
     */
    public static void show(
        @NonNull Context context,
        @NonNull Contact contact,
        @NonNull PublicKey receiverPublicKey,
        @NonNull PublicKey senderPublicKey,
        @NonNull Callback callback
    ) {
        SafetyNumberManager safetyNumberManager = SafetyNumberManager.getInstance(context);

        // 生成双方的安全码
        String yourShortCode = safetyNumberManager.generateShortFingerprint(senderPublicKey);
        String yourFullCode = safetyNumberManager.generateFullFingerprint(senderPublicKey);
        String theirShortCode = safetyNumberManager.generateShortFingerprint(receiverPublicKey);
        String theirFullCode = safetyNumberManager.generateFullFingerprint(receiverPublicKey);

        // 检查是否已验证
        boolean isVerified = safetyNumberManager.isVerified(contact.username, receiverPublicKey);
        boolean publicKeyChanged = safetyNumberManager.hasPublicKeyChanged(contact.username, receiverPublicKey);

        // 创建对话框视图
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_safety_number_verification, null);

        // 设置视图内容
        TextView textContactName = dialogView.findViewById(R.id.textContactName);
        TextView textYourShortCode = dialogView.findViewById(R.id.textYourShortCode);
        TextView textTheirShortCode = dialogView.findViewById(R.id.textTheirShortCode);
        TextView textYourFullCode = dialogView.findViewById(R.id.textYourFullCode);
        TextView textTheirFullCode = dialogView.findViewById(R.id.textTheirFullCode);
        ImageView imageVerifiedIcon = dialogView.findViewById(R.id.imageVerifiedIcon);
        TextView textWarningMessage = dialogView.findViewById(R.id.textWarningMessage);
        Button btnCopyYourCode = dialogView.findViewById(R.id.btnCopyYourCode);
        Button btnCopyTheirCode = dialogView.findViewById(R.id.btnCopyTheirCode);

        // 设置联系人名称
        if (textContactName != null) {
            String displayName = (contact.displayName != null && !contact.displayName.isEmpty())
                ? contact.displayName
                : contact.username;
            textContactName.setText(displayName);
        }

        // 设置安全码
        if (textYourShortCode != null) {
            textYourShortCode.setText(yourShortCode);
        }
        if (textTheirShortCode != null) {
            textTheirShortCode.setText(theirShortCode);
        }
        if (textYourFullCode != null) {
            textYourFullCode.setText(yourFullCode);
        }
        if (textTheirFullCode != null) {
            textTheirFullCode.setText(theirFullCode);
        }

        // 设置验证状态图标
        if (imageVerifiedIcon != null) {
            if (isVerified && !publicKeyChanged) {
                imageVerifiedIcon.setImageResource(R.drawable.ic_verified);
                imageVerifiedIcon.setVisibility(View.VISIBLE);
            } else if (publicKeyChanged) {
                imageVerifiedIcon.setImageResource(R.drawable.ic_warning);
                imageVerifiedIcon.setVisibility(View.VISIBLE);
            } else {
                imageVerifiedIcon.setVisibility(View.GONE);
            }
        }

        // 设置警告消息
        if (textWarningMessage != null) {
            if (publicKeyChanged) {
                // 公钥变化警告
                String oldFingerprint = safetyNumberManager.getStoredShortFingerprint(contact.username);
                String warning = "⚠️ 安全警告\n\n"
                    + "用户的安全码已变化！\n\n"
                    + "如果对方没有重新安装应用或更换设备，这可能是中间人攻击。\n\n"
                    + "旧安全码: " + (oldFingerprint != null ? oldFingerprint : "未知") + "\n"
                    + "新安全码: " + theirShortCode + "\n\n"
                    + "请通过其他渠道联系对方确认。";

                // 高亮警告文字
                SpannableString spannable = new SpannableString(warning);
                int warningStart = warning.indexOf("⚠️");
                int warningEnd = warningStart + "⚠️ 安全警告".length();
                spannable.setSpan(
                    new ForegroundColorSpan(Color.parseColor("#F44336")),
                    warningStart,
                    warningEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                textWarningMessage.setText(spannable);
                textWarningMessage.setVisibility(View.VISIBLE);
            } else if (isVerified) {
                textWarningMessage.setText("✓ 此用户已验证");
                textWarningMessage.setVisibility(View.VISIBLE);
            } else {
                textWarningMessage.setVisibility(View.GONE);
            }
        }

        // 复制按钮
        if (btnCopyYourCode != null) {
            btnCopyYourCode.setOnClickListener(v -> {
                copyToClipboard(context, "你的安全码", yourFullCode);
            });
        }

        if (btnCopyTheirCode != null) {
            btnCopyTheirCode.setOnClickListener(v -> {
                copyToClipboard(context, "对方安全码", theirFullCode);
            });
        }

        // 构建对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setView(dialogView);

        // 根据状态设置标题
        if (publicKeyChanged) {
            builder.setTitle("安全码已变化");
        } else if (isVerified) {
            builder.setTitle("安全码验证");
        } else {
            builder.setTitle("验证安全码");
        }

        // 设置按钮
        if (publicKeyChanged) {
            // 公钥变化：需要重新验证
            builder.setPositiveButton("重新验证", (dialog, which) -> {
                safetyNumberManager.markAsVerified(contact.username, receiverPublicKey);
                Toast.makeText(context, "已重新验证", Toast.LENGTH_SHORT).show();
                callback.onVerified();
            });
            builder.setNegativeButton("联系用户", (dialog, which) -> {
                callback.onNotMatch();
            });
            builder.setNeutralButton("取消", (dialog, which) -> {
                callback.onSkip();
            });
        } else if (isVerified) {
            // 已验证：可以选择查看详情或跳过
            builder.setPositiveButton("确认", (dialog, which) -> {
                callback.onVerified();
            });
            builder.setNegativeButton("重新验证", (dialog, which) -> {
                safetyNumberManager.markAsVerified(contact.username, receiverPublicKey);
                Toast.makeText(context, "已重新验证", Toast.LENGTH_SHORT).show();
                callback.onVerified();
            });
        } else {
            // 首次验证
            builder.setPositiveButton("验证通过", (dialog, which) -> {
                safetyNumberManager.markAsVerified(contact.username, receiverPublicKey);
                Toast.makeText(context, "已验证", Toast.LENGTH_SHORT).show();
                callback.onVerified();
            });
            builder.setNegativeButton("不匹配", (dialog, which) -> {
                callback.onNotMatch();
            });
            builder.setNeutralButton("跳过", (dialog, which) -> {
                Toast.makeText(context, "已跳过验证（建议稍后验证）", Toast.LENGTH_LONG).show();
                callback.onSkip();
            });
        }

        builder.setCancelable(false);
        builder.show();
    }

    /**
     * 复制文本到剪贴板
     */
    private static void copyToClipboard(Context context, String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(label, text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }
}
