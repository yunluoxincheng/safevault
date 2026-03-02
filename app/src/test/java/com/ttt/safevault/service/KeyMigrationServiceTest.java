package com.ttt.safevault.service;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.ttt.safevault.crypto.CryptoConstants;
import com.ttt.safevault.crypto.Ed25519Signer;
import com.ttt.safevault.crypto.X25519KeyManager;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.SecureKeyStorageManager;

import java.security.KeyPair;

import javax.crypto.SecretKey;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KeyMigrationService 单元测试
 *
 * 测试密钥迁移服务的各种场景：
 * - 新用户初始化
 * - 老用户迁移
 * - 迁移幂等性
 * - 迁移失败回滚
 */
@RunWith(MockitoJUnitRunner.class)
public class KeyMigrationServiceTest {

    @Mock
    private Context mockContext;

    @Mock
    private SecureKeyStorageManager mockKeyStorage;

    @Mock
    private BackendService mockBackendService;

    @Mock
    private X25519KeyManager mockX25519Manager;

    @Mock
    private Ed25519Signer mockEd25519Signer;

    private KeyMigrationService migrationService;

    @Before
    public void setUp() {
        migrationService = new KeyMigrationService(mockContext);

        // 使用反射设置 mockKeyStorage（因为 KeyMigrationService 内部创建了实例）
        try {
            java.lang.reflect.Field field = KeyMigrationService.class.getDeclaredField("keyStorage");
            field.setAccessible(true);
            field.set(migrationService, mockKeyStorage);
        } catch (Exception e) {
            fail("Failed to set mock keyStorage: " + e.getMessage());
        }
    }

    /**
     * 测试：检查是否已完成迁移
     */
    @Test
    public void testHasMigratedToV3() {
        // 情况 1：已迁移
        when(mockKeyStorage.hasMigratedToV3()).thenReturn(true);
        assertTrue(migrationService.hasMigratedToV3());

        // 情况 2：未迁移
        when(mockKeyStorage.hasMigratedToV3()).thenReturn(false);
        assertFalse(migrationService.hasMigratedToV3());
    }

    /**
     * 测试：获取当前密钥版本
     */
    @Test
    public void testGetCurrentKeyVersion() {
        // 情况 1：v3 版本
        when(mockKeyStorage.getKeyVersion()).thenReturn(CryptoConstants.KEY_VERSION_V3);
        assertEquals(CryptoConstants.KEY_VERSION_V3, migrationService.getCurrentKeyVersion());

        // 情况 2：v2 版本
        when(mockKeyStorage.getKeyVersion()).thenReturn(CryptoConstants.KEY_VERSION_V2);
        assertEquals(CryptoConstants.KEY_VERSION_V2, migrationService.getCurrentKeyVersion());

        // 情况 3：未知版本
        when(mockKeyStorage.getKeyVersion()).thenReturn(null);
        assertNull(migrationService.getCurrentKeyVersion());
    }

    /**
     * 测试：迁移幂等性
     */
    @Test
    public void testMigrationIdempotency() {
        // 模拟已迁移状态
        when(mockKeyStorage.hasMigratedToV3()).thenReturn(true);

        // 执行迁移应该直接返回成功
        KeyMigrationService.MigrationResult result = migrationService.migrateToX25519(
                "testPassword",
                "testSalt",
                mockBackendService
        );

        assertTrue(result.isSuccess());
        verify(mockKeyStorage, never()).encryptAndSaveX25519PrivateKey(any(), any(), any());
        verify(mockKeyStorage, never()).encryptAndSaveEd25519PrivateKey(any(), any(), any());
    }

    /**
     * 测试：新用户初始化密钥
     */
    @Test
    public void testInitializeCryptoKeys() {
        // 模拟成功保存所有密钥
        when(mockKeyStorage.encryptAndSaveRsaPrivateKey(any(), any(), any())).thenReturn(true);
        when(mockKeyStorage.encryptAndSaveX25519PrivateKey(any(), any(), any())).thenReturn(true);
        when(mockKeyStorage.encryptAndSaveEd25519PrivateKey(any(), any(), any())).thenReturn(true);

        // 创建 mock DataKey
        SecretKey mockDataKey = mock(SecretKey.class);
        when(mockDataKey.getEncoded()).thenReturn(new byte[32]);

        // 执行初始化
        boolean result = migrationService.initializeCryptoKeys(mockDataKey);

        // 验证结果
        assertTrue(result);

        // 验证所有密钥都被保存
        verify(mockKeyStorage, times(1)).encryptAndSaveRsaPrivateKey(any(), eq(mockDataKey), any());
        verify(mockKeyStorage, times(1)).encryptAndSaveX25519PrivateKey(any(), eq(mockDataKey), any());
        verify(mockKeyStorage, times(1)).encryptAndSaveEd25519PrivateKey(any(), eq(mockDataKey), any());

        // 验证版本标识被设置
        verify(mockKeyStorage, times(1)).setKeyVersion(CryptoConstants.KEY_VERSION_V3);
    }

    /**
     * 测试：初始化密钥失败（RSA 保存失败）
     */
    @Test
    public void testInitializeCryptoKeys_RSAFailure() {
        // 模拟 RSA 保存失败
        when(mockKeyStorage.encryptAndSaveRsaPrivateKey(any(), any(), any())).thenReturn(false);

        SecretKey mockDataKey = mock(SecretKey.class);
        when(mockDataKey.getEncoded()).thenReturn(new byte[32]);

        // 执行初始化
        boolean result = migrationService.initializeCryptoKeys(mockDataKey);

        // 验证失败
        assertFalse(result);

        // 验证只调用了 RSA 保存，之后没有继续
        verify(mockKeyStorage, times(1)).encryptAndSaveRsaPrivateKey(any(), any(), any());
        verify(mockKeyStorage, never()).encryptAndSaveX25519PrivateKey(any(), any(), any());
        verify(mockKeyStorage, never()).encryptAndSaveEd25519PrivateKey(any(), any(), any());
    }

    /**
     * 测试：初始化密钥失败（X25519 保存失败）
     */
    @Test
    public void testInitializeCryptoKeys_X25519Failure() {
        // 模拟 RSA 成功，X25519 失败
        when(mockKeyStorage.encryptAndSaveRsaPrivateKey(any(), any(), any())).thenReturn(true);
        when(mockKeyStorage.encryptAndSaveX25519PrivateKey(any(), any(), any())).thenReturn(false);

        SecretKey mockDataKey = mock(SecretKey.class);
        when(mockDataKey.getEncoded()).thenReturn(new byte[32]);

        // 执行初始化
        boolean result = migrationService.initializeCryptoKeys(mockDataKey);

        // 验证失败
        assertFalse(result);

        // 验证 RSA 和 X25519 被调用，Ed25519 未被调用
        verify(mockKeyStorage, times(1)).encryptAndSaveRsaPrivateKey(any(), any(), any());
        verify(mockKeyStorage, times(1)).encryptAndSaveX25519PrivateKey(any(), any(), any());
        verify(mockKeyStorage, never()).encryptAndSaveEd25519PrivateKey(any(), any(), any());
    }

    /**
     * 测试：获取迁移状态
     */
    @Test
    public void testGetMigrationStatus() {
        // 情况 1：未开始
        when(mockKeyStorage.getMigrationStatus())
                .thenReturn(SecureKeyStorageManager.MigrationStatus.NOT_STARTED);
        assertEquals(SecureKeyStorageManager.MigrationStatus.NOT_STARTED,
                migrationService.getMigrationStatus());

        // 情况 2：进行中
        when(mockKeyStorage.getMigrationStatus())
                .thenReturn(SecureKeyStorageManager.MigrationStatus.IN_PROGRESS);
        assertEquals(SecureKeyStorageManager.MigrationStatus.IN_PROGRESS,
                migrationService.getMigrationStatus());

        // 情况 3：已完成
        when(mockKeyStorage.getMigrationStatus())
                .thenReturn(SecureKeyStorageManager.MigrationStatus.COMPLETED);
        assertEquals(SecureKeyStorageManager.MigrationStatus.COMPLETED,
                migrationService.getMigrationStatus());

        // 情况 4：失败
        when(mockKeyStorage.getMigrationStatus())
                .thenReturn(SecureKeyStorageManager.MigrationStatus.FAILED);
        assertEquals(SecureKeyStorageManager.MigrationStatus.FAILED,
                migrationService.getMigrationStatus());
    }

    /**
     * 测试：获取迁移错误消息
     */
    @Test
    public void testGetMigrationError() {
        // 情况 1：有错误消息
        when(mockKeyStorage.getMigrationError()).thenReturn("迁移失败：网络错误");
        assertEquals("迁移失败：网络错误", migrationService.getMigrationError());

        // 情况 2：无错误消息
        when(mockKeyStorage.getMigrationError()).thenReturn(null);
        assertNull(migrationService.getMigrationError());
    }

    /**
     * 测试：获取迁移时间戳
     */
    @Test
    public void testGetMigrationTimestamp() {
        long timestamp = System.currentTimeMillis();
        when(mockKeyStorage.getMigrationTimestamp()).thenReturn(timestamp);
        assertEquals(timestamp, migrationService.getMigrationTimestamp());

        // 未迁移时返回 0
        when(mockKeyStorage.getMigrationTimestamp()).thenReturn(0L);
        assertEquals(0L, migrationService.getMigrationTimestamp());
    }

    /**
     * 测试：迁移结果成功
     */
    @Test
    public void testMigrationResult_Success() {
        KeyMigrationService.MigrationResult result = KeyMigrationService.MigrationResult.success();

        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
        assertEquals(KeyMigrationService.MigrationStatus.COMPLETED, result.getStatus());
        assertTrue(result.getTimestamp() > 0);
    }

    /**
     * 测试：迁移结果失败
     */
    @Test
    public void testMigrationResult_Failure() {
        String errorMessage = "测试错误";
        KeyMigrationService.MigrationResult result = KeyMigrationService.MigrationResult.failed(errorMessage);

        assertFalse(result.isSuccess());
        assertEquals(errorMessage, result.getErrorMessage());
        assertEquals(KeyMigrationService.MigrationStatus.FAILED, result.getStatus());
        assertTrue(result.getTimestamp() > 0);
    }

    /**
     * 测试：迁移结果进行中
     */
    @Test
    public void testMigrationResult_InProgress() {
        KeyMigrationService.MigrationResult result = KeyMigrationService.MigrationResult.inProgress();

        assertFalse(result.isSuccess());
        assertNull(result.getErrorMessage());
        assertEquals(KeyMigrationService.MigrationStatus.IN_PROGRESS, result.getStatus());
        assertTrue(result.getTimestamp() > 0);
    }

    /**
     * 测试：重复上传公钥重试
     */
    @Test
    public void testRetryUploadPublicKeys() {
        // 第一次失败
        when(mockKeyStorage.getX25519PublicKeyBase64()).thenReturn("x25519pubkey");
        when(mockKeyStorage.getEd25519PublicKeyBase64()).thenReturn("ed25519pubkey");

        // 执行重试（应该返回 false，因为实际上传方法会抛出异常）
        boolean result = migrationService.retryUploadPublicKeys(mockBackendService);
        assertFalse(result);
    }

    /**
     * 测试：回滚迁移
     */
    @Test
    public void testRollbackMigration() {
        // 执行回滚（此方法会清除 SharedPreferences）
        migrationService.rollbackMigration();

        // 验证没有异常抛出
        // 实际测试中需要验证 SharedPreferences 的清除
    }

    /**
     * 测试：迁移进度监听器
     */
    @Test
    public void testMigrationProgressListener() {
        KeyMigrationService.MigrationProgressListener listener =
                new KeyMigrationService.MigrationProgressListener() {
            private int lastProgress = -1;
            private String lastMessage = null;
            private KeyMigrationService.MigrationResult lastResult = null;

            @Override
            public void onProgress(int progress, String message) {
                lastProgress = progress;
                lastMessage = message;
            }

            @Override
            public void onComplete(KeyMigrationService.MigrationResult result) {
                lastResult = result;
            }

            public int getLastProgress() { return lastProgress; }
            public String getLastMessage() { return lastMessage; }
            public KeyMigrationService.MigrationResult getLastResult() { return lastResult; }
        };

        // 触发进度更新
        listener.onProgress(50, "迁移进行中...");
        assertEquals(50, listener.getLastProgress());
        assertEquals("迁移进行中...", listener.getLastMessage());

        // 触发完成
        KeyMigrationService.MigrationResult result = KeyMigrationService.MigrationResult.success();
        listener.onComplete(result);
        assertEquals(result, listener.getLastResult());
    }
}