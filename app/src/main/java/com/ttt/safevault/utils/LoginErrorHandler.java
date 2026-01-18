package com.ttt.safevault.utils;

import android.app.Activity;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.ttt.safevault.R;
import com.ttt.safevault.service.manager.EncryptionSyncManager;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import retrofit2.HttpException;

/**
 * 登录错误处理工具类
 * 根据错误类型和严重程度选择合适的提示方式
 */
public class LoginErrorHandler {
    private static final String TAG = "LoginErrorHandler";

    /**
     * 显示错误信息（自动选择合适的展示方式）
     *
     * @param activity 当前Activity
     * @param error    错误对象
     */
    public static void showError(Activity activity, Throwable error) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        ErrorType errorType = classifyError(error);
        String message = getErrorMessage(error, errorType);

        switch (errorType.getSeverity()) {
            case HIGH:
                // 高严重度错误使用 Dialog
                showErrorDialog(activity, message);
                break;
            case MEDIUM:
                // 中等严重度错误使用 Snackbar
                showErrorSnackbar(activity, message, null);
                break;
            case LOW:
            default:
                // 低严重度错误使用 Toast
                showErrorToast(activity, message);
                break;
        }
    }

    /**
     * 显示带重试按钮的错误信息
     *
     * @param activity 当前Activity
     * @param message  错误信息
     * @param onRetry  重试回调
     */
    public static void showErrorWithRetry(Activity activity, String message, Runnable onRetry) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        showErrorSnackbar(activity, message, onRetry);
    }

    /**
     * 显示数据同步冲突对话框
     *
     * @param activity      当前Activity
     * @param cloudVersion  云端版本
     * @param localVersion  本地版本
     * @param onChoice      用户选择回调
     */
    public static void showConflictDialog(Activity activity,
                                          long cloudVersion,
                                          long localVersion,
                                          Consumer<EncryptionSyncManager.SyncStrategy> onChoice) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        String message = activity.getString(R.string.sync_conflict_message, cloudVersion, localVersion);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.sync_conflict_title)
                .setMessage(message)
                .setPositiveButton(R.string.sync_use_cloud, (dialog, which) -> {
                    if (onChoice != null) {
                        onChoice.accept(EncryptionSyncManager.SyncStrategy.USE_CLOUD);
                    }
                })
                .setNeutralButton(R.string.sync_use_local, (dialog, which) -> {
                    if (onChoice != null) {
                        onChoice.accept(EncryptionSyncManager.SyncStrategy.USE_LOCAL);
                    }
                })
                .setNegativeButton(R.string.sync_cancel, (dialog, which) -> {
                    if (onChoice != null) {
                        onChoice.accept(EncryptionSyncManager.SyncStrategy.CANCEL);
                    }
                })
                .setCancelable(false)
                .show();
    }

    /**
     * 显示简单的错误对话框
     */
    private static void showErrorDialog(Activity activity, String message) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.error_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    /**
     * 显示错误 Snackbar（可选重试按钮）
     */
    private static void showErrorSnackbar(Activity activity, String message, Runnable onRetry) {
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) {
            showErrorToast(activity, message);
            return;
        }

        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);

        if (onRetry != null) {
            snackbar.setAction(R.string.retry, v -> onRetry.run());
            snackbar.setDuration(Snackbar.LENGTH_INDEFINITE);
        }

        snackbar.show();
    }

    /**
     * 显示错误 Toast
     */
    private static void showErrorToast(Activity activity, String message) {
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
    }

    /**
     * 分类错误类型
     */
    private static ErrorType classifyError(Throwable error) {
        if (error == null) {
            return ErrorType.UNKNOWN;
        }

        // 网络相关错误
        if (error instanceof IOException) {
            if (error instanceof SocketTimeoutException) {
                return ErrorType.TIMEOUT;
            }
            return ErrorType.NETWORK;
        }

        // HTTP 错误
        if (error instanceof HttpException) {
            HttpException httpException = (HttpException) error;
            int code = httpException.code();

            if (code == 401) {
                return ErrorType.UNAUTHORIZED;
            } else if (code == 404) {
                return ErrorType.NOT_FOUND;
            } else if (code >= 500) {
                return ErrorType.SERVER_ERROR;
            } else if (code >= 400) {
                return ErrorType.CLIENT_ERROR;
            }
        }

        // 检查错误消息中的关键词
        String message = error.getMessage();
        if (message != null) {
            if (message.contains("密码") || message.contains("password")) {
                return ErrorType.PASSWORD_ERROR;
            }
            if (message.contains("解密") || message.contains("decrypt")) {
                return ErrorType.DECRYPT_ERROR;
            }
            if (message.contains("私钥") || message.contains("private key")) {
                return ErrorType.KEY_ERROR;
            }
        }

        return ErrorType.UNKNOWN;
    }

    /**
     * 获取用户友好的错误信息
     */
    private static String getErrorMessage(Throwable error, ErrorType errorType) {
        switch (errorType) {
            case TIMEOUT:
                return "网络连接超时，请稍后重试";
            case NETWORK:
                return "网络连接失败，请检查网络设置";
            case SERVER_ERROR:
                return "服务器暂时不可用，请稍后重试";
            case UNAUTHORIZED:
                return "认证失败，请重新登录";
            case NOT_FOUND:
                return "无法获取云端数据，请联系客服";
            case PASSWORD_ERROR:
                return "主密码错误，请重新输入";
            case DECRYPT_ERROR:
                return "数据解密失败，可能是密码错误或数据损坏";
            case KEY_ERROR:
                return "私钥处理失败，请联系客服";
            case CLIENT_ERROR:
                return "请求错误，请稍后重试";
            default:
                String message = error.getMessage();
                if (message != null && message.length() < 100) {
                    return "操作失败：" + message;
                }
                return "操作失败，请稍后重试";
        }
    }

    /**
     * 错误类型枚举
     */
    private enum ErrorType {
        // 网络错误（中等严重度）
        TIMEOUT(Severity.MEDIUM),
        NETWORK(Severity.MEDIUM),

        // HTTP错误
        UNAUTHORIZED(Severity.HIGH),
        NOT_FOUND(Severity.HIGH),
        SERVER_ERROR(Severity.MEDIUM),
        CLIENT_ERROR(Severity.MEDIUM),

        // 业务错误
        PASSWORD_ERROR(Severity.HIGH),
        DECRYPT_ERROR(Severity.HIGH),
        KEY_ERROR(Severity.HIGH),

        // 未知错误
        UNKNOWN(Severity.MEDIUM);

        private final Severity severity;

        ErrorType(Severity severity) {
            this.severity = severity;
        }

        public Severity getSeverity() {
            return severity;
        }
    }

    /**
     * 错误严重程度
     */
    private enum Severity {
        HIGH,    // 需要用户确认
        MEDIUM,  // 可能有解决方案
        LOW      // 简单提示
    }
}
