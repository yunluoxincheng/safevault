package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ttt.safevaultbackend.dto.PasswordData;
import org.ttt.safevaultbackend.dto.SharePermission;
import org.ttt.safevaultbackend.entity.ContactShareStatus;

/**
 * 接收的联系人分享详情响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivedContactShareResponse {

    private String shareId;
    private String fromUserId;
    private String fromDisplayName;
    private String passwordId;
    private PasswordData passwordData;
    private SharePermission permission;
    private ContactShareStatus status;
    private Long createdAt;
    private Long expiresAt;
    private Long acceptedAt;

    /**
     * RSA加密版本
     * v1 = PKCS1Padding（不安全，仅向后兼容）
     * v2 = OAEPWithSHA-256AndMGF1Padding（安全）
     */
    private String encryptionVersion;
}
