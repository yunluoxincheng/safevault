package com.ttt.safevault.service.manager;

import android.util.Base64;

import com.ttt.safevault.crypto.SecurePaddingUtil;
import com.ttt.safevault.security.SessionGuard;
import com.ttt.safevault.security.SessionLockedException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.*;

/**
 * Tests the core vault encryption lifecycle:
 * unlock → encrypt → lock → operations fail → re-unlock → decrypt succeeds.
 *
 * This validates the DataKey-based AES/GCM + SecurePadding round-trip
 * that underpins all vault CRUD and the create→lock→relogin→decrypt flow.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CoreVaultFlowLifecycleTest {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE = 128;

    private SessionGuard sessionGuard;

    @Before
    public void setUp() {
        sessionGuard = SessionGuard.getInstance();
        // Ensure clean state
        sessionGuard.lock();
    }

    @After
    public void tearDown() {
        sessionGuard.lock();
    }

    // --- Session lifecycle ---

    @Test
    public void initiallyLocked() {
        assertFalse(sessionGuard.isUnlocked());
    }

    @Test
    public void unlockWithKey_sessionIsUnlocked() {
        SecretKey dataKey = generateDataKey();
        sessionGuard.unlockWithDataKey(dataKey);
        assertTrue(sessionGuard.isUnlocked());
    }

    @Test
    public void lockAfterUnlock_sessionIsLocked() {
        SecretKey dataKey = generateDataKey();
        sessionGuard.unlockWithDataKey(dataKey);
        assertTrue(sessionGuard.isUnlocked());

        sessionGuard.lock();
        assertFalse(sessionGuard.isUnlocked());
    }

    @Test(expected = SessionLockedException.class)
    public void requireDataKeyWhenLocked_throwsException() {
        sessionGuard.lock();
        sessionGuard.requireDataKey();
    }

    // --- Core encrypt/decrypt round-trip (simulates vault CRUD) ---

    @Test
    public void encryptDecryptRoundTrip_passwordField() throws Exception {
        SecretKey dataKey = generateDataKey();
        sessionGuard.unlockWithDataKey(dataKey);

        String original = "MySecretP@ssw0rd!2024";

        // Encrypt (same logic as PasswordManager.encryptField)
        String encrypted = encryptField(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        // Decrypt
        String decrypted = decryptField(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    public void encryptDecryptRoundTrip_titleAndUrl() throws Exception {
        SecretKey dataKey = generateDataKey();
        sessionGuard.unlockWithDataKey(dataKey);

        String title = "GitHub Account";
        String url = "https://github.com/login";

        String encTitle = encryptField(title);
        String encUrl = encryptField(url);

        assertEquals(title, decryptField(encTitle));
        assertEquals(url, decryptField(encUrl));
    }

    @Test
    public void encryptDecryptRoundTrip_chineseCharacters() throws Exception {
        SecretKey dataKey = generateDataKey();
        sessionGuard.unlockWithDataKey(dataKey);

        String original = "我的密码测试123！@#特殊字符";
        String encrypted = encryptField(original);
        assertEquals(original, decryptField(encrypted));
    }

    @Test
    public void encryptDecryptRoundTrip_emptyAndNull() throws Exception {
        SecretKey dataKey = generateDataKey();
        sessionGuard.unlockWithDataKey(dataKey);

        // Empty string → null (matches PasswordManager behavior)
        assertNull(encryptField(""));
        assertNull(encryptField(null));
    }

    // --- Lock → re-unlock → decrypt (simulates relogin flow) ---

    @Test
    public void lockAndReUnlock_decryptSucceedsWithSameKey() throws Exception {
        SecretKey dataKey = generateDataKey();

        // Phase 1: unlock and encrypt (simulates first login + create password)
        sessionGuard.unlockWithDataKey(dataKey);
        String encrypted = encryptField("vault-secret-data");
        assertNotNull(encrypted);
        sessionGuard.lock();
        assertFalse(sessionGuard.isUnlocked());

        // Phase 2: verify locked state
        try {
            sessionGuard.requireDataKey();
            fail("Should have thrown SessionLockedException");
        } catch (SessionLockedException expected) {
            // expected
        }

        // Phase 3: re-unlock with same key (simulates relogin)
        sessionGuard.unlockWithDataKey(dataKey);
        assertTrue(sessionGuard.isUnlocked());

        // Phase 4: decrypt succeeds (simulates pull + decrypt after relogin)
        String decrypted = decryptField(encrypted);
        assertEquals("vault-secret-data", decrypted);
    }

    @Test
    public void lockAndReUnlock_differentKey_decryptFails() throws Exception {
        SecretKey key1 = generateDataKey();
        SecretKey key2 = generateDataKey();

        // Encrypt with key1
        sessionGuard.unlockWithDataKey(key1);
        String encrypted = encryptField("data-encrypted-with-key1");

        // Lock and re-unlock with different key (wrong password scenario)
        sessionGuard.lock();
        sessionGuard.unlockWithDataKey(key2);

        // Decrypt with wrong key should fail
        try {
            decryptField(encrypted);
            fail("Decryption with wrong key should fail (AEAD tag mismatch)");
        } catch (Exception e) {
            // Expected: javax.crypto.AEADBadTagException or similar
        }
    }

    // --- Multiple items (simulates vault with multiple records) ---

    @Test
    public void encryptDecryptMultipleItems_allMatch() throws Exception {
        SecretKey dataKey = generateDataKey();
        sessionGuard.unlockWithDataKey(dataKey);

        String[] testData = {
                "password1",
                "user@email.com",
                "https://example.com",
                "notes with special chars: !@#$%^&*()",
                "中文密码内容"
        };

        String[] encrypted = new String[testData.length];
        for (int i = 0; i < testData.length; i++) {
            encrypted[i] = encryptField(testData[i]);
            assertNotNull("Field " + i + " should encrypt", encrypted[i]);
        }

        // Verify each encrypted value is different (different IVs)
        for (int i = 0; i < encrypted.length; i++) {
            for (int j = i + 1; j < encrypted.length; j++) {
                assertNotEquals("Encrypted values should differ (different IVs)", encrypted[i], encrypted[j]);
            }
        }

        // Decrypt all and verify
        for (int i = 0; i < testData.length; i++) {
            assertEquals("Field " + i + " should decrypt correctly", testData[i], decryptField(encrypted[i]));
        }
    }

    // --- runWithUnlockedSession (Guarded Execution) ---

    @Test
    public void guardedExecution_unlocked_executes() {
        SecretKey dataKey = generateDataKey();
        sessionGuard.unlockWithDataKey(dataKey);

        String result = sessionGuard.runWithUnlockedSession(() -> "success");
        assertEquals("success", result);
    }

    @Test(expected = SessionLockedException.class)
    public void guardedExecution_locked_throws() {
        sessionGuard.lock();
        sessionGuard.runWithUnlockedSession(() -> "should not reach");
    }

    @Test
    public void guardedExecution_lockDuringExecution_preventsSubsequentCalls() {
        SecretKey dataKey = generateDataKey();
        sessionGuard.unlockWithDataKey(dataKey);

        // First call succeeds
        sessionGuard.runWithUnlockedSession(() -> {
            sessionGuard.lock();
            return null;
        });

        // Second call fails because session was locked mid-execution
        try {
            sessionGuard.runWithUnlockedSession(() -> "should not reach");
            fail("Should have thrown SessionLockedException");
        } catch (SessionLockedException expected) {
            // expected
        }
    }

    // --- Helper methods (same logic as PasswordManager) ---

    private SecretKey generateDataKey() {
        byte[] keyBytes = new byte[32]; // AES-256
        new SecureRandom().nextBytes(keyBytes);
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts a field using the same format as PasswordManager.encryptField:
     * version:iv:ciphertext with SecurePadding
     */
    private String encryptField(String plaintext) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }

        SecretKey key = sessionGuard.requireDataKey();
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] paddedBytes = SecurePaddingUtil.pad(plaintextBytes);
        byte[] encrypted = cipher.doFinal(paddedBytes);

        return "v2:" + Base64.encodeToString(iv, Base64.NO_WRAP)
                + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    /**
     * Decrypts a field using the same logic as PasswordManager.decryptField:
     * handles v1 and v2 formats with SecurePadding
     */
    private String decryptField(String encrypted) throws Exception {
        if (encrypted == null || encrypted.isEmpty()) {
            return null;
        }

        String[] parts = encrypted.split(":", 3);
        String version;
        String ivBase64;
        String ciphertextBase64;

        if (parts.length == 3) {
            version = parts[0];
            ivBase64 = parts[1];
            ciphertextBase64 = parts[2];
        } else if (parts.length == 2) {
            version = "v1";
            ivBase64 = parts[0];
            ciphertextBase64 = parts[1];
        } else {
            fail("Invalid encrypted format");
            return null;
        }

        byte[] ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP);
        byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);

        SecretKey key = sessionGuard.requireDataKey();

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] decrypted = cipher.doFinal(ciphertext);

        if ("v2".equals(version)) {
            byte[] unpadded = SecurePaddingUtil.unpad(decrypted);
            return new String(unpadded, StandardCharsets.UTF_8);
        } else {
            return new String(decrypted, StandardCharsets.UTF_8);
        }
    }
}
