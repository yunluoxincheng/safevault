package org.ttt.safevaultbackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分享权限
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharePermission {

    @Builder.Default
    private boolean canView = true;

    @Builder.Default
    private boolean canSave = true;

    @JsonProperty("isRevocable")
    @Builder.Default
    private boolean isRevocable = true;
}
