# Core Vault Flow

This document describes the verified core vault flow for SafeVault.

## Canonical Flow

```
Register → Master Password Setup → Key Init → Login/Unlock →
Create Password → Local Encrypted Save → Encrypted Cloud Sync →
Lock/Logout → Relogin → Pull → Decrypt → Display
```

## Registration Flow

1. User enters email + username → verification email sent
2. User verifies email
3. User sets master password (min 8 chars)
4. Local key initialization:
   - Salt generated (Base64, 32 bytes)
   - PasswordKey derived via Argon2id (master password + salt, 128MB, t=3, p=4)
   - Random DataKey generated (AES-256)
   - RSA-2048 key pair generated
   - DataKey encrypted with PasswordKey + DeviceKey (dual encryption)
   - RSA private key encrypted with DataKey
5. Registration request to backend includes: RSA public key, encrypted private key, salt, password verifier
6. On success: JWT tokens stored, user redirected to login

## Login Flow

### Password Login
1. Challenge-response: precheck → derive key → HMAC signature → login
2. JWT tokens (access + refresh) stored by TokenManager
3. Check `isInitialized()`:
   - True (existing user): `unlock(masterPassword)` → derive PasswordKey → decrypt DataKey → SessionGuard
   - False (new device): `initialize(masterPassword)` → new local keys → pull cloud data
4. Session master password saved in AccountManager (memory only)
5. Cloud data sync triggered (background, non-blocking)

### Biometric Unlock
1. DeviceKey from AndroidKeyStore (requires biometric auth)
2. DataKey decrypted using DeviceKey
3. SessionGuard populated with DataKey
4. Cloud sync attempted only if master password is available in memory
5. If master password not available: local CRUD works, sync deferred until password login

## Password CRUD

All operations require `SessionGuard.isUnlocked() == true`.

### Create/Update
- Each sensitive field encrypted independently with DataKey (AES-256-GCM)
- Secure padding v2 applied before encryption
- Format: `v2:base64(iv):base64(ciphertext)`
- Stored in Room database as encrypted entities

### Read
- Fields decrypted with DataKey from SessionGuard
- Guarded Execution: `requireDataKey()` throws `SessionLockedException` if locked

### Delete
- Remove encrypted entity from Room
- Optional sync push to remove from cloud

## Cloud Sync

### Push
1. Serialize all local items to JSON
2. Generate random salt
3. Encrypt with master password + salt via Argon2id/AES-GCM (fixed params: 128MB, t=3, p=4)
4. POST to `/v1/vault/sync` with version conflict check
5. Backend stores only encrypted blob

### Pull
1. GET `/v1/vault/{userId}` → download encrypted vault
2. Decrypt with master password + cloud salt via Argon2id/AES-GCM
3. Deserialize JSON → replace local data
4. Update local version number

### Version Conflict
- Server returns conflict if `clientVersion < serverVersion`
- Client receives both local and server versions
- User chooses: USE_CLOUD (overwrite local) or USE_LOCAL (overwrite cloud) or CANCEL

### Master Password Dependency
- Cloud sync encryption uses the master password, not the DataKey
- After biometric-only unlock, master password may not be available
- In this case, sync is gracefully skipped and user is notified

## Lock/Logout

### Lock (background timeout)
- `SessionGuard.lock()` → DataKey zeroed from memory
- Session master password remains in AccountManager (for quick re-unlock)
- All decrypted data inaccessible

### Logout
- `SessionGuard.lock()` → DataKey zeroed
- `AccountManager.clearSessionMasterPassword()` → master password removed from memory
- Tokens revoked via backend
- Navigate to LoginActivity

## Key Architecture (Three-Tier)

```
Level 3: RSA/X25519/Ed25519 private keys (encrypted with DataKey)
Level 2: DataKey (encrypted with PasswordKey + DeviceKey)
Level 1: PasswordKey (Argon2id from master password) + DeviceKey (AndroidKeyStore)
```

## Error Handling

| Error | Behavior |
|---|---|
| Session locked | `SessionLockedException` thrown, UI prompts for unlock |
| Master password unavailable for sync | Sync skipped, user notified |
| Encryption failure on save | Operation aborted, explicit error shown |
| Registration backend failure | Local keys rolled back, retry allowed |
| Token expired | Auto-refresh attempted, redirect to login on failure |
| Version conflict | User prompted to choose resolution strategy |
