package com.ttt.safevault.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * 密码条目数据模型
 * 前端使用的明文模型，实际数据由后端加密存储
 */
public class PasswordItem implements Parcelable {
    private int id;
    private String title;
    private String username;
    private String password;
    private String url;
    private String notes;
    private long updatedAt;
    private List<String> tags;  // 标签列表

    // 构造函数
    public PasswordItem() {
        this.updatedAt = System.currentTimeMillis();
        this.tags = new ArrayList<>();
    }

    public PasswordItem(String title, String username, String password) {
        this();
        this.title = title;
        this.username = username;
        this.password = password;
    }

    public PasswordItem(int id, String title, String username, String password, String url, String notes) {
        this(title, username, password);
        this.id = id;
        this.url = url;
        this.notes = notes;
    }

    // Getter和Setter方法
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Tags 相关方法
    public List<String> getTags() {
        return tags != null ? tags : new ArrayList<>();
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    /**
     * 添加单个标签（避免重复）
     */
    public void addTag(String tag) {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        String trimmedTag = tag.trim();
        if (!trimmedTag.isEmpty() && !tags.contains(trimmedTag)) {
            tags.add(trimmedTag);
        }
    }

    /**
     * 移除标签
     */
    public void removeTag(String tag) {
        if (tags != null) {
            tags.remove(tag);
        }
    }

    /**
     * 检查是否包含指定标签
     */
    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }

    // 更新时间戳
    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }

    // 获取显示名称（用于列表展示）
    public String getDisplayName() {
        if (title != null && !title.isEmpty()) {
            return title;
        } else if (username != null && !username.isEmpty()) {
            return username;
        } else if (url != null && !url.isEmpty()) {
            // 从URL中提取域名
            try {
                String domain = url.replace("https://", "")
                                 .replace("http://", "")
                                 .replace("www.", "");
                int slashIndex = domain.indexOf('/');
                if (slashIndex > 0) {
                    domain = domain.substring(0, slashIndex);
                }
                return domain;
            } catch (Exception e) {
                return url;
            }
        }
        return "未命名条目";
    }

    // 克隆方法
    public PasswordItem clone() {
        PasswordItem clone = new PasswordItem();
        clone.id = this.id;
        clone.title = this.title;
        clone.username = this.username;
        clone.password = this.password;
        clone.url = this.url;
        clone.notes = this.notes;
        clone.updatedAt = this.updatedAt;
        clone.tags = this.tags != null ? new ArrayList<>(this.tags) : new ArrayList<>();
        return clone;
    }

    @Override
    public String toString() {
        return "PasswordItem{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", username='" + username + '\'' +
                ", url='" + url + '\'' +
                ", updatedAt=" + updatedAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PasswordItem that = (PasswordItem) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    // Parcelable implementation
    protected PasswordItem(Parcel in) {
        id = in.readInt();
        title = in.readString();
        username = in.readString();
        password = in.readString();
        url = in.readString();
        notes = in.readString();
        updatedAt = in.readLong();
        tags = in.createStringArrayList();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(title);
        dest.writeString(username);
        dest.writeString(password);
        dest.writeString(url);
        dest.writeString(notes);
        dest.writeLong(updatedAt);
        dest.writeStringList(tags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PasswordItem> CREATOR = new Creator<PasswordItem>() {
        @Override
        public PasswordItem createFromParcel(Parcel in) {
            return new PasswordItem(in);
        }

        @Override
        public PasswordItem[] newArray(int size) {
            return new PasswordItem[size];
        }
    };
}