package org.ttt.safevaultbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.ttt.safevaultbackend.entity.FriendRequest;
import org.ttt.safevaultbackend.entity.FriendRequestStatus;

import java.util.List;
import java.util.Optional;

/**
 * 好友请求数据访问层
 */
@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, String> {

    /**
     * 查找用户收到的待处理请求
     *
     * @param toUserId 接收用户ID
     * @param status 请求状态
     * @return 好友请求列表
     */
    List<FriendRequest> findByToUserIdAndStatusOrderByCreatedAtDesc(String toUserId, FriendRequestStatus status);

    /**
     * 检查是否已存在待处理请求
     *
     * @param fromUserId 发送用户ID
     * @param toUserId 接收用户ID
     * @param status 请求状态
     * @return 如果存在待处理请求返回true，否则返回false
     */
    boolean existsByFromUserIdAndToUserIdAndStatus(String fromUserId, String toUserId, FriendRequestStatus status);

    /**
     * 查找两个用户之间的请求
     *
     * @param fromUserId 发送用户ID
     * @param toUserId 接收用户ID
     * @return 好友请求对象（如果存在）
     */
    Optional<FriendRequest> findByFromUserIdAndToUserIdOrderByCreatedAtDesc(String fromUserId, String toUserId);

    // ========== 额外的实用查询方法 ==========

    /**
     * 查询用户发送的待处理请求
     */
    List<FriendRequest> findByFromUserIdAndStatusOrderByCreatedAtDesc(String fromUserId, FriendRequestStatus status);

    /**
     * 查询两个用户之间的待处理请求（指定方向）
     */
    @Query("SELECT fr FROM FriendRequest fr WHERE fr.fromUserId = :fromUserId AND fr.toUserId = :toUserId AND fr.status = :status")
    Optional<FriendRequest> findPendingRequest(@Param("fromUserId") String fromUserId,
                                               @Param("toUserId") String toUserId,
                                               @Param("status") FriendRequestStatus status);

    /**
     * 查询任意方向的待处理请求
     */
    @Query("SELECT fr FROM FriendRequest fr WHERE ((fr.fromUserId = :userId1 AND fr.toUserId = :userId2) OR (fr.fromUserId = :userId2 AND fr.toUserId = :userId1)) AND fr.status = :status")
    Optional<FriendRequest> findPendingRequestBetweenUsers(@Param("userId1") String userId1,
                                                           @Param("userId2") String userId2,
                                                           @Param("status") FriendRequestStatus status);

    /**
     * 查询用户的所有请求（包括发送和接收）
     */
    @Query("SELECT fr FROM FriendRequest fr WHERE fr.fromUserId = :userId OR fr.toUserId = :userId")
    List<FriendRequest> findAllByUserId(@Param("userId") String userId);
}
