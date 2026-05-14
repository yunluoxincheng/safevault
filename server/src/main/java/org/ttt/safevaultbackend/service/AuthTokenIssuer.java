package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.entity.RefreshTokenRecord;
import org.ttt.safevaultbackend.repository.RefreshTokenRecordRepository;
import org.ttt.safevaultbackend.security.JwtTokenProvider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthTokenIssuer {

    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenRecordRepository refreshTokenRecordRepository;

    /**
     * 签发初始 token 对（登录/注册时）
     * 创建新的 token family
     */
    @Transactional
    public IssuedTokens issueTokens(String userId) {
        String accessToken = tokenProvider.generateAccessToken(userId);

        String jti = tokenProvider.generateJti();
        String family = jti; // 首次签发时 family = jti
        String refreshToken = tokenProvider.generateRefreshToken(userId, jti, family);

        // 持久化 refresh token 记录
        saveRefreshTokenRecord(jti, family, userId);

        return new IssuedTokens(accessToken, refreshToken, tokenProvider.getAccessTokenExpirationSeconds());
    }

    /**
     * 轮换 refresh token：旧 token 标记 rotated，签发新 token（同 family）
     *
     * @return 新的 token 对，如果检测到重用返回 null
     */
    @Transactional
    public RotationResult rotateRefreshToken(String oldRefreshToken, String userId) {
        String oldJti = tokenProvider.getJtiFromToken(oldRefreshToken);
        String family = tokenProvider.getFamilyFromToken(oldRefreshToken);

        if (oldJti == null || family == null) {
            return new RotationResult(null, null, 0, "INVALID_TOKEN");
        }

        // 查找旧 token 记录
        RefreshTokenRecord oldRecord = refreshTokenRecordRepository.findByJti(oldJti).orElse(null);

        if (oldRecord == null) {
            return new RotationResult(null, null, 0, "TOKEN_NOT_FOUND");
        }

        // 重用检测：如果 token 已被轮换过（rotated=true），说明被重用了
        if (oldRecord.getRotated()) {
            // 撤销整个 family
            int revoked = refreshTokenRecordRepository.revokeFamily(family);
            return new RotationResult(null, null, revoked, "REUSE_DETECTED");
        }

        // 如果已经被撤销
        if (oldRecord.getRevoked()) {
            return new RotationResult(null, null, 0, "REVOKED");
        }

        // 标记旧 token 为已轮换
        refreshTokenRecordRepository.markRotated(oldJti);

        // 签发新 token（同 family，新 jti）
        String newJti = tokenProvider.generateJti();
        String accessToken = tokenProvider.generateAccessToken(userId);
        String newRefreshToken = tokenProvider.generateRefreshToken(userId, newJti, family);

        // 持久化新 refresh token 记录
        saveRefreshTokenRecord(newJti, family, userId);

        return new RotationResult(
                new IssuedTokens(accessToken, newRefreshToken, tokenProvider.getAccessTokenExpirationSeconds()),
                null,
                0,
                "SUCCESS"
        );
    }

    /**
     * 撤销指定 family 下所有 refresh token
     */
    @Transactional
    public int revokeFamily(String family) {
        return refreshTokenRecordRepository.revokeFamily(family);
    }

    private void saveRefreshTokenRecord(String jti, String family, String userId) {
        Date expiryDate = new Date(System.currentTimeMillis()
                + tokenProvider.getRefreshTokenExpirationSeconds() * 1000);
        LocalDateTime expiresAt = expiryDate.toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDateTime();

        RefreshTokenRecord record = RefreshTokenRecord.builder()
                .jti(jti)
                .family(family)
                .userId(userId)
                .expiresAt(expiresAt)
                .build();

        refreshTokenRecordRepository.save(record);
    }

    public record IssuedTokens(String accessToken, String refreshToken, long expiresInSeconds) {}
    public record RotationResult(IssuedTokens tokens, String error, int revokedCount, String status) {}
}
