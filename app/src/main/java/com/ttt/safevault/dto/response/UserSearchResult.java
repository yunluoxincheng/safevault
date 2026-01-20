package com.ttt.safevault.dto.response;

import java.io.Serializable;

/**
 * 用户搜索结果响应DTO
 */
public class UserSearchResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String username;
    private String email;
    private String displayName;

    public UserSearchResult() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
