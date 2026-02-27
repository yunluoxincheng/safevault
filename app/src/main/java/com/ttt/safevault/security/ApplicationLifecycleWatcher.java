package com.ttt.safevault.security;

import android.content.ComponentCallbacks2;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * 应用生命周期监听器（内存安全强化）
 *
 * 监听 Android 应用生命周期事件，在应用进入后台或系统内存压力时主动清除敏感数据：
 * - onTrimMemory(): 系统内存压力回调，清除敏感数据
 * - onBackground(): 应用进入后台回调，清除敏感数据
 *
 * 设计原则：
 * - 最小化敏感数据在内存中的驻留时间
 * - 主动响应系统内存压力事件
 * - 应用后台时立即清除敏感数据
 *
 * 使用方式：
 * <pre>
 * // 在 Application.onCreate() 中注册
 * ApplicationLifecycleWatcher.register(application);
 * </pre>
 *
 * @since SafeVault 3.5.0 (内存安全强化)
 */
public class ApplicationLifecycleWatcher implements ComponentCallbacks2 {
    private static final String TAG = "ApplicationLifecycleWatcher";

    /** 单例实例 */
    private static volatile ApplicationLifecycleWatcher INSTANCE;

    /** 是否已注册 */
    private volatile boolean registered;

    /** CryptoSession 引用 */
    private final CryptoSession cryptoSession;

    /**
     * 私有构造函数
     */
    private ApplicationLifecycleWatcher() {
        this.cryptoSession = CryptoSession.getInstance();
        this.registered = false;
        Log.i(TAG, "ApplicationLifecycleWatcher 初始化");
    }

    /**
     * 获取单例实例
     */
    @NonNull
    public static ApplicationLifecycleWatcher getInstance() {
        if (INSTANCE == null) {
            synchronized (ApplicationLifecycleWatcher.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ApplicationLifecycleWatcher();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 注册到 Application（实现 ComponentCallbacks2）
     *
     * @param application Application 实例
     */
    public static void register(@NonNull android.app.Application application) {
        ApplicationLifecycleWatcher watcher = getInstance();
        if (watcher.registered) {
            Log.w(TAG, "ApplicationLifecycleWatcher 已注册，跳过重复注册");
            return;
        }

        application.registerComponentCallbacks(watcher);
        watcher.registered = true;
        Log.i(TAG, "ApplicationLifecycleWatcher 已注册到 Application");
    }

    /**
     * 注销监听器
     *
     * @param application Application 实例
     */
    public static void unregister(@NonNull android.app.Application application) {
        ApplicationLifecycleWatcher watcher = getInstance();
        if (!watcher.registered) {
            Log.w(TAG, "ApplicationLifecycleWatcher 未注册，跳过注销");
            return;
        }

        application.unregisterComponentCallbacks(watcher);
        watcher.registered = false;
        Log.i(TAG, "ApplicationLifecycleWatcher 已从 Application 注销");
    }

    /**
     * 检查是否已注册
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * 系统内存压力回调（ComponentCallbacks2）
     *
     * 当系统内存紧张时，Android 会调用此方法通知应用释放资源。
     * 我们利用这个机会主动清除敏感数据。
     *
     * TRIM_MEMORY_UI_HIDDEN 及以上级别表示应用 UI 已不可见（进入后台）。
     *
     * @param level 内存压力级别
     */
    @Override
    public void onTrimMemory(int level) {
        String levelName = getTrimLevelName(level);
        Log.d(TAG, "onTrimMemory() 被调用 - level=" + level + " (" + levelName + ")");

        // TRIM_MEMORY_UI_HIDDEN 表示应用 UI 已不可见（进入后台）
        // 这是我们清除敏感数据的最佳时机
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            Log.i(TAG, "应用进入后台（UI 隐藏），清除敏感数据");
            clearSensitiveData("onTrimMemory(" + levelName + ")");
        }

        // 更高级别的内存压力也触发清零
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            Log.i(TAG, "系统内存紧张，清除敏感数据");
            clearSensitiveData("onTrimMemory(" + levelName + ")");
        }

        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            Log.w(TAG, "系统内存严重不足，强制清除敏感数据");
            clearSensitiveData("onTrimMemory(COMPLETE)");
        }
    }

    /**
     * 配置改变回调（ComponentCallbacks2）
     *
     * 当系统配置发生改变时调用（如屏幕方向、语言等）。
     * 此方法不需要做任何操作，因为敏感数据清除由 onTrimMemory 处理。
     *
     * @param newConfig 新的配置
     */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        // 不需要做任何操作
        Log.d(TAG, "onConfigurationChanged() 被调用");
    }

    /**
     * 低内存回调（ComponentCallbacks）
     *
     * 当系统整体内存不足时调用。
     * 我们利用这个机会主动清除敏感数据。
     */
    @Override
    public void onLowMemory() {
        Log.w(TAG, "onLowMemory() 被调用 - 系统内存不足，清除敏感数据");
        clearSensitiveData("onLowMemory()");
    }

    /**
     * 应用进入后台回调（手动触发）
     *
     * 此方法由 SafeVaultApplication 在检测到应用进入后台时调用。
     * 与 onTrimMemory() 配合使用，提供双重保障。
     */
    public void onBackground() {
        Log.i(TAG, "onBackground() 被调用 - 应用进入后台");
        clearSensitiveData("onBackground()");
    }

    /**
     * 应用进入前台回调（手动触发）
     *
     * 此方法由 SafeVaultApplication 在检测到应用回到前台时调用。
     * 用于日志记录和调试，不执行敏感数据清除。
     */
    public void onForeground() {
        Log.i(TAG, "onForeground() 被调用 - 应用回到前台");
        // 不需要做任何操作，用户需要重新验证才能访问敏感数据
    }

    /**
     * 清除敏感数据
     *
     * 调用 CryptoSession.clear() 清除内存中的 DataKey。
     *
     * @param reason 清除原因（用于日志）
     */
    private void clearSensitiveData(@NonNull String reason) {
        try {
            if (cryptoSession.isUnlocked()) {
                Log.i(TAG, "清除敏感数据 - 原因: " + reason);
                cryptoSession.clear();
                Log.i(TAG, "敏感数据已清除（CryptoSession 已锁定）");
            } else {
                Log.d(TAG, "CryptoSession 已锁定，无需清除");
            }
        } catch (Exception e) {
            Log.e(TAG, "清除敏感数据时出现异常", e);
        }
    }

    /**
     * 获取内存压力级别名称（用于调试）
     *
     * @param level 内存压力级别
     * @return 级别名称
     */
    @NonNull
    private String getTrimLevelName(int level) {
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
                return "UI_HIDDEN";
            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
                return "BACKGROUND";
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
                return "MODERATE";
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                return "COMPLETE";
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
                return "RUNNING_CRITICAL";
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
                return "RUNNING_LOW";
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
                return "RUNNING_MODERATE";
            default:
                return "UNKNOWN(" + level + ")";
        }
    }

    /**
     * 获取调试信息
     *
     * @return 调试信息字符串
     */
    @NonNull
    @Override
    public String toString() {
        return "ApplicationLifecycleWatcher{" +
                "registered=" + registered +
                ", cryptoSession.unlocked=" + cryptoSession.isUnlocked() +
                '}';
    }
}
