package com.ttt.safevault.crypto;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * X25519 密钥管理器接口
 *
 * 处理 X25519 (Curve25519 ECDH) 密钥的生成、密钥交换和验证
 *
 * X25519 特性：
 * - 密钥大小：公私钥各 32 字节
 * - 安全性：256 位安全级别
 * - 性能：比 RSA 快 10-100 倍
 * - 常数时间实现：抗侧信道攻击
 */
public interface X25519KeyManager {

    /**
     * 生成 X25519 密钥对
     *
     * @return 新生成的 X25519 密钥对（公私钥各 32 字节）
     * @throws Exception 如果密钥生成失败
     */
    KeyPair generateKeyPair() throws Exception;

    /**
     * 执行 ECDH 密钥交换
     *
     * 使用发送方私钥和接收方公钥生成共享密钥
     *
     * @param privateKey 发送方的 X25519 私钥
     * @param publicKey 接收方的 X25519 公钥
     * @return ECDH 共享密钥（32 字节）
     * @throws Exception 如果密钥交换失败
     */
    byte[] performECDH(PrivateKey privateKey, PublicKey publicKey) throws Exception;

    /**
     * 验证公钥有效性
     *
     * 防止 Invalid Curve 攻击，确保公钥是有效的曲线点
     *
     * @param encodedKey Base64 编码的公钥
     * @throws Exception 如果公钥无效或不是有效的曲线点
     */
    void validatePublicKey(byte[] encodedKey) throws Exception;

    /**
     * 从字节数组解码公钥
     *
     * @param encodedKey Base64 编码的公钥（32 字节）
     * @return 解码后的 X25519 公钥对象
     * @throws Exception 如果解码失败
     */
    PublicKey decodePublicKey(byte[] encodedKey) throws Exception;

    /**
     * 从字节数组解码私钥
     *
     * @param encodedKey Base64 编码的私钥（32 字节）
     * @return 解码后的 X25519 私钥对象
     * @throws Exception 如果解码失败
     */
    PrivateKey decodePrivateKey(byte[] encodedKey) throws Exception;
}