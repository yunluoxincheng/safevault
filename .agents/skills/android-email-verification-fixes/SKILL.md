---
name: android-email-verification-fixes
description: Use when email format validation fails, verification emails don't send, verification code is invalid, email confirmation doesn't complete, or email verification issues arise in Android apps
---

# Android Email Verification Fixes

## Overview

Systematic debugging and fixing for email verification flows including format validation, verification code sending/receiving, code validation, and confirmation completion.

**Core principle:** Email verification is a multi-stage flow. Debug each stage independently before moving to the next.

**REQUIRED PREREQUISITE:** Use `android-debugging-fixes` first for general debugging foundation.

## When to Use

Use this skill when:
- Email format validation rejects valid emails
- Email format validation accepts invalid emails
- Verification emails don't arrive
- Verification code is rejected as invalid
- Verification code expires too quickly
- User can't complete email confirmation
- Auto-read verification codes don't work

## Email Verification Flow

```
┌─────────────────────────────────────────────────────────────┐
│  Stage 1: Frontend Validation                                │
│  - User enters email                                         │
│  - Regex validation checks format                            │
│  - Real-time feedback on validity                            │
└──────────────────────┬──────────────────────────────────────┘
                       │ Valid format
                       v
┌─────────────────────────────────────────────────────────────┐
│  Stage 2: API Request                                        │
│  - App calls POST /v1/auth/send-verification                │
│  - Server sends email via SMTP service                      │
│  - Server returns success/error                             │
└──────────────────────┬──────────────────────────────────────┘
                       │ Email sent
                       v
┌─────────────────────────────────────────────────────────────┐
│  Stage 3: Email Delivery                                     │
│  - Email service delivers to user's inbox                   │
│  - User opens email                                          │
│  - User sees verification code                              │
└──────────────────────┬──────────────────────────────────────┘
                       │ User receives code
                       v
┌─────────────────────────────────────────────────────────────┐
│  Stage 4: User Input (or Auto-read)                          │
│  - User manually enters code, OR                            │
│  - App auto-reads from email (SMS-style for email)          │
└──────────────────────┬──────────────────────────────────────┘
                       │ Code entered
                       v
┌─────────────────────────────────────────────────────────────┐
│  Stage 5: Code Validation                                    │
│  - App calls POST /v1/auth/verify-code                      │
│  - Server validates code                                     │
│  - Server marks email as verified                           │
└──────────────────────┬──────────────────────────────────────┘
                       │ Valid code
                       v
┌─────────────────────────────────────────────────────────────┐
│  Stage 6: Completion                                         │
│  - User receives confirmation                               │
│  - App proceeds to next screen                              │
└─────────────────────────────────────────────────────────────┘
```

## Diagnostic Flowchart

```
┌─────────────────────────────────────────────────────────────┐
│  Stage 1: Test Email Format Validation                       │
│  - Enter test email addresses                                │
│  - Valid: user@example.com                                  │
│  - Invalid: user@, user@.com, @example.com                  │
│  └─> Validation wrong? ──> Fix regex                        │
│         │                                                    │
│         Correct                                              │
│         │                                                    │
│         v                                                    │
│  Stage 2: Test API Request                                   │
│  - Log request to /v1/auth/send-verification                │
│  - Check HTTP status (200, 400, 500)                         │
│  - Check response body                                       │
│  └─> Error? ──> Check network, API endpoint                │
│         │                                                    │
│         Success                                              │
│         │                                                    │
│         v                                                    │
│  Stage 3: Verify Email Delivery                              │
│  - Check spam/junk folder                                    │
│  - Verify email address is correct                          │
│  - Check server logs (if available)                          │
│  └─> Not received? ──> Check server, SMTP service          │
│         │                                                    │
│         Received                                             │
│         │                                                    │
│         v                                                    │
│  Stage 4: Test Code Input                                    │
│  - Log entered code                                          │
│  - Check for whitespace/case issues                          │
│  - Check input method (autofill issues)                      │
│  └─> Wrong format? ──> Fix input handling                  │
│         │                                                    │
│         Correct format                                       │
│         │                                                    │
│         v                                                    │
│  Stage 5: Test Code Validation                               │
│  - Log request to /v1/auth/verify-code                      │
│  - Check server response                                     │
│  - Verify code match on server                               │
│  └─> Invalid? ──> Check code, expiration time              │
│         │                                                    │
│         Valid                                                │
│         │                                                    │
│         v                                                    │
│  Stage 6: Verify Completion                                  │
│  - Check UI state update                                     │
│  - Verify user session updated                               │
│  - Check navigation to next screen                           │
└─────────────────────────────────────────────────────────────┘
```

## Common Issues and Solutions

### Issue 1: Email Regex Too Strict or Too Lenient

**Symptoms:**
- Valid email addresses rejected (e.g., `user+tag@example.com`, `user.name@example.co.uk`)
- Invalid email addresses accepted (e.g., `user@`, `@example.com`, `user..name@example.com`)

**Recommended Solution (Kotlin):**
```kotlin
// ✅ Comprehensive RFC 5322 compliant validation
object EmailValidator {

    private val EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +  // local-part
        "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"               // domain
    )

    fun isValid(email: String): Boolean {
        if (email.isBlank()) return false
        if (email.length > 254) return false // RFC 5321 max length

        return EMAIL_PATTERN.matcher(email.trim()).matches()
    }

    // Additional checks for common issues
    fun isValidWithDetails(email: String): EmailValidationResult {
        val trimmed = email.trim()

        when {
            trimmed.isBlank() ->
                return EmailValidationResult.Empty
            trimmed.length > 254 ->
                return EmailValidationResult.TooLong
            !trimmed.contains("@") ->
                return EmailValidationResult.MissingAtSymbol
            trimmed.count { it == '@' } > 1 ->
                return EmailValidationResult.MultipleAtSymbols
            trimmed.startsWith("@") || trimmed.endsWith("@") ->
                return EmailValidationResult.InvalidFormat
            !EMAIL_PATTERN.matcher(trimmed).matches() ->
                return EmailValidationResult.InvalidPattern
            else ->
                return EmailValidationResult.Valid
        }
    }
}

sealed class EmailValidationResult {
    object Valid : EmailValidationResult()
    object Empty : EmailValidationResult()
    object TooLong : EmailValidationResult()
    object MissingAtSymbol : EmailValidationResult()
    object MultipleAtSymbols : EmailValidationResult()
    object InvalidFormat : EmailValidationResult()
    object InvalidPattern : EmailValidationResult()

    val isValid: Boolean get() = this is Valid

    fun getErrorMessage(): String = when (this) {
        Valid -> ""
        Empty -> "Email cannot be empty"
        TooLong -> "Email is too long (max 254 characters)"
        MissingAtSymbol -> "Email must contain @ symbol"
        MultipleAtSymbols -> "Email can only contain one @ symbol"
        InvalidFormat -> "Email format is invalid"
        InvalidPattern -> "Please enter a valid email address"
    }
}
```

**Recommended Solution (Java):**
```java
// ✅ Comprehensive RFC 5322 compliant validation (Java version)
public final class EmailValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +  // local-part
        "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"               // domain
    );

    public static boolean isValid(String email) {
        if (email == null || email.isEmpty()) return false;
        if (email.length() > 254) return false; // RFC 5321 max length

        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    // Additional checks for common issues
    public static EmailValidationResult isValidWithDetails(String email) {
        if (email == null) return EmailValidationResult.EMPTY;

        String trimmed = email.trim();

        if (trimmed.isEmpty()) {
            return EmailValidationResult.EMPTY;
        }
        if (trimmed.length() > 254) {
            return EmailValidationResult.TOO_LONG;
        }
        if (!trimmed.contains("@")) {
            return EmailValidationResult.MISSING_AT_SYMBOL;
        }
        if (trimmed.indexOf("@") != trimmed.lastIndexOf("@")) {
            return EmailValidationResult.MULTIPLE_AT_SYMBOLS;
        }
        if (trimmed.startsWith("@") || trimmed.endsWith("@")) {
            return EmailValidationResult.INVALID_FORMAT;
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            return EmailValidationResult.INVALID_PATTERN;
        }

        return EmailValidationResult.VALID;
    }
}

public enum EmailValidationResult {
    VALID,
    EMPTY,
    TOO_LONG,
    MISSING_AT_SYMBOL,
    MULTIPLE_AT_SYMBOLS,
    INVALID_FORMAT,
    INVALID_PATTERN;

    public boolean isValid() {
        return this == VALID;
    }

    public String getErrorMessage() {
        switch (this) {
            case VALID: return "";
            case EMPTY: return "Email cannot be empty";
            case TOO_LONG: return "Email is too long (max 254 characters)";
            case MISSING_AT_SYMBOL: return "Email must contain @ symbol";
            case MULTIPLE_AT_SYMBOLS: return "Email can only contain one @ symbol";
            case INVALID_FORMAT: return "Email format is invalid";
            case INVALID_PATTERN: return "Please enter a valid email address";
            default: return "Invalid email address";
        }
    }
}
```

---

### Issue 2: Verification Email Not Received

**Symptoms:**
- API returns success (200)
- User never receives verification email
- No error message shown

**Solution:**
```kotlin
// 1. Inform user about spam folder
fun showEmailSentDialog(email: String) {
    AlertDialog.Builder(context)
        .setTitle("Check your inbox")
        .setMessage(
            "We sent a verification email to:\n$email\n\n" +
            "If you don't see it within a few minutes:\n" +
            "• Check your spam/junk folder\n" +
            "• Make sure the email address is correct\n" +
            "• Try resending the verification email"
        )
        .setPositiveButton("OK") { _, _ ->
            startVerificationTimer()
        }
        .setNeutralButton("Resend") { _, _ ->
            resendVerificationEmail()
        }
        .show()
}

// 2. Add rate limiting on client
class VerificationEmailSender(
    private val apiService: ApiService
) {
    private var lastSentTime = 0L
    private val MINIMUM_INTERVAL = 60_000 // 1 minute

    suspend fun send(email: String): Result<Unit> {
        val now = System.currentTimeMillis()
        if (now - lastSentTime < MINIMUM_INTERVAL) {
            val remaining = (MINIMUM_INTERVAL - (now - lastSentTime)) / 1000
            return Result.Error("Please wait $remaining seconds before resending")
        }

        return try {
            val response = apiService.sendVerificationEmail(email)
            if (response.isSuccessful && response.body()?.success == true) {
                lastSentTime = now
                Result.Success(Unit)
            } else {
                Result.Error(response.body()?.message ?: "Failed to send email")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
}
```

---

### Issue 3: Verification Code Invalid/Expired

**Solution:**
```kotlin
// 1. Handle whitespace and case
fun sanitizeInput(code: String): String {
    return code.trim().uppercase() // Or lowercase, depending on server
}

// 2. Show expiration time clearly
fun startVerificationTimer(expiresInMinutes: Int) {
    val totalMs = expiresInMinutes * 60 * 1000L
    val remainingMs = totalMs - (System.currentTimeMillis() - codeSentTime)

    countdownTimer = object : CountDownTimer(remainingMs, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            val seconds = millisUntilFinished / 1000
            timerText.text = "Code expires in ${seconds}s"
        }

        override fun onFinish() {
            timerText.text = "Code expired. Please request a new one."
            verifyButton.isEnabled = false
        }
    }.start()
}

// 3. Format code input for better UX
class CodeFormattingTextWatcher : TextWatcher {
    override fun afterTextChanged(s: Editable?) {
        var code = s?.toString()?.replace(" ", "")?.uppercase() ?: ""
        s?.clear()
        s?.append(code)
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}
```

---

### Issue 4: Auto-Read Verification Code Doesn't Work

**Solution:**
```xml
<!-- Input XML must include autocomplete hint -->
<com.google.android.material.textfield.TextInputLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="Verification code"
    app:boxBackgroundMode="outlined">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/verificationCodeInput"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="number"
        android:maxLength="6"
        android:importantForAutofill="yes"
        android:autofillHints="smsVerificationCode" />
</com.google.android.material.textfield.TextInputLayout>
```

```kotlin
// For email-based codes, implement manual "copy from email" feature
// Use deep links in email: yourapp://verify?code=123456&email=user@example.com

// Handle deep link in activity
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val code = intent?.data?.getQueryParameter("code")
    val email = intent?.data?.getQueryParameter("email")

    if (code != null && email != null) {
        codeInput.setText(code)
        verifyCode(email, code)
    }
}
```

---

## Testing Checklist

- [ ] Test with valid email formats (various TLDs)
- [ ] Test with invalid email formats
- [ ] Test email verification send
- [ ] Test verification code entry
- [ ] Test expired code
- [ ] Test incorrect code
- [ ] Test resend functionality
- [ ] Test spam/junk folder instructions
- [ ] Test deep link verification
- [ ] Test with slow network

## Encountering Unknown Issues?

1. **Log every stage** - Email, API, validation, completion
2. **Test with real email services** - Gmail, Outlook, Yahoo
3. **Check server logs** - Verify email was sent
4. **Verify SMTP service status** - SendGrid, Mailgun, etc.
5. **Review backend implementation**

**After solving:** Update this skill with the new issue and solution

## Related Skills

- **android-debugging-fixes** - General Android debugging (use first)
- **android-network-sync-fixes** - API request issues

## Red Flags - STOP and Verify

If you catch yourself thinking:
- "Just use a simple regex for email"
- "Email always arrives instantly"
- "Users won't make typos in the code"
- "Skip the resend button"

**ALL of these mean: STOP. Email verification has specific UX requirements.**
