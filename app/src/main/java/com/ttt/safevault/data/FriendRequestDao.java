package com.ttt.safevault.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 好友请求数据访问对象
 */
@Dao
public interface FriendRequestDao {
    @Query("SELECT * FROM friend_requests WHERE status = 'PENDING' ORDER BY created_at DESC")
    List<FriendRequest> getPendingRequests();

    @Query("SELECT * FROM friend_requests WHERE request_id = :requestId")
    FriendRequest getRequest(String requestId);

    @Query("SELECT * FROM friend_requests WHERE from_user_id = :userId ORDER BY created_at DESC")
    List<FriendRequest> getRequestsByUser(String userId);

    @Query("SELECT * FROM friend_requests WHERE status = :status ORDER BY created_at DESC")
    List<FriendRequest> getRequestsByStatus(String status);

    @Query("SELECT * FROM friend_requests ORDER BY created_at DESC")
    List<FriendRequest> getAllRequests();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertFriendRequest(FriendRequest request);

    @Update
    int updateFriendRequest(FriendRequest request);

    @Delete
    int deleteFriendRequest(FriendRequest request);

    @Query("DELETE FROM friend_requests WHERE request_id = :requestId")
    int deleteFriendRequest(String requestId);

    @Query("DELETE FROM friend_requests WHERE status = 'ACCEPTED' OR status = 'REJECTED'")
    int deleteProcessedRequests();

    @Query("DELETE FROM friend_requests WHERE created_at < :timestamp")
    int deleteOldRequests(long timestamp);

    @Query("UPDATE friend_requests SET status = :status, responded_at = :timestamp WHERE request_id = :requestId")
    int updateRequestStatus(String requestId, String status, long timestamp);

    @Query("SELECT COUNT(*) FROM friend_requests WHERE status = 'PENDING'")
    int getPendingCount();
}
