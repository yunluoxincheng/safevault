package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.ShareRecord;
import com.ttt.safevault.data.ShareRecordDao;
import com.ttt.safevault.model.SharePermission;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 分享记录管理器
 * 负责管理密码分享的记录（发送和接收）
 */
public class ShareRecordManager {
    private static final String TAG = "ShareRecordManager";

    private final ShareRecordDao shareRecordDao;

    public ShareRecordManager(@NonNull Context context) {
        this.shareRecordDao = AppDatabase.getInstance(context).shareRecordDao();
    }

    /**
     * 保存分享记录
     *
     * @param record 分享记录
     * @return true表示保存成功
     */
    public boolean saveShareRecord(@NonNull ShareRecord record) {
        try {
            long result = shareRecordDao.insertShareRecord(record);
            if (result > 0) {
                Log.d(TAG, "Successfully saved share record: " + record.shareId);
                return true;
            } else {
                Log.e(TAG, "Failed to save share record");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save share record", e);
            return false;
        }
    }

    /**
     * 创建新的分享记录
     *
     * @param passwordId 密码ID
     * @param type 类型（sent 或 received）
     * @param contactId 联系人ID
     * @param encryptedData 加密的分享数据
     * @param permission 分享权限
     * @param expireAt 过期时间戳（0表示永不过期）
     * @return 分享记录ID
     */
    @Nullable
    public String createShareRecord(
            int passwordId,
            @NonNull String type,
            @Nullable String contactId,
            @NonNull String encryptedData,
            @NonNull SharePermission permission,
            long expireAt
    ) {
        try {
            ShareRecord record = new ShareRecord();
            record.shareId = generateShareId();
            record.passwordId = passwordId;
            record.type = type;
            record.contactId = contactId;
            record.remoteUserId = null;
            record.encryptedData = encryptedData;
            record.permission = serializePermission(permission);
            record.expireAt = expireAt;
            record.status = "active";
            record.createdAt = System.currentTimeMillis();
            record.accessedAt = 0;

            if (saveShareRecord(record)) {
                return record.shareId;
            } else {
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create share record", e);
            return null;
        }
    }

    /**
     * 获取我发送的分享
     */
    @NonNull
    public List<ShareRecord> getMySentShares() {
        try {
            return shareRecordDao.getMySentShares();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get sent shares", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取我接收的分享
     */
    @NonNull
    public List<ShareRecord> getMyReceivedShares() {
        try {
            return shareRecordDao.getMyReceivedShares();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get received shares", e);
            return new ArrayList<>();
        }
    }

    /**
     * 撤销分享
     *
     * @param shareId 分享ID
     * @return true表示撤销成功
     */
    public boolean revokeShare(@NonNull String shareId) {
        try {
            int result = shareRecordDao.revokeShare(shareId);
            Log.d(TAG, "Revoke share result: " + result);
            return result > 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to revoke share", e);
            return false;
        }
    }

    /**
     * 获取分享详情
     *
     * @param shareId 分享ID
     * @return 分享记录，不存在返回null
     */
    @Nullable
    public ShareRecord getShareRecord(@NonNull String shareId) {
        try {
            return shareRecordDao.getShareRecord(shareId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get share record", e);
            return null;
        }
    }

    /**
     * 检查分享是否过期
     *
     * @param shareId 分享ID
     * @return true表示已过期
     */
    public boolean isShareExpired(@NonNull String shareId) {
        ShareRecord record = getShareRecord(shareId);
        if (record == null) {
            return true;
        }

        if (record.expireAt == 0) {
            return false; // 永不过期
        }

        return System.currentTimeMillis() > record.expireAt;
    }

    /**
     * 更新分享的最后访问时间
     *
     * @param shareId 分享ID
     */
    public void updateAccessedAt(@NonNull String shareId) {
        try {
            shareRecordDao.updateAccessedAt(shareId, System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "Failed to update accessed time", e);
        }
    }

    /**
     * 删除过期的分享记录
     */
    public int deleteExpiredShares() {
        try {
            return shareRecordDao.deleteExpiredShares(System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete expired shares", e);
            return 0;
        }
    }

    /**
     * 序列化权限为JSON
     */
    @NonNull
    private String serializePermission(@NonNull SharePermission permission) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"canView\":").append(permission.isCanView());
        json.append(",\"canSave\":").append(permission.isCanSave());
        json.append(",\"revocable\":").append(permission.isRevocable());
        json.append("}");
        return json.toString();
    }

    /**
     * 生成分享ID
     */
    @NonNull
    private String generateShareId() {
        return "share_" + UUID.randomUUID().toString();
    }
}
