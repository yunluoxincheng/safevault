package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 加密私钥响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivateKeyResponse {

    private String encryptedPrivateKey;
    private String iv;
    private String salt;
    private String authTag;
    private String version;
    private LocalDateTime updatedAt;
}
