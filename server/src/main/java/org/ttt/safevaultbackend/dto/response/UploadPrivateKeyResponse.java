package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 上传加密私钥响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadPrivateKeyResponse {

    private boolean success;
    private String version;
    private LocalDateTime uploadedAt;
}
