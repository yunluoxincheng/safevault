package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 密码库同步响应
 * 包含同步后的密码库状态和可能的冲突信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultSyncResponse {

    /**
     * 同步是否成功
     */
    private boolean success;

    /**
     * 是否有冲突
     */
    private boolean hasConflict;

    /**
     * 冲突描述（当 hasConflict 为 true 时提供）
     */
    private String conflictMessage;

    /**
     * 服务器版本号（同步前）
     */
    private Long serverVersion;

    /**
     * 客户端版本号（同步前）
     */
    private Long clientVersion;

    /**
     * 新的版本号（同步后）
     */
    private Long newVersion;

    /**
     * 同步后的密码库数据
     */
    private VaultResponse vault;

    /**
     * 冲突时的服务器数据（供客户端合并）
     */
    private VaultResponse serverVault;

    /**
     * 最后同步时间
     */
    private LocalDateTime lastSyncedAt;
}
