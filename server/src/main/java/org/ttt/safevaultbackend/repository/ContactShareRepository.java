package org.ttt.safevaultbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.ttt.safevaultbackend.entity.ContactShare;
import org.ttt.safevaultbackend.entity.ContactShareStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 联系人分享 Repository
 */
@Repository
public interface ContactShareRepository extends JpaRepository<ContactShare, String> {

    /**
     * 查找用户发送的所有分享
     */
    List<ContactShare> findByFromUser_UserIdOrderByCreatedAtDesc(String fromUserId);

    /**
     * 查找用户接收的所有分享
     */
    List<ContactShare> findByToUser_UserIdOrderByCreatedAtDesc(String toUserId);

    /**
     * 查找特定状态的分享
     */
    List<ContactShare> findByStatus(ContactShareStatus status);

    /**
     * 查找已过期但状态不是 EXPIRED 的分享
     */
    @Query("SELECT cs FROM ContactShare cs WHERE cs.expiresAt < :now AND cs.status != :expiredStatus")
    List<ContactShare> findExpiredShares(@Param("now") LocalDateTime now, @Param("expiredStatus") ContactShareStatus expiredStatus);

    /**
     * 检查两个用户间是否存在特定状态的分享
     */
    @Query("SELECT cs FROM ContactShare cs WHERE ((cs.fromUser.userId = :user1 AND cs.toUser.userId = :user2) " +
           "OR (cs.fromUser.userId = :user2 AND cs.toUser.userId = :user1)) " +
           "AND cs.passwordId = :passwordId AND cs.status IN :statuses")
    Optional<ContactShare> findExistingShare(@Param("user1") String user1,
                                              @Param("user2") String user2,
                                              @Param("passwordId") String passwordId,
                                              @Param("statuses") List<ContactShareStatus> statuses);

    /**
     * 查找用户发送的所有活跃分享
     */
    @Query("SELECT cs FROM ContactShare cs WHERE cs.fromUser.userId = :userId AND cs.status IN :statuses")
    List<ContactShare> findActiveSharesByFromUser(@Param("userId") String userId,
                                                    @Param("statuses") List<ContactShareStatus> statuses);

    /**
     * 查找用户接收的所有活跃分享
     */
    @Query("SELECT cs FROM ContactShare cs WHERE cs.toUser.userId = :userId AND cs.status IN :statuses")
    List<ContactShare> findActiveSharesByToUser(@Param("userId") String userId,
                                                  @Param("statuses") List<ContactShareStatus> statuses);
}
