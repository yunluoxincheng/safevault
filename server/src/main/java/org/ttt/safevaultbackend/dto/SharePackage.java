package org.ttt.safevaultbackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 分享包模型
 * 包含加密的密码数据和元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharePackage {

    @JsonProperty("version")
    @Builder.Default
    private String version = "1.0";

    @JsonProperty("shareId")
    private String shareId;

    @JsonProperty("fromUserId")
    private String fromUserId;

    @JsonProperty("encryptedSessionKey")
    private String encryptedSessionKey;

    @JsonProperty("encryptedData")
    private Map<String, String> encryptedData;

    @JsonProperty("permission")
    private SharePermission permission;

    @JsonProperty("expiresAt")
    private Long expiresAt;

    @JsonProperty("signature")
    private String signature;

    /**
     * 序列化为 JSON 字符串
     */
    public String toJson() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize SharePackage", e);
        }
    }

    /**
     * 从 JSON 字符串反序列化
     */
    public static SharePackage fromJson(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, SharePackage.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize SharePackage", e);
        }
    }

    /**
     * 编码为 Base64 Token
     */
    public String toBase64Token() {
        return java.util.Base64.getEncoder().encodeToString(toJson().getBytes());
    }

    /**
     * 从 Base64 Token 解码
     */
    public static SharePackage fromBase64Token(String token) {
        String json = new String(java.util.Base64.getDecoder().decode(token));
        return fromJson(json);
    }
}
