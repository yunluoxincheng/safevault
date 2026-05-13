package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 响应好友请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespondFriendRequestRequest {

    @NotNull(message = "accept不能为空")
    private Boolean accept;  // true=同意, false=拒绝
}
