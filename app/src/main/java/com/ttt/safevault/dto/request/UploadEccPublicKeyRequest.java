package com.ttt.safevault.dto.request;

/**
 * 上传 X25519/Ed25519 椭圆曲线公钥请求
 *
 * 用于将新生成的 ECC 公钥上传到云端，支持协议版本 3.0
 *
 * @since SafeVault 3.6.0
 */
public class UploadEccPublicKeyRequest {

    private String x25519PublicKey;
    private String ed25519PublicKey;
    private String keyVersion;

    public UploadEccPublicKeyRequest() {
    }

    public UploadEccPublicKeyRequest(String x25519PublicKey, String ed25519PublicKey, String keyVersion) {
        this.x25519PublicKey = x25519PublicKey;
        this.ed25519PublicKey = ed25519PublicKey;
        this.keyVersion = keyVersion;
    }

    public String getX25519PublicKey() {
        return x25519PublicKey;
    }

    public void setX25519PublicKey(String x25519PublicKey) {
        this.x25519PublicKey = x25519PublicKey;
    }

    public String getEd25519PublicKey() {
        return ed25519PublicKey;
    }

    public void setEd25519PublicKey(String ed25519PublicKey) {
        this.ed25519PublicKey = ed25519PublicKey;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(String keyVersion) {
        this.keyVersion = keyVersion;
    }
}