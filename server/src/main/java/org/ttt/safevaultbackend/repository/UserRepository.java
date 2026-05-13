package org.ttt.safevaultbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.ttt.safevaultbackend.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * 通过设备ID查找用户
     */
    Optional<User> findByDeviceId(String deviceId);

    /**
     * 通过用户名查找用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 通过用户ID查找用户
     */
    Optional<User> findByUserId(String userId);

    /**
     * 通过用户名模糊搜索
     */
    List<User> findByUsernameContainingIgnoreCase(String username);

    /**
     * 通过用户ID或用户名模糊搜索
     */
    @Query("SELECT u FROM User u WHERE u.userId = :query OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchByUserIdOrUsername(@Param("query") String query);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查用户ID是否存在
     */
    boolean existsByUserId(String userId);

    /**
     * 检查设备ID是否存在
     */
    boolean existsByDeviceId(String deviceId);

    // ========== 零知识架构：邮箱认证相关方法 ==========

    /**
     * 通过邮箱查找用户（零知识架构主要登录方式）
     */
    Optional<User> findByEmail(String email);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 通过邮箱或用户名搜索
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchByEmailOrUsername(@Param("query") String query);

    // ========== 邮箱验证相关方法 ==========

    /**
     * 通过验证令牌查找用户
     */
    Optional<User> findByVerificationToken(String verificationToken);

    // ========== 注册状态追踪相关方法 ==========

    /**
     * 查找超时未完成注册的用户
     * 状态为 EMAIL_VERIFIED 且 verified_at 早于指定时间
     */
    @Query("SELECT u FROM User u WHERE u.registrationStatus = 'EMAIL_VERIFIED' AND u.verifiedAt < :cutoffTime")
    List<User> findTimeoutRegistrations(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 删除超时未完成注册的用户
     */
    @Modifying
    @Query("DELETE FROM User u WHERE u.registrationStatus = 'EMAIL_VERIFIED' AND u.verifiedAt < :cutoffTime")
    int deleteTimeoutRegistrations(@Param("cutoffTime") LocalDateTime cutoffTime);
}
