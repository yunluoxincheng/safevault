package com.ttt.safevault.dto;

import com.google.gson.annotations.SerializedName;

/**
 * 密码数据DTO
 */
public class PasswordData {
    private String title;
    private String username;

    // 后端使用 encryptedPassword，前端使用 password 作为别名
    @SerializedName("encryptedPassword")
    private String password;

    private String url;
    private String notes;

    public PasswordData() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
