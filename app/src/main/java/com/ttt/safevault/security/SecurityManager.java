package com.ttt.safevault.security;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.utils.ClipboardManager;

import java.util.List;

/**
 * 安全管理器
 * 负责管理应用的安全措施，包括防截图、锁定等
 * 使用单例模式确保整个应用共享同一个实例
 *
 * 注意：会话锁定功能由 MainActivity 和 SafeVaultApplication 统一处理
 */
public class SecurityManager {

    private static final String TAG = "SecurityManager";
    private static final int CLIPBOARD_CLEAR_DELAY = 30000; // 30秒

    // 单例实例
    private static volatile SecurityManager instance;

    private final Context context;
    private final ClipboardManager clipboardManager;
    private boolean isLocked = false;

    /**
     * 获取单例实例
     */
    public static SecurityManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SecurityManager.class) {
                if (instance == null) {
                    instance = new SecurityManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 重置单例实例（用于测试或重新初始化）
     */
    public static void resetInstance() {
        synchronized (SecurityManager.class) {
            instance = null;
        }
    }

    SecurityManager(@NonNull Context context) {
        this.context = context;
        this.clipboardManager = new ClipboardManager(this.context);
    }

    /**
     * 应用安全措施到Activity
     */
    public void applySecurityMeasures(@NonNull Activity activity) {
        // 防止截图
        preventScreenshots(activity);

        // 检查开发者选项
        if (isDeveloperOptionsEnabled()) {
            activity.finish();
            return;
        }

        // 检查设备是否rooted
        if (isDeviceRooted()) {
            // 可以选择警告用户或禁用某些功能
            showRootWarning(activity);
        }
    }

    /**
     * 应用安全措施到Fragment
     */
    public void applySecurityMeasures(@NonNull Fragment fragment) {
        Activity activity = fragment.getActivity();
        if (activity != null) {
            applySecurityMeasures(activity);
        }
    }

    /**
     * 应用安全措施到View
     */
    public void applySecurityMeasures(@NonNull View view) {
        // 防止View所在Activity被截图
        Context context = view.getContext();
        if (context instanceof Activity) {
            preventScreenshots((Activity) context);
        }
    }

    /**
     * 防止截图和录屏
     * 根据 SecurityConfig 中的设置决定是否启用截图保护
     */
    public void preventScreenshots(@NonNull Activity activity) {
        SecurityConfig config = new SecurityConfig(context);
        boolean screenshotProtectionEnabled = config.isScreenshotProtectionEnabled();

        // 如果启用了截图保护（不允许截图），则添加 FLAG_SECURE 标志
        if (screenshotProtectionEnabled) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            // 如果允许截图，则清除 FLAG_SECURE 标志
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        // 始终保持屏幕常亮
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 如果是敏感页面，可以添加更多保护
        if (isSensitiveActivity(activity)) {
            activity.getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            );
        }
    }


    /**
     * 锁定应用
     */
    public void lock() {
        isLocked = true;

        // 清理剪贴板
        clipboardManager.clearClipboard();

        // 清除BackendService中的会话密钥，确保自动填充服务也能正确识别锁定状态
        try {
            BackendService backendService = ServiceLocator.getInstance().getBackendService();
            if (backendService != null) {
                backendService.lock();
                android.util.Log.d(TAG, "SecurityManager: 已调用BackendService.lock()清除会话密钥");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "SecurityManager: 调用BackendService.lock()失败", e);
        }
    }

    /**
     * 解锁应用
     */
    public void unlock() {
        isLocked = false;
    }

    /**
     * 检查开发者选项是否开启
     */
    public boolean isDeveloperOptionsEnabled() {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    /**
     * 检查设备是否已root
     */
    public boolean isDeviceRooted() {
        // 检查常见的root文件
        String[] rootFiles = {
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
        };

        for (String file : rootFiles) {
            if (new java.io.File(file).exists()) {
                return true;
            }
        }

        // 检查是否可以执行su命令
        try {
            Process process = Runtime.getRuntime().exec("su");
            process.destroy();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查是否为敏感Activity
     */
    private boolean isSensitiveActivity(@NonNull Activity activity) {
        String className = activity.getClass().getSimpleName();
        return className.contains("Password") ||
               className.contains("Login") ||
               className.contains("Detail");
    }

    /**
     * 显示root警告
     */
    private void showRootWarning(@NonNull Activity activity) {
        // 创建警告对话框
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(activity);
        builder.setTitle("安全警告");
        builder.setMessage("检测到设备可能已root，这可能会影响应用的安全性。建议在非root设备上使用。");
        builder.setPositiveButton("继续", null);
        builder.setCancelable(false);
        builder.show();
    }

    /**
     * 检查应用是否处于前台
     */
    public boolean isAppInForeground() {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return false;

        List<ActivityManager.RunningAppProcessInfo> processes =
                activityManager.getRunningAppProcesses();
        if (processes == null) return false;

        String packageName = context.getPackageName();
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                process.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否有调试器附加
     */
    public boolean isDebuggerAttached() {
        return android.os.Debug.isDebuggerConnected() ||
               android.os.Debug.waitingForDebugger();
    }

    /**
     * 检查应用是否处于调试模式
     */
    public boolean isDebugMode() {
        return (context.getApplicationInfo().flags &
                ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    /**
     * 获取剪贴板管理器
     */
    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }

    /**
     * 检查生物识别是否已启用且可用
     * @return true表示生物识别已启用且设备支持
     */
    public boolean isBiometricAuthenticationAvailable() {
        // 检查生物识别是否在设置中启用
        SecurityConfig config = new SecurityConfig(context);
        if (!config.isBiometricEnabled()) {
            return false;
        }

        // 检查设备是否支持生物识别
        return com.ttt.safevault.security.BiometricAuthHelper.isBiometricSupported(context);
    }

    /**
     * 检查生物识别是否已启用但设备不支持
     * @return true表示已启用但不可用
     */
    public boolean isBiometricEnabledButUnavailable() {
        SecurityConfig config = new SecurityConfig(context);
        return config.isBiometricEnabled() &&
               !com.ttt.safevault.security.BiometricAuthHelper.isBiometricSupported(context);
    }
}