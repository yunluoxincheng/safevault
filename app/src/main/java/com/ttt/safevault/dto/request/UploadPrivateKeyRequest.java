package com.ttt.safevault.dto.request;

/**
 * 上传加密私钥请求
 */
public class UploadPrivateKeyRequest {

    private String encryptedPrivateKey;
    private String iv;
    private String salt;
    private String authTag;
    private String version;

    public UploadPrivateKeyRequest() {
    }

    public UploadPrivateKeyRequest(String encryptedPrivateKey, String iv, String salt, String authTag, String version) {
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.iv = iv;
        this.salt = salt;
        this.authTag = authTag;
        this.version = version;
    }

    public String getEncryptedPrivateKey() {
        return encryptedPrivateKey;
    }

    public void setEncryptedPrivateKey(String encryptedPrivateKey) {
        this.encryptedPrivateKey = encryptedPrivateKey;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getAuthTag() {
        return authTag;
    }

    public void setAuthTag(String authTag) {
        this.authTag = authTag;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
