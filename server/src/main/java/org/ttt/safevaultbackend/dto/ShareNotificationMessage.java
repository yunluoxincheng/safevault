package org.ttt.safevaultbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分享通知消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareNotificationMessage {

    private String type;
    private String shareId;
    private String fromUserId;
    private String fromDisplayName;
    private String message;
    private Long timestamp;
}
