package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ttt.safevaultbackend.entity.ContactShareStatus;

/**
 * 联系人分享响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactShareResponse {

    private String shareId;
    private String passwordId;
    private ContactShareStatus status;
    private Long createdAt;
    private Long expiresAt;

    /**
     * RSA加密版本
     * v1 = PKCS1Padding（不安全，仅向后兼容）
     * v2 = OAEPWithSHA-256AndMGF1Padding（安全）
     */
    private String encryptionVersion;
}
