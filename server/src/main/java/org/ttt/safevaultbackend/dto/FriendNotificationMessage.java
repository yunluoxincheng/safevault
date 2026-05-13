package org.ttt.safevaultbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友通知消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendNotificationMessage {

    private String type; // FRIEND_REQUEST, FRIEND_ACCEPTED, FRIEND_DELETED
    private String requestId;
    private String fromUserId;
    private String fromDisplayName;
    private String toUserId;
    private String message;
    private Long timestamp;
}
