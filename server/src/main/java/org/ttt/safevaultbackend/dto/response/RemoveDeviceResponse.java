package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 移除设备响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoveDeviceResponse {

    private boolean success;
    private String message;
    private String removedDeviceId;
}
