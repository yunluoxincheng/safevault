package com.ttt.safevault.crypto;

/**
 * 加密安全常量配置
 *
 * 包含所有加密算法和协议使用的常量配置
 */
public class CryptoConstants {
    private CryptoConstants() {
        // 私有构造函数，防止实例化
    }

    // ==================== 时间戳相关 ====================

    /**
     * 最大允许的时间戳偏差（毫秒）
     * 用于防重放攻击，设置为 10 分钟
     */
    public static final long MAX_TIMESTAMP_DRIFT = 10 * 60 * 1000;

    // ==================== AES 配置 ====================

    /**
     * AES 加密算法配置（使用 GCM 模式提供认证加密）
     */
    public static final String AES_ALGORITHM = "AES/GCM/NoPadding";

    /**
     * AES 密钥大小（位）- AES-256
     */
    public static final int AES_KEY_SIZE = 256;

    /**
     * GCM 初始化向量大小（字节）- 12 字节是 GCM 标准推荐值
     */
    public static final int GCM_IV_SIZE = 12;

    /**
     * GCM 认证标签大小（位）- 128 位提供最强的安全性
     */
    public static final int GCM_TAG_SIZE = 128;

    // ==================== HKDF 配置 ====================

    /**
     * HKDF 哈希算法 - HMAC-SHA256
     */
    public static final String HKDF_HASH_ALGORITHM = "HmacSHA256";

    /**
     * HKDF 输出密钥大小（字节）- 32 字节用于 AES-256
     */
    public static final int HKDF_OUTPUT_SIZE = 32;

    /**
     * HKDF info 参数前缀
     * 用于密钥派生时的身份绑定，防止密钥混淆攻击
     */
    public static final String HKDF_INFO_PREFIX = "safevault-sharing\0";

    // ==================== X25519 配置 ====================

    /**
     * X25519 公钥/私钥大小（字节）
     */
    public static final int X25519_KEY_SIZE = 32;

    /**
     * X25519 ECDH 算法名称（系统 API 使用）
     */
    public static final String X25519_ALGORITHM = "XDH";

    /**
     * X25519 曲线名称（系统 API 使用）
     */
    public static final String X25519_CURVE = "X25519";

    // ==================== Ed25519 配置 ====================

    /**
     * Ed25519 签名大小（字节）
     */
    public static final int ED25519_SIGNATURE_SIZE = 64;

    /**
     * Ed25519 公钥大小（字节）
     */
    public static final int ED25519_PUBLIC_KEY_SIZE = 32;

    /**
     * Ed25519 私钥大小（字节）
     */
    public static final int ED25519_PRIVATE_KEY_SIZE = 32;

    /**
     * Ed25519 签名算法名称（系统 API 使用）
     */
    public static final String ED25519_ALGORITHM = "EdDSA";

    /**
     * Ed25519 曲线名称（系统 API 使用）
     */
    public static final String ED25519_CURVE = "Ed25519";

    // ==================== RSA 配置（版本 2.0） ====================

    /**
     * RSA 密钥大小（位）- RSA-2048
     */
    public static final int RSA_KEY_SIZE = 2048;

    /**
     * RSA OAEP 变换配置
     */
    public static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /**
     * RSA 签名算法
     */
    public static final String RSA_SIGNATURE_ALGORITHM = "SHA256withRSA";

    // ==================== 协议版本 ====================

    /**
     * 当前活跃的协议版本
     */
    public static final String CURRENT_PROTOCOL_VERSION = "3.0";

    /**
     * 旧协议版本（RSA）- 保持兼容
     */
    public static final String PROTOCOL_VERSION_V2 = "2.0";

    /**
     * 新协议版本（X25519/Ed25519）
     */
    public static final String PROTOCOL_VERSION_V3 = "3.0";

    // ==================== 密钥版本标识 ====================

    /**
     * 旧密钥版本（RSA）
     */
    public static final String KEY_VERSION_V2 = "v2";

    /**
     * 新密钥版本（X25519/Ed25519）
     */
    public static final String KEY_VERSION_V3 = "v3";

    // ==================== SharedPreferences 键名 ====================

    // RSA 密钥（v2.0）
    public static final String KEY_RSA_PUBLIC = "rsa_public_key";
    public static final String KEY_RSA_PRIVATE_ENCRYPTED = "rsa_private_key_encrypted";

    // X25519 密钥（v3.0）
    public static final String KEY_X25519_PUBLIC = "x25519_public_key";
    public static final String KEY_X25519_PRIVATE_ENCRYPTED = "x25519_private_key_encrypted";

    // Ed25519 密钥（v3.0）
    public static final String KEY_ED25519_PUBLIC = "ed25519_public_key";
    public static final String KEY_ED25519_PRIVATE_ENCRYPTED = "ed25519_private_key_encrypted";

    // 密钥版本标识
    public static final String KEY_VERSION = "key_version";
    public static final String KEY_HAS_MIGRATED = "has_migrated_to_v3";

    // ==================== 安全验证常量 ====================

    /**
     * ECDH 共享密钥大小（字节）- X25519 输出 32 字节
     */
    public static final int ECDH_SHARED_SECRET_SIZE = 32;

    /**
     * 最小允许的分享过期时间（毫秒）- 5 分钟
     */
    public static final long MIN_SHARE_EXPIRY = 5 * 60 * 1000;

    /**
     * 最大允许的分享过期时间（毫秒）- 30 天
     */
    public static final long MAX_SHARE_EXPIRY = 30L * 24 * 60 * 60 * 1000;
}