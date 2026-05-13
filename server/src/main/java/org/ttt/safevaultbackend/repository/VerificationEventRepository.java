package org.ttt.safevaultbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.ttt.safevaultbackend.entity.VerificationEvent;
import org.ttt.safevaultbackend.enums.VerificationEventType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 验证事件数据访问层
 * 提供验证事件的查询和操作接口
 */
@Repository
public interface VerificationEventRepository extends JpaRepository<VerificationEvent, Long> {

    /**
     * 根据用户ID查询事件列表
     */
    List<VerificationEvent> findByUserIdOrderByEventTimestampDesc(String userId);

    /**
     * 根据邮箱查询事件列表
     */
    List<VerificationEvent> findByEmailOrderByEventTimestampDesc(String email);

    /**
     * 根据用户ID和事件类型查询
     */
    List<VerificationEvent> findByUserIdAndEventTypeOrderByEventTimestampDesc(
        String userId,
        VerificationEventType eventType
    );

    /**
     * 根据验证令牌查询事件
     */
    List<VerificationEvent> findByVerificationTokenOrderByEventTimestampDesc(String verificationToken);

    /**
     * 统计指定时间范围内用户的事件数量
     */
    @Query("SELECT COUNT(e) FROM VerificationEvent e " +
           "WHERE e.userId = :userId " +
           "AND e.eventTimestamp BETWEEN :start AND :end")
    long countByUserIdAndTimeRange(
        @Param("userId") String userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * 统计指定时间范围内邮箱的事件数量
     */
    @Query("SELECT COUNT(e) FROM VerificationEvent e " +
           "WHERE e.email = :email " +
           "AND e.eventType = :eventType " +
           "AND e.eventTimestamp > :since")
    long countByEmailAndEventTypeAfter(
        @Param("email") String email,
        @Param("eventType") VerificationEventType eventType,
        @Param("since") LocalDateTime since
    );

    /**
     * 查询最近的失败事件
     */
    @Query("SELECT e FROM VerificationEvent e " +
           "WHERE e.userId = :userId " +
           "AND e.success = false " +
           "ORDER BY e.eventTimestamp DESC")
    List<VerificationEvent> findRecentFailuresByUserId(@Param("userId") String userId);

    /**
     * 查询指定令牌的验证成功事件
     */
    @Query("SELECT e FROM VerificationEvent e " +
           "WHERE e.verificationToken = :token " +
           "AND e.eventType = 'TOKEN_VERIFIED' " +
           "ORDER BY e.eventTimestamp DESC")
    List<VerificationEvent> findVerificationSuccessByToken(@Param("token") String token);

    /**
     * 删除过期的事件记录 (定时清理任务使用)
     */
    @Modifying
    @Query("DELETE FROM VerificationEvent e WHERE e.eventTimestamp < :date")
    int deleteExpiredEvents(@Param("date") LocalDateTime date);

    /**
     * 检查用户在指定时间范围内是否超过重试限制
     */
    @Query("SELECT COUNT(e) > :limit FROM VerificationEvent e " +
           "WHERE e.email = :email " +
           "AND e.eventType = :eventType " +
           "AND e.eventTimestamp > :since")
    boolean exceedsRetryLimit(
        @Param("email") String email,
        @Param("eventType") VerificationEventType eventType,
        @Param("since") LocalDateTime since,
        @Param("limit") long limit
    );
}
