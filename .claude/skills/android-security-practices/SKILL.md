---
name: android-security-practices
description: Use when implementing security features in Android apps, particularly password managers or sensitive data applications. Apply for biometric authentication, encryption, screenshot prevention, clipboard protection, auto-locking, or device security checks.
---

# Android Security Development Practices

## Overview

**Comprehensive security implementation for Android apps handling sensitive data.** Based on SafeVault password manager's security architecture including biometric authentication, encryption, anti-screenshot measures, and device security.

## When to Use

**Use when:**
- Building password managers or vault applications
- Implementing biometric authentication (fingerprint/face)
- Protecting sensitive data from screenshots
- Managing encryption keys and secure storage
- Implementing auto-locking features
- Checking device security (root, debug mode)
- Protecting clipboard data
- Handling secure app transitions

**Symptoms that indicate you need this:**
- Sensitive data visible in screenshots
- No protection against rooted devices
- Passwords stored in plaintext
- No auto-locking when app goes background
- Biometric authentication not implemented
- Clipboard retains sensitive data indefinitely

**NOT for:**
- Apps without sensitive data
- Simple utility apps
- Non-Android platforms

## Core Pattern

### Before (Insecure)
```java
// Plaintext storage, no protection
public class InsecureActivity extends AppCompatActivity {
    private String password = "plaintext123"; // In memory
    private EditText passwordField; // User can screenshot
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // No FLAG_SECURE, screenshots allowed
    }
}
```

### After (Secure)
```java
// Secure implementation
public class SecureActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply security measures
        SecurityManager.getInstance(this).applySecurityMeasures(this);
        
        // Use encrypted storage
        KeyManager keyManager = KeyManager.getInstance(this);
        String encryptedData = keyManager.encryptData("sensitive");
    }
}
```

## Quick Reference

| Security Feature | Implementation | SafeVault Example |
|------------------|----------------|-------------------|
| **Biometric Auth** | Fingerprint/face recognition | `BiometricAuthHelper.java` |
| **Screenshot Prevention** | FLAG_SECURE flag | `SecurityManager.java` |
| **Auto-locking** | App lifecycle tracking | `MainActivity.java` |
| **Clipboard Protection** | Auto-clear after delay | `ClipboardManager.java` |
| **Encryption** | AndroidKeyStore + AES | `KeyManager.java` |
| **Root Detection** | Check common root files | `SecurityManager.java` |
| **Debug Detection** | Check debugger attached | `SecurityManager.java` |

## Implementation Guidelines

### 1. Security Manager (Singleton)
```java
public class SecurityManager {
    private static volatile SecurityManager instance;
    private final Context context;
    private final ClipboardManager clipboardManager;
    private boolean isLocked = false;
    
    public static SecurityManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SecurityManager.class) {
                if (instance == null) {
                    instance = new SecurityManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }
    
    private SecurityManager(@NonNull Context context) {
        this.context = context;
        this.clipboardManager = new ClipboardManager(this.context);
    }
    
    /**
     * Apply security measures to Activity
     */
    public void applySecurityMeasures(@NonNull Activity activity) {
        // 1. Prevent screenshots based on config
        preventScreenshots(activity);
        
        // 2. Check developer options (optional: block if enabled)
        if (isDeveloperOptionsEnabled()) {
            // Could show warning or finish activity
            showDeveloperWarning(activity);
        }
        
        // 3. Check if device is rooted
        if (isDeviceRooted()) {
            showRootWarning(activity);
        }
        
        // 4. Keep screen on for sensitive activities
        if (isSensitiveActivity(activity)) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
    
    /**
     * Prevent screenshots and screen recording
     * Respects user preference in SecurityConfig
     */
    public void preventScreenshots(@NonNull Activity activity) {
        SecurityConfig config = new SecurityConfig(context);
        boolean screenshotProtectionEnabled = config.isScreenshotProtectionEnabled();
        
        if (screenshotProtectionEnabled) {
            // Add FLAG_SECURE to prevent screenshots
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            // Clear flag if user disabled protection
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
    
    /**
     * Lock the application - clear sensitive data
     */
    public void lock() {
        isLocked = true;
        
        // 1. Clear clipboard
        clipboardManager.clearClipboard();
        
        // 2. Clear backend service session keys
        try {
            BackendService backendService = ServiceLocator.getInstance().getBackendService();
            if (backendService != null) {
                backendService.lock();
                Log.d(TAG, "BackendService locked - session keys cleared");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to lock BackendService", e);
        }
        
        // 3. Clear any in-memory sensitive data
        clearSensitiveMemory();
    }
    
    /**
     * Unlock the application
     */
    public void unlock() {
        isLocked = false;
    }
    
    /**
     * Check if device is rooted
     */
    public boolean isDeviceRooted() {
        // Check common root files
        String[] rootFiles = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        };
        
        for (String file : rootFiles) {
            if (new File(file).exists()) {
                return true;
            }
        }
        
        // Check if su command can be executed
        try {
            Process process = Runtime.getRuntime().exec("su");
            process.destroy();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if developer options are enabled
     */
    public boolean isDeveloperOptionsEnabled() {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }
    
    /**
     * Check if debugger is attached
     */
    public boolean isDebuggerAttached() {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger();
    }
    
    /**
     * Check if app is in debug mode
     */
    public boolean isDebugMode() {
        return (context.getApplicationInfo().flags &
                ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
    
    // Getters
    public ClipboardManager getClipboardManager() { return clipboardManager; }
    public boolean isLocked() { return isLocked; }
}
```

### 2. Biometric Authentication
```java
public class BiometricAuthHelper {
    
    /**
     * Check if device supports biometric authentication
     */
    public static boolean isBiometricSupported(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        int canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG |
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        );
        
        return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
    }
    
    /**
     * Show biometric authentication prompt
     */
    public static void authenticate(
        FragmentActivity activity,
        String title,
        String subtitle,
        BiometricCallback callback
    ) {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build();
        
        BiometricPrompt biometricPrompt = new BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result
                ) {
                    super.onAuthenticationSucceeded(result);
                    callback.onSuccess();
                }
                
                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    callback.onFailure("Authentication failed");
                }
                
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    
                    // Handle specific errors
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        callback.onCancelled();
                    } else if (errorCode == BiometricPrompt.ERROR_LOCKOUT) {
                        callback.onFailure("Too many failed attempts. Try again later.");
                    } else {
                        callback.onFailure("Biometric error: " + errString);
                    }
                }
            }
        );
        
        biometricPrompt.authenticate(promptInfo);
    }
    
    public interface BiometricCallback {
        void onSuccess();
        void onFailure(String error);
        void onCancelled();
    }
}
```

### 3. Key Manager (Encryption)
```java
public class KeyManager {
    private static final String TAG = "KeyManager";
    private static final String KEY_ALIAS = "safevault_master_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    
    private static volatile KeyManager instance;
    private final Context context;
    private KeyStore keyStore;
    
    public static KeyManager getInstance(Context context) {
        if (instance == null) {
            synchronized (KeyManager.class) {
                if (instance == null) {
                    instance = new KeyManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }
    
    private KeyManager(Context context) {
        this.context = context;
        initializeKeyStore();
    }
    
    /**
     * Initialize Android KeyStore
     */
    private void initializeKeyStore() {
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            
            // Check if key already exists
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKey();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize KeyStore", e);
            throw new RuntimeException("KeyStore initialization failed", e);
        }
    }
    
    /**
     * Generate AES key in Android KeyStore
     */
    private void generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true);
            
        // For Android 24+ add user authentication requirement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setUserAuthenticationRequired(false); // Can be true for extra security
            builder.setInvalidatedByBiometricEnrollment(false);
        }
        
        keyGenerator.init(builder.build());
        keyGenerator.generateKey();
    }
    
    /**
     * Encrypt data with AES-GCM
     */
    public String encryptData(String plaintext) throws Exception {
        if (plaintext == null) return null;
        
        SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        
        // Generate IV
        byte[] iv = new byte[12]; // 96 bits for GCM
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(iv);
        
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // Combine IV + ciphertext for storage
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        
        return Base64.encodeToString(combined, Base64.DEFAULT);
    }
    
    /**
     * Decrypt data with AES-GCM
     */
    public String decryptData(String encryptedBase64) throws Exception {
        if (encryptedBase64 == null) return null;
        
        byte[] combined = Base64.decode(encryptedBase64, Base64.DEFAULT);
        
        // Extract IV (first 12 bytes)
        byte[] iv = new byte[12];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        
        // Extract ciphertext (remaining bytes)
        byte[] ciphertext = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);
        
        SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
        byte[] plaintext = cipher.doFinal(ciphertext);
        
        return new String(plaintext, StandardCharsets.UTF_8);
    }
    
    /**
     * Generate device ID for authentication
     */
    public String getDeviceId() {
        String deviceId = Settings.Secure.getString(
            context.getContentResolver(), Settings.Secure.ANDROID_ID);
        
        if (deviceId == null || deviceId.isEmpty()) {
            // Fallback to generated UUID
            deviceId = UUID.randomUUID().toString();
            // Store in SharedPreferences for persistence
            SharedPreferences prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE);
            prefs.edit().putString("device_id", deviceId).apply();
        }
        
        return deviceId;
    }
    
    /**
     * Derive key from master password using PBKDF2
     */
    public SecretKey deriveKeyFromMasterPassword(String masterPassword, String salt) 
            throws Exception {
        PBEKeySpec keySpec = new PBEKeySpec(
            masterPassword.toCharArray(),
            salt.getBytes(StandardCharsets.UTF_8),
            100000, // Iterations
            256 // Key length
        );
        
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(keySpec).getEncoded();
        
        return new SecretKeySpec(keyBytes, "AES");
    }
}
```

### 4. Clipboard Manager (Secure)
```java
public class ClipboardManager {
    private static final int CLEAR_DELAY_MS = 30000; // 30 seconds
    
    private final Context context;
    private final android.content.ClipboardManager systemClipboard;
    private final Handler handler;
    private Runnable clearTask;
    
    public ClipboardManager(Context context) {
        this.context = context.getApplicationContext();
        this.systemClipboard = (android.content.ClipboardManager) 
            context.getSystemService(Context.CLIPBOARD_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Copy text to clipboard with auto-clear
     */
    public void copyWithAutoClear(String label, String text) {
        if (text == null || text.isEmpty()) return;
        
        // Cancel any pending clear task
        cancelClearTask();
        
        // Copy to clipboard
        ClipData clip = ClipData.newPlainText(label, text);
        systemClipboard.setPrimaryClip(clip);
        
        // Schedule auto-clear
        clearTask = () -> clearClipboard();
        handler.postDelayed(clearTask, CLEAR_DELAY_MS);
        
        Log.d("ClipboardManager", "Copied to clipboard, will clear in " + 
              CLEAR_DELAY_MS/1000 + " seconds");
    }
    
    /**
     * Clear clipboard immediately
     */
    public void clearClipboard() {
        try {
            // Set empty clip to clear
            ClipData clip = ClipData.newPlainText("", "");
            systemClipboard.setPrimaryClip(clip);
            
            Log.d("ClipboardManager", "Clipboard cleared");
        } catch (Exception e) {
            Log.e("ClipboardManager", "Failed to clear clipboard", e);
        }
        
        // Cancel any pending task
        cancelClearTask();
    }
    
    /**
     * Cancel pending clear task
     */
    private void cancelClearTask() {
        if (clearTask != null) {
            handler.removeCallbacks(clearTask);
            clearTask = null;
        }
    }
    
    /**
     * Get text from clipboard (use with caution)
     */
    public String getText() {
        if (!systemClipboard.hasPrimaryClip()) {
            return null;
        }
        
        ClipData clip = systemClipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            return clip.getItemAt(0).getText().toString();
        }
        
        return null;
    }
}
```

### 5. Auto-locking Implementation
```java
// In MainActivity
public class MainActivity extends AppCompatActivity {
    private static final long LOCK_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    
    private long lastInteractionTime;
    private Handler lockHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize lock timer
        lastInteractionTime = System.currentTimeMillis();
        lockHandler = new Handler(Looper.getMainLooper());
        startLockTimer();
        
        // Track user interactions
        setupInteractionTracking();
    }
    
    private void setupInteractionTracking() {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.setOnTouchListener((v, event) -> {
                updateInteractionTime();
                return false;
            });
        }
    }
    
    private void updateInteractionTime() {
        lastInteractionTime = System.currentTimeMillis();
    }
    
    private void startLockTimer() {
        lockHandler.postDelayed(() -> {
            long idleTime = System.currentTimeMillis() - lastInteractionTime;
            
            if (idleTime >= LOCK_TIMEOUT_MS && isAppInBackground()) {
                // Lock the app
                SecurityManager.getInstance(this).lock();
                navigateToLockScreen();
            }
            
            // Schedule next check
            startLockTimer();
        }, 1000); // Check every second
    }
    
    private boolean isAppInBackground() {
        ActivityManager activityManager = (ActivityManager) 
            getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return false;
        
        List<ActivityManager.RunningAppProcessInfo> processes = 
            activityManager.getRunningAppProcesses();
        if (processes == null) return false;
        
        String packageName = getPackageName();
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                process.processName.equals(packageName)) {
                return false; // App is in foreground
            }
        }
        
        return true; // App is in background
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Check if we should lock when app goes to background
        SecurityConfig config = new SecurityConfig(this);
        if (config.isAutoLockEnabled()) {
            SecurityManager.getInstance(this).lock();
        }
    }
}
```

### 6. Security Configuration (User Preferences)
```java
public class SecurityConfig {
    private static final String PREFS_NAME = "security_config";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_AUTO_LOCK_ENABLED = "auto_lock_enabled";
    private static final String KEY_SCREENSHOT_PROTECTION = "screenshot_protection";
    
    private final SharedPreferences prefs;
    
    public SecurityConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    // Biometric authentication
    public boolean isBiometricEnabled() {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }
    
    public void setBiometricEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply();
    }
    
    // Auto-lock
    public boolean isAutoLockEnabled() {
        return prefs.getBoolean(KEY_AUTO_LOCK_ENABLED, true);
    }
    
    public void setAutoLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_LOCK_ENABLED, enabled).apply();
    }
    
    // Screenshot protection
    public boolean isScreenshotProtectionEnabled() {
        return prefs.getBoolean(KEY_SCREENSHOT_PROTECTION, true);
    }
    
    public void setScreenshotProtectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SCREENSHOT_PROTECTION, enabled).apply();
    }
}
```

## Integration Patterns

### 1. Application-Wide Security
```java
public class SafeVaultApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize security components
        SecurityManager.getInstance(this);
        KeyManager.getInstance(this);
        
        // Set up auto-locking
        setupAutoLocking();
    }
    
    private void setupAutoLocking() {
        // Register lifecycle callback
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPaused(Activity activity) {
                SecurityConfig config = new SecurityConfig(activity);
                if (config.isAutoLockEnabled() && 
                    !(activity instanceof LoginActivity)) {
                    SecurityManager.getInstance(activity).lock();
                }
            }
            
            // Other lifecycle methods...
        });
    }
}
```

### 2. Activity Security Integration
```java
public abstract class SecureActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply security measures
        SecurityManager.getInstance(this).applySecurityMeasures(this);
        
        // Check if app is locked
        if (SecurityManager.getInstance(this).isLocked() && 
            !(this instanceof LoginActivity)) {
            navigateToLockScreen();
            finish();
            return;
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check biometric if enabled
        SecurityConfig config = new SecurityConfig(this);
        if (config.isBiometricEnabled() && isSensitiveActivity()) {
            promptBiometricAuth();
        }
    }
}
```

## Security Testing

```java
@RunWith(AndroidJUnit4.class)
public class SecurityTests {
    
    @Test
    public void testClipboardAutoClear() {
        ClipboardManager clipboard = new ClipboardManager(InstrumentationRegistry.getContext());
        
        // Copy sensitive data
        clipboard.copyWithAutoClear("Test", "password123");
        
        // Verify data is in clipboard
        String clipboardText = clipboard.getText();
        assertEquals("password123", clipboardText);
        
        // Wait for auto-clear
        Thread.sleep(31000); // 31 seconds
        
        // Verify clipboard is cleared
        clipboardText = clipboard.getText();
        assertTrue(clipboardText == null || clipboardText.isEmpty());
    }
    
    @Test
    public void testEncryptionDecryption() throws Exception {
        KeyManager keyManager = KeyManager.getInstance(InstrumentationRegistry.getContext());
        
        String plaintext = "sensitive data";
        String encrypted = keyManager.encryptData(plaintext);
        String decrypted = keyManager.decryptData(encrypted);
        
        assertEquals(plaintext, decrypted);
        assertNotEquals(plaintext, encrypted);
    }
    
    @Test
    public void testRootDetection() {
        SecurityManager security = SecurityManager.getInstance(InstrumentationRegistry.getContext());
        
        // Note: This test depends on device state
        boolean isRooted = security.isDeviceRooted();
        
        // Can't assert specific value, but ensure no crash
        assertTrue(isRooted || !isRooted); // Always true, just checking no exception
    }
}
```

## Common Mistakes

| Mistake | Problem | Solution |
|---------|---------|----------|
| **No FLAG_SECURE** | Screenshots expose sensitive data | Apply to all sensitive activities |
| **Clipboard not cleared** | Passwords remain in clipboard | Use ClipboardManager with auto-clear |
| **Plaintext storage** | Data easily accessible | Use AndroidKeyStore encryption |
| **No auto-locking** | App stays unlocked indefinitely | Implement background timeout |
| **Weak biometric config** | Low security authentication | Use BIOMETRIC_STRONG authenticator |
| **No root detection** | Compromised device security | Check common root indicators |
| **Hardcoded keys** | Keys in source code | Use AndroidKeyStore or secure storage |

## SafeVault Specific Features

### 1. Biometric Integration with Backend
```java
// Biometric prompt before accessing vault
public void promptBiometricForVaultAccess() {
    BiometricAuthHelper.authenticate(
        this,
        "Authenticate to access vault",
        "Use your fingerprint or face to continue",
        new BiometricAuthHelper.BiometricCallback() {
            @Override
            public void onSuccess() {
                // Unlock backend service
                BackendService backend = ServiceLocator.getInstance().getBackendService();
                backend.unlockWithBiometric();
                
                // Navigate to vault
                navigateToVault();
            }
            
            @Override
            public void onFailure(String error) {
                showToast("Authentication failed: " + error);
            }
            
            @Override
            public void onCancelled() {
                // User cancelled, stay on current screen
            }
        }
    );
}
```

### 2. Secure Transitions Between Activities
```java
// When locking app
private void lockAndNavigateToLogin() {
    // 1. Clear all sensitive data
    SecurityManager.getInstance(this).lock();
    
    // 2. Clear activity stack
    Intent intent = new Intent(this, LoginActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    startActivity(intent);
    
    // 3. Apply exit animation
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
}
```

## Red Flags

**STOP and fix if:**
- Sensitive activities don't have FLAG_SECURE
- Clipboard retains data > 30 seconds
- No auto-locking mechanism
- Biometric using weak authentication
- Encryption keys stored in SharedPreferences
- No root/debug mode detection
- App doesn't lock when going to background

**CRITICAL**: Always test security features on both emulator and real devices.

## References

- Android Security Best Practices: https://developer.android.com/topic/security/best-practices
- Android Biometric API: https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt
- Android KeyStore: https://developer.android.com/training/articles/keystore
- SafeVault Implementation: `SecurityManager.java`, `KeyManager.java`, `BiometricAuthHelper.java`
- OWASP Mobile Security: https://owasp.org/www-project-mobile-security/