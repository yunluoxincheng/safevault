package org.ttt.safevaultbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ttt.safevaultbackend.dto.request.UploadPrivateKeyRequest;
import org.ttt.safevaultbackend.dto.response.UploadPrivateKeyResponse;
import org.ttt.safevaultbackend.entity.UserPrivateKey;
import org.ttt.safevaultbackend.exception.BusinessException;
import org.ttt.safevaultbackend.repository.UserPrivateKeyRepository;
import org.ttt.safevaultbackend.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrivateKeyServiceTest {

    @Mock
    private UserPrivateKeyRepository privateKeyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PrivateKeyResponseMapper privateKeyResponseMapper;

    @InjectMocks
    private PrivateKeyService privateKeyService;

    private UploadPrivateKeyRequest baseRequest;

    @BeforeEach
    void setUp() {
        baseRequest = UploadPrivateKeyRequest.builder()
                .encryptedPrivateKey("encKey")
                .iv("ivValue")
                .salt("saltValue")
                .authTag("tagValue")
                .version("v1")
                .build();
    }

    // --- uploadPrivateKey: user not found ---

    @Test
    void uploadPrivateKey_userNotFound_throwsBusinessException() {
        when(userRepository.existsById("unknown")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> privateKeyService.uploadPrivateKey("unknown", baseRequest));
        assertEquals("USER_NOT_FOUND", ex.getErrorCode());
    }

    // --- uploadPrivateKey: new key creation ---

    @Test
    void uploadPrivateKey_noExistingKey_createsNew() {
        when(userRepository.existsById("user1")).thenReturn(true);
        when(privateKeyRepository.findById("user1")).thenReturn(Optional.empty());
        when(privateKeyRepository.save(any(UserPrivateKey.class))).thenAnswer(inv -> inv.getArgument(0));
        when(privateKeyResponseMapper.mapUploadResponse(any(UserPrivateKey.class), any()))
                .thenReturn(UploadPrivateKeyResponse.builder().success(true).version("v1").build());

        UploadPrivateKeyResponse response = privateKeyService.uploadPrivateKey("user1", baseRequest);

        assertTrue(response.isSuccess());
        verify(privateKeyRepository).save(any(UserPrivateKey.class));
    }

    // --- uploadPrivateKey: update existing key with same/newer version ---

    @Test
    void uploadPrivateKey_existingKey_sameVersion_updates() {
        UserPrivateKey existingKey = UserPrivateKey.builder()
                .userId("user1")
                .encryptedPrivateKey("oldKey")
                .iv("oldIv")
                .salt("oldSalt")
                .authTag("oldTag")
                .version("v1")
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.existsById("user1")).thenReturn(true);
        when(privateKeyRepository.findById("user1")).thenReturn(Optional.of(existingKey));
        when(privateKeyRepository.save(any(UserPrivateKey.class))).thenAnswer(inv -> inv.getArgument(0));
        when(privateKeyResponseMapper.mapUploadResponse(any(UserPrivateKey.class), any()))
                .thenReturn(UploadPrivateKeyResponse.builder().success(true).version("v1").build());

        UploadPrivateKeyResponse response = privateKeyService.uploadPrivateKey("user1", baseRequest);

        assertTrue(response.isSuccess());
        verify(privateKeyRepository).save(any(UserPrivateKey.class));
    }

    @Test
    void uploadPrivateKey_existingKey_newerVersion_updates() {
        UserPrivateKey existingKey = UserPrivateKey.builder()
                .userId("user1")
                .encryptedPrivateKey("oldKey")
                .version("v1")
                .updatedAt(LocalDateTime.now())
                .build();

        UploadPrivateKeyRequest v2Request = UploadPrivateKeyRequest.builder()
                .encryptedPrivateKey("newKey")
                .iv("iv")
                .salt("salt")
                .authTag("tag")
                .version("v2")
                .build();

        when(userRepository.existsById("user1")).thenReturn(true);
        when(privateKeyRepository.findById("user1")).thenReturn(Optional.of(existingKey));
        when(privateKeyRepository.save(any(UserPrivateKey.class))).thenAnswer(inv -> inv.getArgument(0));
        when(privateKeyResponseMapper.mapUploadResponse(any(UserPrivateKey.class), any()))
                .thenReturn(UploadPrivateKeyResponse.builder().success(true).version("v2").build());

        UploadPrivateKeyResponse response = privateKeyService.uploadPrivateKey("user1", v2Request);

        assertTrue(response.isSuccess());
    }

    // --- uploadPrivateKey: version conflict (client older than server) ---

    @Test
    void uploadPrivateKey_clientVersionOlder_throwsVersionConflict() {
        UserPrivateKey existingKey = UserPrivateKey.builder()
                .userId("user1")
                .version("v3")
                .updatedAt(LocalDateTime.now())
                .build();

        UploadPrivateKeyRequest oldRequest = UploadPrivateKeyRequest.builder()
                .encryptedPrivateKey("key")
                .iv("iv")
                .salt("salt")
                .authTag("tag")
                .version("v1")
                .build();

        when(userRepository.existsById("user1")).thenReturn(true);
        when(privateKeyRepository.findById("user1")).thenReturn(Optional.of(existingKey));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> privateKeyService.uploadPrivateKey("user1", oldRequest));
        assertEquals("VERSION_CONFLICT", ex.getErrorCode());
    }

    // --- version handling: v-prefix optional ---

    @Test
    void uploadPrivateKey_versionWithoutPrefix_works() {
        UserPrivateKey existingKey = UserPrivateKey.builder()
                .userId("user1")
                .version("1")
                .updatedAt(LocalDateTime.now())
                .build();

        UploadPrivateKeyRequest noPrefixRequest = UploadPrivateKeyRequest.builder()
                .encryptedPrivateKey("key")
                .iv("iv")
                .salt("salt")
                .authTag("tag")
                .version("2")
                .build();

        when(userRepository.existsById("user1")).thenReturn(true);
        when(privateKeyRepository.findById("user1")).thenReturn(Optional.of(existingKey));
        when(privateKeyRepository.save(any(UserPrivateKey.class))).thenAnswer(inv -> inv.getArgument(0));
        when(privateKeyResponseMapper.mapUploadResponse(any(UserPrivateKey.class), any()))
                .thenReturn(UploadPrivateKeyResponse.builder().success(true).version("2").build());

        UploadPrivateKeyResponse response = privateKeyService.uploadPrivateKey("user1", noPrefixRequest);
        assertTrue(response.isSuccess());
    }

    @Test
    void uploadPrivateKey_versionMixedPrefix_handled() {
        UserPrivateKey existingKey = UserPrivateKey.builder()
                .userId("user1")
                .version("v2")
                .updatedAt(LocalDateTime.now())
                .build();

        UploadPrivateKeyRequest noPrefixRequest = UploadPrivateKeyRequest.builder()
                .encryptedPrivateKey("key")
                .iv("iv")
                .salt("salt")
                .authTag("tag")
                .version("1")
                .build();

        when(userRepository.existsById("user1")).thenReturn(true);
        when(privateKeyRepository.findById("user1")).thenReturn(Optional.of(existingKey));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> privateKeyService.uploadPrivateKey("user1", noPrefixRequest));
        assertEquals("VERSION_CONFLICT", ex.getErrorCode());
    }

    // --- version handling: invalid format throws ---

    @Test
    void uploadPrivateKey_invalidVersionFormat_throwsInvalidVersion() {
        UserPrivateKey existingKey = UserPrivateKey.builder()
                .userId("user1")
                .version("v1")
                .updatedAt(LocalDateTime.now())
                .build();

        UploadPrivateKeyRequest badRequest = UploadPrivateKeyRequest.builder()
                .encryptedPrivateKey("key")
                .iv("iv")
                .salt("salt")
                .authTag("tag")
                .version("abc")
                .build();

        when(userRepository.existsById("user1")).thenReturn(true);
        when(privateKeyRepository.findById("user1")).thenReturn(Optional.of(existingKey));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> privateKeyService.uploadPrivateKey("user1", badRequest));
        assertEquals("INVALID_VERSION", ex.getErrorCode());
    }

    // --- getPrivateKey ---

    @Test
    void getPrivateKey_found_returnsResponse() {
        UserPrivateKey key = UserPrivateKey.builder()
                .userId("user1")
                .encryptedPrivateKey("encKey")
                .iv("iv")
                .salt("salt")
                .authTag("tag")
                .version("v1")
                .updatedAt(LocalDateTime.now())
                .build();

        when(privateKeyRepository.findById("user1")).thenReturn(Optional.of(key));
        when(privateKeyResponseMapper.mapPrivateKeyResponse(key))
                .thenReturn(org.ttt.safevaultbackend.dto.response.PrivateKeyResponse.builder()
                        .encryptedPrivateKey("encKey").iv("iv").salt("salt")
                        .authTag("tag").version("v1").build());

        var response = privateKeyService.getPrivateKey("user1");
        assertEquals("encKey", response.getEncryptedPrivateKey());
    }

    @Test
    void getPrivateKey_notFound_throws() {
        when(privateKeyRepository.findById("user1")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> privateKeyService.getPrivateKey("user1"));
        assertEquals("PRIVATE_KEY_NOT_FOUND", ex.getErrorCode());
    }

    // --- deletePrivateKey ---

    @Test
    void deletePrivateKey_exists_deletes() {
        when(privateKeyRepository.existsById("user1")).thenReturn(true);
        privateKeyService.deletePrivateKey("user1");
        verify(privateKeyRepository).deleteById("user1");
    }

    @Test
    void deletePrivateKey_notFound_throws() {
        when(privateKeyRepository.existsById("user1")).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> privateKeyService.deletePrivateKey("user1"));
        assertEquals("PRIVATE_KEY_NOT_FOUND", ex.getErrorCode());
    }
}
