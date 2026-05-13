# Manual Verification Checklist

Use this checklist to verify the core vault flow against a local backend.

## Prerequisites

- [ ] PostgreSQL and Redis running (`docker compose up -d postgres redis` from `server/`)
- [ ] Backend running (`./mvnw spring-boot:run` from `server/`)
- [ ] Android debug APK installed on device/emulator (`./gradlew :app:assembleDebug` from `android/`)

## Test Cases

### 1. Registration
- [ ] Enter valid email and username
- [ ] Receive and submit verification code
- [ ] Set master password (8+ chars)
- [ ] Registration completes successfully
- [ ] Redirected to login screen

### 2. First Login
- [ ] Login with registered email and master password
- [ ] Vault initialized (local keys created)
- [ ] Navigate to main screen
- [ ] No passwords displayed (empty vault)

### 3. Create Password
- [ ] Add a new password entry (title, username, password, URL)
- [ ] Entry appears in password list
- [ ] Entry details are correct after tapping

### 4. Cloud Sync Push
- [ ] Trigger manual sync
- [ ] Sync completes successfully
- [ ] Check backend database: `SELECT encrypted_data, data_iv, data_auth_tag, salt FROM user_vaults`
- [ ] Verify backend stores only ciphertext (no plaintext fields)

### 5. Lock/Unlock
- [ ] Lock the app (background timeout or manual lock)
- [ ] Password list is not visible or accessible
- [ ] Unlock with master password
- [ ] Passwords are displayed correctly after unlock

### 6. Logout and Relogin
- [ ] Logout from app
- [ ] Login again with same credentials
- [ ] Sync triggered automatically
- [ ] Previously created password appears after sync pull

### 7. Biometric Unlock (if supported)
- [ ] Enable biometric authentication
- [ ] Lock app
- [ ] Unlock with biometric
- [ ] Local data accessible
- [ ] Cloud sync behavior: sync attempted only if master password available

### 8. Security Checks
- [ ] No plaintext passwords in Logcat output
- [ ] No plaintext passwords in backend logs
- [ ] Backend database contains only encrypted blobs
- [ ] `FLAG_SECURE` prevents screenshots on password screens
- [ ] Clipboard cleared after copying password

### 9. Error Handling
- [ ] Sync failure: error message displayed, login not blocked
- [ ] Wrong master password: clear error, retry allowed
- [ ] Network offline: sync deferred, local operations work
- [ ] Token expiry: auto-refresh or redirect to login

## Backend Verification

```sql
-- Verify vault data is encrypted (no plaintext)
SELECT user_id, LENGTH(encrypted_data) as data_len,
       LENGTH(data_iv) as iv_len, LENGTH(salt) as salt_len,
       version FROM user_vaults;

-- Verify private keys are encrypted
SELECT user_id, LENGTH(encrypted_private_key) as key_len,
       LENGTH(iv) as iv_len, version FROM user_private_keys;

-- Verify no plaintext password fields exist
SELECT column_name FROM information_schema.columns
WHERE table_name = 'user_vaults' AND column_name LIKE '%password%';
-- Expected: no results
```

## Verification Commands

```bash
# Android unit tests (from android/)
./gradlew test

# Android compile (from android/)
./gradlew :app:assembleDebug

# Backend tests (from server/)
./mvnw test
```
