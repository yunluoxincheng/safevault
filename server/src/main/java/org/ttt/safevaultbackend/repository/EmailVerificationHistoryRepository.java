package org.ttt.safevaultbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.ttt.safevaultbackend.entity.EmailVerificationHistory;
import org.ttt.safevaultbackend.enums.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 邮箱验证历史数据访问层
 * 提供验证历史记录的查询和操作接口
 */
@Repository
public interface EmailVerificationHistoryRepository extends JpaRepository<EmailVerificationHistory, Long> {

    /**
     * 根据用户ID查询验证历史
     */
    List<EmailVerificationHistory> findByUserIdOrderBySentAtDesc(String userId);

    /**
     * 根据邮箱查询验证历史
     */
    List<EmailVerificationHistory> findByEmailOrderBySentAtDesc(String email);

    /**
     * 根据验证码查询
     */
    Optional<EmailVerificationHistory> findByVerificationCode(String verificationCode);

    /**
     * 根据验证令牌查询
     */
    Optional<EmailVerificationHistory> findByVerificationToken(String verificationToken);

    /**
     * 根据验证码查询未使用的记录
     */
    @Query("SELECT h FROM EmailVerificationHistory h " +
           "WHERE h.verificationCode = :code " +
           "AND h.isUsed = false " +
           "AND h.expiresAt > :now")
    Optional<EmailVerificationHistory> findUnusedByCodeAndExpiresAfter(
        @Param("code") String code,
        @Param("now") LocalDateTime now
    );

    /**
     * 根据验证令牌查询未使用的记录
     */
    @Query("SELECT h FROM EmailVerificationHistory h " +
           "WHERE h.verificationToken = :token " +
           "AND h.isUsed = false " +
           "AND h.expiresAt > :now")
    Optional<EmailVerificationHistory> findUnusedByTokenAndExpiresAfter(
        @Param("token") String token,
        @Param("now") LocalDateTime now
    );

    /**
     * 统计用户在指定时间范围内发送的验证次数
     */
    @Query("SELECT COUNT(h) FROM EmailVerificationHistory h " +
           "WHERE h.email = :email " +
           "AND h.sentAt > :since")
    long countByEmailAfter(
        @Param("email") String email,
        @Param("since") LocalDateTime since
    );

    /**
     * 查询用户的未使用且未过期的验证记录
     */
    @Query("SELECT h FROM EmailVerificationHistory h " +
           "WHERE h.userId = :userId " +
           "AND h.isUsed = false " +
           "AND h.expiresAt > :now " +
           "ORDER BY h.sentAt DESC")
    List<EmailVerificationHistory> findUnusedAndValidByUserId(
        @Param("userId") String userId,
        @Param("now") LocalDateTime now
    );

    /**
     * 标记验证记录为已使用
     */
    @Modifying
    @Query("UPDATE EmailVerificationHistory h SET " +
           "h.isUsed = true, " +
           "h.verifiedAt = :verifiedAt " +
           "WHERE h.verificationCode = :code OR h.verificationToken = :token")
    int markAsUsed(
        @Param("code") String code,
        @Param("token") String token,
        @Param("verifiedAt") LocalDateTime verifiedAt
    );

    /**
     * 更新邮件投递状态
     */
    @Modifying
    @Query("UPDATE EmailVerificationHistory h SET " +
           "h.deliveryStatus = :status " +
           "WHERE h.id = :id")
    int updateDeliveryStatus(
        @Param("id") Long id,
        @Param("status") DeliveryStatus status
    );

    /**
     * 删除过期的验证历史记录
     */
    @Modifying
    @Query("DELETE FROM EmailVerificationHistory h " +
           "WHERE h.isUsed = true " +
           "AND h.verifiedAt < :date")
    int deleteUsedHistoryBefore(@Param("date") LocalDateTime date);

    /**
     * 查询指定时间范围内发送失败的记录
     */
    @Query("SELECT h FROM EmailVerificationHistory h " +
           "WHERE h.sentAt > :since " +
           "AND h.deliveryStatus = :status")
    List<EmailVerificationHistory> findFailedDeliveriesAfter(
        @Param("since") LocalDateTime since,
        @Param("status") DeliveryStatus status
    );

    /**
     * 检查是否超过发送频率限制
     */
    @Query("SELECT COUNT(h) > :limit FROM EmailVerificationHistory h " +
           "WHERE h.email = :email " +
           "AND h.sentAt > :since")
    boolean exceedsSendLimit(
        @Param("email") String email,
        @Param("since") LocalDateTime since,
        @Param("limit") long limit
    );

    /**
     * 查询最近的验证记录 (用于重发检测)
     */
    @Query("SELECT h FROM EmailVerificationHistory h " +
           "WHERE h.email = :email " +
           "ORDER BY h.sentAt DESC " +
           "LIMIT 1")
    Optional<EmailVerificationHistory> findMostRecentByEmail(@Param("email") String email);
}
