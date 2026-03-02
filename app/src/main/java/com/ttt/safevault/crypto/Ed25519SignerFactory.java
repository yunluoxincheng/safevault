package com.ttt.safevault.crypto;

import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * Ed25519 签名器工厂
 *
 * 根据系统 Android 版本自动选择合适的实现：
 * - API 34+ (Android 14+): 优先使用系统 API (SystemEd25519Signer)
 * - API 29-33 (Android 10-13): 使用 Bouncy Castle (BouncyCastleEd25519Signer)
 * - 如果系统 API 不可用，自动回退到 Bouncy Castle
 *
 * 优先使用系统 API 以获得最佳性能和最小 APK 体积
 */
public class Ed25519SignerFactory {
    private static final String TAG = "Ed25519SignerFactory";

    private static volatile Ed25519Signer instance;
    private static volatile boolean useSystemApi = true; // 默认尝试使用系统 API
    private static volatile Ed25519Signer fallbackInstance; // 回退实例

    /**
     * 创建 Ed25519 签名器实例
     *
     * @param context Android 上下文（用于检查系统版本）
     * @return Ed25519 签名器实例
     */
    public static Ed25519Signer create(Context context) {
        if (instance != null) {
            return instance;
        }

        synchronized (Ed25519SignerFactory.class) {
            if (instance != null) {
                return instance;
            }

            Ed25519Signer signer;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && useSystemApi) {
                // API 34+ (Android 14+): 优先尝试使用系统 API
                try {
                    signer = new SystemEd25519Signer();
                    // 尝试生成密钥来验证系统 API 是否可用
                    signer.generateKeyPair();
                    Log.i(TAG, "Using SystemEd25519Signer (API " + Build.VERSION.SDK_INT + ")");
                    instance = signer;
                    return instance;
                } catch (Exception e) {
                    Log.w(TAG, "SystemEd25519Signer not available, falling back to BouncyCastle", e);
                    useSystemApi = false; // 标记系统 API 不可用，下次直接使用回退方案
                }
            }

            // 回退到 Bouncy Castle
            if (fallbackInstance == null) {
                fallbackInstance = new BouncyCastleEd25519Signer();
                Log.i(TAG, "Using BouncyCastleEd25519Signer (fallback, API " + Build.VERSION.SDK_INT + ")");
            }
            instance = fallbackInstance;
            return instance;
        }
    }

    /**
     * 创建新的 Ed25519 签名器实例（不使用缓存）
     *
     * @param context Android 上下文
     * @return 新的 Ed25519 签名器实例
     */
    public static Ed25519Signer createNew(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && useSystemApi) {
            try {
                SystemEd25519Signer signer = new SystemEd25519Signer();
                signer.generateKeyPair(); // 验证可用性
                Log.i(TAG, "Created new SystemEd25519Signer");
                return signer;
            } catch (Exception e) {
                Log.w(TAG, "SystemEd25519Signer creation failed, falling back", e);
            }
        }

        Log.i(TAG, "Created new BouncyCastleEd25519Signer (fallback)");
        return new BouncyCastleEd25519Signer();
    }

    /**
     * 重置实例（用于测试或特殊情况）
     */
    public static void reset() {
        synchronized (Ed25519SignerFactory.class) {
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

        if (instance instanceof SystemEd25519Signer) {
            return "System";
        } else {
            return "BouncyCastle";
        }
    }

    /**
     * 强制使用 Bouncy Castle 实现（用于系统 API 有问题的设备）
     */
    public static void forceBouncyCastle() {
        synchronized (Ed25519SignerFactory.class) {
            useSystemApi = false;
            instance = null;
            fallbackInstance = new BouncyCastleEd25519Signer();
            Log.w(TAG, "Forced to use BouncyCastle implementation");
        }
    }
}