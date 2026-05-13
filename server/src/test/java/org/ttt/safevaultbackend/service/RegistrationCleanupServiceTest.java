package org.ttt.safevaultbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ttt.safevaultbackend.entity.User;
import org.ttt.safevaultbackend.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationCleanupServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationEventService verificationEventService;

    @InjectMocks
    private RegistrationCleanupService registrationCleanupService;

    @BeforeEach
    void setUp() {
        // 设置默认超时时间为5分钟
        org.springframework.test.util.ReflectionTestUtils.setField(
            registrationCleanupService, "timeoutMinutes", 5
        );
    }

    @Test
    void cleanupTimeoutRegistrations_WhenNoTimeoutUsers_ShouldReturnZero() {
        // Arrange
        when(userRepository.findTimeoutRegistrations(any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // Act
        int result = registrationCleanupService.cleanupTimeoutRegistrations();

        // Assert
        assertEquals(0, result);
        verify(userRepository, never()).deleteTimeoutRegistrations(any(LocalDateTime.class));
    }

    @Test
    void cleanupTimeoutRegistrations_WhenHasTimeoutUsers_ShouldDeleteAndRecordEvents() {
        // Arrange
        User user1 = User.builder()
            .userId("user1")
            .email("test1@example.com")
            .registrationStatus("EMAIL_VERIFIED")
            .verifiedAt(LocalDateTime.now().minusMinutes(10))
            .build();

        User user2 = User.builder()
            .userId("user2")
            .email("test2@example.com")
            .registrationStatus("EMAIL_VERIFIED")
            .verifiedAt(LocalDateTime.now().minusMinutes(10))
            .build();

        List<User> timeoutUsers = Arrays.asList(user1, user2);

        when(userRepository.findTimeoutRegistrations(any(LocalDateTime.class)))
            .thenReturn(timeoutUsers);
        when(userRepository.deleteTimeoutRegistrations(any(LocalDateTime.class)))
            .thenReturn(2);

        // Act
        int result = registrationCleanupService.cleanupTimeoutRegistrations();

        // Assert
        assertEquals(2, result);
        verify(verificationEventService, times(1)).recordRegistrationCleanup(
            eq("user1"), eq("test1@example.com"), any(LocalDateTime.class)
        );
        verify(verificationEventService, times(1)).recordRegistrationCleanup(
            eq("user2"), eq("test2@example.com"), any(LocalDateTime.class)
        );
        verify(userRepository, times(1)).deleteTimeoutRegistrations(any(LocalDateTime.class));
    }

    @Test
    void cleanupTimeoutRegistrations_WhenExceptionOccurs_ShouldReturnZero() {
        // Arrange
        when(userRepository.findTimeoutRegistrations(any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        int result = registrationCleanupService.cleanupTimeoutRegistrations();

        // Assert
        assertEquals(0, result);
        verify(userRepository, never()).deleteTimeoutRegistrations(any(LocalDateTime.class));
    }

    @Test
    void getTimeoutMinutes_ShouldReturnConfiguredValue() {
        // Arrange
        org.springframework.test.util.ReflectionTestUtils.setField(
            registrationCleanupService, "timeoutMinutes", 10
        );

        // Act
        int result = registrationCleanupService.getTimeoutMinutes();

        // Assert
        assertEquals(10, result);
    }
}
