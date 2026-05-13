package org.ttt.safevaultbackend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.ttt.safevaultbackend.dto.request.CompleteRegistrationRequest;
import org.ttt.safevaultbackend.dto.request.EmailRegistrationRequest;
import org.ttt.safevaultbackend.dto.request.UploadEccPublicKeyRequest;
import org.ttt.safevaultbackend.dto.response.*;
import org.ttt.safevaultbackend.entity.User;
import org.ttt.safevaultbackend.repository.UserRepository;
import org.ttt.safevaultbackend.security.JwtTokenProvider;
import org.ttt.safevaultbackend.service.AuthService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密钥管理集成测试
 *
 * 测试 X25519/Ed25519 迁移相关的 API：
 * - GET /v1/users/{userId}/keys - 获取用户密钥信息
 * - POST /v1/users/me/ecc-public-keys - 上传 ECC 公钥
 * - 新用户注册流程（自动生成 ECC 密钥）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CryptoKeyManagementIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String baseUrl;
    private String testUserId;
    private String testJwtToken;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1/users";

        // 清理测试数据
        deleteUserByEmail("crypto-test@example.com");
        deleteUserByEmail("old-user@example.com");
        deleteUserByEmail("upload-test@example.com");
        deleteUserByEmail("validation-test@example.com");
        deleteUserByEmail("old-nego@example.com");
        deleteUserByEmail("new-user@example.com");
    }

    private void deleteUserByEmail(String email) {
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
    }

    private <T> ResponseEntity<T> getUserKeys(String userId, String jwtToken, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(baseUrl + "/" + userId + "/keys", HttpMethod.GET, entity, responseType);
    }

    /**
     * 测试：创建一个具有完整密钥信息的测试用户
     */
    private User createTestUserWithAllKeys() {
        User user = User.builder()
                .userId("test-crypto-user-" + System.currentTimeMillis())
                .username("cryptotest")
                .displayName("Crypto Test User")
                .email("crypto-test@example.com")
                .emailVerified(true)
                .registrationStatus("ACTIVE")
                .verifiedAt(LocalDateTime.now())
                .registrationCompletedAt(LocalDateTime.now())
                // RSA 密钥（协议版本 2.0）
                .publicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuFj8C3A5K8J9N5B5p8H2K2M3N6L7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0QwIDAQAB")
                .privateKeyEncrypted("encrypted-rsa-private-key")
                .privateKeyIv("test-iv")
                // X25519 公钥（协议版本 3.0）
                .x25519PublicKey("JBFzNz8GnZ2F1Q3B5cWx5a3Zhc2l3ZXJ0eXVpb3Bhcw==")
                .ed25519PublicKey("LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0")
                // 密钥版本
                .keyVersion("v2")
                .publicKeysUpdatedAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);
        testUserId = user.getUserId();
        testJwtToken = jwtTokenProvider.generateAccessToken(user.getUserId());

        return user;
    }

    /**
     * 测试：获取用户密钥信息 - RSA Only (旧用户)
     */
    @Test
    void testGetUserKeys_RsaOnly_Success() {
        // 创建一个只有 RSA 密钥的用户（旧用户）
        User user = User.builder()
                .userId("old-user-" + System.currentTimeMillis())
                .username("olduser")
                .displayName("Old User")
                .email("old-user@example.com")
                .emailVerified(true)
                .registrationStatus("ACTIVE")
                .verifiedAt(LocalDateTime.now())
                .registrationCompletedAt(LocalDateTime.now())
                .publicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuFj8C3A5K8J9N5B5p8H2K2M3N6L7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F6G7H8I9J0K1L2M3N4O5P6Q7R8S9T0U1V2W3X4Y5Z6A7B8C9D0E1F2G3H4I5J6K7L8M9N0O1P2Q3R4S5T6U7V8W9X0Y1Z2A3B4C5D6E7F8G9H0I1J2K3L4M5N6O7P8Q9R0S1T2U3V4W5X6Y7Z8A9B0C1D2E3F4G5H6I7J8K9L0M1N2O3P4Q5R6S7T8U9V0W1X2Y3Z4A5B6C7D8E9F0G1H2I3J4K5L6M7N8O9P0Q1R2S3T4U5V6W7X8Y9Z0A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q7R8S9T0U1V2W3X4Y5Z6A7B8C9D0E1F2G3H4I5J6K7L8M9N0O1P2Q3R4S5T6U7V8W9X0Y1Z2A3B4C5D6E7F8G9H0I1J2K3L4M5N6O7P8Q9R0S1T2U3V4W5X6Y7Z8A9B0C1D2E3F4G5H6I7J8K9L0M1N2O3P4Q5R6S7T8U9V0W1X2Y3Z4A5B6C7D8E9F0G1H2I3J4K5L6M7N8O9P0Q1R2S3T4U5V6W7X8Y9Z0A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q7R8S9T0U1V2W3X4Y5Z6A7B8C9D0E1F2G3H4I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0QwIDAQAB")
                .privateKeyEncrypted("encrypted-rsa-private-key")
                .privateKeyIv("test-iv")
                .keyVersion("v1")
                .build();

        user = userRepository.save(user);
        String userId = user.getUserId();

        // 调用 API 获取密钥信息
        String jwtToken = jwtTokenProvider.generateAccessToken(userId);
        ResponseEntity<UserKeyInfoResponse> response = getUserKeys(userId, jwtToken, UserKeyInfoResponse.class);

        // 验证响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        UserKeyInfoResponse keyInfo = response.getBody();
        assertEquals(userId, keyInfo.getUserId());
        assertNotNull(keyInfo.getRsaPublicKey());
        assertNull(keyInfo.getX25519PublicKey());
        assertNull(keyInfo.getEd25519PublicKey());
        assertEquals("v1", keyInfo.getKeyVersion());
    }

    /**
     * 测试：获取用户密钥信息 - 完整密钥 (已迁移用户)
     */
    @Test
    void testGetUserKeys_AllKeys_Success() {
        // 创建具有完整密钥的用户
        User user = createTestUserWithAllKeys();

        // 调用 API 获取密钥信息
        ResponseEntity<UserKeyInfoResponse> response = getUserKeys(testUserId, testJwtToken, UserKeyInfoResponse.class);

        // 验证响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        UserKeyInfoResponse keyInfo = response.getBody();
        assertEquals(testUserId, keyInfo.getUserId());
        assertNotNull(keyInfo.getRsaPublicKey());
        assertNotNull(keyInfo.getX25519PublicKey());
        assertNotNull(keyInfo.getEd25519PublicKey());
        assertEquals("v2", keyInfo.getKeyVersion());
    }

    /**
     * 测试：获取用户密钥信息 - 用户不存在
     */
    @Test
    void testGetUserKeys_UserNotFound() {
        String jwtToken = jwtTokenProvider.generateAccessToken("requesting-user");
        ResponseEntity<String> response = getUserKeys("non-existent-user-id", jwtToken, String.class);

        // 预期返回 404 或错误响应
        assertTrue(response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError());
    }

    /**
     * 测试：上传 ECC 公钥 - 成功
     */
    @Test
    void testUploadEccPublicKey_Success() {
        // 创建测试用户（只有 RSA 密钥）
        User user = User.builder()
                .userId("upload-test-user-" + System.currentTimeMillis())
                .username("uploadtest")
                .displayName("Upload Test User")
                .email("upload-test@example.com")
                .emailVerified(true)
                .registrationStatus("ACTIVE")
                .verifiedAt(LocalDateTime.now())
                .registrationCompletedAt(LocalDateTime.now())
                .publicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuFj8C3A5K8J9N5B5p8H2K2M3N6L7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0QwIDAQAB")
                .privateKeyEncrypted("encrypted-rsa-private-key")
                .privateKeyIv("test-iv")
                .keyVersion("v1")
                .build();

        user = userRepository.save(user);
        String userId = user.getUserId();
        String jwtToken = jwtTokenProvider.generateAccessToken(userId);

        // 准备请求
        UploadEccPublicKeyRequest request = UploadEccPublicKeyRequest.builder()
                .x25519PublicKey("JBFzNz8GnZ2F1Q3B5cWx5a3Zhc2l3ZXJ0eXVpb3Bhcw==")
                .ed25519PublicKey("LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0")
                .keyVersion("v2")
                .build();

        // 设置认证头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken);
        HttpEntity<UploadEccPublicKeyRequest> entity = new HttpEntity<>(request, headers);

        // 调用 API
        String url = baseUrl + "/me/ecc-public-keys";
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        // 验证响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"success\":true"));

        // 验证数据库中的更新
        User updatedUser = userRepository.findById(userId).orElseThrow();
        assertEquals("JBFzNz8GnZ2F1Q3B5cWx5a3Zhc2l3ZXJ0eXVpb3Bhcw==", updatedUser.getX25519PublicKey());
        assertEquals("LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0", updatedUser.getEd25519PublicKey());
        assertEquals("v2", updatedUser.getKeyVersion());
        assertNotNull(updatedUser.getPublicKeysUpdatedAt());
    }

    /**
     * 测试：上传 ECC 公钥 - 验证失败（缺少必需字段）
     */
    @Test
    void testUploadEccPublicKey_ValidationFailure() {
        // 创建测试用户
        User user = User.builder()
                .userId("validation-test-user-" + System.currentTimeMillis())
                .username("validationtest")
                .displayName("Validation Test User")
                .email("validation-test@example.com")
                .emailVerified(true)
                .registrationStatus("ACTIVE")
                .verifiedAt(LocalDateTime.now())
                .registrationCompletedAt(LocalDateTime.now())
                .publicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuFj8C3A5K8J9N5B5p8H2K2M3N6L7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0QwIDAQAB")
                .privateKeyEncrypted("encrypted-rsa-private-key")
                .privateKeyIv("test-iv")
                .keyVersion("v1")
                .build();

        user = userRepository.save(user);
        String userId = user.getUserId();
        String jwtToken = jwtTokenProvider.generateAccessToken(userId);

        // 准备请求（缺少 x25519PublicKey）
        UploadEccPublicKeyRequest request = UploadEccPublicKeyRequest.builder()
                .ed25519PublicKey("LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0")
                .keyVersion("v2")
                .build();

        // 设置认证头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken);
        HttpEntity<UploadEccPublicKeyRequest> entity = new HttpEntity<>(request, headers);

        // 调用 API
        String url = baseUrl + "/me/ecc-public-keys";
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        // 验证响应（应该返回 400 Bad Request）
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    /**
     * 测试：上传 ECC 公钥 - 未认证
     */
    @Test
    void testUploadEccPublicKey_Unauthorized() {
        // 准备请求
        UploadEccPublicKeyRequest request = UploadEccPublicKeyRequest.builder()
                .x25519PublicKey("JBFzNz8GnZ2F1Q3B5cWx5a3Zhc2l3ZXJ0eXVpb3Bhcw==")
                .ed25519PublicKey("LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0")
                .keyVersion("v2")
                .build();

        // 不设置认证头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UploadEccPublicKeyRequest> entity = new HttpEntity<>(request, headers);

        // 调用 API
        String url = baseUrl + "/me/ecc-public-keys";
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        // 验证响应（应该返回 401 Unauthorized）
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    /**
     * 测试：版本协商 - 迁移后用户
     */
    @Test
    void testVersionNegotiation_MigratedUser_ShouldUseV3() {
        // 创建已迁移的用户
        User user = createTestUserWithAllKeys();

        // 调用 API 获取密钥信息
        ResponseEntity<UserKeyInfoResponse> response = getUserKeys(testUserId, testJwtToken, UserKeyInfoResponse.class);

        // 验证响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        UserKeyInfoResponse keyInfo = response.getBody();
        assertEquals(testUserId, keyInfo.getUserId());

        // 版本协商逻辑：检查是否支持 X25519
        // 如果 x25519PublicKey 不为空，应该使用 v3.0
        assertNotNull(keyInfo.getX25519PublicKey());
        assertNotNull(keyInfo.getEd25519PublicKey());

        // 返回的 keyVersion 应该是 v2（表示已迁移到 ECC）
        assertEquals("v2", keyInfo.getKeyVersion());
    }

    /**
     * 测试：版本协商 - 旧用户（只有 RSA）
     */
    @Test
    void testVersionNegotiation_OldUser_ShouldUseV2() {
        // 创建只有 RSA 的用户
        User user = User.builder()
                .userId("old-nego-user-" + System.currentTimeMillis())
                .username("oldnego")
                .displayName("Old Nego User")
                .email("old-nego@example.com")
                .emailVerified(true)
                .registrationStatus("ACTIVE")
                .verifiedAt(LocalDateTime.now())
                .registrationCompletedAt(LocalDateTime.now())
                .publicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuFj8C3A5K8J9N5B5p8H2K2M3N6L7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0QwIDAQAB")
                .privateKeyEncrypted("encrypted-rsa-private-key")
                .privateKeyIv("test-iv")
                .keyVersion("v1")
                .build();

        user = userRepository.save(user);
        String userId = user.getUserId();

        // 调用 API 获取密钥信息
        String jwtToken = jwtTokenProvider.generateAccessToken(userId);
        ResponseEntity<UserKeyInfoResponse> response = getUserKeys(userId, jwtToken, UserKeyInfoResponse.class);

        // 验证响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        UserKeyInfoResponse keyInfo = response.getBody();
        assertEquals(userId, keyInfo.getUserId());

        // 版本协商逻辑：检查是否只支持 RSA
        // 如果 x25519PublicKey 为空，应该使用 v2.0
        assertNull(keyInfo.getX25519PublicKey());
        assertNull(keyInfo.getEd25519PublicKey());
        assertNotNull(keyInfo.getRsaPublicKey());

        // 返回的 keyVersion 应该是 v1（表示只有 RSA）
        assertEquals("v1", keyInfo.getKeyVersion());
    }

    /**
     * 测试：新用户注册流程（模拟新用户自动生成 ECC 密钥）
     *
     * 注意：这是集成测试，实际注册流程在 AuthService 中处理
     */
    @Test
    void testNewUserRegistration_WithEccKeys() {
        // 创建一个模拟的新用户（包含所有密钥类型）
        User newUser = User.builder()
                .userId("new-user-" + System.currentTimeMillis())
                .username("newuser")
                .displayName("New User")
                .email("new-user@example.com")
                .emailVerified(true)
                .registrationStatus("ACTIVE")
                .verifiedAt(LocalDateTime.now())
                .registrationCompletedAt(LocalDateTime.now())
                // RSA 密钥
                .publicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuFj8C3A5K8J9N5B5p8H2K2M3N6L7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0QwIDAQAB")
                .privateKeyEncrypted("encrypted-rsa-private-key")
                .privateKeyIv("test-iv")
                // X25519/Ed25519 密钥
                .x25519PublicKey("JBFzNz8GnZ2F1Q3B5cWx5a3Zhc2l3ZXJ0eXVpb3Bhcw==")
                .ed25519PublicKey("LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0")
                // 密钥版本（新用户应该是 v2）
                .keyVersion("v2")
                .publicKeysUpdatedAt(LocalDateTime.now())
                .build();

        newUser = userRepository.save(newUser);
        String userId = newUser.getUserId();

        // 调用 API 获取密钥信息
        String jwtToken = jwtTokenProvider.generateAccessToken(userId);
        ResponseEntity<UserKeyInfoResponse> response = getUserKeys(userId, jwtToken, UserKeyInfoResponse.class);

        // 验证响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        UserKeyInfoResponse keyInfo = response.getBody();
        assertEquals(userId, keyInfo.getUserId());
        assertNotNull(keyInfo.getRsaPublicKey());
        assertNotNull(keyInfo.getX25519PublicKey());
        assertNotNull(keyInfo.getEd25519PublicKey());
        assertEquals("v2", keyInfo.getKeyVersion());
    }
}
