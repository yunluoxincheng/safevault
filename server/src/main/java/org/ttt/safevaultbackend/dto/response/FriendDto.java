package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友信息响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendDto {

    private String userId;
    private String username;
    private String email;        // 用户的邮箱
    private String displayName;
    private String publicKey;    // 用户的公钥
    private Long addedAt;
    private Boolean isOnline;
}
