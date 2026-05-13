package org.ttt.safevaultbackend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 删除账户响应
 */
@Schema(description = "删除账户响应")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountResponse {

    @Schema(description = "是否删除成功")
    private boolean success;

    @Schema(description = "响应消息")
    private String message;
}
