package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ttt.safevaultbackend.dto.DeviceInfo;

import java.util.List;

/**
 * 设备列表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceListResponse {

    private List<DeviceInfo> devices;
    private int totalDevices;
}
