package org.ttt.safevaultbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ttt.safevaultbackend.service.EmailVerificationHistoryService;
import org.ttt.safevaultbackend.service.RegistrationCleanupService;
import org.ttt.safevaultbackend.service.VerificationEventService;

import java.time.LocalDateTime;

/**
 * 定时任务配置
 * 执行数据清理和维护任务
 */
@Component
@RequiredArgsConstructor
@EnableScheduling
@Slf4j
public class ScheduledTasks {

    private final VerificationEventService verificationEventService;
    private final EmailVerificationHistoryService historyService;
    private final RegistrationCleanupService registrationCleanupService;

    @Value("${registration.cleanup-scheduled-enabled:true}")
    private boolean cleanupEnabled;

    /**
     * 清理90天前的验证事件
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldVerificationEvents() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
            int deleted = verificationEventService.cleanupExpiredEvents(cutoffDate);
            log.info("定时任务: 清理了 {} 条过期验证事件记录", deleted);
        } catch (Exception e) {
            log.error("定时任务执行失败: 清理验证事件记录", e);
        }
    }

    /**
     * 清理60天前的已使用验证历史
     * 每天凌晨3点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldVerificationHistory() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(60);
            int deleted = historyService.cleanupUsedHistory(cutoffDate);
            log.info("定时任务: 清理了 {} 条已使用验证历史记录", deleted);
        } catch (Exception e) {
            log.error("定时任务执行失败: 清理验证历史记录", e);
        }
    }

    /**
     * 清理超时未完成注册的用户
     * 每5分钟执行一次（可配置）
     */
    @Scheduled(fixedRateString = "${registration.cleanup-scheduled-interval-ms:300000}")
    public void cleanupTimeoutRegistrations() {
        if (!cleanupEnabled) {
            log.debug("注册清理任务已禁用，跳过执行");
            return;
        }

        try {
            int deleted = registrationCleanupService.cleanupTimeoutRegistrations();
            if (deleted > 0) {
                log.info("定时任务: 清理了 {} 个超时未完成注册的用户", deleted);
            }
        } catch (Exception e) {
            log.error("定时任务执行失败: 清理超时注册用户", e);
        }
    }
}
