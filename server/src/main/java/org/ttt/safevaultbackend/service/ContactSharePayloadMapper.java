package org.ttt.safevaultbackend.service;

import org.springframework.stereotype.Component;
import org.ttt.safevaultbackend.dto.PasswordData;
import org.ttt.safevaultbackend.dto.SharePermission;
import org.ttt.safevaultbackend.dto.request.CreateContactShareRequest;
import org.ttt.safevaultbackend.dto.response.ReceivedContactShareResponse;
import org.ttt.safevaultbackend.dto.response.SentContactShareResponse;
import org.ttt.safevaultbackend.entity.ContactShare;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Component
public class ContactSharePayloadMapper {

    public Map<String, String> buildEncryptedData(CreateContactShareRequest request) {
        Map<String, String> data = new HashMap<>();
        data.put("title", request.getTitle());
        data.put("username", request.getUsername() != null ? request.getUsername() : "");
        data.put("password", request.getEncryptedPassword());
        data.put("url", request.getUrl() != null ? request.getUrl() : "");
        data.put("notes", request.getNotes() != null ? request.getNotes() : "");
        return data;
    }

    public String serializeEncryptedData(Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (sb.length() > 0) {
                sb.append("|");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    public Map<String, String> deserializeEncryptedData(String serialized) {
        Map<String, String> data = new HashMap<>();
        if (serialized != null && !serialized.isEmpty()) {
            String[] pairs = serialized.split("\\|");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    data.put(kv[0], kv[1]);
                }
            }
        }
        return data;
    }

    public SentContactShareResponse mapToSentShareResponse(ContactShare share) {
        Map<String, String> encryptedData = deserializeEncryptedData(share.getEncryptedData());

        return SentContactShareResponse.builder()
                .shareId(share.getShareId())
                .toUserId(share.getToUser().getUserId())
                .toDisplayName(share.getToUser().getDisplayName())
                .passwordId(share.getPasswordId())
                .passwordTitle(encryptedData.get("title"))
                .status(share.getStatus())
                .createdAt(share.getCreatedAt().toEpochSecond(ZoneOffset.UTC))
                .expiresAt(share.getExpiresAt() != null ? share.getExpiresAt().toEpochSecond(ZoneOffset.UTC) : null)
                .encryptionVersion(share.getEncryptionVersion())
                .build();
    }

    public ReceivedContactShareResponse mapToReceivedShareResponse(ContactShare share) {
        Map<String, String> encryptedData = deserializeEncryptedData(share.getEncryptedData());
        PasswordData passwordData = PasswordData.builder()
                .title(encryptedData.get("title"))
                .username(encryptedData.get("username"))
                .encryptedPassword(encryptedData.get("password"))
                .url(encryptedData.get("url"))
                .notes(encryptedData.get("notes"))
                .build();

        SharePermission permission = SharePermission.builder()
                .canView(share.isCanView())
                .canSave(share.isCanSave())
                .isRevocable(share.isRevocable())
                .build();

        return ReceivedContactShareResponse.builder()
                .shareId(share.getShareId())
                .fromUserId(share.getFromUser().getUserId())
                .fromDisplayName(share.getFromUser().getDisplayName())
                .passwordId(share.getPasswordId())
                .passwordData(passwordData)
                .permission(permission)
                .status(share.getStatus())
                .createdAt(share.getCreatedAt().toEpochSecond(ZoneOffset.UTC))
                .expiresAt(share.getExpiresAt() != null ? share.getExpiresAt().toEpochSecond(ZoneOffset.UTC) : null)
                .acceptedAt(share.getAcceptedAt() != null ? share.getAcceptedAt().toEpochSecond(ZoneOffset.UTC) : null)
                .encryptionVersion(share.getEncryptionVersion())
                .build();
    }
}
