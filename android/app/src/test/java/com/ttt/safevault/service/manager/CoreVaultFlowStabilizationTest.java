package com.ttt.safevault.service.manager;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for the stabilization fixes in the core vault flow.
 * Covers EncryptionSyncManager data types and sync result behavior.
 */
public class CoreVaultFlowStabilizationTest {

    @Test
    public void syncResultFailure_returnsCorrectMessage() {
        EncryptionSyncManager.SyncResult result = EncryptionSyncManager.SyncResult.failure("test error");
        assertFalse(result.isSuccess());
        assertEquals("test error", result.getMessage());
        assertEquals(0, result.getNewVersion());
    }

    @Test
    public void syncResultSuccess_returnsCorrectVersion() {
        EncryptionSyncManager.SyncResult result = EncryptionSyncManager.SyncResult.success(42L);
        assertTrue(result.isSuccess());
        assertEquals(42L, result.getNewVersion());
    }

    @Test
    public void syncResultConflict_returnsCorrectVersions() {
        EncryptionSyncManager.SyncResult result = EncryptionSyncManager.SyncResult.conflict(5L, 3L);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("5"));
        assertTrue(result.getMessage().contains("3"));
    }

    @Test
    public void encryptedPrivateKey_fieldsAreCorrect() {
        EncryptionSyncManager.EncryptedPrivateKey key =
                new EncryptionSyncManager.EncryptedPrivateKey("enc", "iv", "salt", "tag");
        assertEquals("enc", key.getEncryptedKey());
        assertEquals("iv", key.getIv());
        assertEquals("salt", key.getSalt());
        assertEquals("tag", key.getAuthTag());
    }

    @Test
    public void syncStrategy_enumValuesExist() {
        assertEquals(3, EncryptionSyncManager.SyncStrategy.values().length);
        assertNotNull(EncryptionSyncManager.SyncStrategy.valueOf("USE_CLOUD"));
        assertNotNull(EncryptionSyncManager.SyncStrategy.valueOf("USE_LOCAL"));
        assertNotNull(EncryptionSyncManager.SyncStrategy.valueOf("CANCEL"));
    }

    @Test
    public void syncResultConflict_zeroVersionsHandled() {
        EncryptionSyncManager.SyncResult result = EncryptionSyncManager.SyncResult.conflict(0L, 0L);
        assertFalse(result.isSuccess());
        assertEquals(0, result.getNewVersion());
    }

    @Test
    public void syncResultFailure_emptyMessageHandled() {
        EncryptionSyncManager.SyncResult result = EncryptionSyncManager.SyncResult.failure("");
        assertFalse(result.isSuccess());
        assertEquals("", result.getMessage());
    }
}
