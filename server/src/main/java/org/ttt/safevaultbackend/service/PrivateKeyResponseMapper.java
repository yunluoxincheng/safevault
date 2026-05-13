package org.ttt.safevaultbackend.service;

import org.springframework.stereotype.Component;
import org.ttt.safevaultbackend.dto.response.PrivateKeyResponse;
import org.ttt.safevaultbackend.dto.response.UploadPrivateKeyResponse;
import org.ttt.safevaultbackend.entity.UserPrivateKey;

import java.time.LocalDateTime;

@Component
public class PrivateKeyResponseMapper {

    public UploadPrivateKeyResponse mapUploadResponse(UserPrivateKey key, LocalDateTime uploadedAt) {
        return UploadPrivateKeyResponse.builder()
                .success(true)
                .version(key.getVersion())
                .uploadedAt(uploadedAt)
                .build();
    }

    public PrivateKeyResponse mapPrivateKeyResponse(UserPrivateKey key) {
        return PrivateKeyResponse.builder()
                .encryptedPrivateKey(key.getEncryptedPrivateKey())
                .iv(key.getIv())
                .salt(key.getSalt())
                .authTag(key.getAuthTag())
                .version(key.getVersion())
                .updatedAt(key.getUpdatedAt())
                .build();
    }
}
