package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友请求响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestDto {

    private String requestId;
    private String fromUserId;
    private String fromUsername;
    private String fromDisplayName;
    private String message;
    private String status;
    private Long createdAt;
    private Long respondedAt;
}
