package org.ttt.safevaultbackend.service;

import org.springframework.stereotype.Component;
import org.ttt.safevaultbackend.dto.response.VaultResponse;
import org.ttt.safevaultbackend.entity.UserVault;

@Component
public class VaultResponseMapper {

    public VaultResponse mapToResponse(UserVault vault) {
        return VaultResponse.builder()
                .vaultId(vault.getVaultId())
                .userId(vault.getUserId())
                .encryptedData(vault.getEncryptedData())
                .dataIv(vault.getDataIv())
                .dataAuthTag(vault.getDataAuthTag())
                .salt(vault.getSalt())
                .version(vault.getVersion())
                .lastSyncedAt(vault.getLastSyncedAt())
                .createdAt(vault.getCreatedAt())
                .updatedAt(vault.getUpdatedAt())
                .build();
    }
}
