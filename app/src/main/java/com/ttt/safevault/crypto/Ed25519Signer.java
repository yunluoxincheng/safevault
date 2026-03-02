package com.ttt.safevault.crypto;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Ed25519 签名器接口
 *
 * 处理 Ed25519 (Curve25519 EdDSA) 签名和验证
 *
 * Ed25519 特性：
 * - 签名大小：64 字节
 * - 公钥大小：32 字节
 * - 私钥大小：32 字节
 * - 性能：比 RSA 签名快 100 倍
 * - 安全性：抗侧信道攻击，确定性签名
 */
public interface Ed25519Signer {

    /**
     * 生成 Ed25519 密钥对
     *
     * @return 新生成的 Ed25519 密钥对
     * @throws Exception 如果密钥生成失败
     */
    KeyPair generateKeyPair() throws Exception;

    /**
     * 对数据进行签名
     *
     * @param data 要签名的数据（原始字节数组）
     * @param privateKey Ed25519 私钥
     * @return 签名数据（64 字节）
     * @throws Exception 如果签名失败
     */
    byte[] sign(byte[] data, PrivateKey privateKey) throws Exception;

    /**
     * 验证签名
     *
     * @param data 原始数据
     * @param signature 签名数据（64 字节）
     * @param publicKey Ed25519 公钥
     * @return true 表示签名有效，false 表示签名无效
     * @throws Exception 如果验证过程出错
     */
    boolean verify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception;

    /**
     * 从字节数组解码公钥
     *
     * @param encodedKey Base64 编码的公钥（32 字节）
     * @return 解码后的 Ed25519 公钥对象
     * @throws Exception 如果解码失败
     */
    PublicKey decodePublicKey(byte[] encodedKey) throws Exception;

    /**
     * 从字节数组解码私钥
     *
     * @param encodedKey Base64 编码的私钥（32 字节）
     * @return 解码后的 Ed25519 私钥对象
     * @throws Exception 如果解码失败
     */
    PrivateKey decodePrivateKey(byte[] encodedKey) throws Exception;

    /**
     * 验证公钥有效性
     *
     * @param encodedKey Base64 编码的公钥
     * @throws Exception 如果公钥无效
     */
    void validatePublicKey(byte[] encodedKey) throws Exception;

    /**
     * 验证签名大小
     *
     * @param signature 签名数据
     * @throws Exception 如果签名大小不正确
     */
    void validateSignatureSize(byte[] signature) throws Exception;
}