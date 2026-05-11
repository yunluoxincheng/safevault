package com.ttt.safevault.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.ttt.safevault.viewmodel.AccountSecurityViewModel.EnrollmentReadiness;
import com.ttt.safevault.viewmodel.AccountSecurityViewModel.KeyVersionInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class AccountSecurityViewModelTest {

    private AccountSecurityViewModel viewModel;

    @Before
    public void setUp() {
        Application application = RuntimeEnvironment.getApplication();
        viewModel = new AccountSecurityViewModel(application, null, null);
    }

    // ========== Enrollment Password Management ==========

    @Test
    public void enrollmentPassword_initiallyNull() {
        assertNull(viewModel.getEnrollmentPassword());
    }

    @Test
    public void saveEnrollmentPassword_storesPassword() {
        viewModel.saveEnrollmentPassword("test-password");
        assertEquals("test-password", viewModel.getEnrollmentPassword());
    }

    @Test
    public void clearEnrollmentPassword_removesPassword() {
        viewModel.saveEnrollmentPassword("test-password");
        viewModel.clearEnrollmentPassword();
        assertNull(viewModel.getEnrollmentPassword());
    }

    @Test
    public void onCleared_clearsEnrollmentPassword() {
        viewModel.saveEnrollmentPassword("test-password");
        viewModel.onCleared();
        assertNull(viewModel.getEnrollmentPassword());
    }

    // ========== EnrollmentReadiness ==========

    @Test
    public void enrollmentReadiness_notAvailable_hasReason() {
        EnrollmentReadiness readiness = EnrollmentReadiness.notAvailable("设备不支持");
        assertFalse(readiness.canEnroll);
        assertFalse(readiness.needsPassword);
        assertEquals("设备不支持", readiness.notAvailableReason);
    }

    @Test
    public void enrollmentReadiness_readyWithSession_noPasswordNeeded() {
        EnrollmentReadiness readiness = EnrollmentReadiness.readyWithSession();
        assertTrue(readiness.canEnroll);
        assertFalse(readiness.needsPassword);
        assertNull(readiness.notAvailableReason);
    }

    @Test
    public void enrollmentReadiness_readyNeedsPassword() {
        EnrollmentReadiness readiness = EnrollmentReadiness.readyNeedsPassword();
        assertTrue(readiness.canEnroll);
        assertTrue(readiness.needsPassword);
        assertNull(readiness.notAvailableReason);
    }

    // ========== KeyVersionInfo ==========

    @Test
    public void keyVersionInfo_holdsValues() {
        KeyVersionInfo info = new KeyVersionInfo("v3", true);
        assertEquals("v3", info.version);
        assertTrue(info.hasMigratedToV3);
    }

    @Test
    public void keyVersionInfo_nullVersion() {
        KeyVersionInfo info = new KeyVersionInfo(null, false);
        assertNull(info.version);
        assertFalse(info.hasMigratedToV3);
    }

    // ========== Session State ==========

    @Test
    public void isSessionReadyForMigration_withoutBackendService_returnsFalse() {
        // BackendService from ServiceLocator may be null in test env
        assertFalse(viewModel.isSessionReadyForMigration());
    }

    // ========== Error/Success Clearing ==========

    @Test
    public void clearError_setsNull() {
        viewModel.clearError();
        assertNull(viewModel.errorMessage.getValue());
    }

    @Test
    public void clearSuccess_setsNull() {
        viewModel.clearSuccess();
        assertNull(viewModel.successMessage.getValue());
    }
}
