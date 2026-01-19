---
name: android-encryption-fixes
description: Use when encryption/decryption fails, javax.crypto exceptions occur (BadPaddingException, IllegalBlockSizeException), keys cannot be stored/retrieved from AndroidKeyStore, KeyPermanentlyInvalidatedException occurs, or cryptographic operations fail in Android apps
---

# Android Encryption/Decryption Fixes

## Overview

Systematic debugging and fixing for cryptographic operations in Android including encryption, decryption, key generation, key storage in AndroidKeyStore, and handling common cryptographic exceptions.

**Core principle:** Cryptographic errors are symptoms. Root causes are key management, algorithm configuration, or data handling issues.

**REQUIRED PREREQUISITE:** Use `android-debugging-fixes` first for general debugging foundation.

## When to Use

Use this skill when:
- Encryption throws `BadPaddingException`
- Decryption throws `IllegalBlockSizeException`
- Key generation fails with `InvalidAlgorithmParameterException`
- Key operations throw `KeyPermanentlyInvalidatedException`
- Keys disappear from AndroidKeyStore
- Encrypted data cannot be decrypted
- Biometric enrollment invalidates encrypted data
- Encryption works on some Android versions but not others

## Exception Reference

| Exception | Common Cause | Diagnosis | Fix |
|-----------|--------------|-----------|-----|
| **BadPaddingException** | Wrong decryption key, corrupted data, encoding issue | Verify key matches encryption key, check data wasn't modified | Re-encrypt with valid key, ensure data integrity |
| **IllegalBlockSizeException** | Data too large for block size, wrong padding mode | Check data length, verify padding mode matches | Use GCM (NoPadding), ensure data size is valid |
| **InvalidKeyException** | Key expired, key wrong size, key algorithm mismatch | Check key generation parameters | Regenerate key with correct parameters |
| **KeyPermanentlyInvalidatedException** | Biometric enrollment changed, security settings updated | Check if user added/removed fingerprint or face | Regenerate key, prompt user to re-authenticate and re-encrypt |
| **NoSuchAlgorithmException** | Algorithm not available on device | Check Android API level, verify algorithm name | Use standard algorithms (AES/GCM/NoPadding) |

## Common Issues and Solutions

### Issue 1: BadPaddingException During Decryption

**Root Cause:** IV (Initialization Vector) not stored with encrypted data. GCM mode requires the same IV for decryption that was used for encryption.

**Solution (Kotlin):**
```kotlin
// Store IV with encrypted data
data class EncryptedData(
    val cipherText: ByteArray,
    val iv: ByteArray
) {
    fun toBase64(): String {
        val cipherTextB64 = Base64.encodeToString(cipherText, Base64.DEFAULT)
        val ivB64 = Base64.encodeToString(iv, Base64.DEFAULT)
        return "$ivB64:$cipherTextB64"
    }

    companion object {
        fun fromBase64(base64: String): EncryptedData {
            val parts = base64.split(":")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid encrypted data format")
            }

            val iv = Base64.decode(parts[0], Base64.DEFAULT)
            val cipherText = Base64.decode(parts[1], Base64.DEFAULT)

            return EncryptedData(cipherText, iv)
        }
    }
}
```

**Solution (Java):**
```java
// Store IV with encrypted data (Java version)
public class EncryptedData {
    private final byte[] cipherText;
    private final byte[] iv;

    public EncryptedData(byte[] cipherText, byte[] iv) {
        this.cipherText = cipherText;
        this.iv = iv;
    }

    public byte[] getCipherText() {
        return cipherText;
    }

    public byte[] getIv() {
        return iv;
    }

    public String toBase64() {
        String cipherTextB64 = Base64.encodeToString(cipherText, Base64.NO_WRAP);
        String ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP);
        return ivB64 + ":" + cipherTextB64;
    }

    public static EncryptedData fromBase64(String base64) {
        String[] parts = base64.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid encrypted data format");
        }

        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] cipherText = Base64.decode(parts[1], Base64.NO_WRAP);

        return new EncryptedData(cipherText, iv);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncryptedData that = (EncryptedData) o;
        return Arrays.equals(cipherText, that.cipherText) &&
               Arrays.equals(iv, that.iv);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(cipherText);
        result = 31 * result + Arrays.hashCode(iv);
        return result;
    }
}
```

---

### Issue 2: KeyPermanentlyInvalidatedException

**Solution:**
```kotlin
class SecureDataManager(
    private val context: Context,
    private val keyAlias: String
) {

    fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    fun isKeyValid(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias(keyAlias)
        } catch (e: Exception) {
            false
        }
    }
}
```

---

## Implementation Template

```kotlin
class EncryptionManager(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "app_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey()
        }
    }

    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setUserAuthenticationRequired(true)
        .setUserAuthenticationValidityDurationSeconds(30)
        .setInvalidatedByBiometricEnrollment(true)
        .build()

        keyGenerator.init(keyGenSpec)
        keyGenerator.generateKey()
    }

    fun encrypt(plaintext: String): String {
        val key = getKey() ?: throw IllegalStateException("Key not available")

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val encryptedData = EncryptedData(ciphertext, iv)
        return encryptedData.toBase64()
    }

    fun decrypt(encryptedBase64: String): String {
        val key = getKey() ?: throw IllegalStateException("Key not available")

        val encryptedData = EncryptedData.fromBase64(encryptedBase64)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, encryptedData.iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plaintext = cipher.doFinal(encryptedData.cipherText)
        return String(plaintext, Charsets.UTF_8)
    }

    fun getKey(): SecretKey? {
        return try {
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }
}

data class EncryptedData(
    val cipherText: ByteArray,
    val iv: ByteArray
) {
    fun toBase64(): String {
        val cipherTextB64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        return "$ivB64:$cipherTextB64"
    }

    companion object {
        fun fromBase64(base64: String): EncryptedData {
            val parts = base64.split(":")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid encrypted data format")
            }

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)

            return EncryptedData(cipherText, iv)
        }
    }
}
```

### Java Version

```java
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.util.Arrays;

public class EncryptionManager {

    private static final String KEY_ALIAS = "app_master_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    private KeyStore keyStore;

    public EncryptionManager() throws Exception {
        this.keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        ensureKeyExists();
    }

    private void ensureKeyExists() throws Exception {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey();
        }
    }

    private void generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        );

        KeyGenParameterSpec keyGenSpec = new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setUserAuthenticationRequired(true)
        .setUserAuthenticationValidityDurationSeconds(30)
        .setInvalidatedByBiometricEnrollment(true)
        .build();

        keyGenerator.init(keyGenSpec);
        keyGenerator.generateKey();
    }

    public String encrypt(String plaintext) throws Exception {
        SecretKey key = getKey();
        if (key == null) {
            throw new IllegalStateException("Key not available");
        }

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

        EncryptedData encryptedData = new EncryptedData(ciphertext, iv);
        return encryptedData.toBase64();
    }

    public String decrypt(String encryptedBase64) throws Exception {
        SecretKey key = getKey();
        if (key == null) {
            throw new IllegalStateException("Key not available");
        }

        EncryptedData encryptedData = EncryptedData.fromBase64(encryptedBase64);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, encryptedData.getIv());
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] plaintext = cipher.doFinal(encryptedData.getCipherText());
        return new String(plaintext, "UTF-8");
    }

    public SecretKey getKey() {
        try {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        } catch (Exception e) {
            Log.e("EncryptionManager", "Error getting key", e);
            return null;
        }
    }

    public void deleteKey() throws Exception {
        keyStore.deleteEntry(KEY_ALIAS);
    }

    public boolean isKeyValid() {
        try {
            return keyStore.containsAlias(KEY_ALIAS) &&
                   keyStore.getKey(KEY_ALIAS, null) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
```

---

## Data Migration Guide

### Problem: Old Data Encrypted Without IV

If you have existing data encrypted using the **incorrect method** (without storing IV), decryption will fail with `BadPaddingException`.

### Migration Strategy

**Option 1: Prompt User to Re-enter Data (Recommended for passwords)**
```java
// When app detects old format:
if (!encryptedData.contains(":")) {
    // Old format detected (no IV)
    showMigrationDialog();
}

private void showMigrationDialog() {
    new AlertDialog.Builder(context)
        .setTitle("Data Update Required")
        .setMessage("Due to a security update, please re-enter your master password.")
        .setPositiveButton("OK", (dialog, which) -> {
            // Navigate to password re-entry screen
        })
        .show();
}
```

**Option 2: Server-Side Re-encryption**
```java
// If data is synced with server:
public void migrateFromServer() {
    // 1. Fetch original plaintext from server
    String plaintext = fetchFromServer(key);

    // 2. Re-encrypt with new format (includes IV)
    String newEncrypted = encryptionManager.encrypt(plaintext);

    // 3. Save locally
    saveToLocal(key, newEncrypted);

    // 4. Mark as migrated
    setMigrationFlag(key, true);
}
```

**Option 3: Gradual Migration (Access-based)**
```java
public String decryptWithMigration(String encryptedData) {
    try {
        // Try new format first
        return encryptionManager.decrypt(encryptedData);
    } catch (Exception e) {
        // Old format detected - attempt migration
        if (!encryptedData.contains(":")) {
            return handleOldFormat(encryptedData);
        }
        throw e;
    }
}

private String handleOldFormat(String oldEncrypted) {
    // Prompt user for original data
    // Re-encrypt with new format
    // Return decrypted value
}
```

### Migration Checklist

- [ ] Detect old vs new data format (check for ":" separator)
- [ ] Backup existing data before migration attempt
- [ ] Test migration on non-critical data first
- [ ] Handle migration failures gracefully
- [ ] Mark successfully migrated items
- [ ] Remove migration code after all users updated

## Testing Checklist

- [ ] Test encryption and decryption
- [ ] Test with empty string
- [ ] Test with large data
- [ ] Test after biometric enrollment change
- [ ] Test on Android 10 (API 29)
- [ ] Test on Android 11+ (API 30+)
- [ ] Test key invalidation handling
- [ ] Test data persistence

## Encountering Unknown Issues?

1. **Log all crypto operations** - Key generation, cipher init, encryption, decryption
2. **Verify algorithm compatibility** - Check Android API level requirements
3. **Test on physical devices** - KeyStore behavior differs on emulators
4. **Check device compatibility** - Some devices don't have hardware-backed KeyStore
5. **Consult Android security docs** - https://developer.android.com/training/articles/keystore

**After solving:** Update this skill with the new issue and solution

## Related Skills

- **android-debugging-fixes** - General Android debugging (use first)
- **android-biometric-fixes** - Biometric authentication for key operations

## Red Flags - STOP and Verify

If you catch yourself thinking:
- "Just catch the exception and ignore it"
- "Emulator testing is sufficient for crypto"
- "Store the IV as a constant"
- "Skip key invalidation handling"

**ALL of these mean: STOP. Cryptographic issues have security implications.**
