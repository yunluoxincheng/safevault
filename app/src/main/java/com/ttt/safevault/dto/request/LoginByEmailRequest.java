package com.ttt.safevault.dto.request;

import com.google.gson.annotations.SerializedName;

/**
 * 邮箱登录请求（Challenge-Response 机制）
 */
public class LoginByEmailRequest {

    @SerializedName("email")
    private String email;

    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("deviceName")
    private String deviceName;

    @SerializedName("derivedKeySignature")
    private String derivedKeySignature;

    @SerializedName("nonce")
    private String nonce;

    @SerializedName("deviceType")
    private String deviceType;

    @SerializedName("osVersion")
    private String osVersion;

    public LoginByEmailRequest() {
    }

    public LoginByEmailRequest(String email, String deviceId, String deviceName,
                               String derivedKeySignature, String nonce,
                               String deviceType, String osVersion) {
        this.email = email;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.derivedKeySignature = derivedKeySignature;
        this.nonce = nonce;
        this.deviceType = deviceType;
        this.osVersion = osVersion;
    }

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDerivedKeySignature() {
        return derivedKeySignature;
    }

    public void setDerivedKeySignature(String derivedKeySignature) {
        this.derivedKeySignature = derivedKeySignature;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }
}
