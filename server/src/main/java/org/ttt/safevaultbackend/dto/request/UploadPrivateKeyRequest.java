package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传加密私钥请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadPrivateKeyRequest {

    @NotBlank(message = "加密私钥不能为空")
    private String encryptedPrivateKey;

    @NotBlank(message = "初始化向量不能为空")
    private String iv;

    @NotBlank(message = "盐值不能为空")
    private String salt;

    @NotBlank(message = "认证标签不能为空")
    private String authTag;

    @NotBlank(message = "版本号不能为空")
    private String version;
}
