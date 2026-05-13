package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ttt.safevaultbackend.entity.ContactShareStatus;

/**
 * 接受分享响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcceptShareResponse {

    private String shareId;
    private ContactShareStatus status;
    private Long acceptedAt;
}
