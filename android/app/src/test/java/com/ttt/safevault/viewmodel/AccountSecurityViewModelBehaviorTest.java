package com.ttt.safevault.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.PasswordShare;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.viewmodel.AccountSecurityViewModel.EnrollmentReadiness;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class AccountSecurityViewModelBehaviorTest {

    private Application application;

    @Before
    public void setUp() {
        application = RuntimeEnvironment.getApplication();
    }

    private static class FakeBackendService implements BackendService {
        boolean unlocked = false;
        boolean biometricStorageReady = true;
        boolean enrollWithPasswordResult = true;
        boolean enrollWithSessionResult = true;
        boolean logoutCalled = false;
        boolean forceLogoutCalled = false;

        @Override public boolean unlock(String masterPassword) { unlocked = true; return true; }
        @Override public void lock() { unlocked = false; }
        @Override public PasswordItem decryptItem(int id) { return null; }
        @Override public List<PasswordItem> search(String query) { return Collections.emptyList(); }
        @Override public int saveItem(PasswordItem item) { return 0; }
        @Override public boolean deleteItem(int id) { return false; }
        @Override public String generatePassword(int length, boolean symbols) { return ""; }
        @Override public String generatePassword(int length, boolean useUppercase, boolean useLowercase, boolean useNumbers, boolean useSymbols) { return ""; }
        @Override public boolean isUnlocked() { return unlocked; }
        @Override public List<PasswordItem> getAllItems() { return Collections.emptyList(); }
        @Override public String getMasterPassword() { return null; }
        @Override public void setSessionMasterPassword(String masterPassword) {}
        @Override public boolean unlockSessionWithBiometric() { return true; }
        @Override public boolean shouldShowBiometricLogin() { return false; }
        @Override public boolean isBiometricStorageReady() { return biometricStorageReady; }
        @Override public boolean completeBiometricEnrollmentWithPassword(String masterPassword) { return enrollWithPasswordResult; }
        @Override public boolean completeBiometricEnrollmentWithSessionDataKey() { return enrollWithSessionResult; }
        @Override public String getKeyVersion() { return "v3"; }
        @Override public boolean hasMigratedToV3() { return true; }
        @Override public PublicKey getSessionRsaPublicKey() { return null; }
        @Override public KeyPair getSessionRsaKeyPair() { return null; }
        @Override public boolean isInitialized() { return true; }
        @Override public boolean initialize(String masterPassword) { return true; }
        @Override public boolean changeMasterPassword(String oldPassword, String newPassword) { return true; }
        @Override public boolean exportData(String exportPath) { return false; }
        @Override public boolean importData(String importPath) { return false; }
        @Override public AppStats getStats() { return null; }
        @Override public void recordBackgroundTime() {}
        @Override public void clearBackgroundTime() {}
        @Override public long getBackgroundTime() { return 0; }
        @Override public boolean shouldLockByBackgroundTimeout(android.content.Context context) { return false; }
        @Override public int getAutoLockTimeout() { return 0; }
        @Override public boolean setPinCode(String pinCode) { return true; }
        @Override public boolean verifyPinCode(String pinCode) { return true; }
        @Override public boolean clearPinCode() { return true; }
        @Override public boolean isPinCodeEnabled() { return false; }
        @Override public void logout() {}
        @Override public boolean deleteAccount() { return true; }
        @Override public boolean resetLocalVault() { return false; }
        @Override public com.ttt.safevault.dto.DeviceRecoveryResult recoverDeviceData(String email, String masterPassword) { return null; }
        @Override public com.ttt.safevault.dto.response.AuthResponse register(String username, String password, String displayName) { return null; }
        @Override public com.ttt.safevault.dto.response.AuthResponse login(String username, String password) { return null; }
        @Override public com.ttt.safevault.dto.response.AuthResponse refreshToken(String refreshToken) { return null; }
        @Override public com.ttt.safevault.dto.response.ShareResponse createCloudShare(int passwordId, String toUserId, int expireInMinutes, SharePermission permission) { return null; }
        @Override public com.ttt.safevault.dto.response.ReceivedShareResponse receiveCloudShare(String shareId) { return null; }
        @Override public void revokeCloudShare(String shareId) {}
        @Override public void saveCloudShare(String shareId) {}
        @Override public List<com.ttt.safevault.dto.response.ReceivedShareResponse> getMyCloudShares() { return Collections.emptyList(); }
        @Override public List<com.ttt.safevault.dto.response.ReceivedShareResponse> getReceivedCloudShares() { return Collections.emptyList(); }
        @Override public String createOfflineShare(int passwordId, int expireInMinutes, SharePermission permission) { return ""; }
        @Override public PasswordItem receiveOfflineShare(String encryptedData) { return null; }
        @Override public int saveSharedPassword(String shareId) { return -1; }
        @Override public boolean revokePasswordShare(String shareId) { return false; }
        @Override public List<PasswordShare> getMyShares() { return Collections.emptyList(); }
        @Override public List<PasswordShare> getReceivedShares() { return Collections.emptyList(); }
        @Override public PasswordShare getShareDetails(String shareId) { return null; }
        @Override public int addPassword(String title, String username, String password, String url, String notes) { return -1; }
        @Override public boolean addPassword(PasswordItem password) { return false; }
        @Override public PasswordItem getPasswordById(int passwordId) { return null; }
        @Override public List<PasswordItem> getAllPasswords() { return Collections.emptyList(); }
        @Override public boolean isCloudLoggedIn() { return false; }
        @Override public void logoutCloud() { logoutCalled = true; }
        @Override public void clearLocalCloudTokens() { forceLogoutCalled = true; }
        @Override public com.ttt.safevault.dto.response.CompleteRegistrationResponse completeRegistration(String email, String username, String masterPassword) { return null; }
        @Override public boolean uploadEncryptedPrivateKey(String encryptedPrivateKey, String iv, String salt, String authTag) { return false; }
        @Override public com.ttt.safevault.service.manager.EncryptionSyncManager.EncryptedPrivateKey downloadEncryptedPrivateKey() { return null; }
        @Override public boolean uploadEncryptedVaultData(String encryptedVaultData, String iv, String authTag) { return false; }
        @Override public EncryptedVaultData downloadEncryptedVaultData() { return null; }
        @Override public boolean uploadEccPublicKey(String x25519PublicKey, String ed25519PublicKey, String keyVersion) { return false; }
    }

    // ========== Enrollment Eligibility ==========

    @Test
    public void checkEnrollmentEligibility_nullBiometricManager_notAvailable() {
        FakeBackendService fake = new FakeBackendService();
        fake.unlocked = true;
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, fake, null);

        vm.checkEnrollmentEligibility();
        EnrollmentReadiness readiness = vm.enrollmentReadiness.getValue();
        assertFalse(readiness.canEnroll);
        assertEquals("生物识别不可用", readiness.notAvailableReason);
    }

    // ========== Enrollment Password Cleanup ==========

    @Test
    public void enrollmentPassword_clearedAfterOnCleared() {
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, new FakeBackendService(), null);
        vm.saveEnrollmentPassword("secret-password");
        assertEquals("secret-password", vm.getEnrollmentPassword());
        vm.onCleared();
        assertNull(vm.getEnrollmentPassword());
    }

    @Test
    public void enrollmentPassword_clearedAfterExplicitClear() {
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, new FakeBackendService(), null);
        vm.saveEnrollmentPassword("secret-password");
        vm.clearEnrollmentPassword();
        assertNull(vm.getEnrollmentPassword());
    }

    @Test
    public void enrollmentPassword_clearedAfterEnrollmentWithPassword() throws InterruptedException {
        FakeBackendService fake = new FakeBackendService();
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, fake, null);

        vm.saveEnrollmentPassword("secret-password");
        vm.completeEnrollmentWithPassword("secret-password");
        Thread.sleep(200);

        assertNull(vm.getEnrollmentPassword());
    }

    // ========== Verify Password ==========

    @Test
    public void verifyPassword_callsBackendUnlock() {
        FakeBackendService fake = new FakeBackendService();
        assertFalse(fake.unlocked);

        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, fake, null);
        boolean result = vm.verifyPassword("test-password");

        assertTrue(result);
        assertTrue(fake.unlocked);
    }

    // ========== Logout ==========

    @Test
    public void performLogout_callsLogoutCloud() throws InterruptedException {
        FakeBackendService fake = new FakeBackendService();
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, fake, null);

        vm.performLogout();
        Thread.sleep(200);

        assertTrue(fake.logoutCalled);
    }

    @Test
    public void forceLogout_callsClearLocalTokens() {
        FakeBackendService fake = new FakeBackendService();
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, fake, null);

        vm.forceLogout();
        assertTrue(fake.forceLogoutCalled);
    }

    // ========== Session State ==========

    @Test
    public void sessionReadyForMigration_reflectsUnlockState() {
        FakeBackendService fake = new FakeBackendService();
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, fake, null);

        assertFalse(vm.isSessionReadyForMigration());
        fake.unlocked = true;
        assertTrue(vm.isSessionReadyForMigration());
    }

    // ========== Disable Biometric ==========

    @Test
    public void disableBiometric_clearsLiveData() {
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, new FakeBackendService(), null);
        vm.disableBiometric();
        assertFalse(vm.biometricEnabled.getValue());
    }

    // ========== Null BackendService Safety ==========

    @Test
    public void verifyPassword_withNullBackend_returnsFalse() {
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, null, null);
        assertFalse(vm.verifyPassword("any"));
    }

    @Test
    public void isSessionReadyForMigration_withNullBackend_returnsFalse() {
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, null, null);
        assertFalse(vm.isSessionReadyForMigration());
    }

    @Test
    public void setPinCode_withNullBackend_returnsFalse() {
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, null, null);
        assertFalse(vm.setPinCode("1234"));
    }

    @Test
    public void exportData_withNullBackend_returnsFalse() {
        AccountSecurityViewModel vm = new AccountSecurityViewModel(application, null, null);
        assertFalse(vm.exportData("/fake/path"));
    }
}
