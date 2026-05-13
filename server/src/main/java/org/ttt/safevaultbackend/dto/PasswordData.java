package org.ttt.safevaultbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 密码数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordData {

    private String title;
    private String username;
    private String encryptedPassword;
    private String url;
    private String notes;
}
