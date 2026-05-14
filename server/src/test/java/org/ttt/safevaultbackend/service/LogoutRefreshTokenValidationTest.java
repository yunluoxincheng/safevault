package org.ttt.safevaultbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ttt.safevaultbackend.dto.request.LogoutRequest;
import org.ttt.safevaultbackend.dto.response.LogoutResponse;
import org.ttt.safevaultbackend.entity.User;
import org.ttt.safevaultbackend.repository.UserPrivateKeyRepository;
import org.ttt.safevaultbackend.repository.UserRepository;
import org.ttt.safevaultbackend.security.Argon2PasswordHasher;
import org.ttt.safevaultbackend.security.JwtTokenProvider;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutRefreshTokenValidationTest {

    @Mock private UserRepository userRepository;
    @Mock private UserPrivateKeyRepository userPrivateKeyRepository;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private EmailService emailService;
    @Mock private VerificationTokenService verificationTokenService;
    @Mock private PendingUserService pendingUserService;
    @Mock private CryptoService cryptoService;
    @Mock private TokenRevokeService tokenRevokeService;
    @Mock private VerificationEventService verificationEventService;
    @Mock private EmailVerificationHistoryService verificationHistoryService;
    @Mock private Argon2PasswordHasher argon2PasswordHasher;
    @Mock private NonceService nonceService;
    @Mock private AuthTokenIssuer authTokenIssuer;
    @Mock private EmailVerificationLinkFactory emailVerificationLinkFactory;

    private AuthService authService;

    private static final String USER_ID = "user-123";
    private static final String OTHER_USER_ID = "user-456";
    private static final String FAMILY = "family-abc";

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, userPrivateKeyRepository, tokenProvider,
                emailService, verificationTokenService, pendingUserService,
                cryptoService, tokenRevokeService, verificationEventService,
                verificationHistoryService, argon2PasswordHasher, nonceService,
                authTokenIssuer, emailVerificationLinkFactory
        );
    }

    @Test
    void logout_withValidRefreshToken_shouldRevokeFamily() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().userId(USER_ID).build()));
        when(tokenProvider.isRefreshToken("valid-rt")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid-rt")).thenReturn(USER_ID);
        when(tokenProvider.getFamilyFromToken("valid-rt")).thenReturn(FAMILY);
        when(authTokenIssuer.revokeFamily(FAMILY)).thenReturn(2);

        LogoutRequest request = LogoutRequest.builder()
                .deviceId("device-1")
                .refreshToken("valid-rt")
                .build();

        LogoutResponse response = authService.logout(USER_ID, null, request);

        assertTrue(response.getSuccess());
        verify(authTokenIssuer).revokeFamily(FAMILY);
    }

    @Test
    void logout_withNonRefreshToken_shouldSkipFamilyRevoke() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().userId(USER_ID).build()));
        when(tokenProvider.isRefreshToken("access-token")).thenReturn(false);

        LogoutRequest request = LogoutRequest.builder()
                .deviceId("device-1")
                .refreshToken("access-token")
                .build();

        LogoutResponse response = authService.logout(USER_ID, null, request);

        assertTrue(response.getSuccess());
        verify(authTokenIssuer, never()).revokeFamily(anyString());
    }

    @Test
    void logout_withMismatchedUserId_shouldSkipFamilyRevoke() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().userId(USER_ID).build()));
        when(tokenProvider.isRefreshToken("other-user-rt")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("other-user-rt")).thenReturn(OTHER_USER_ID);

        LogoutRequest request = LogoutRequest.builder()
                .deviceId("device-1")
                .refreshToken("other-user-rt")
                .build();

        LogoutResponse response = authService.logout(USER_ID, null, request);

        assertTrue(response.getSuccess());
        verify(authTokenIssuer, never()).revokeFamily(anyString());
    }

    @Test
    void logout_withoutRefreshToken_shouldNotRevokeFamily() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().userId(USER_ID).build()));

        LogoutRequest request = LogoutRequest.builder()
                .deviceId("device-1")
                .build();

        LogoutResponse response = authService.logout(USER_ID, null, request);

        assertTrue(response.getSuccess());
        verify(tokenProvider, never()).isRefreshToken(anyString());
        verify(authTokenIssuer, never()).revokeFamily(anyString());
    }
}
