package org.ttt.safevaultbackend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.dto.request.CompleteRegistrationRequest;
import org.ttt.safevaultbackend.dto.request.EmailRegistrationRequest;
import org.ttt.safevaultbackend.dto.response.EmailRegistrationResponse;
import org.ttt.safevaultbackend.dto.response.VerifyEmailResponse;
import org.ttt.safevaultbackend.entity.User;
import org.ttt.safevaultbackend.exception.BusinessException;
import org.ttt.safevaultbackend.repository.UserRepository;
import org.ttt.safevaultbackend.service.AuthService;
import org.ttt.safevaultbackend.service.EmailService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 注册流程集成测试
 * 测试完整的注册流程，包括邮箱验证、完成注册、超时处理等
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegistrationFlowIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private EmailService emailService;

    @Test
    void completeRegistrationFlow_Success() {
        when(emailService.sendVerificationEmail(anyString(), anyString())).thenReturn(true);

        // 步骤1: 发起注册
        EmailRegistrationRequest registerRequest = EmailRegistrationRequest.builder()
            .email("integration-test@example.com")
            .username("integrationtest")
            .build();

        EmailRegistrationResponse registerResponse = authService.registerWithEmail(registerRequest);
        assertTrue(registerResponse.getEmailSent());
        assertEquals("integration-test@example.com", registerResponse.getEmail());

        // 注意: 实际验证需要从 Redis 获取 token，这里测试完整流程需要模拟
        // 在真实环境中，用户会点击邮件中的验证链接
    }

    @Test
    void registrationStatusTransition_EmailVerifiedToActive() {
        // 创建一个邮箱已验证的用户
        User user = User.builder()
            .userId("test-user-id")
            .email("status-transition@example.com")
            .username("statustest")
            .displayName("statustest")
            .emailVerified(true)
            .registrationStatus("EMAIL_VERIFIED")
            .verifiedAt(LocalDateTime.now())
            .build();

        userRepository.save(user);

        // 完成注册
        CompleteRegistrationRequest completeRequest = CompleteRegistrationRequest.builder()
            .email("status-transition@example.com")
            .username("statustest")
            .passwordVerifier("test-verifier")
            .salt("test-salt")
            .publicKey("test-public-key")
            .encryptedPrivateKey("test-encrypted-key")
            .privateKeyIv("test-iv")
            .deviceId("test-device")
            .build();

        var response = authService.completeRegistration(completeRequest);

        // 验证状态已转换为 ACTIVE
        User updatedUser = userRepository.findByEmail("status-transition@example.com").orElseThrow();
        assertEquals("ACTIVE", updatedUser.getRegistrationStatus());
        assertNotNull(updatedUser.getRegistrationCompletedAt());
        assertTrue(response.getSuccess());
    }

    @Test
    void registrationTimeout_UserDeletedAfterTimeout() {
        // 创建一个超时的用户（验证时间超过5分钟）
        User user = User.builder()
            .userId("timeout-user-id")
            .email("timeout-test@example.com")
            .username("timeoutuser")
            .displayName("timeoutuser")
            .emailVerified(true)
            .registrationStatus("EMAIL_VERIFIED")
            .verifiedAt(LocalDateTime.now().minusMinutes(10)) // 10分钟前验证
            .build();

        userRepository.save(user);

        // 尝试完成注册应该抛出超时异常
        CompleteRegistrationRequest completeRequest = CompleteRegistrationRequest.builder()
            .email("timeout-test@example.com")
            .username("timeoutuser")
            .build();

        BusinessException exception = assertThrows(BusinessException.class,
            () -> authService.completeRegistration(completeRequest));

        assertEquals("REGISTRATION_TIMEOUT", exception.getErrorCode());

        // 用户应该已被删除
        assertFalse(userRepository.findByEmail("timeout-test@example.com").isPresent());
    }

    @Test
    void invalidRegistrationStatus_Rejected() {
        // 创建一个状态为 ACTIVE 的用户（已完成注册）
        User user = User.builder()
            .userId("active-user-id")
            .email("active-status@example.com")
            .username("activeuser")
            .displayName("activeuser")
            .emailVerified(true)
            .registrationStatus("ACTIVE") // 状态是 ACTIVE 而不是 EMAIL_VERIFIED
            .verifiedAt(LocalDateTime.now().minusMinutes(1))
            .build();

        userRepository.save(user);

        // 尝试完成注册应该抛出状态无效异常
        CompleteRegistrationRequest completeRequest = CompleteRegistrationRequest.builder()
            .email("active-status@example.com")
            .username("activeuser")
            .build();

        BusinessException exception = assertThrows(BusinessException.class,
            () -> authService.completeRegistration(completeRequest));

        assertEquals("INVALID_REGISTRATION_STATUS", exception.getErrorCode());
    }
}
