package com.ttt.safevault.viewmodel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.ttt.safevault.model.PasswordStrength;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class EditPasswordViewModelTest {

    private EditPasswordViewModel viewModel;

    @Before
    public void setUp() {
        Application application = RuntimeEnvironment.getApplication();
        viewModel = new EditPasswordViewModel(application, null);
    }

    @Test
    public void isUnlocked_withNullBackendService_returnsFalse() {
        assertFalse(viewModel.isUnlocked());
    }

    @Test
    public void checkPasswordStrength_weakPassword() {
        PasswordStrength strength = viewModel.checkPasswordStrength("abc");
        assertNotNull(strength);
    }

    @Test
    public void checkPasswordStrength_emptyPassword() {
        PasswordStrength strength = viewModel.checkPasswordStrength("");
        assertNotNull(strength);
    }

    @Test
    public void checkPasswordStrength_nullPassword() {
        PasswordStrength strength = viewModel.checkPasswordStrength(null);
        assertNotNull(strength);
    }

    @Test
    public void checkPasswordStrength_strongPassword() {
        PasswordStrength strength = viewModel.checkPasswordStrength("MyStr0ng!Pass#2024");
        assertNotNull(strength);
    }

    @Test
    public void hasUnsavedChanges_initiallyFalse() {
        assertFalse(viewModel.hasUnsavedChanges());
    }

    @Test
    public void markChanges_setsHasChanges() {
        viewModel.markChanges();
        assertTrue(viewModel.hasUnsavedChanges());
    }

    @Test
    public void clearError_doesNotThrow() {
        viewModel.clearError();
    }

    @Test
    public void clearGeneratedPassword_doesNotThrow() {
        viewModel.clearGeneratedPassword();
    }
}
