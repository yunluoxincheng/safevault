package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.entity.EmailVerificationHistory;
import org.ttt.safevaultbackend.enums.DeliveryStatus;
import org.ttt.safevaultbackend.enums.VerificationRequestType;
import org.ttt.safevaultbackend.repository.EmailVerificationHistoryRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 邮箱验证历史服务
 * 负责追踪验证码/令牌的完整生命周期
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationHistoryService {

    private final EmailVerificationHistoryRepository historyRepository;

    /**
     * 创建验证历史记录
     */
    @Transactional
    public EmailVerificationHistory createHistory(EmailVerificationHistory history) {
        return historyRepository.save(history);
    }

    /**
     * 异步创建验证历史记录 (不影响主流程)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createHistoryAsync(EmailVerificationHistory history) {
        try {
            historyRepository.save(history);
            log.debug("Verification history created for email: {}", history.getEmail());
        } catch (Exception e) {
            log.error("Failed to create verification history: {}", e.getMessage(), e);
        }
    }

    /**
     * 记录注册验证历史
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRegistrationHistory(String userId, String email, String token, String ipAddress, LocalDateTime expiresAt, String provider) {
        EmailVerificationHistory history = EmailVerificationHistory.builder()
            .userId(userId)
            .email(email)
            .verificationToken(token)
            .requestType(VerificationRequestType.REGISTRATION)
            .ipAddress(ipAddress)
            .expiresAt(expiresAt)
            .deliveryStatus(DeliveryStatus.SENT)
            .provider(provider)
            .build();
        createHistoryAsync(history);
    }

    /**
     * 根据验证令牌查询有效记录
     */
    @Transactional(readOnly = true)
    public Optional<EmailVerificationHistory> findValidByToken(String token) {
        return historyRepository.findUnusedByTokenAndExpiresAfter(token, LocalDateTime.now());
    }

    /**
     * 标记验证记录为已使用
     */
    @Transactional
    public int markAsUsed(String token) {
        return historyRepository.markAsUsed(null, token, LocalDateTime.now());
    }

    /**
     * 获取用户的验证历史
     */
    @Transactional(readOnly = true)
    public List<EmailVerificationHistory> getUserHistory(String userId) {
        return historyRepository.findByUserIdOrderBySentAtDesc(userId);
    }

    /**
     * 获取邮箱的验证历史
     */
    @Transactional(readOnly = true)
    public List<EmailVerificationHistory> getEmailHistory(String email) {
        return historyRepository.findByEmailOrderBySentAtDesc(email);
    }

    /**
     * 检查邮箱发送频率限制
     */
    @Transactional(readOnly = true)
    public boolean exceedsSendLimit(String email, LocalDateTime since, long limit) {
        return historyRepository.exceedsSendLimit(email, since, limit);
    }

    /**
     * 统计指定时间范围内发送的验证次数
     */
    @Transactional(readOnly = true)
    public long countSentAfter(String email, LocalDateTime since) {
        return historyRepository.countByEmailAfter(email, since);
    }

    /**
     * 获取最近的验证记录
     */
    @Transactional(readOnly = true)
    public Optional<EmailVerificationHistory> getMostRecent(String email) {
        return historyRepository.findMostRecentByEmail(email);
    }

    /**
     * 清理已使用的历史记录 (定时任务使用)
     */
    @Transactional
    public int cleanupUsedHistory(LocalDateTime cutoffDate) {
        int deleted = historyRepository.deleteUsedHistoryBefore(cutoffDate);
        log.info("Cleaned up {} used verification history before {}", deleted, cutoffDate);
        return deleted;
    }
}
