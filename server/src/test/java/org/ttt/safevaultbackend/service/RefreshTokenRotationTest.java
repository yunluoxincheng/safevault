package org.ttt.safevaultbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ttt.safevaultbackend.entity.RefreshTokenRecord;
import org.ttt.safevaultbackend.repository.RefreshTokenRecordRepository;
import org.ttt.safevaultbackend.security.JwtTokenProvider;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRotationTest {

    @Mock private JwtTokenProvider tokenProvider;
    @Mock private RefreshTokenRecordRepository refreshTokenRecordRepository;

    @InjectMocks private AuthTokenIssuer authTokenIssuer;

    private static final String USER_ID = "user-123";
    private static final String JTI_1 = "jti1";
    private static final String FAMILY = "family1";

    @BeforeEach
    void setUp() {
        lenient().when(tokenProvider.generateAccessToken(USER_ID)).thenReturn("access-token");
        lenient().when(tokenProvider.generateRefreshToken(eq(USER_ID), anyString(), anyString()))
                .thenReturn("refresh-token");
        lenient().when(tokenProvider.getRefreshTokenExpirationSeconds()).thenReturn(604800L);
    }

    @Test
    void issueTokens_shouldCreateRecordWithFamilyEqualToJti() {
        when(tokenProvider.generateJti()).thenReturn(JTI_1);

        AuthTokenIssuer.IssuedTokens tokens = authTokenIssuer.issueTokens(USER_ID);

        assertNotNull(tokens);
        assertEquals("access-token", tokens.accessToken());
        assertEquals("refresh-token", tokens.refreshToken());

        verify(refreshTokenRecordRepository).save(argThat(record ->
                record.getJti().equals(JTI_1) &&
                record.getFamily().equals(JTI_1) &&
                record.getUserId().equals(USER_ID)
        ));
    }

    @Test
    void rotateRefreshToken_success_shouldMarkOldAndCreateNew() {
        RefreshTokenRecord oldRecord = RefreshTokenRecord.builder()
                .jti(JTI_1).family(FAMILY).userId(USER_ID)
                .rotated(false).revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(tokenProvider.getJtiFromToken("old-rt")).thenReturn(JTI_1);
        when(tokenProvider.getFamilyFromToken("old-rt")).thenReturn(FAMILY);
        when(refreshTokenRecordRepository.findByJti(JTI_1)).thenReturn(Optional.of(oldRecord));
        when(tokenProvider.generateJti()).thenReturn("new-jti");

        AuthTokenIssuer.RotationResult result = authTokenIssuer.rotateRefreshToken("old-rt", USER_ID);

        assertEquals("SUCCESS", result.status());
        assertNotNull(result.tokens());

        verify(refreshTokenRecordRepository).markRotated(JTI_1);
        verify(refreshTokenRecordRepository).save(argThat(record ->
                record.getFamily().equals(FAMILY) && !record.getJti().equals(JTI_1)
        ));
    }

    @Test
    void rotateRefreshToken_reuseDetected_shouldRevokeFamily() {
        RefreshTokenRecord reusedRecord = RefreshTokenRecord.builder()
                .jti(JTI_1).family(FAMILY).userId(USER_ID)
                .rotated(true).revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(tokenProvider.getJtiFromToken("old-rt")).thenReturn(JTI_1);
        when(tokenProvider.getFamilyFromToken("old-rt")).thenReturn(FAMILY);
        when(refreshTokenRecordRepository.findByJti(JTI_1)).thenReturn(Optional.of(reusedRecord));
        when(refreshTokenRecordRepository.revokeFamily(FAMILY)).thenReturn(3);

        AuthTokenIssuer.RotationResult result = authTokenIssuer.rotateRefreshToken("old-rt", USER_ID);

        assertEquals("REUSE_DETECTED", result.status());
        assertNull(result.tokens());
        assertEquals(3, result.revokedCount());

        verify(refreshTokenRecordRepository).revokeFamily(FAMILY);
        verify(refreshTokenRecordRepository, never()).markRotated(anyString());
        verify(refreshTokenRecordRepository, never()).save(any());
    }

    @Test
    void rotateRefreshToken_revokedToken_shouldReject() {
        RefreshTokenRecord revokedRecord = RefreshTokenRecord.builder()
                .jti(JTI_1).family(FAMILY).userId(USER_ID)
                .rotated(false).revoked(true)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(tokenProvider.getJtiFromToken("old-rt")).thenReturn(JTI_1);
        when(tokenProvider.getFamilyFromToken("old-rt")).thenReturn(FAMILY);
        when(refreshTokenRecordRepository.findByJti(JTI_1)).thenReturn(Optional.of(revokedRecord));

        AuthTokenIssuer.RotationResult result = authTokenIssuer.rotateRefreshToken("old-rt", USER_ID);

        assertEquals("REVOKED", result.status());
        assertNull(result.tokens());

        verify(refreshTokenRecordRepository, never()).markRotated(anyString());
        verify(refreshTokenRecordRepository, never()).save(any());
    }

    @Test
    void rotateRefreshToken_tokenNotFound_shouldReject() {
        when(tokenProvider.getJtiFromToken("old-rt")).thenReturn(JTI_1);
        when(tokenProvider.getFamilyFromToken("old-rt")).thenReturn(FAMILY);
        when(refreshTokenRecordRepository.findByJti(JTI_1)).thenReturn(Optional.empty());

        AuthTokenIssuer.RotationResult result = authTokenIssuer.rotateRefreshToken("old-rt", USER_ID);

        assertEquals("TOKEN_NOT_FOUND", result.status());
    }

    @Test
    void rotateRefreshToken_invalidToken_noJti() {
        when(tokenProvider.getJtiFromToken("bad-token")).thenReturn(null);

        AuthTokenIssuer.RotationResult result = authTokenIssuer.rotateRefreshToken("bad-token", USER_ID);

        assertEquals("INVALID_TOKEN", result.status());
    }

    @Test
    void revokeFamily_shouldDelegateToRepository() {
        when(refreshTokenRecordRepository.revokeFamily(FAMILY)).thenReturn(5);

        int count = authTokenIssuer.revokeFamily(FAMILY);

        assertEquals(5, count);
        verify(refreshTokenRecordRepository).revokeFamily(FAMILY);
    }
}
