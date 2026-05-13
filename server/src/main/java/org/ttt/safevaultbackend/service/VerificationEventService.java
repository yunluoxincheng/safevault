package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.entity.VerificationEvent;
import org.ttt.safevaultbackend.enums.VerificationEventType;
import org.ttt.safevaultbackend.repository.VerificationEventRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 验证事件服务
 * 负责记录和查询验证相关的事件
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationEventService {

    private final VerificationEventRepository eventRepository;

    /**
     * 记录验证事件 (异步执行,不影响主流程)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEventAsync(VerificationEvent event) {
        try {
            eventRepository.save(event);
            log.debug("Verification event recorded: {}", event.getEventType());
        } catch (Exception e) {
            log.error("Failed to record verification event: {}", e.getMessage(), e);
        }
    }

    /**
     * 记录验证事件 (同步执行)
     */
    @Transactional
    public VerificationEvent recordEvent(VerificationEvent event) {
        return eventRepository.save(event);
    }

    /**
     * 记录令牌生成事件
     */
    @Transactional
    public VerificationEvent recordTokenGenerated(String userId, String email, String token, String ipAddress) {
        VerificationEvent event = VerificationEvent.builder()
            .userId(userId)
            .email(email)
            .eventType(VerificationEventType.TOKEN_GENERATED)
            .verificationToken(token)
            .ipAddress(ipAddress)
            .success(true)
            .build();
        return recordEvent(event);
    }

    /**
     * 记录令牌验证成功事件
     */
    @Transactional
    public VerificationEvent recordTokenVerified(String userId, String email, String token, String ipAddress) {
        VerificationEvent event = VerificationEvent.builder()
            .userId(userId)
            .email(email)
            .eventType(VerificationEventType.TOKEN_VERIFIED)
            .verificationToken(token)
            .ipAddress(ipAddress)
            .success(true)
            .build();
        return recordEvent(event);
    }

    /**
     * 记录令牌验证失败事件
     */
    @Transactional
    public VerificationEvent recordTokenInvalid(String userId, String email, String token, String failureReason, String ipAddress) {
        VerificationEvent event = VerificationEvent.builder()
            .userId(userId)
            .email(email)
            .eventType(VerificationEventType.TOKEN_INVALID)
            .verificationToken(token)
            .ipAddress(ipAddress)
            .success(false)
            .failureReason(failureReason)
            .build();
        return recordEvent(event);
    }

    /**
     * 记录邮件发送事件
     */
    @Transactional
    public VerificationEvent recordEmailSent(String userId, String email, String token, boolean success, String failureReason) {
        VerificationEvent event = VerificationEvent.builder()
            .userId(userId)
            .email(email)
            .eventType(success ? VerificationEventType.EMAIL_SENT : VerificationEventType.EMAIL_FAILED)
            .verificationToken(token)
            .success(success)
            .failureReason(failureReason)
            .build();
        return recordEvent(event);
    }

    /**
     * 获取用户的事件列表
     */
    @Transactional(readOnly = true)
    public List<VerificationEvent> getUserEvents(String userId) {
        return eventRepository.findByUserIdOrderByEventTimestampDesc(userId);
    }

    /**
     * 获取指定令牌的事件历史
     */
    @Transactional(readOnly = true)
    public List<VerificationEvent> getTokenEvents(String token) {
        return eventRepository.findByVerificationTokenOrderByEventTimestampDesc(token);
    }

    /**
     * 检查用户在指定时间范围内的事件次数
     */
    @Transactional(readOnly = true)
    public long countUserEventsInPeriod(String userId, LocalDateTime start, LocalDateTime end) {
        return eventRepository.countByUserIdAndTimeRange(userId, start, end);
    }

    /**
     * 检查邮箱在指定时间范围内的事件次数
     */
    @Transactional(readOnly = true)
    public long countEmailEventsInPeriod(String email, VerificationEventType eventType, LocalDateTime since) {
        return eventRepository.countByEmailAndEventTypeAfter(email, eventType, since);
    }

    /**
     * 检查是否超过重试限制
     */
    @Transactional(readOnly = true)
    public boolean exceedsRetryLimit(String email, VerificationEventType eventType, LocalDateTime since, long limit) {
        return eventRepository.exceedsRetryLimit(email, eventType, since, limit);
    }

    /**
     * 清理过期的事件记录 (定时任务使用)
     */
    @Transactional
    public int cleanupExpiredEvents(LocalDateTime cutoffDate) {
        int deleted = eventRepository.deleteExpiredEvents(cutoffDate);
        log.info("Cleaned up {} expired verification events before {}", deleted, cutoffDate);
        return deleted;
    }

    /**
     * 记录注册超时事件
     * 当用户完成注册时发现已超时，记录此事件
     */
    @Transactional
    public VerificationEvent recordRegistrationTimeout(String userId, String email, LocalDateTime verifiedAt, LocalDateTime timeout) {
        VerificationEvent event = VerificationEvent.builder()
            .userId(userId)
            .email(email)
            .eventType(VerificationEventType.REGISTRATION_TIMEOUT)
            .success(false)
            .failureReason("注册超时，验证时间: " + verifiedAt + "，超时时间: " + timeout)
            .build();
        return recordEvent(event);
    }

    /**
     * 记录注册清理事件
     * 定时任务清理超时用户时记录此事件
     */
    @Transactional
    public void recordRegistrationCleanup(String userId, String email, LocalDateTime verifiedAt) {
        VerificationEvent event = VerificationEvent.builder()
            .userId(userId)
            .email(email)
            .eventType(VerificationEventType.REGISTRATION_CLEANUP)
            .success(true)
            .build();
        recordEvent(event);
    }
}
