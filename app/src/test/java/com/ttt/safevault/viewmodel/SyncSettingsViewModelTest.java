package com.ttt.safevault.viewmodel;

import static org.junit.Assert.assertEquals;

import com.ttt.safevault.sync.SyncConfig;

import org.junit.Test;

/**
 * Tests for SyncSettingsViewModel.
 *
 * Coverage note: sync orchestration methods (performManualSync, resolveWithCloud/Local/Cancel,
 * updateSyncEnabled/WifiOnly/Interval) depend on VaultSyncManager/SyncScheduler singletons
 * that require a full Android framework environment. These paths are covered by manual
 * real-device verification (task 7.6). This test class covers the value models and
 * configuration defaults that can be verified in isolation.
 */
public class SyncSettingsViewModelTest {

    @Test
    public void conflictData_holdsVersions() {
        SyncSettingsViewModel.ConflictData data = new SyncSettingsViewModel.ConflictData(5L, 3L);
        assertEquals(5L, data.cloudVersion);
        assertEquals(3L, data.localVersion);
    }

    @Test
    public void conflictData_zeroVersions() {
        SyncSettingsViewModel.ConflictData data = new SyncSettingsViewModel.ConflictData(0L, 0L);
        assertEquals(0L, data.cloudVersion);
        assertEquals(0L, data.localVersion);
    }

    @Test
    public void conflictData_largeVersions() {
        SyncSettingsViewModel.ConflictData data = new SyncSettingsViewModel.ConflictData(Long.MAX_VALUE, Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE, data.cloudVersion);
        assertEquals(Long.MAX_VALUE - 1, data.localVersion);
    }

    @Test
    public void syncConfig_defaultValues() {
        SyncConfig config = new SyncConfig();
        assertEquals(true, config.isSyncEnabled());
    }
}
