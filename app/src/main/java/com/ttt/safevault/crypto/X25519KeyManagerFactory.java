package com.ttt.safevault.crypto;

import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * X25519 密钥管理器工厂
 *
 * 根据系统 Android 版本自动选择合适的实现：
 * - API 33+ (Android 13+): 优先使用系统 API (SystemX25519KeyManager)
 * - API 29-32 (Android 10-12): 使用 Bouncy Castle (BouncyCastleX25519KeyManager)
 * - 如果系统 API 不可用，自动回退到 Bouncy Castle
 *
 * 优先使用系统 API 以获得最佳性能和最小 APK 体积
 */
public class X25519KeyManagerFactory {
    private static final String TAG = "X25519KeyManagerFactory";

    private static volatile X25519KeyManager instance;
    private static volatile boolean useSystemApi = true; // 默认尝试使用系统 API
    private static volatile X25519KeyManager fallbackInstance; // 回退实例

    /**
     * 创建 X25519 密钥管理器实例
     *
     * @param context Android 上下文（用于检查系统版本）
     * @return X25519 密钥管理器实例
     */
    public static X25519KeyManager create(Context context) {
        if (instance != null) {
            return instance;
        }

        synchronized (X25519KeyManagerFactory.class) {
            if (instance != null) {
                return instance;
            }

            X25519KeyManager manager;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && useSystemApi) {
                // API 33+ (Android 13+): 优先尝试使用系统 API
                try {
                    manager = new SystemX25519KeyManager();
                    // 尝试生成密钥来验证系统 API 是否可用
                    manager.generateKeyPair();
                    Log.i(TAG, "Using SystemX25519KeyManager (API " + Build.VERSION.SDK_INT + ")");
                    instance = manager;
                    return instance;
                } catch (Exception e) {
                    Log.w(TAG, "SystemX25519KeyManager not available, falling back to BouncyCastle", e);
                    useSystemApi = false; // 标记系统 API 不可用，下次直接使用回退方案
                }
            }

            // 回退到 Bouncy Castle
            if (fallbackInstance == null) {
                fallbackInstance = new BouncyCastleX25519KeyManager();
                Log.i(TAG, "Using BouncyCastleX25519KeyManager (fallback, API " + Build.VERSION.SDK_INT + ")");
            }
            instance = fallbackInstance;
            return instance;
        }
    }

    /**
     * 创建新的 X25519 密钥管理器实例（不使用缓存）
     *
     * @param context Android 上下文
     * @return 新的 X25519 密钥管理器实例
     */
    public static X25519KeyManager createNew(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && useSystemApi) {
            try {
                SystemX25519KeyManager manager = new SystemX25519KeyManager();
                manager.generateKeyPair(); // 验证可用性
                Log.i(TAG, "Created new SystemX25519KeyManager");
                return manager;
            } catch (Exception e) {
                Log.w(TAG, "SystemX25519KeyManager creation failed, falling back", e);
            }
        }

        Log.i(TAG, "Created new BouncyCastleX25519KeyManager (fallback)");
        return new BouncyCastleX25519KeyManager();
    }

    /**
     * 重置实例（用于测试或特殊情况）
     */
    public static void reset() {
        synchronized (X25519KeyManagerFactory.class) {
            instance = null;
            useSystemApi = true; // 重置后重新尝试系统 API
            fallbackInstance = null;
            Log.d(TAG, "Instance reset");
        }
    }

    /**
     * 获取当前使用的实现类型
     *
     * @return 实现类型名称（"System" 或 "BouncyCastle"）
     */
    public static String getImplementationType() {
        if (instance == null) {
            return "Not initialized";
        }

        if (instance instanceof SystemX25519KeyManager) {
            return "System";
        } else {
            return "BouncyCastle";
        }
    }

    /**
     * 强制使用 Bouncy Castle 实现（用于系统 API 有问题的设备）
     */
    public static void forceBouncyCastle() {
        synchronized (X25519KeyManagerFactory.class) {
            useSystemApi = false;
            instance = null;
            fallbackInstance = new BouncyCastleX25519KeyManager();
            Log.w(TAG, "Forced to use BouncyCastle implementation");
        }
    }
}