package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传 X25519/Ed25519 椭圆曲线公钥请求
 *
 * 用于将新生成的 ECC 公钥上传到云端，支持协议版本 3.0
 *
 * @since SafeVault 3.6.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadEccPublicKeyRequest {

    @NotBlank(message = "X25519 公钥不能为空")
    private String x25519PublicKey;

    @NotBlank(message = "Ed25519 公钥不能为空")
    private String ed25519PublicKey;

    @NotBlank(message = "公钥版本不能为空")
    private String keyVersion;
}
