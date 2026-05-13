package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送好友请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendFriendRequestRequest {

    @NotBlank(message = "toUserId不能为空")
    private String toUserId;

    private String message;  // 可选的请求消息
}
