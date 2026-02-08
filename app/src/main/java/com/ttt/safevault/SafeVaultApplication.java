package com.ttt.safevault;

import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.SecurityConfig;

/**
 * SafeVault应用程序类
 * 负责初始化全局服务和跟踪应用前后台状态
 */
public class SafeVaultApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "SafeVaultApplication";
    private int activityCount = 0;
    private boolean isAppInForeground = true;
    private BackendService backendService;

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化服务定位器
        ServiceLocator.init(this);

        // 获取 BackendService 实例
        backendService = ServiceLocator.getInstance().getBackendService();

        // 注册 Activity 生命周期回调
        registerActivityLifecycleCallbacks(this);

        // 加载并应用主题设置
        applyThemeSettings();

        // 清理旧安全架构的遗留数据
        cleanupLegacyData();
    }

    /**
     * 清理旧安全架构的遗留数据
     * - 清空密码数据库表（旧架构加密的数据无法解密）
     * - 清理 SharedPreferences 中的旧数据
     */
    private void cleanupLegacyData() {
        try {
            // 1. 清理 crypto_prefs 中的会话数据
            android.content.SharedPreferences cryptoPrefs =
                    getSharedPreferences("crypto_prefs", MODE_PRIVATE);
            cryptoPrefs.edit()
                    .remove("session_master_key")
                    .remove("session_master_iv")
                    .remove("unlock_time")
                    .remove("is_locked")
                    .apply();
            Log.i(TAG, "已清理 crypto_prefs 中的旧会话数据");

            // 2. 删除 key_prefs 整个文件
            android.content.SharedPreferences keyPrefs =
                    getSharedPreferences("key_prefs", MODE_PRIVATE);
            keyPrefs.edit().clear().commit();
            Log.i(TAG, "已删除 key_prefs 整个文件");

            // 3. 清理 autofill_prefs 中的 master_password
            android.content.SharedPreferences autofillPrefs =
                    getSharedPreferences("autofill_prefs", MODE_PRIVATE);
            autofillPrefs.edit()
                    .remove("master_password")
                    .apply();
            Log.i(TAG, "已清理 autofill_prefs 中的明文主密码");

            Log.i(TAG, "旧安全架构遗留数据清理完成");

        } catch (Exception e) {
            Log.e(TAG, "清理旧安全架构遗留数据失败", e);
        }
    }

    @Override
    public void onActivityStarted(android.app.Activity activity) {
        activityCount++;
        Log.d(TAG, "onActivityStarted: activity=" + activity.getClass().getSimpleName() + ", count=" + activityCount);
        if (activityCount == 1) {
            // 应用从后台回到前台
            Log.d(TAG, "=== 应用回到前台 ===");
            isAppInForeground = true;
        }
    }

    @Override
    public void onActivityStopped(android.app.Activity activity) {
        Log.d(TAG, "onActivityStopped: activity=" + activity.getClass().getSimpleName() + ", count before=" + activityCount);
        activityCount--;
        Log.d(TAG, "onActivityStopped: count after=" + activityCount);
        if (activityCount == 0) {
            // 应用真正进入后台（所有Activity都停止了）
            Log.d(TAG, "=== 应用进入后台，记录时间 ===");
            isAppInForeground = false;
            if (backendService != null) {
                backendService.recordBackgroundTime();
                Log.d(TAG, "后台时间已记录: " + backendService.getBackgroundTime());
            } else {
                Log.e(TAG, "backendService 为 null，无法记录后台时间");
            }
        }
    }

    @Override
    public void onActivityCreated(android.app.Activity activity, Bundle savedInstanceState) {}

    @Override
    public void onActivityResumed(android.app.Activity activity) {}

    @Override
    public void onActivityPaused(android.app.Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(android.app.Activity activity, Bundle outState) {}

    @Override
    public void onActivityDestroyed(android.app.Activity activity) {}

    /**
     * 检查应用是否在前台
     */
    public boolean isAppInForeground() {
        return isAppInForeground;
    }

    /**
     * 应用主题设置
     * 从SharedPreferences加载保存的主题模式并应用
     */
    private void applyThemeSettings() {
        SecurityConfig securityConfig = new SecurityConfig(this);
        SecurityConfig.ThemeMode themeMode = securityConfig.getThemeMode();

        int nightMode;
        switch (themeMode) {
            case LIGHT:
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case DARK:
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case SYSTEM:
            default:
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }

        AppCompatDelegate.setDefaultNightMode(nightMode);
    }
}
