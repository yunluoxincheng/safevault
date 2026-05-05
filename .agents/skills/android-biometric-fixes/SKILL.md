---
name: android-biometric-fixes
description: Use when biometric authentication fails, BiometricPrompt errors occur, fingerprint/face recognition doesn't work, biometric callbacks not invoked, or biometric hardware issues arise in Android apps
---

# Android Biometric Fixes

## Overview

Systematic debugging and fixing for biometric authentication (fingerprint, face recognition) issues in Android applications using the BiometricPrompt API.

**Core principle:** Always verify hardware capability, permissions, and error handling before assuming biometric API is broken.

**REQUIRED PREREQUISITE:** Use `android-debugging-fixes` first for general debugging foundation.

## When to Use

Use this skill when:
- BiometricPrompt doesn't launch or show UI
- Authentication fails with error codes
- Biometric callbacks (onSucceeded, onFailed, onError) not invoked
- Device reports "No biometric features"
- Inconsistent behavior across Android versions

## Biometric API Architecture

```
Application Layer
    ↓
BiometricPrompt (API 28+)
    ↓
BiometricManager (checks capability)
    ↓
BiometricFragment (internal system UI)
    ↓
Hardware Fingerprint/Face Sensor
```

## Diagnostic Flowchart

```
┌─────────────────────────────────────────────────────────────┐
│  1. Check Device Capability                                  │
│  BiometricManager.from(context).canAuthenticate()           │
│  └─> BIOMETRIC_SUCCESS? ──No──> Hardware unavailable        │
│         │                                                    │
│         Yes                                                  │
│         │                                                    │
│         v                                                    │
│  2. Check Permissions                                       │
│  USE_BIOMETRIC permission in manifest?                      │
│  Runtime permission granted?                                │
│  └─> No ──> Request permission                             │
│         │                                                    │
│         Yes                                                  │
│         │                                                    │
│         v                                                    │
│  3. Check Biometric Enrollment                              │
│  Device has fingerprint/face enrolled?                      │
│  └─> No ──> User must enroll in Settings                   │
│         │                                                    │
│         Yes                                                  │
│         │                                                    │
│         v                                                    │
│  4. Check BiometricPrompt Implementation                     │
│  - Executor provided?                                        │
│  - Fragment/Activity context valid?                          │
│  - Callbacks properly implemented?                           │
│  └─> No ──> Fix implementation                             │
│         │                                                    │
│         Yes                                                  │
│         │                                                    │
│         v                                                    │
│  5. Check CryptoObject (if used)                             │
│  - Cipher initialized correctly?                             │
│  - Key properly generated in KeyStore?                       │
│  - Key invalidated?                                          │
│  └─> No ──> Fix crypto setup                               │
│         │                                                    │
│         Yes                                                  │
│         │                                                    │
│         v                                                    │
│  6. Test on Physical Device                                  │
│  (Biometric authentication doesn't work on emulators)       │
└─────────────────────────────────────────────────────────────┘
```

## Common Issues and Solutions

### Issue 1: BiometricPrompt UI Doesn't Show

**Symptoms:**
- `authenticate()` called but no dialog appears
- No error callback invoked

**Diagnosis:**
```java
// Check capability first
BiometricManager manager = BiometricManager.from(context);
int canAuthenticate = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

switch (canAuthenticate) {
    case BiometricManager.BIOMETRIC_SUCCESS:
        Log.d("Biometric", "App can authenticate using biometrics.");
        break;
    case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
        Log.e("Biometric", "No biometric features available on this device.");
        break;
    case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
        Log.e("Biometric", "Biometric features are currently unavailable.");
        break;
    case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
        Log.e("Biometric", "User hasn't enrolled any biometric credentials.");
        break;
}
```

**Common causes:**
- Testing on emulator (no biometric hardware)
- Device has no enrolled fingerprint/face
- Biometric hardware disabled in settings

**Solution:**
- Test on physical device
- Enroll at least one fingerprint/face in device Settings
- Check `canAuthenticate()` result before calling `authenticate()`

---

### Issue 2: Error Callback with ERROR_HW_NOT_PRESENT, ERROR_HW_UNAVAILABLE, or ERROR_NONE_ENROLLED

**Symptoms:**
- `onError()` called with specific error codes
- Authentication never succeeds

**Error Code Reference:**

| Error Code | Meaning | Solution |
|------------|---------|----------|
| `ERROR_HW_NOT_PRESENT` | Device has no biometric hardware | Cannot fix - device limitation |
| `ERROR_HW_UNAVAILABLE` | Hardware currently unavailable | Check if device is busy/locked |
| `ERROR_NONE_ENROLLED` | No biometric credentials enrolled | Prompt user to enroll in Settings |
| `ERROR_NEGATIVE_BUTTON` | User clicked cancel button | Expected behavior - handle gracefully |
| `ERROR_NO_DEVICE_CREDENTIAL` | User opted out of device credentials | Allow alternative authentication |
| `ERROR_TIMEOUT` | Authentication timed out | Increase timeout or retry |
| `ERROR_CANCELED` | Operation canceled by system | Check if app went to background |
| `ERROR_LOCKOUT` | Too many attempts, temporarily locked | Wait for lockout period |
| `ERROR_LOCKOUT_PERMANENT` | Too many attempts, permanently locked | User must reset in Settings |
| `ERROR_USER_CANCELED` | User canceled (e.g., pressed back) | Expected behavior |
| `ERROR_VENDOR` | Vendor-specific error | Check device manufacturer documentation |

---

### Issue 3: CryptoObject Invalidated - ERROR_KEY_INVALIDATED

**Symptoms:**
- Authentication worked before, now fails with `ERROR_KEY_INVALIDATED`
- Common after:
  - User added/removed a fingerprint
  - User enrolled a new face
  - Device security settings changed

**Diagnosis:**
```java
// Check if key still valid
try {
    KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
    keyStore.load(null);
    if (!keyStore.containsAlias(KEY_NAME)) {
        // Key was invalidated
        Log.e("Biometric", "Key invalidated - need to regenerate");
    }
} catch (Exception e) {
    Log.e("Biometric", "Error checking key: " + e.getMessage());
}
```

**Solution:**
1. Delete the invalidated key from KeyStore
2. Generate a new key
3. Re-encrypt sensitive data with new key
4. Prompt user to re-authenticate

```java
// Regenerate key after invalidation
private void regenerateKey() throws Exception {
    KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
    keyStore.load(null);
    keyStore.deleteEntry(KEY_NAME);

    KeyGenerator keyGenerator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

    KeyGenParameterSpec keyGenSpec = new KeyGenParameterSpec.Builder(
        KEY_NAME,
        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .build();

    keyGenerator.init(keyGenSpec);
    keyGenerator.generateKey();
}
```

---

### Issue 4: Callbacks Not Invoked on Main Thread

**Symptoms:**
- UI updates inside callbacks crash or don't work
- Logs show callbacks on wrong thread

**Diagnosis:**
```java
// Log thread in callback
@Override
public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
    Log.d("Biometric", "Callback thread: " + Thread.currentThread().getName());
    Log.d("Biometric", "Is main thread: " + (Looper.myLooper() == Looper.getMainLooper()));
}
```

**Solution:**
```java
// Always provide main thread executor
Executor executor = ContextCompat.getMainExecutor(context); // Returns main thread executor

BiometricPrompt prompt = new BiometricPrompt(fragmentActivity, executor, callback);
```

---

### Issue 5: BiometricPrompt Works on Android 11+ but Fails on Android 10

**Symptoms:**
- Code works on API 30+ devices
- Fails silently or with errors on API 29

**Common causes:**
- Using APIs added in Android 11 without version checks
- Wrong authenticator strength level

**Solution:**
```java
// Choose appropriate authenticator based on Android version
BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
    .setTitle("Biometric Authentication")
    .setSubtitle("Authenticate to continue")
    .setNegativeButtonText("Cancel")
    // Use BIOMETRIC_WEAK for Android 10 compatibility if needed
    // BIOMETRIC_STRONG requires Android 11+ for some features
    .build();
```

---

## Implementation Template

### Standard BiometricPrompt Setup

```java
public class BiometricHelper {

    private final FragmentActivity activity;
    private final BiometricCallback callback;

    public interface BiometricCallback {
        void onAuthenticationSucceeded();
        void onAuthenticationFailed(String error);
    }

    public BiometricHelper(FragmentActivity activity, BiometricCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public boolean canAuthenticate() {
        BiometricManager manager = BiometricManager.from(activity);
        int result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG);
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public void authenticate(String title, String subtitle) {
        if (!canAuthenticate()) {
            callback.onAuthenticationFailed("Biometric not available");
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(
            activity,
            executor,
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    callback.onAuthenticationSucceeded();
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    callback.onAuthenticationFailed("Authentication failed");
                }

                @Override
                public void onAuthenticationError(int errorCode,
                    @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);

                    // Handle user cancellation gracefully
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        callback.onAuthenticationFailed("User canceled");
                        return;
                    }

                    // Handle lockout
                    if (errorCode == BiometricPrompt.ERROR_LOCKOUT) {
                        callback.onAuthenticationFailed("Too many attempts. Try again later.");
                        return;
                    }

                    callback.onAuthenticationFailed("Error: " + errString);
                }
            }
        );

        BiometricPrompt.PromptInfo promptInfo =
            new BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build();

        biometricPrompt.authenticate(promptInfo);
    }
}
```

## Manifest Permissions

```xml
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
```

**Note:** No runtime permission request needed for USE_BIOMETRIC (normal permission)

## Testing Checklist

- [ ] Test on physical device (not emulator)
- [ ] Device has enrolled fingerprint/face
- [ ] Test successful authentication
- [ ] Test failed authentication (wrong finger/face)
- [ ] Test user cancellation
- [ ] Test when no biometric enrolled
- [ ] Test on Android 10 (API 29)
- [ ] Test on Android 11+ (API 30+)
- [ ] Test with CryptoObject (if using encryption)
- [ ] Test after adding new fingerprint (key invalidation)

## Encountering Unknown Issues?

When you encounter issues not covered here:

1. **Enable detailed logging** - Add Log.d at each step
2. **Check Android version compatibility** - Verify API level requirements
3. **Test on different devices** - Hardware/manufacturer variations exist
4. **Review device logs** - Use `adb logcat -b all` for system-wide logs
5. **Consult official docs** - https://developer.android.com/training/sign-in/biometric-auth

**After solving:** Update this skill with the new issue and solution

## Related Skills

- **android-debugging-fixes** - General Android debugging (use first)
- **android-encryption-fixes** - CryptoObject and KeyStore issues

## Red Flags - STOP and Verify

If you catch yourself thinking:
- "Just skip the canAuthenticate check"
- "Emulator testing is sufficient for biometric"
- "CryptoObject is optional, ignore it"
- "All devices work the same way"

**ALL of these mean: STOP. Biometric has specific requirements.**
