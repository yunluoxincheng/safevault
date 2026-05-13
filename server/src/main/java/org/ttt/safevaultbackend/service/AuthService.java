package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.dto.request.*;
import org.ttt.safevaultbackend.dto.response.AuthResponse;
import org.ttt.safevaultbackend.dto.response.CompleteRegistrationResponse;
import org.ttt.safevaultbackend.dto.response.EmailLoginResponse;
import org.ttt.safevaultbackend.dto.response.EmailRegistrationResponse;
import org.ttt.safevaultbackend.dto.response.LoginPrecheckResponse;
import org.ttt.safevaultbackend.dto.response.LogoutResponse;
import org.ttt.safevaultbackend.dto.response.VerifyEmailResponse;
import org.ttt.safevaultbackend.dto.response.VerificationStatusResponse;
import org.ttt.safevaultbackend.dto.DeviceInfo;
import org.ttt.safevaultbackend.dto.PendingUser;
import org.ttt.safevaultbackend.entity.User;
import org.ttt.safevaultbackend.entity.UserPrivateKey;
import org.ttt.safevaultbackend.exception.BusinessException;
import org.ttt.safevaultbackend.exception.ResourceNotFoundException;
import org.ttt.safevaultbackend.repository.UserRepository;
import org.ttt.safevaultbackend.repository.UserPrivateKeyRepository;
import org.ttt.safevaultbackend.security.Argon2PasswordHasher;
import org.ttt.safevaultbackend.security.JwtTokenProvider;
import org.ttt.safevaultbackend.service.EmailService;
import org.ttt.safevaultbackend.service.PendingUserService;
import org.ttt.safevaultbackend.service.VerificationTokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 认证服务
 * 支持旧的设备 ID 认证和新的邮箱认证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserPrivateKeyRepository userPrivateKeyRepository;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;
    private final VerificationTokenService verificationTokenService;
    private final PendingUserService pendingUserService;
    private final CryptoService cryptoService;
    private final TokenRevokeService tokenRevokeService;
    private final VerificationEventService verificationEventService;
    private final EmailVerificationHistoryService verificationHistoryService;
    private final Argon2PasswordHasher argon2PasswordHasher;
    private final NonceService nonceService;
    private final AuthTokenIssuer authTokenIssuer;
    private final EmailVerificationLinkFactory emailVerificationLinkFactory;

    @Value("${email.verification.token-expiration-minutes:10}")
    private int tokenExpirationMinutes;

    @Value("${registration.cleanup-timeout-minutes:5}")
    private int registrationTimeoutMinutes;

    @Value("${app.base-url:http://localhost:8080/api}")
    private String baseUrl;

    /**
     * 用户注册
     *
     * 支持协议版本 2.0 (RSA) 和 3.0 (X25519/Ed25519)
     * 新用户默认生成所有类型的密钥
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("USER_ALREADY_EXISTS", "用户名已存在");
        }

        // 检查设备ID是否已存在
        if (userRepository.existsByDeviceId(request.getDeviceId())) {
            throw new BusinessException("DEVICE_ALREADY_EXISTS", "设备ID已存在");
        }

        // 创建新用户
        User.UserBuilder userBuilder = User.builder()
                .userId(UUID.randomUUID().toString())
                .deviceId(request.getDeviceId())
                .username(request.getUsername())
                .displayName(request.getDisplayName())
                .publicKey(request.getPublicKey());

        // 添加 X25519/Ed25519 公钥（如果提供）
        if (request.getX25519PublicKey() != null && !request.getX25519PublicKey().isBlank()) {
            userBuilder.x25519PublicKey(request.getX25519PublicKey());
        }
        if (request.getEd25519PublicKey() != null && !request.getEd25519PublicKey().isBlank()) {
            userBuilder.ed25519PublicKey(request.getEd25519PublicKey());
        }

        // 设置密钥版本
        if (request.getKeyVersion() != null && !request.getKeyVersion().isBlank()) {
            userBuilder.keyVersion(request.getKeyVersion());
        } else {
            // 默认为 v1 (仅 RSA)
            userBuilder.keyVersion("v1");
        }

        User user = userBuilder.build();
        user = userRepository.save(user);

        log.info("新用户注册: userId={}, username={}, keyVersion={}, hasX25519={}, hasEd25519={}",
                user.getUserId(), user.getUsername(), user.getKeyVersion(),
                user.getX25519PublicKey() != null, user.getEd25519PublicKey() != null);

        // 生成 Token
        AuthTokenIssuer.IssuedTokens tokens = authTokenIssuer.issueTokens(user.getUserId());

        return AuthResponse.builder()
                .userId(user.getUserId())
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .expiresIn(tokens.expiresInSeconds())
                .build();
    }

    /**
     * 用户登录
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // 查找用户
        User user = userRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", request.getUserId()));

        // 验证设备ID
        if (!user.getDeviceId().equals(request.getDeviceId())) {
            throw new BusinessException("INVALID_DEVICE", "设备ID不匹配");
        }

        // 验证签名（验证时间戳签名，防止重放攻击）
        verifyLoginSignature(request.getUserId(), request.getDeviceId(), request.getSignature(), request.getTimestamp());

        // 生成 Token
        AuthTokenIssuer.IssuedTokens tokens = authTokenIssuer.issueTokens(user.getUserId());

        return AuthResponse.builder()
                .userId(user.getUserId())
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .expiresIn(tokens.expiresInSeconds())
                .build();
    }

    /**
     * 通过用户名登录
     */
    @Transactional(readOnly = true)
    public AuthResponse loginByUsername(LoginByUsernameRequest request) {
        // 通过用户名查找用户
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", request.getUsername()));

        // 验证签名（验证用户名+设备ID的时间戳签名，防止重放攻击）
        verifyUsernameLoginSignature(request.getUsername(), user.getDeviceId(), request.getSignature(), request.getTimestamp());

        // 生成 Token
        AuthTokenIssuer.IssuedTokens tokens = authTokenIssuer.issueTokens(user.getUserId());

        return AuthResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .expiresIn(tokens.expiresInSeconds())
                .build();
    }

    /**
     * 刷新令牌
     */
    public AuthResponse refreshToken(String refreshToken) {
        // 验证刷新令牌
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "无效的刷新令牌");
        }

        // 获取用户ID
        String userId = tokenProvider.getUserIdFromToken(refreshToken);

        // 验证用户是否存在
        if (!userRepository.existsByUserId(userId)) {
            throw new ResourceNotFoundException("User", "userId", userId);
        }

        // 生成新的访问令牌
        AuthTokenIssuer.IssuedTokens tokens = authTokenIssuer.issueTokens(userId);

        return AuthResponse.builder()
                .userId(userId)
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .expiresIn(tokens.expiresInSeconds())
                .build();
    }

    /**
     * 验证令牌
     */
    public boolean validateToken(String token) {
        return tokenProvider.validateToken(token);
    }

    // ========== 统一邮箱认证方法 ==========

    /**
     * 邮箱注册第一步：发起注册并发送验证邮件
     * 数据先存入 Redis，验证成功后才写入数据库
     *
     * @param request 邮箱和用户名
     * @return 注册响应
     */
    public EmailRegistrationResponse registerWithEmail(EmailRegistrationRequest request) {
        // 检查邮箱是否已在数据库中注册
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "该邮箱已被注册");
        }

        // 检查用户名是否已被使用
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("USERNAME_ALREADY_EXISTS", "该用户名已被使用");
        }

        // 检查邮箱是否已有待验证记录
        PendingUser existingPendingUser = pendingUserService.getPendingUserByEmail(request.getEmail());
        if (existingPendingUser != null) {
            // 检查是否过期
            if (existingPendingUser.isTokenExpired()) {
                // 过期：自动清理并继续注册流程
                log.info("待验证记录已过期，自动清理: email={}", request.getEmail());
                pendingUserService.deletePendingUser(request.getEmail());
            } else {
                // 未过期：检查用户名是否一致
                if (!existingPendingUser.getUsername().equals(request.getUsername())) {
                    throw new BusinessException("USERNAME_MISMATCH",
                            "该邮箱已有待验证的注册申请（用户名: " + existingPendingUser.getUsername() + "），请使用相同用户名或等待过期");
                }

                // 检查是否可以重发（60秒冷却）
                if (!existingPendingUser.canResendEmail()) {
                    long waitSeconds = 60 - java.time.Duration.between(
                            existingPendingUser.getLastEmailSentAt(),
                            java.time.LocalDateTime.now()
                    ).getSeconds();
                    throw new BusinessException("RESEND_TOO_SOON",
                            "验证邮件已发送，请查收。如需重发，请等待 " + waitSeconds + " 秒后重试");
                }

                // 自动重发验证邮件
                log.info("检测到已有待验证记录，自动重发邮件: email={}", request.getEmail());
                return resendVerificationEmail(new ResendVerificationRequest(request.getEmail()));
            }
        }

        // 生成验证令牌
        String token = pendingUserService.generateVerificationToken();

        // 计算过期时间
        LocalDateTime tokenExpiresAt = LocalDateTime.now().plusMinutes(tokenExpirationMinutes);

        // 创建待验证用户（存入 Redis）
        PendingUser pendingUser = PendingUser.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .displayName(request.getUsername())
                .verificationToken(token)
                .tokenExpiresAt(tokenExpiresAt)
                .createdAt(LocalDateTime.now())
                .lastEmailSentAt(LocalDateTime.now())
                .build();

        // 保存到 Redis
        pendingUserService.savePendingUser(pendingUser);

        // 记录令牌生成事件
        verificationEventService.recordTokenGenerated(null, request.getEmail(), token, null);

        // 记录验证历史（异步）
        verificationHistoryService.recordRegistrationHistory(
            null, request.getEmail(), token, null, tokenExpiresAt, "aliyun"
        );

        // 构建验证链接（Deep Link）
        String verificationUrl = emailVerificationLinkFactory.buildRegistrationVerificationUrl(token);

        // 发送验证邮件
        boolean emailSent = emailService.sendVerificationEmail(request.getEmail(), verificationUrl);

        // 记录邮件发送事件
        verificationEventService.recordEmailSent(null, request.getEmail(), token, emailSent, emailSent ? null : "邮件发送失败");

        log.info("发起邮箱注册: email={}, username={}, token={}", request.getEmail(), request.getUsername(), token);

        return EmailRegistrationResponse.builder()
                .message("注册成功，请查收验证邮件")
                .email(request.getEmail())
                .emailSent(emailSent)
                .expiresInSeconds((long) tokenExpirationMinutes * 60)
                .build();
    }

    /**
     * 验证邮箱
     * 从 Redis 获取待验证用户，验证成功后写入数据库
     *
     * @param request 验证令牌
     * @return 验证响应
     */
    @Transactional
    public VerifyEmailResponse verifyEmail(VerifyEmailRequest request) {
        try {
            log.info("收到邮箱验证请求: token={}", request.getToken());

            // 从 Redis 获取待验证用户
            PendingUser pendingUser = pendingUserService.getPendingUserByToken(request.getToken());

            if (pendingUser == null) {
                log.warn("验证失败: 未找到待验证用户, token={}", request.getToken());
                return VerifyEmailResponse.builder()
                        .success(false)
                        .message("验证令牌无效或已过期")
                        .build();
            }

            log.info("找到待验证用户: email={}, username={}, tokenExpiresAt={}, createdAt={}",
                    pendingUser.getEmail(),
                    pendingUser.getUsername(),
                    pendingUser.getTokenExpiresAt(),
                    pendingUser.getCreatedAt());

            // 检查令牌是否过期
            if (pendingUser.isTokenExpired()) {
                log.warn("验证失败: 令牌已过期, email={}, tokenExpiresAt={}, now={}",
                        pendingUser.getEmail(),
                        pendingUser.getTokenExpiresAt(),
                        LocalDateTime.now());
                // 清理过期的待验证用户
                pendingUserService.deletePendingUser(pendingUser.getEmail());
                return VerifyEmailResponse.builder()
                        .success(false)
                        .message("验证令牌已过期，请重新注册")
                        .build();
            }

            // 再次检查数据库中是否已存在该邮箱（防止并发注册）
            if (userRepository.existsByEmail(pendingUser.getEmail())) {
                log.warn("验证失败: 该邮箱已被注册, email={}", pendingUser.getEmail());
                // 清理 Redis 中的数据
                pendingUserService.deletePendingUser(pendingUser.getEmail());
                return VerifyEmailResponse.builder()
                        .success(false)
                        .message("该邮箱已被注册")
                        .build();
            }

            // 再次检查用户名是否已存在
            if (userRepository.existsByUsername(pendingUser.getUsername())) {
                log.warn("验证失败: 该用户名已被使用, username={}", pendingUser.getUsername());
                // 清理 Redis 中的数据
                pendingUserService.deletePendingUser(pendingUser.getEmail());
                return VerifyEmailResponse.builder()
                        .success(false)
                        .message("该用户名已被使用")
                        .build();
            }

            // 创建用户记录（邮箱已验证状态）
            User user = User.builder()
                    .userId(UUID.randomUUID().toString())
                    .email(pendingUser.getEmail())
                    .username(pendingUser.getUsername())
                    .displayName(pendingUser.getDisplayName())
                    .emailVerified(true)
                    .registrationStatus("EMAIL_VERIFIED")
                    .verifiedAt(LocalDateTime.now())
                    .build();

            user = userRepository.save(user);

            // 清除 Redis 中的待验证用户
            pendingUserService.deletePendingUser(pendingUser.getEmail());

            // 记录验证成功事件
            verificationEventService.recordTokenVerified(user.getUserId(), pendingUser.getEmail(), request.getToken(), null);

            // 标记验证历史为已使用
            verificationHistoryService.markAsUsed(request.getToken());

            log.info("邮箱验证成功，创建用户: email={}, username={}, userId={}",
                    user.getEmail(), user.getUsername(), user.getUserId());

            return VerifyEmailResponse.builder()
                    .success(true)
                    .message("邮箱验证成功，请设置主密码")
                    .email(user.getEmail())
                    .username(user.getUsername())
                    .build();

        } catch (Exception e) {
            log.error("邮箱验证失败: token={}", request.getToken(), e);
            return VerifyEmailResponse.builder()
                    .success(false)
                    .message("验证失败，请重试")
                    .build();
        }
    }

    /**
     * 重新发送验证邮件
     * 更新 Redis 中的待验证用户记录
     *
     * @param request 邮箱
     * @return 注册响应
     */
    public EmailRegistrationResponse resendVerificationEmail(ResendVerificationRequest request) {
        // 从 Redis 获取待验证用户
        PendingUser pendingUser = pendingUserService.getPendingUserByEmail(request.getEmail());

        if (pendingUser == null) {
            throw new BusinessException("PENDING_USER_NOT_FOUND", "该邮箱没有待验证的注册申请，请先注册");
        }

        // 检查令牌是否过期
        if (pendingUser.isTokenExpired()) {
            log.info("重发验证邮件时发现令牌已过期，自动更新: email={}", request.getEmail());
            // 令牌已过期，直接更新，不检查频率限制
        } else {
            // 频率限制：60秒内只能请求一次
            if (!pendingUser.canResendEmail()) {
                long waitSeconds = 60 - java.time.Duration.between(
                        pendingUser.getLastEmailSentAt(),
                        java.time.LocalDateTime.now()
                ).getSeconds();
                throw new BusinessException("RESEND_TOO_SOON", "请等待 " + waitSeconds + " 秒后再试");
            }
        }

        // 生成新的验证令牌
        String newToken = pendingUserService.generateVerificationToken();
        LocalDateTime tokenExpiresAt = LocalDateTime.now().plusMinutes(tokenExpirationMinutes);

        // 更新 Redis 中的待验证用户
        pendingUserService.updateVerificationToken(request.getEmail(), newToken, tokenExpiresAt);

        // 记录新令牌生成事件
        verificationEventService.recordTokenGenerated(null, request.getEmail(), newToken, null);

        // 记录重发验证历史
        verificationHistoryService.recordRegistrationHistory(
            null, request.getEmail(), newToken, null, tokenExpiresAt, "aliyun"
        );

        // 构建验证链接
        String verificationUrl = emailVerificationLinkFactory.buildRegistrationVerificationUrl(newToken);

        // 发送验证邮件
        boolean emailSent = emailService.sendVerificationEmail(request.getEmail(), verificationUrl);

        // 记录邮件发送事件
        verificationEventService.recordEmailSent(null, request.getEmail(), newToken, emailSent, emailSent ? null : "邮件发送失败");

        log.info("重新发送验证邮件: email={}, newToken={}", request.getEmail(), newToken);

        return EmailRegistrationResponse.builder()
                .message("验证邮件已重新发送")
                .email(request.getEmail())
                .emailSent(emailSent)
                .expiresInSeconds((long) tokenExpirationMinutes * 60)
                .build();
    }

    /**
     * 检查邮箱验证状态
     * 用于前端轮询检查用户是否已在 Web 页面完成验证
     *
     * @param email 邮箱地址
     * @return 验证状态响应
     */
    @Transactional(readOnly = true)
    public VerificationStatusResponse checkVerificationStatus(String email) {
        // 首先检查数据库中是否已有该邮箱的用户（表示已验证）
        if (userRepository.existsByEmail(email)) {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null && user.getEmailVerified()) {
                return VerificationStatusResponse.builder()
                        .status("VERIFIED")
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .build();
            }
        }

        // 检查 Redis 中是否有待验证用户
        PendingUser pendingUser = pendingUserService.getPendingUserByEmail(email);
        if (pendingUser != null) {
            // 检查是否过期
            if (pendingUser.isTokenExpired()) {
                // 过期，清理并返回 NOT_FOUND
                pendingUserService.deletePendingUser(email);
                return VerificationStatusResponse.builder()
                        .status("NOT_FOUND")
                        .email(email)
                        .build();
            }

            // 待验证
            return VerificationStatusResponse.builder()
                    .status("PENDING")
                    .email(pendingUser.getEmail())
                    .username(pendingUser.getUsername())
                    .tokenExpiresAt(pendingUser.getTokenExpiresAt().toString())
                    .build();
        }

        // 无记录
        return VerificationStatusResponse.builder()
                .status("NOT_FOUND")
                .email(email)
                .build();
    }

    // ========== 统一邮箱认证方法 ==========

    /**
     * 登录预检查（Challenge-Response 机制第一步）
     * 生成并返回服务器挑战码（nonce）
     *
     * @param request 登录预检查请求
     * @return 登录预检查响应，包含 nonce 和过期时间
     */
    @Transactional(readOnly = true)
    public LoginPrecheckResponse loginPrecheck(LoginPrecheckRequest request) {
        // 检查用户是否存在
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        // 检查邮箱是否已验证
        if (!user.getEmailVerified()) {
            throw new BusinessException("EMAIL_NOT_VERIFIED", "请先验证邮箱后再登录");
        }

        // 生成 nonce
        String nonce = nonceService.generateNonce(request.getEmail());

        // 计算过期时间
        long expiresAt = System.currentTimeMillis() / 1000 + 30; // 30 秒后过期

        // 检查派生密钥是否存在
        if (user.getPasswordVerifier() == null || user.getPasswordVerifier().isEmpty()) {
            throw new BusinessException("PASSWORD_VERIFIER_MISSING",
                "用户派生密钥未配置，请先完成注册");
        }

        log.info("登录预检查成功: email={}, nonce={}, userId={}",
                request.getEmail(), nonce, user.getUserId());

        return LoginPrecheckResponse.builder()
                .nonce(nonce)
                .expiresAt(expiresAt)
                .userId(user.getUserId())
                .passwordVerifier(user.getPasswordVerifier())
                .build();
    }

    /**
     * 邮箱登录（支持设备管理和并发控制）
     * 安全加固第三阶段：使用 Challenge-Response 机制防止重放攻击
     *
     * @param request 邮箱登录请求
     * @return 邮箱登录响应
     */
    @Transactional
    public EmailLoginResponse loginByEmail(LoginByEmailRequest request) {
        // 查找用户
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        // 检查邮箱是否已验证
        if (!user.getEmailVerified()) {
            throw new BusinessException("EMAIL_NOT_VERIFIED", "请先验证邮箱后再登录");
        }

        // 验证派生密钥签名（使用 nonce，防止重放攻击）
        verifyDerivedKeySignature(request.getEmail(), request.getDeviceId(),
                request.getDerivedKeySignature(), request.getNonce());

        // 获取当前设备列表
        List<DeviceInfo> devices = getDevicesList(user);

        // 检查是否为新设备
        boolean isNewDevice = devices.stream()
                .noneMatch(d -> d.getDeviceId().equals(request.getDeviceId()));

        if (isNewDevice) {
            // 新设备：检查设备数量限制
            int maxDevices = user.getMaxDevices() != null ? user.getMaxDevices() : 5;

            if (devices.size() >= maxDevices) {
                // 已达到上限，撤销最久未使用的设备
                DeviceInfo oldestDevice = devices.stream()
                        .min((d1, d2) -> {
                            LocalDateTime t1 = d1.getLastActiveAt() != null ? d1.getLastActiveAt() : d1.getCreatedAt();
                            LocalDateTime t2 = d2.getLastActiveAt() != null ? d2.getLastActiveAt() : d2.getCreatedAt();
                            return t1.compareTo(t2);
                        })
                        .orElseThrow();

                // 撤销最旧设备的Token
                try {
                    tokenRevokeService.revokeDevice(
                        oldestDevice.getDeviceId(),
                        user.getUserId()
                    );
                    log.info("达到设备数量上限，撤销最旧设备: userId={}, deviceId={}, oldestDeviceId={}",
                        user.getUserId(), request.getDeviceId(), oldestDevice.getDeviceId());
                } catch (Exception e) {
                    log.warn("撤销最旧设备失败，继续添加新设备: userId={}, oldestDeviceId={}",
                        user.getUserId(), oldestDevice.getDeviceId(), e);
                }

                // 从列表中移除
                devices.removeIf(d -> d.getDeviceId().equals(oldestDevice.getDeviceId()));
            }

            // 添加新设备
            DeviceInfo newDevice = DeviceInfo.builder()
                    .deviceId(request.getDeviceId())
                    .deviceName(request.getDeviceName())
                    .deviceType(request.getDeviceType() != null ? request.getDeviceType() : "unknown")
                    .osVersion(request.getOsVersion())
                    .createdAt(LocalDateTime.now())
                    .lastActiveAt(LocalDateTime.now())
                    .isCurrentDevice(true)
                    .build();

            devices.add(newDevice);

            // 更新用户记录
            user.setDevices(DeviceInfo.toJson(devices));
            userRepository.save(user);

            // 生成 Token
            AuthTokenIssuer.IssuedTokens tokens = authTokenIssuer.issueTokens(user.getUserId());

            return EmailLoginResponse.builder()
                    .userId(user.getUserId())
                    .email(user.getEmail())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .accessToken(tokens.accessToken())
                    .refreshToken(tokens.refreshToken())
                    .expiresIn(tokens.expiresInSeconds())
                    .emailVerified(user.getEmailVerified())
                    .devices(devices)
                    .isNewDevice(true)
                    .message("新设备登录成功")
                    .maxDevices(maxDevices)
                    .build();

        } else {
            // 已注册设备：更新最后活跃时间
            devices = devices.stream()
                    .map(d -> {
                        if (d.getDeviceId().equals(request.getDeviceId())) {
                            d.setLastActiveAt(LocalDateTime.now());
                            d.setCurrentDevice(true);
                        } else {
                            d.setCurrentDevice(false);
                        }
                        return d;
                    })
                    .collect(Collectors.toList());

            // 更新用户记录
            user.setDevices(DeviceInfo.toJson(devices));
            userRepository.save(user);

            // 生成 Token
            AuthTokenIssuer.IssuedTokens tokens = authTokenIssuer.issueTokens(user.getUserId());

            return EmailLoginResponse.builder()
                    .userId(user.getUserId())
                    .email(user.getEmail())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .accessToken(tokens.accessToken())
                    .refreshToken(tokens.refreshToken())
                    .expiresIn(tokens.expiresInSeconds())
                    .emailVerified(user.getEmailVerified())
                    .devices(devices)
                    .isNewDevice(false)
                    .maxDevices(user.getMaxDevices() != null ? user.getMaxDevices() : 5)
                    .build();
        }
    }

    /**
     * 获取用户的设备列表
     *
     * @param user 用户
     * @return 设备列表
     */
    private List<DeviceInfo> getDevicesList(User user) {
        if (user.getDevices() == null || user.getDevices().isEmpty()) {
            return new ArrayList<>();
        }
        return DeviceInfo.fromJson(user.getDevices());
    }

    // ========== 签名验证方法 ==========

    /**
     * 验证设备ID登录的签名
     * 签名格式：Base64(SHA256(userId + deviceId + timestamp))
     *
     * @param userId    用户ID
     * @param deviceId  设备ID
     * @param signature Base64编码的签名
     * @param timestamp 时间戳（毫秒）
     * @throws BusinessException 验证失败时抛出异常
     */
    private void verifyLoginSignature(String userId, String deviceId, String signature, Long timestamp) {
        // 时间戳验证：允许5分钟的时间窗口，防止重放攻击
        long currentTime = System.currentTimeMillis();
        long timeDiff = timestamp != null ? Math.abs(currentTime - timestamp) : Long.MAX_VALUE;

        // 允许5分钟（300000毫秒）的时间差
        if (timeDiff > 300000) {
            throw new BusinessException("INVALID_TIMESTAMP", "请求时间戳无效或已过期");
        }

        // 如果提供了签名，进行签名验证
        if (signature != null && !signature.isEmpty()) {
            String expectedSignature = computeHashSignature(userId + deviceId + (timestamp != null ? timestamp : currentTime));

            if (!expectedSignature.equals(signature)) {
                throw new BusinessException("INVALID_SIGNATURE", "签名验证失败");
            }
        }
        // 如果未提供签名，允许通过（向后兼容）
    }

    /**
     * 验证用户名登录的签名
     * 签名格式：Base64(SHA256(username + deviceId + timestamp))
     *
     * @param username  用户名
     * @param deviceId  设备ID
     * @param signature Base64编码的签名
     * @param timestamp 时间戳（毫秒）
     * @throws BusinessException 验证失败时抛出异常
     */
    private void verifyUsernameLoginSignature(String username, String deviceId, String signature, Long timestamp) {
        // 时间戳验证：允许5分钟的时间窗口，防止重放攻击
        long currentTime = System.currentTimeMillis();
        long timeDiff = timestamp != null ? Math.abs(currentTime - timestamp) : Long.MAX_VALUE;

        // 允许5分钟（300000毫秒）的时间差
        if (timeDiff > 300000) {
            throw new BusinessException("INVALID_TIMESTAMP", "请求时间戳无效或已过期");
        }

        // 如果提供了签名，进行签名验证
        if (signature != null && !signature.isEmpty()) {
            String expectedSignature = computeHashSignature(username + deviceId + (timestamp != null ? timestamp : currentTime));

            if (!expectedSignature.equals(signature)) {
                throw new BusinessException("INVALID_SIGNATURE", "签名验证失败");
            }
        }
        // 如果未提供签名，允许通过（向后兼容）
    }

    /**
     * 验证邮箱登录的派生密钥签名（Challenge-Response 机制）
     * 签名格式：Base64(HMAC-SHA256(email + deviceId + nonce, derivedKey))
     *
     * 安全加固第三阶段：使用服务器生成的 nonce 防止重放攻击
     *
     * @param email    邮箱
     * @param deviceId 设备ID
     * @param signature Base64编码的HMAC签名
     * @param nonce    服务器生成的挑战码
     * @throws BusinessException 验证失败时抛出异常
     */
    private void verifyDerivedKeySignature(String email, String deviceId, String signature, String nonce) {
        if (signature == null || signature.isEmpty()) {
            throw new BusinessException("INVALID_SIGNATURE", "派生密钥签名不能为空");
        }

        if (nonce == null || nonce.isEmpty()) {
            throw new BusinessException("INVALID_NONCE", "挑战码不能为空");
        }

        // 验证并消费 nonce（一次性使用）
        if (!nonceService.validateAndConsumeNonce(nonce, email)) {
            log.warn("Nonce 验证失败: email={}, nonce={}", email, nonce);
            throw new BusinessException("INVALID_NONCE", "挑战码无效或已过期");
        }

        // 获取用户以验证签名
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        // 使用派生密钥验证器作为HMAC密钥
        // 注意：passwordVerifier 存储的是 PBKDF2 派生密钥的 Base64 编码
        // 客户端使用相同的派生密钥生成签名，实现零知识证明
        String derivedKeyBase64 = user.getPasswordVerifier();

        if (derivedKeyBase64 == null || derivedKeyBase64.isEmpty()) {
            throw new BusinessException("INVALID_SIGNATURE", "用户派生密钥未配置，无法验证签名");
        }

        // 计算期望的签名（使用派生密钥字节数组）
        String data = email + deviceId + nonce;
        String expectedSignature = computeHmacSignatureWithDerivedKey(data, derivedKeyBase64);

        // 时间安全比较签名
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            log.warn("HMAC签名验证失败: email={}, deviceId={}, nonce={}, expected={}, provided={}",
                email, deviceId, nonce, expectedSignature, signature);
            throw new BusinessException("INVALID_SIGNATURE", "签名验证失败");
        }

        log.info("签名验证成功: email={}, deviceId={}, nonce={}", email, deviceId, nonce);
    }

    /**
     * 计算HMAC-SHA256签名（使用派生密钥）
     * 安全加固第三阶段：使用派生密钥字节数组作为HMAC密钥
     *
     * @param data 待签名数据
     * @param derivedKeyBase64 Base64编码的派生密钥
     * @return Base64编码的签名
     */
    private String computeHmacSignatureWithDerivedKey(String data, String derivedKeyBase64) {
        try {
            // 解码派生密钥
            byte[] derivedKeyBytes = java.util.Base64.getDecoder().decode(derivedKeyBase64);

            // 使用派生密钥字节数组作为 HMAC 密钥
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                derivedKeyBytes,
                "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            log.error("HMAC签名计算失败", e);
            throw new BusinessException("SIGNATURE_ERROR", "签名计算失败");
        }
    }

    /**
     * 计算HMAC-SHA256签名（使用字符串密钥，用于其他场景）
     *
     * @param data 待签名数据
     * @param key  HMAC密钥（字符串）
     * @return Base64编码的签名
     */
    private String computeHmacSignature(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            log.error("HMAC签名计算失败", e);
            throw new BusinessException("SIGNATURE_ERROR", "签名计算失败");
        }
    }

    /**
     * 计算SHA-256哈希签名（与前端保持一致）
     *
     * @param data 待签名数据
     * @return Base64编码的签名
     */
    private String computeHashSignature(String data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new BusinessException("SIGNATURE_ERROR", "签名计算失败");
        }
    }

    // ========== 设备管理方法 ==========

    /**
     * 注销登录
     * 清除服务器端会话并撤销令牌
     *
     * @param userId   用户 ID
     * @param token    JWT 令牌（可选，用于撤销）
     * @param request  注销请求
     * @return 注销响应
     */
    @Transactional
    public LogoutResponse logout(String userId, String token, LogoutRequest request) {
        // 验证用户存在
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        // 撤销令牌（如果提供）
        if (token != null && !token.isEmpty()) {
            try {
                tokenRevokeService.revokeToken(
                    token,
                    userId,
                    request.getDeviceId(),
                    "LOGOUT"
                );
            } catch (Exception e) {
                log.warn("撤销令牌失败，但继续注销流程: userId={}, deviceId={}", userId, request.getDeviceId(), e);
            }
        }

        log.info("用户注销: userId={}, deviceId={}", userId, request.getDeviceId());

        return LogoutResponse.builder()
                .success(true)
                .message("注销成功")
                .build();
    }

    /**
     * 完成注册
     * 邮箱验证后设置主密码并完成注册
     *
     * @param request 完成注册请求
     * @return 完成注册响应
     */
    @Transactional
    public CompleteRegistrationResponse completeRegistration(CompleteRegistrationRequest request) {
        // 查找用户（通过邮箱）
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        // 验证邮箱已验证
        if (!user.getEmailVerified()) {
            throw new BusinessException("EMAIL_NOT_VERIFIED", "请先验证邮箱");
        }

        // 验证用户名匹配
        if (!user.getUsername().equals(request.getUsername())) {
            throw new BusinessException("USERNAME_MISMATCH", "用户名不匹配");
        }

        // 检查注册状态是否有效
        if (!"EMAIL_VERIFIED".equals(user.getRegistrationStatus())) {
            throw new BusinessException("INVALID_REGISTRATION_STATUS",
                "注册状态无效，当前状态: " + user.getRegistrationStatus());
        }

        // 检查是否超时
        if (user.getVerifiedAt() != null) {
            LocalDateTime timeout = user.getVerifiedAt().plusMinutes(registrationTimeoutMinutes);
            if (LocalDateTime.now().isAfter(timeout)) {
                // 超时，删除用户并提示重新注册
                userRepository.delete(user);
                verificationEventService.recordRegistrationTimeout(user.getUserId(), user.getEmail(), user.getVerifiedAt(), timeout);
                log.warn("注册超时，已删除用户: email={}, verifiedAt={}, timeout={}",
                    user.getEmail(), user.getVerifiedAt(), timeout);
                throw new BusinessException("REGISTRATION_TIMEOUT",
                    "注册超时（" + registrationTimeoutMinutes + "分钟），请重新注册");
            }
        }

        // 检查是否已完成注册
        if (user.getPasswordVerifier() != null && !user.getPasswordVerifier().isEmpty()) {
            throw new BusinessException("REGISTRATION_ALREADY_COMPLETED", "注册已完成，请直接登录");
        }

        // 保存密码验证器和盐值（前端已经使用Argon2id哈希密码）
        user.setPasswordVerifier(request.getPasswordVerifier());
        user.setPasswordSalt(request.getSalt());

        // 设置密码哈希算法为 Argon2id（新用户默认使用）
        user.setPasswordHashAlgorithm("ARGON2ID");
        log.info("新用户使用 Argon2id 密码哈希算法: userId={}", user.getUserId());

        // 保存公钥和加密的私钥
        user.setPublicKey(request.getPublicKey());
        user.setPrivateKeyEncrypted(request.getEncryptedPrivateKey());
        user.setPrivateKeyIv(request.getPrivateKeyIv());

        // 设置设备ID（如果是新用户）
        if (user.getDeviceId() == null || user.getDeviceId().isEmpty()) {
            user.setDeviceId(request.getDeviceId());
        }

        // 更新注册状态为 ACTIVE
        user.setRegistrationStatus("ACTIVE");
        user.setRegistrationCompletedAt(LocalDateTime.now());

        user = userRepository.save(user);

        // 保存私钥到 user_private_keys 表
        UserPrivateKey userPrivateKey = UserPrivateKey.builder()
                .userId(user.getUserId())
                .encryptedPrivateKey(request.getEncryptedPrivateKey())
                .iv(request.getPrivateKeyIv())
                .salt(request.getSalt())
                .authTag(request.getAuthTag() != null ? request.getAuthTag() : "")
                .version("v1")
                .build();
        userPrivateKeyRepository.save(userPrivateKey);
        log.info("保存私钥到 user_private_keys 表: userId={}, authTag长度={}", user.getUserId(), request.getAuthTag() != null ? request.getAuthTag().length() : 0);

        // 生成访问令牌和刷新令牌
        AuthTokenIssuer.IssuedTokens tokens = authTokenIssuer.issueTokens(user.getUserId());

        log.info("用户完成注册: userId={}, email={}, username={}", user.getUserId(), user.getEmail(), user.getUsername());

        return CompleteRegistrationResponse.builder()
                .success(true)
                .message("注册成功")
                .userId(user.getUserId())
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .displayName(user.getDisplayName())
                .build();
    }

    /**
     * 获取用户设备列表
     *
     * @param userId 用户 ID
     * @return 设备列表
     */
    @Transactional(readOnly = true)
    public java.util.List<DeviceInfo> getUserDevices(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        if (user.getDevices() == null || user.getDevices().isEmpty()) {
            return new java.util.ArrayList<>();
        }

        return DeviceInfo.fromJson(user.getDevices());
    }

    /**
     * 移除设备
     *
     * @param userId           用户 ID
     * @param deviceIdToRemove 要移除的设备 ID
     * @return 是否成功移除
     */
    @Transactional
    public boolean removeDevice(String userId, String deviceIdToRemove) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        java.util.List<DeviceInfo> devices = getUserDevices(userId);

        // 检查是否尝试移除当前设备
        DeviceInfo currentDevice = devices.stream()
                .filter(DeviceInfo::isCurrentDevice)
                .findFirst()
                .orElse(null);

        if (currentDevice != null && currentDevice.getDeviceId().equals(deviceIdToRemove)) {
            throw new BusinessException("CANNOT_REMOVE_CURRENT_DEVICE",
                    "无法移除当前正在使用的设备");
        }

        // 移除设备
        boolean removed = devices.removeIf(d -> d.getDeviceId().equals(deviceIdToRemove));

        if (removed) {
            user.setDevices(DeviceInfo.toJson(devices));
            userRepository.save(user);
            log.info("移除设备: userId={}, deviceId={}", userId, deviceIdToRemove);
        }

        return removed;
    }

    // ========== 调试方法 ==========

    /**
     * 调试方法：根据邮箱获取待验证用户
     */
    public java.util.Map<String, Object> debugGetPendingUserByEmail(String email) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        try {
            org.ttt.safevaultbackend.dto.PendingUser pendingUser =
                    pendingUserService.getPendingUserByEmail(email);

            if (pendingUser != null) {
                result.put("found", true);
                result.put("email", pendingUser.getEmail());
                result.put("username", pendingUser.getUsername());
                result.put("token", pendingUser.getVerificationToken());
                result.put("tokenExpiresAt", pendingUser.getTokenExpiresAt().toString());
                result.put("createdAt", pendingUser.getCreatedAt().toString());
                result.put("isExpired", pendingUser.isTokenExpired());
                result.put("canResend", pendingUser.canResendEmail());
            } else {
                result.put("found", false);
                result.put("message", "未找到待验证用户");
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 调试方法：根据token获取待验证用户
     */
    public java.util.Map<String, Object> debugGetPendingUserByToken(String token) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        try {
            org.ttt.safevaultbackend.dto.PendingUser pendingUser =
                    pendingUserService.getPendingUserByToken(token);

            if (pendingUser != null) {
                result.put("found", true);
                result.put("email", pendingUser.getEmail());
                result.put("username", pendingUser.getUsername());
                result.put("token", pendingUser.getVerificationToken());
                result.put("tokenExpiresAt", pendingUser.getTokenExpiresAt().toString());
                result.put("createdAt", pendingUser.getCreatedAt().toString());
                result.put("isExpired", pendingUser.isTokenExpired());
                result.put("canResend", pendingUser.canResendEmail());
            } else {
                result.put("found", false);
                result.put("message", "未找到待验证用户");
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 调试方法：获取 Redis 原始数据
     */
    public java.util.Map<String, Object> debugGetRedisRawValue(String email) {
        return pendingUserService.debugGetRawValue(email);
    }

    // ========== 密码哈希验证方法（安全加固第二阶段）==========

    /**
     * 验证用户密码（使用 Argon2id）
     *
     * @param user 用户实体
     * @param password 明文密码
     * @return 密码是否匹配
     */
    public boolean verifyPassword(User user, String password) {
        if (user == null || password == null) {
            return false;
        }

        String storedHash = user.getPasswordVerifier();
        if (storedHash == null || storedHash.isEmpty()) {
            log.warn("用户密码验证器为空: userId={}", user.getUserId());
            return false;
        }

        boolean verified = argon2PasswordHasher.verify(storedHash, password);
        if (verified) {
            log.info("密码验证成功: userId={}", user.getUserId());
        } else {
            log.warn("密码验证失败: userId={}", user.getUserId());
        }
        return verified;
    }

    /**
     * 使用 Argon2id 哈希密码
     *
     * @param password 明文密码
     * @return Argon2id 哈希值（包含盐值和参数）
     */
    public String hashPasswordWithArgon2(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return argon2PasswordHasher.hash(password);
    }

    /**
     * 检查用户密码哈希是否需要重新计算
     */
    public boolean needsPasswordRehash(User user) {
        if (user == null || user.getPasswordVerifier() == null) {
            return false;
        }
        return argon2PasswordHasher.needsRehash(user.getPasswordVerifier());
    }

    /**
     * 获取密码哈希算法信息
     */
    public String getPasswordHashAlgorithmInfo() {
        return argon2PasswordHasher.getParametersInfo();
    }
}
