package org.ttt.safevaultbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ttt.safevaultbackend.dto.request.CompleteRegistrationRequest;
import org.ttt.safevaultbackend.dto.request.VerifyEmailRequest;
import org.ttt.safevaultbackend.dto.response.CompleteRegistrationResponse;
import org.ttt.safevaultbackend.dto.response.VerifyEmailResponse;
import org.ttt.safevaultbackend.dto.PendingUser;
import org.ttt.safevaultbackend.entity.User;
import org.ttt.safevaultbackend.exception.BusinessException;
import org.ttt.safevaultbackend.exception.ResourceNotFoundException;
import org.ttt.safevaultbackend.repository.UserPrivateKeyRepository;
import org.ttt.safevaultbackend.repository.UserRepository;
import org.ttt.safevaultbackend.security.JwtTokenProvider;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegistrationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPrivateKeyRepository userPrivateKeyRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthTokenIssuer authTokenIssuer;

    @Mock
    private PendingUserService pendingUserService;

    @Mock
    private VerificationEventService verificationEventService;

    @Mock
    private EmailVerificationHistoryService verificationHistoryService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // 设置默认超时时间为5分钟
        org.springframework.test.util.ReflectionTestUtils.setField(
            authService, "registrationTimeoutMinutes", 5
        );
        org.springframework.test.util.ReflectionTestUtils.setField(
            authService, "tokenExpirationMinutes", 10
        );
    }

    @Test
    void verifyEmail_WhenTokenValid_ShouldCreateUserWithEmailVerifiedStatus() {
        // Arrange
        String token = "valid-token";
        PendingUser pendingUser = PendingUser.builder()
            .email("test@example.com")
            .username("testuser")
            .displayName("testuser")
            .verificationToken(token)
            .tokenExpiresAt(LocalDateTime.now().plusMinutes(10))
            .createdAt(LocalDateTime.now())
            .build();

        when(pendingUserService.getPendingUserByToken(token)).thenReturn(pendingUser);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        VerifyEmailResponse response = authService.verifyEmail(new VerifyEmailRequest(token));

        // Assert
        assertTrue(response.getSuccess());
        assertEquals("邮箱验证成功，请设置主密码", response.getMessage());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("testuser", response.getUsername());

        // 验证用户创建时设置了正确的状态
        verify(userRepository).save(argThat(user ->
            "EMAIL_VERIFIED".equals(user.getRegistrationStatus()) &&
            user.getVerifiedAt() != null &&
            user.getEmailVerified()
        ));

        verify(pendingUserService).deletePendingUser("test@example.com");
        verify(verificationEventService).recordTokenVerified(anyString(), eq("test@example.com"), eq(token), isNull());
    }

    @Test
    void verifyEmail_WhenTokenExpired_ShouldReturnFailure() {
        // Arrange
        String token = "expired-token";
        PendingUser pendingUser = PendingUser.builder()
            .email("test@example.com")
            .username("testuser")
            .verificationToken(token)
            .tokenExpiresAt(LocalDateTime.now().minusMinutes(1)) // 过期
            .createdAt(LocalDateTime.now().minusMinutes(15))
            .build();

        when(pendingUserService.getPendingUserByToken(token)).thenReturn(pendingUser);

        // Act
        VerifyEmailResponse response = authService.verifyEmail(new VerifyEmailRequest(token));

        // Assert
        assertFalse(response.getSuccess());
        assertEquals("验证令牌已过期，请重新注册", response.getMessage());
        verify(pendingUserService).deletePendingUser("test@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void completeRegistration_WhenStatusIsEmailVerifiedAndNotTimeout_ShouldSucceed() {
        // Arrange
        String email = "test@example.com";
        String username = "testuser";

        User user = User.builder()
            .userId(UUID.randomUUID().toString())
            .email(email)
            .username(username)
            .displayName("testuser")
            .emailVerified(true)
            .registrationStatus("EMAIL_VERIFIED")
            .verifiedAt(LocalDateTime.now().minusMinutes(2)) // 2分钟前验证，未超时
            .build();

        CompleteRegistrationRequest request = CompleteRegistrationRequest.builder()
            .email(email)
            .username(username)
            .passwordVerifier("verifier")
            .salt("salt")
            .publicKey("publicKey")
            .encryptedPrivateKey("encryptedKey")
            .privateKeyIv("iv")
            .deviceId("device123")
            .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userPrivateKeyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authTokenIssuer.issueTokens(anyString()))
            .thenReturn(new AuthTokenIssuer.IssuedTokens("accessToken", "refreshToken", 900));

        // Act
        CompleteRegistrationResponse response = authService.completeRegistration(request);

        // Assert
        assertTrue(response.getSuccess());
        assertEquals("ACTIVE", user.getRegistrationStatus());
        assertNotNull(user.getRegistrationCompletedAt());

        verify(userRepository).save(argThat(savedUser ->
            "ACTIVE".equals(savedUser.getRegistrationStatus()) &&
            savedUser.getRegistrationCompletedAt() != null &&
            "verifier".equals(savedUser.getPasswordVerifier())
        ));
    }

    @Test
    void completeRegistration_WhenStatusIsNotEmailVerified_ShouldThrowException() {
        // Arrange
        String email = "test@example.com";

        User user = User.builder()
            .userId(UUID.randomUUID().toString())
            .email(email)
            .username("testuser")
            .emailVerified(true)
            .registrationStatus("ACTIVE") // 状态不是 EMAIL_VERIFIED
            .verifiedAt(LocalDateTime.now())
            .build();

        CompleteRegistrationRequest request = CompleteRegistrationRequest.builder()
            .email(email)
            .username("testuser")
            .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
            () -> authService.completeRegistration(request));

        assertEquals("INVALID_REGISTRATION_STATUS", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("注册状态无效"));
    }

    @Test
    void completeRegistration_WhenTimeout_ShouldDeleteUserAndThrowException() {
        // Arrange
        String email = "test@example.com";
        String userId = UUID.randomUUID().toString();

        User user = User.builder()
            .userId(userId)
            .email(email)
            .username("testuser")
            .emailVerified(true)
            .registrationStatus("EMAIL_VERIFIED")
            .verifiedAt(LocalDateTime.now().minusMinutes(10)) // 10分钟前验证，已超时（默认5分钟）
            .build();

        CompleteRegistrationRequest request = CompleteRegistrationRequest.builder()
            .email(email)
            .username("testuser")
            .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
            () -> authService.completeRegistration(request));

        assertEquals("REGISTRATION_TIMEOUT", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("注册超时"));

        verify(userRepository).delete(user);
        verify(verificationEventService).recordRegistrationTimeout(
            eq(userId), eq(email), any(LocalDateTime.class), any(LocalDateTime.class)
        );
    }

    @Test
    void completeRegistration_WhenAlreadyCompleted_ShouldThrowException() {
        // Arrange
        String email = "test@example.com";

        User user = User.builder()
            .userId(UUID.randomUUID().toString())
            .email(email)
            .username("testuser")
            .emailVerified(true)
            .registrationStatus("EMAIL_VERIFIED")
            .verifiedAt(LocalDateTime.now().minusMinutes(2))
            .passwordVerifier("existingVerifier") // 已设置密码验证器
            .build();

        CompleteRegistrationRequest request = CompleteRegistrationRequest.builder()
            .email(email)
            .username("testuser")
            .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
            () -> authService.completeRegistration(request));

        assertEquals("REGISTRATION_ALREADY_COMPLETED", exception.getErrorCode());
    }

    @Test
    void completeRegistration_WhenEmailNotFound_ShouldThrowException() {
        // Arrange
        CompleteRegistrationRequest request = CompleteRegistrationRequest.builder()
            .email("notfound@example.com")
            .username("testuser")
            .build();

        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
            () -> authService.completeRegistration(request));
    }

    @Test
    void completeRegistration_WhenUsernameMismatch_ShouldThrowException() {
        // Arrange
        String email = "test@example.com";

        User user = User.builder()
            .userId(UUID.randomUUID().toString())
            .email(email)
            .username("correctuser") // 用户名不匹配
            .emailVerified(true)
            .registrationStatus("EMAIL_VERIFIED")
            .verifiedAt(LocalDateTime.now().minusMinutes(2))
            .build();

        CompleteRegistrationRequest request = CompleteRegistrationRequest.builder()
            .email(email)
            .username("wronguser") // 错误的用户名
            .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
            () -> authService.completeRegistration(request));

        assertEquals("USERNAME_MISMATCH", exception.getErrorCode());
    }
}
