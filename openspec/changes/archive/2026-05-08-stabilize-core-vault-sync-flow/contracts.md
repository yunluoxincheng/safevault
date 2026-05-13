# Core Vault Flow Contracts

## 1. Canonical Register-Login-VaultInit-Unlock Flow

### 1.1 Registration

```
User → RegisterActivity
  → AuthViewModel.registerWithEmail(email, username)
    → POST /v1/auth/register/email  (pending user in Redis)
  → poll verification status (max 10 min, 3s interval)
    → GET /v1/auth/verification-status?email=...
  → POST /v1/auth/verify-email  (creates User entity)
  → User sets master password
  → BackendService.initialize(masterPassword):
    1. Generate salt (Base64, 32 bytes)
    2. Derive PasswordKey via Argon2id(masterPassword, salt, 128MB, t=3, p=4)
    3. Generate random DataKey (AES-256)
    4. Generate RSA-2048 key pair
    5. Encrypt DataKey with PasswordKey → store in SharedPreferences
    6. Encrypt DataKey with DeviceKey (optional, biometric path)
    7. Encrypt RSA private key with DataKey → store in SharedPreferences
    8. Store RSA public key (plaintext) in SharedPreferences
    9. Save salt + initialized flag in SharedPreferences
   10. Cache DataKey in SessionGuard (unlocked state)
  → Upload RSA public key to backend
  → Navigate to MainActivity
```

**Required post-condition**: `SecureKeyStorageManager.isMigrated()` returns true,
`SessionGuard.isUnlocked()` returns true, backend has User record with publicKey.

### 1.2 Login (returning user, known device)

```
User → LoginActivity
  → AuthViewModel.loginWithEmail(email, password)
    → POST /v1/auth/login/precheck (get nonce)
    → Derive key, compute HMAC signature
    → POST /v1/auth/login/email (challenge-response)
    → Receive accessToken + refreshToken
  → TokenManager stores tokens
  → BackendService.unlock(masterPassword):
    1. Read salt from SharedPreferences
    2. Derive PasswordKey via Argon2id(masterPassword, salt, ...)
    3. Decrypt DataKey using PasswordKey
    4. Cache DataKey in SessionGuard
    5. Save masterPassword in AccountManager (memory only)
  → Trigger sync if returning user
  → Navigate to MainActivity
```

**Required post-condition**: `SessionGuard.isUnlocked()` returns true,
vault data available for display.

### 1.3 Login (new device)

```
User → LoginActivity (same as 1.2 through authentication)
  → BackendService.isInitialized() returns false (no local salt/DataKey)
  → BackendService.initialize(masterPassword):
    1. Generate new local salt (independent of other devices)
    2. Derive new PasswordKey
    3. Generate new DataKey
    4. Generate new RSA key pair
    5. Store all keys locally as in registration
  → Trigger cloud vault pull:
    1. Download encrypted vault from backend
    2. Decrypt with masterPassword + cloudSalt
    3. Deserialize and save to local Room DB
  → Navigate to MainActivity
```

**Required post-condition**: Local keys created, cloud data imported.
Note: the new device gets a fresh local DataKey but uses the master password
to decrypt the cloud vault blob.

### 1.4 Unlock (biometric)

```
User → biometric prompt
  → SecureKeyStorageManager.unlockDataKeyWithBiometric():
    1. Get DeviceKey from AndroidKeyStore (biometric auth required)
    2. Decrypt DataKey using DeviceKey
  → SessionGuard.unlockWithDataKey(dataKey)
  → App continues without re-entering master password
```

### 1.5 Lock/Logout

```
Lock trigger (background timeout or manual):
  → SessionGuard.lock() / lockSession()
    1. SensitiveData.close() on DataKey (memory zeroing)
    2. unlocked = false
  → All decrypted data inaccessible until next unlock

Logout:
  → SessionGuard.lock()
  → TokenManager.clearTokens()
  → Navigate to LoginActivity
```

### 1.6 Relogin

```
Same as 1.2 (login with password) or 1.4 (biometric unlock).
After unlock, sync is triggered to pull latest data from cloud.
```

## 2. Encrypted Vault Item CRUD

### 2.1 Create

```
User → AddPasswordFragment
  → EditPasswordViewModel.save(item)
    → PasswordManager.saveItem(item):
      1. sessionGuard.requireDataKey() → DataKey
      2. For each sensitive field (title, username, password, url, notes):
         - Generate random IV (12 bytes)
         - AES-GCM encrypt with DataKey
         - Add secure padding (v2)
         - Store as "v2:base64(iv):base64(ciphertext)"
      3. Save EncryptedPasswordEntity to Room
    → Return item ID
```

### 2.2 Read

```
User → PasswordListFragment / PasswordDetailFragment
  → PasswordManager.getAllItems() / decryptItem():
    1. sessionGuard.requireDataKey() → DataKey
    2. Read EncryptedPasswordEntity from Room
    3. For each encrypted field:
       - Parse version, IV, ciphertext
       - AES-GCM decrypt with DataKey
       - Remove secure padding
    4. Return PasswordItem with plaintext fields
```

### 2.3 Update

```
Same as Create, but with existing item ID.
Room replaces the existing record.
```

### 2.4 Delete

```
User → PasswordDetailFragment (delete)
  → PasswordManager.deleteItem(id):
    1. Delete EncryptedPasswordEntity from Room
  → Optionally trigger sync push (data removed from cloud too)
```

### 2.5 Constraints

- All CRUD operations require `SessionGuard.isUnlocked() == true`.
- Local storage is always encrypted at rest (Room contains ciphertext only).
- Backend never sees plaintext fields; sync sends a single encrypted blob.

## 3. Sync Push and Sync Pull Contracts

### 3.1 Sync Push

```
Android → Backend:
POST /v1/vault/sync
Authorization: Bearer <accessToken>

Request Body (VaultSyncRequest):
{
  "encryptedData": "<Base64 AES-256-GCM ciphertext>",
  "dataIv": "<Base64 12-byte IV>",
  "dataAuthTag": "<Base64 16-byte GCM tag>",
  "salt": "<Base64 random salt for this upload>",
  "clientVersion": <long, current local version>,
  "forceSync": false
}

Backend Behavior:
1. Extract userId from JWT
2. Load server vault for userId
3. If clientVersion < server.version AND !forceSync → return conflict
4. If no server vault exists → create new vault (version 1)
5. If clientVersion >= server.version → update vault, increment version
6. Return VaultSyncResponse

Success Response (VaultSyncResponse):
{
  "success": true,
  "hasConflict": false,
  "newVersion": <long>,
  "vault": { "encryptedData": "...", "dataIv": "...", "dataAuthTag": "...", "salt": "...", "version": ... },
  "lastSyncedAt": "<ISO datetime>"
}

Conflict Response:
{
  "success": false,
  "hasConflict": true,
  "conflictMessage": "...",
  "serverVersion": <long>,
  "clientVersion": <long>,
  "serverVault": { ... }
}
```

### 3.2 Sync Pull

```
Android → Backend:
GET /v1/vault/{userId}
Authorization: Bearer <accessToken>

Response (VaultResponse):
{
  "encryptedData": "<Base64 AES-256-GCM ciphertext>",
  "dataIv": "<Base64 12-byte IV>",
  "dataAuthTag": "<Base64 16-byte GCM tag>",
  "salt": "<Base64 salt used during encryption>",
  "version": <long>
}

Android Behavior:
1. Check SessionGuard.isUnlocked()
2. Get masterPassword from AccountManager (memory)
3. Decrypt: Argon2id(masterPassword, cloudSalt) → AES-GCM decrypt
4. Deserialize JSON → List<PasswordItem>
5. Clear local Room data
6. Insert decrypted items into Room
7. Update local vault version
```

### 3.3 Version Rules

- Each successful push increments the server version by 1.
- Client tracks local version in SharedPreferences (`vault_version`).
- On sync: if versions differ, conflict is reported to user.
- Conflict resolution: user chooses USE_CLOUD (overwrite local) or USE_LOCAL (overwrite cloud).

## 4. Sync State Model

```
States:
  IDLE       - No sync in progress, no recent error
  SYNCING    - Sync operation in progress
  SUCCESS    - Sync completed successfully (transitions to IDLE after 2s)
  FAILED     - Sync failed (transitions to IDLE after 3s)
  CONFLICT   - Version conflict detected, awaiting user resolution
  OFFLINE    - No network connectivity

State Transitions:
  IDLE → SYNCING (user triggers sync or auto-sync fires)
  SYNCING → SUCCESS (push or pull completed)
  SYNCING → FAILED (network/auth/decryption error)
  SYNCING → CONFLICT (version mismatch detected)
  SUCCESS → IDLE (after 2s delay)
  FAILED → IDLE (after 3s delay)
  CONFLICT → SYNCING (user chooses resolution strategy)
  CONFLICT → IDLE (user cancels)
```

## 5. Retry and Error Behavior

### 5.1 Error Classification

| Error Type | Cause | User Message | Retry |
|---|---|---|---|
| NETWORK | IOException, timeout, SSL | "网络连接失败" | Auto-retry up to 3x with backoff |
| AUTH_EXPIRED | 401, token refresh failed | "登录已过期，请重新登录" | Redirect to login |
| VALIDATION | 400, missing fields | "数据格式错误" | No retry (fix input) |
| CONFLICT | 409, version mismatch | "数据冲突，请选择保留哪个版本" | Manual resolution |
| DECRYPTION_FAILED | AEADBadTagException | "解密失败" | No retry (data integrity issue) |
| SERVER | 500, 502, 503 | "服务器错误" | Auto-retry up to 3x |
| OFFLINE | No connectivity | "无网络连接" | Auto-retry when connectivity restored |

### 5.2 Token Refresh Behavior

- On 401 response: automatically attempt token refresh via `POST /v1/auth/refresh`.
- If refresh succeeds: retry the original request with new token.
- If refresh fails: redirect to LoginActivity, lock SessionGuard.

### 5.3 Conflict Resolution

- Present both versions to user (cloud vs local).
- Options: USE_CLOUD (download and replace local), USE_LOCAL (upload and replace cloud), CANCEL.
- After resolution: sync state returns to IDLE.
- No automatic merge; entire vault is a single blob.

### 5.4 Decryption Failure

- If cloud data cannot be decrypted with current master password:
  - Possible causes: wrong password, corrupted data, salt mismatch.
  - Do NOT overwrite local data.
  - Show error to user, suggest re-login with correct password.
  - If persistent, suggest contacting support or resetting vault.
