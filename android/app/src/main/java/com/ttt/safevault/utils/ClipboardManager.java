package com.ttt.safevault.utils;

import android.content.ClipData;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 安全剪贴板管理器
 * 提供自动清理、敏感信息保护等功能
 */
public class ClipboardManager {

    private static final String TAG = "ClipboardManager";
    private static final int AUTO_CLEAR_DELAY = 30000; // 30秒后自动清理
    private static final int MAX_CLIP_LENGTH = 1000; // 最大剪贴板长度限制

    private final Context context;
    private final android.content.ClipboardManager systemClipboard;
    private final Handler mainHandler;
    private Runnable clearClipboardTask;
    private String lastCopiedData;
    private boolean isSensitiveData = false;

    public ClipboardManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.systemClipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 复制文本到剪贴板
     */
    public void copyText(@NonNull String text, @Nullable String label) {
        copyText(text, label, true);
    }

    /**
     * 复制敏感文本到剪贴板（密码等）
     */
    public void copySensitiveText(@NonNull String text, @Nullable String label) {
        copyText(text, label, true);
        isSensitiveData = true;
        showToast("已复制到剪贴板，将在30秒后自动清理");
    }

    /**
     * 复制文本到剪贴板
     */
    private void copyText(@NonNull String text, @Nullable String label, boolean enableAutoClear) {
        // 验证输入
        if (TextUtils.isEmpty(text)) {
            return;
        }

        // 限制剪贴板长度
        if (text.length() > MAX_CLIP_LENGTH) {
            text = text.substring(0, MAX_CLIP_LENGTH);
        }

        // 保存当前数据
        lastCopiedData = text;

        // 创建剪贴数据
        ClipData clipData = ClipData.newPlainText(label != null ? label : "SafeVault", text);
        systemClipboard.setPrimaryClip(clipData);

        // 设置自动清理任务
        if (enableAutoClear) {
            scheduleClearClipboard();
        }
    }

    /**
     * 获取剪贴板文本
     */
    @Nullable
    public String getText() {
        if (!hasText()) {
            return null;
        }

        ClipData.Item item = systemClipboard.getPrimaryClip().getItemAt(0);
        CharSequence text = item.getText();
        return text != null ? text.toString() : null;
    }

    /**
     * 检查剪贴板是否有文本
     */
    public boolean hasText() {
        return systemClipboard.hasPrimaryClip() &&
               systemClipboard.getPrimaryClip().getItemCount() > 0;
    }

    /**
     * 清理剪贴板
     */
    public void clearClipboard() {
        if (systemClipboard.hasPrimaryClip()) {
            // 使用空字符串覆盖，确保数据被清除
            ClipData emptyClip = ClipData.newPlainText("", "");
            systemClipboard.setPrimaryClip(emptyClip);

            // 如果可能，也可以尝试清除系统剪贴板
            try {
                systemClipboard.setPrimaryClip(ClipData.newPlainText("", null));
            } catch (Exception e) {
                // 清除剪贴板失败，记录日志但不影响功能
                android.util.Log.w(TAG, "Failed to clear system clipboard", e);
            }
        }

        // 清除清理任务
        cancelClearClipboardTask();

        // 重置状态
        lastCopiedData = null;
        isSensitiveData = false;
    }

    /**
     * 检查是否有敏感数据在剪贴板中
     */
    public boolean hasSensitiveData() {
        return isSensitiveData && hasText();
    }

    /**
     * 获取最后复制的数据
     */
    @Nullable
    public String getLastCopiedData() {
        return lastCopiedData;
    }

    /**
     * 立即清理剪贴板（用于敏感操作）
     */
    public void clearImmediately() {
        clearClipboard();
        showToast("剪贴板已清理");
    }

    /**
     * 获取剪贴板中的密码（如果存在）
     */
    @Nullable
    public String getPasswordFromClipboard() {
        String text = getText();
        if (text == null || text.length() < 8) {
            return null; // 密码通常至少8位
        }

        // 简单的启发式判断是否为密码
        if (containsPasswordCharacteristics(text)) {
            return text;
        }

        return null;
    }

    /**
     * 检查文本是否具有密码特征
     */
    private boolean containsPasswordCharacteristics(@NonNull String text) {
        // 检查长度
        if (text.length() < 8 || text.length() > 128) {
            return false;
        }

        // 检查是否包含数字
        boolean hasDigit = false;
        // 检查是否包含大写字母
        boolean hasUpper = false;
        // 检查是否包含小写字母
        boolean hasLower = false;
        // 检查是否包含特殊字符
        boolean hasSpecial = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (isSpecialCharacter(c)) {
                hasSpecial = true;
            }
        }

        // 强密码至少满足3个条件
        int score = 0;
        if (hasDigit) score++;
        if (hasUpper) score++;
        if (hasLower) score++;
        if (hasSpecial) score++;

        return score >= 2;
    }

    /**
     * 检查是否为特殊字符
     */
    private boolean isSpecialCharacter(char c) {
        return "!@#$%^&*()_+-=[]{}|;:,.<>?".indexOf(c) >= 0;
    }

    /**
     * 调度清理剪贴板任务
     */
    private void scheduleClearClipboard() {
        // 取消之前的任务
        cancelClearClipboardTask();

        // 创建新任务
        clearClipboardTask = this::clearClipboard;

        // 延迟执行
        mainHandler.postDelayed(clearClipboardTask, AUTO_CLEAR_DELAY);
    }

    /**
     * 取消清理剪贴板任务
     */
    private void cancelClearClipboardTask() {
        if (clearClipboardTask != null) {
            mainHandler.removeCallbacks(clearClipboardTask);
            clearClipboardTask = null;
        }
    }

    /**
     * 显示Toast消息
     */
    private void showToast(@NonNull String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 检查剪贴板内容是否过长
     */
    public boolean isClipboardTooLong() {
        String text = getText();
        return text != null && text.length() > MAX_CLIP_LENGTH;
    }

    /**
     * 清理过长的剪贴板内容
     */
    public void trimClipboardIfNeeded() {
        String text = getText();
        if (text != null && text.length() > MAX_CLIP_LENGTH) {
            String trimmedText = text.substring(0, MAX_CLIP_LENGTH);
            copyText(trimmedText, "Trimmed", false);
        }
    }

    /**
     * 安全地复制到剪贴板（带长度限制）
     */
    public void copySecurely(@NonNull String text, @Nullable String label) {
        if (text.length() > MAX_CLIP_LENGTH) {
            showToast("文本过长，已截断");
            text = text.substring(0, MAX_CLIP_LENGTH);
        }

        copyText(text, label);
    }
}