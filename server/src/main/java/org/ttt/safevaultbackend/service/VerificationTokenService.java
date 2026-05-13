package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ttt.safevaultbackend.entity.User;
import org.ttt.safevaultbackend.exception.BusinessException;
import org.ttt.safevaultbackend.repository.UserRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 验证令牌服务
 * 生成和验证邮箱验证令牌
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationTokenService {

    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${email.verification.token-expiration-minutes:10}")
    private int tokenExpirationMinutes;

    /**
     * 为用户生成验证令牌
     *
     * @param email 用户邮箱
     * @return 验证令牌（Base64 编码）
     */
    public String generateVerificationToken(String email) {
        // 生成 256 位（32 字节）安全随机数
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);

        // Base64 URL 安全编码
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // 计算过期时间
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(tokenExpirationMinutes);

        // 更新用户记录
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));

        user.setVerificationToken(token);
        user.setVerificationExpiresAt(expiresAt);
        userRepository.save(user);

        log.info("Generated verification token for email {}, expires at {}", email, expiresAt);

        return token;
    }

    /**
     * 验证令牌
     *
     * @param token 验证令牌
     * @return 验证通过的用户
     * @throws BusinessException 如果令牌无效或过期
     */
    public User verifyToken(String token) {
        // 查找持有此令牌的用户
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new BusinessException("INVALID_TOKEN", "验证令牌无效"));

        // 检查令牌是否过期
        if (user.getVerificationExpiresAt() == null ||
            user.getVerificationExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("TOKEN_EXPIRED", "验证令牌已过期");
        }

        log.info("Token verification successful for user {}", user.getEmail());

        return user;
    }

    /**
     * 清除用户的验证令牌
     *
     * @param user 用户
     */
    public void clearVerificationToken(User user) {
        user.setVerificationToken(null);
        user.setVerificationExpiresAt(null);
        userRepository.save(user);
    }

    /**
     * 验证令牌并标记用户邮箱已验证
     *
     * @param token 验证令牌
     * @return 验证通过的用户
     * @throws BusinessException 如果令牌无效或过期
     */
    public User verifyEmailAndConfirm(String token) {
        User user = verifyToken(token);

        // 标记邮箱已验证
        user.setEmailVerified(true);
        clearVerificationToken(user);

        log.info("Email verified for user {}", user.getEmail());
        return user;
    }

    /**
     * 检查令牌是否即将过期（用于重发逻辑）
     *
     * @param token 验证令牌
     * @return 是否即将过期（5 分钟内）
     */
    public boolean isTokenExpiringSoon(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElse(null);

        if (user == null || user.getVerificationExpiresAt() == null) {
            return true;
        }

        LocalDateTime fiveMinutesLater = LocalDateTime.now().plusMinutes(5);
        return user.getVerificationExpiresAt().isBefore(fiveMinutesLater);
    }

    /**
     * 获取令牌剩余有效时间（分钟）
     *
     * @param token 验证令牌
     * @return 剩余分钟数，-1 表示令牌无效
     */
    public long getTokenRemainingMinutes(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElse(null);

        if (user == null || user.getVerificationExpiresAt() == null) {
            return -1;
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getVerificationExpiresAt().isBefore(now)) {
            return 0;
        }

        return java.time.Duration.between(now, user.getVerificationExpiresAt()).toMinutes();
    }
}
