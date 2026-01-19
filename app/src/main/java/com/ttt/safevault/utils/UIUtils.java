package com.ttt.safevault.utils;

import android.content.Context;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import android.view.View;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * UI 工具类
 * 提供统一的 Toast、Snackbar 和错误对话框
 */
public class UIUtils {

    /**
     * 安全获取字符串资源
     * @param context 上下文
     * @param stringRes 字符串资源 ID
     * @return 字符串，如果获取失败返回 null
     */
    private static String getStringSafely(Context context, @StringRes int stringRes) {
        if (context == null) return null;
        try {
            return context.getString(stringRes);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    /**
     * 显示短时间 Toast
     * @param context 上下文
     * @param message 消息内容
     */
    public static void showToastShort(Context context, String message) {
        if (context != null && message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     *显示短时间 Toast（字符串资源）
     * @param context 上下文
     * @param messageRes 消息字符串资源 ID
     */
    public static void showToastShort(Context context, @StringRes int messageRes) {
        String message = getStringSafely(context, messageRes);
        if (message != null) {
            showToastShort(context, message);
        }
    }

    /**
     * 显示长时间 Toast
     * @param context 上下文
     * @param message 消息内容
     */
    public static void showToastLong(Context context, String message) {
        if (context != null && message != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 显示长时间 Toast（字符串资源）
     * @param context 上下文
     * @param messageRes 消息字符串资源 ID
     */
    public static void showToastLong(Context context, @StringRes int messageRes) {
        String message = getStringSafely(context, messageRes);
        if (message != null) {
            showToastLong(context, message);
        }
    }

    /**
     * 显示 Snackbar（短时间）
     * @param view 视图
     * @param message 消息内容
     * @return Snackbar 对象
     */
    public static Snackbar showSnackbarShort(View view, String message) {
        if (view == null || message == null) return null;

        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
        snackbar.show();
        return snackbar;
    }

    /**
     * 显示 Snackbar（短时间，字符串资源）
     * @param view 视图
     * @param messageRes 消息字符串资源 ID
     * @return Snackbar 对象
     */
    public static Snackbar showSnackbarShort(View view, @StringRes int messageRes) {
        if (view == null) return null;

        Context context = view.getContext();
        String message = getStringSafely(context, messageRes);
        return message != null ? showSnackbarShort(view, message) : null;
    }

    /**
     * 显示 Snackbar（长时间）
     * @param view 视图
     * @param message 消息内容
     * @return Snackbar 对象
     */
    public static Snackbar showSnackbarLong(View view, String message) {
        if (view == null || message == null) return null;

        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        snackbar.show();
        return snackbar;
    }

    /**
     * 显示 Snackbar（长时间，字符串资源）
     * @param view 视图
     * @param messageRes 消息字符串资源 ID
     * @return Snackbar 对象
     */
    public static Snackbar showSnackbarLong(View view, @StringRes int messageRes) {
        if (view == null) return null;

        Context context = view.getContext();
        String message = getStringSafely(context, messageRes);
        return message != null ? showSnackbarLong(view, message) : null;
    }

    /**
     * 显示带操作的 Snackbar
     * @param view 视图
     * @param message 消息内容
     * @param actionText 操作文本
     * @param listener 操作监听器
     * @return Snackbar 对象
     */
    public static Snackbar showSnackbarWithAction(View view, String message,
                                                  String actionText, View.OnClickListener listener) {
        if (view == null || message == null) return null;

        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        if (actionText != null && listener != null) {
            snackbar.setAction(actionText, listener);
        }
        snackbar.show();
        return snackbar;
    }

    /**
     * 显示错误对话框
     * @param context 上下文
     * @param title 标题
     * @param message 错误消息
     */
    public static void showErrorDialog(Context context, String title, String message) {
        if (context == null || title == null || message == null) return;

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * 显示错误对话框（字符串资源）
     * @param context 上下文
     * @param titleRes 标题字符串资源 ID
     * @param messageRes 错误消息字符串资源 ID
     */
    public static void showErrorDialog(Context context, @StringRes int titleRes,
                                      @StringRes int messageRes) {
        String title = getStringSafely(context, titleRes);
        String message = getStringSafely(context, messageRes);
        if (title != null && message != null) {
            showErrorDialog(context, title, message);
        }
    }

    /**
     * 显示确认对话框
     * @param context 上下文
     * @param title 标题
     * @param message 消息
     * @param onConfirm 确认回调
     */
    public static void showConfirmDialog(Context context, String title, String message,
                                        Runnable onConfirm) {
        if (context == null || title == null || message == null) return;

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                })
                .show();
    }

    /**
     * 显示确认对话框（字符串资源）
     * @param context 上下文
     * @param titleRes 标题字符串资源 ID
     * @param messageRes 消息字符串资源 ID
     * @param onConfirm 确认回调
     */
    public static void showConfirmDialog(Context context, @StringRes int titleRes,
                                        @StringRes int messageRes, Runnable onConfirm) {
        String title = getStringSafely(context, titleRes);
        String message = getStringSafely(context, messageRes);
        if (title != null && message != null) {
            showConfirmDialog(context, title, message, onConfirm);
        }
    }

    /**
     * 显示信息对话框
     * @param context 上下文
     * @param title 标题
     * @param message 消息
     * @param onClose 关闭回调
     */
    public static void showInfoDialog(Context context, String title, String message,
                                     Runnable onClose) {
        if (context == null || title == null || message == null) return;

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (onClose != null) {
                        onClose.run();
                    }
                })
                .show();
    }

    /**
     * 显示成功提示
     * @param context 上下文
     * @param message 成功消息
     */
    public static void showSuccess(Context context, String message) {
        showToastShort(context, message);
    }

    /**
     * 显示成功提示（字符串资源）
     * @param context 上下文
     * @param messageRes 成功消息字符串资源 ID
     */
    public static void showSuccess(Context context, @StringRes int messageRes) {
        showToastShort(context, messageRes);
    }

    /**
     * 显示错误提示
     * @param context 上下文
     * @param message 错误消息
     */
    public static void showError(Context context, String message) {
        showToastLong(context, message);
    }

    /**
     * 显示错误提示（字符串资源）
     * @param context 上下文
     * @param messageRes 错误消息字符串资源 ID
     */
    public static void showError(Context context, @StringRes int messageRes) {
        showToastLong(context, messageRes);
    }

    /**
     * 显示警告提示
     * @param context 上下文
     * @param message 警告消息
     */
    public static void showWarning(Context context, String message) {
        showToastShort(context, message);
    }

    /**
     * 显示警告提示（字符串资源）
     * @param context 上下文
     * @param messageRes 警告消息字符串资源 ID
     */
    public static void showWarning(Context context, @StringRes int messageRes) {
        showToastShort(context, messageRes);
    }

    /**
     * 显示信息提示
     * @param context 上下文
     * @param message 信息消息
     */
    public static void showInfo(Context context, String message) {
        showToastShort(context, message);
    }

    /**
     * 显示信息提示（字符串资源）
     * @param context 上下文
     * @param messageRes 信息消息字符串资源 ID
     */
    public static void showInfo(Context context, @StringRes int messageRes) {
        showToastShort(context, messageRes);
    }

    /**
     * 显示操作成功反馈（带动画）
     * @param view 视图
     * @param message 成功消息
     */
    public static void showOperationSuccess(View view, String message) {
        if (view == null || message == null) return;

        AnimationUtils.buttonPressFeedback(view);
        showSnackbarShort(view, message);
    }

    /**
     * 显示操作失败反馈
     * @param view 视图
     * @param message 失败消息
     */
    public static void showOperationFailure(View view, String message) {
        if (view == null || message == null) return;

        AnimationUtils.shake(view);
        showSnackbarLong(view, message);
    }

    /**
     * 显示加载中提示
     * @param context 上下文
     * @param message 加载消息
     */
    public static void showLoading(Context context, String message) {
        showToastShort(context, message);
    }

    /**
     * 显示加载中提示（字符串资源）
     * @param context 上下文
     * @param messageRes 加载消息字符串资源 ID
     */
    public static void showLoading(Context context, @StringRes int messageRes) {
        showToastShort(context, messageRes);
    }

    /**
     * 获取统一的错误消息
     * @param throwable 异常
     * @return 错误消息字符串
     */
    public static String getErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }

        String message = throwable.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }

        return throwable.getClass().getSimpleName();
    }

    /**
     * 显示异常错误对话框
     * @param context 上下文
     * @param title 标题
     * @param throwable 异常
     */
    public static void showErrorDialog(Context context, String title, Throwable throwable) {
        if (context == null || title == null) return;

        String message = getErrorMessage(throwable);
        showErrorDialog(context, title, message);
    }

    /**
     * 显示无网络连接提示
     * @param context 上下文
     */
    public static void showNoNetworkError(Context context) {
        showError(context, "网络连接不可用，请检查网络设置");
    }

    /**
     * 显示操作成功对话框
     * @param context 上下文
     * @param title 标题
     * @param message 成功消息
     * @param onSuccess 成功回调
     */
    public static void showSuccessDialog(Context context, String title, String message,
                                        Runnable onSuccess) {
        if (context == null || title == null || message == null) return;

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .show();
    }
}
