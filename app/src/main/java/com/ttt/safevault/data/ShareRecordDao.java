package com.ttt.safevault.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 分享记录数据访问对象
 */
@Dao
public interface ShareRecordDao {
    @Query("SELECT * FROM share_records WHERE type = 'sent' ORDER BY created_at DESC")
    List<ShareRecord> getMySentShares();

    @Query("SELECT * FROM share_records WHERE type = 'received' ORDER BY created_at DESC")
    List<ShareRecord> getMyReceivedShares();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertShareRecord(ShareRecord record);

    @Update
    int updateShareRecord(ShareRecord record);

    @Query("UPDATE share_records SET status = 'revoked' WHERE share_id = :shareId")
    int revokeShare(String shareId);

    @Query("SELECT * FROM share_records WHERE share_id = :shareId")
    ShareRecord getShareRecord(String shareId);

    @Query("SELECT * FROM share_records WHERE status = :status ORDER BY created_at DESC")
    List<ShareRecord> getSharesByStatus(String status);

    @Query("DELETE FROM share_records WHERE share_id = :shareId")
    int deleteShareRecord(String shareId);

    @Query("UPDATE share_records SET accessed_at = :timestamp WHERE share_id = :shareId")
    int updateAccessedAt(String shareId, long timestamp);

    @Query("SELECT * FROM share_records WHERE password_id = :passwordId")
    List<ShareRecord> getSharesByPasswordId(int passwordId);

    @Query("SELECT COUNT(*) FROM share_records WHERE type = :type")
    int getShareCount(String type);

    @Query("DELETE FROM share_records WHERE expire_at > 0 AND expire_at < :timestamp")
    int deleteExpiredShares(long timestamp);
}
