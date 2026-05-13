package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户搜索结果响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchResult {

    private String userId;
    private String username;
    private String email;
    private String displayName;
}
