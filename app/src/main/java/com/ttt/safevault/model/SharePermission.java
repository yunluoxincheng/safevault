package com.ttt.safevault.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.Gson;

/**
 * 分享权限数据模型
 */
public class SharePermission implements Parcelable {
    public boolean canView;         // 是否可查看
    public boolean canSave;         // 是否可保存到本地
    public boolean revocable;       // 是否可撤销

    private static final Gson gson = new Gson();

    public SharePermission() {
        this.canView = true;         // 默认可查看
        this.canSave = true;         // 默认可保存
        this.revocable = true;       // 默认可撤销
    }

    public SharePermission(boolean canView, boolean canSave, boolean revocable) {
        this.canView = canView;
        this.canSave = canSave;
        this.revocable = revocable;
    }

    /**
     * 从JSON字符串创建SharePermission
     */
    public static SharePermission fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new SharePermission();
        }
        try {
            return gson.fromJson(json, SharePermission.class);
        } catch (Exception e) {
            return new SharePermission();
        }
    }

    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        return gson.toJson(this);
    }

    // Getter 和 Setter 方法
    public boolean isCanView() {
        return canView;
    }

    public void setCanView(boolean canView) {
        this.canView = canView;
    }

    public boolean isCanSave() {
        return canSave;
    }

    public void setCanSave(boolean canSave) {
        this.canSave = canSave;
    }

    public boolean isRevocable() {
        return revocable;
    }

    public void setRevocable(boolean revocable) {
        this.revocable = revocable;
    }

    @Override
    public String toString() {
        return "SharePermission{" +
                "canView=" + canView +
                ", canSave=" + canSave +
                ", revocable=" + revocable +
                '}';
    }

    // Parcelable implementation
    protected SharePermission(Parcel in) {
        canView = in.readByte() != 0;
        canSave = in.readByte() != 0;
        revocable = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (canView ? 1 : 0));
        dest.writeByte((byte) (canSave ? 1 : 0));
        dest.writeByte((byte) (revocable ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SharePermission> CREATOR = new Creator<SharePermission>() {
        @Override
        public SharePermission createFromParcel(Parcel in) {
            return new SharePermission(in);
        }

        @Override
        public SharePermission[] newArray(int size) {
            return new SharePermission[size];
        }
    };
}
