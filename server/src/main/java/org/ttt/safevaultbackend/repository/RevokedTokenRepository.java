package org.ttt.safevaultbackend.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.ttt.safevaultbackend.entity.RevokedToken;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 已撤销令牌仓库
 */
@Repository
public interface RevokedTokenRepository extends ListCrudRepository<RevokedToken, Long> {

    /**
     * 根据令牌哈希查找撤销记录
     */
    Optional<RevokedToken> findByToken(String token);

    /**
     * 检查令牌是否已被撤销
     */
    boolean existsByToken(String token);

    /**
     * 检查用户和设备的令牌是否已被撤销
     */
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RevokedToken r " +
           "WHERE r.userId = :userId AND r.deviceId = :deviceId AND r.token = :token " +
           "AND r.expiresAt > :now")
    boolean isTokenRevoked(@Param("userId") String userId,
                          @Param("deviceId") String deviceId,
                          @Param("token") String token,
                          @Param("now") LocalDateTime now);

    /**
     * 删除过期的撤销记录（定时清理任务使用）
     */
    @Modifying
    @Query("DELETE FROM RevokedToken r WHERE r.expiresAt < :date")
    int deleteExpiredTokens(@Param("date") LocalDateTime date);

    /**
     * 撤销用户所有设备的令牌（用于密码修改或账户删除）
     */
    @Modifying
    @Query("UPDATE RevokedToken r SET r.revokeReason = :reason WHERE r.userId = :userId")
    int markAllUserTokens(@Param("userId") String userId, @Param("reason") String reason);

    /**
     * 撤销特定设备的令牌
     * 安全加固第三阶段：用于并发登录控制
     */
    @Modifying
    @Query("UPDATE RevokedToken r SET r.revokeReason = :reason WHERE r.userId = :userId AND r.deviceId = :deviceId")
    int markDeviceTokens(@Param("userId") String userId, @Param("deviceId") String deviceId, @Param("reason") String reason);
}
