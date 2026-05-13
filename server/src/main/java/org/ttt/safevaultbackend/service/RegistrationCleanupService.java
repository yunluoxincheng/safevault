package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.entity.User;
import org.ttt.safevaultbackend.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 注册清理服务
 * 负责清理超时未完成注册的用户
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationCleanupService {

    private final UserRepository userRepository;
    private final VerificationEventService verificationEventService;

    @Value("${registration.cleanup-timeout-minutes:5}")
    private int timeoutMinutes;

    /**
     * 清理超时未完成注册的用户
     * 删除状态为 EMAIL_VERIFIED 且 verified_at 早于指定时间的用户
     *
     * @return 删除的用户数量
     */
    @Transactional
    public int cleanupTimeoutRegistrations() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(timeoutMinutes);

        log.info("开始清理超时未完成注册的用户，超时时间: {} 分钟，截止时间: {}", timeoutMinutes, cutoffTime);

        try {
            // 先查询超时用户（用于日志记录）
            List<User> timeoutUsers = userRepository.findTimeoutRegistrations(cutoffTime);

            if (timeoutUsers.isEmpty()) {
                log.info("没有需要清理的超时用户");
                return 0;
            }

            log.info("找到 {} 个超时用户: {}", timeoutUsers.size(),
                timeoutUsers.stream().map(u -> u.getEmail() + "(" + u.getUserId() + ")").toList());

            // 记录清理事件到 verification_events 表
            for (User user : timeoutUsers) {
                verificationEventService.recordRegistrationCleanup(
                    user.getUserId(),
                    user.getEmail(),
                    user.getVerifiedAt()
                );
            }

            // 执行删除操作
            int deleted = userRepository.deleteTimeoutRegistrations(cutoffTime);

            log.info("定时任务: 清理了 {} 个超时未完成注册的用户", deleted);
            return deleted;

        } catch (Exception e) {
            log.error("清理超时用户失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 获取当前超时配置（分钟）
     */
    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }
}
