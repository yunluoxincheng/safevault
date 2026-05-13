package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 上传 X25519/Ed25519 椭圆曲线公钥响应
 *
 * @since SafeVault 3.6.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadEccPublicKeyResponse {

    private boolean success;
    private String message;
    private LocalDateTime uploadedAt;
}
