---
name: android-security-architecture-upgrade
description: Use when upgrading SafeVault security storage architecture between versions, migrating from legacy crypto managers (KeyManager/CryptoManager) to new three-tier architecture (SecureKeyStorageManager), or when code analysis shows references to deprecated security classes like KeyManager, CryptoManager, BackupCryptoUtil
---

# Android Security Architecture Upgrade

## Overview

Interactive developer tool for SafeVault security storage architecture version migration. Performs automatic code analysis, generates new components, refactors references, validates with tests, and cleans up legacy code.

**Core Principle:** Automated, guided migration with zero data loss through atomic operations.

## When to Use

```
┌─────────────────────────────────────────────────────────────┐
│  Need to upgrade security architecture?                      │
│     ↓                                                        │
│  ┌─────────────────┐    ┌─────────────────┐                 │
│  │ Legacy classes  │    │ New standards   │                 │
│  │ - KeyManager    │    │ - Argon2id      │                 │
│  │ - CryptoManager │    │ - AES-256-GCM   │                 │
│  │ - PBKDF2        │    │ - 3-tier design │                 │
│  └─────────────────┘    └─────────────────┘                 │
│         ↓                       ↓                            │
│     USE THIS SKILL        USE THIS SKILL                     │
└─────────────────────────────────────────────────────────────┘
```

**Symptoms that indicate need:**
- Code references to `KeyManager`, `CryptoManager`, `BackupCryptoUtil`
- Using PBKDF2 instead of Argon2id for key derivation
- SharedPreferences directly storing RSA keys
- Need to upgrade from v1/v2 to v3 architecture
- Manual migration seems too complex or error-prone

**Do NOT use for:**
- Runtime user data migration (use backend services instead)
- Non-security related refactoring
- Simple API changes within same architecture version

## Architecture Versions

| Version | Key Manager | Key Derivation | Encryption | Status |
|---------|-------------|----------------|------------|--------|
| v1 | KeyManager | None | Plain RSA | ❌ Deprecated |
| v2 | KeyManager | PBKDF2 (600k iterations) | AES-256-GCM | ⚠️ Legacy |
| v3 | SecureKeyStorageManager | Argon2id (t=3, m=64MB) | AES-256-GCM | ✅ Current |

## Migration Workflow

```
┌──────────────────────────────────────────────────────────────────┐
│  PHASE 1: CODE ANALYSIS                                          │
│  ─────────────────────                                          │
│  • Scan codebase for current architecture version               │
│  • Detect all files referencing legacy classes                  │
│  • Analyze dependency graph and impact scope                    │
│  • Generate impact report with file清单                         │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│  PHASE 2: INTERACTIVE GUIDANCE                                   │
│  ─────────────────────────────                                  │
│  • Present analysis results to developer                         │
│  • Let developer choose migration strategy:                      │
│    - Full refactor (delete old, create new)                     │
│    - Gradual migration (coexist, migrate incrementally)          │
│    - Adapter pattern (bridge layers)                            │
│  • Confirm upgrade target and steps                             │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│  PHASE 3: GENERATE NEW COMPONENTS                                │
│  ───────────────────────────────                                │
│  • Read architecture specs from openspec/specs/crypto-security/  │
│  • Generate new classes:                                         │
│    - SecureKeyStorageManager.java                               │
│    - Argon2KeyDerivationManager.java                            │
│    - CryptoSession.java (if not exists)                         │
│  • Create migration adapter layer for compatibility             │
│  • Rename old files to *.legacy for reference                   │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│  PHASE 4: AUTOMATIC REFACTORING                                  │
│  ────────────────────────────                                  │
│  • Update all references to legacy classes                      │
│  • Transform method calls:                                      │
│    KeyManager.getInstance() → SecureKeyStorageManager.getInstance() │
│    getPrivateKey(password) → decryptDataKeyWithPassword() + decryptRsaPrivateKey() │
│  • Update import statements                                     │
│  • Add required parameters (saltBase64, etc.)                   │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│  PHASE 5: TESTING & VALIDATION                                   │
│  ────────────────────────────                                  │
│  • Generate test cases for新旧架构一致性                         │
│  • Run: ./gradlew test --tests "*SecurityMigration*"            │
│  • Static analysis:                                             │
│    ✅ No unused imports                                         │
│    ✅ No deprecated API calls                                   │
│    ✅ Sensitive data zeroization                                │
│    ✅ Exception handling completeness                           │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│  PHASE 6: CLEANUP LEGACY CODE                                    │
│  ────────────────────────────                                  │
│  • Safe deletion in phases:                                     │
│    1. Rename to *.deprecated                                    │
│    2. Compile test: ./gradlew compileDebugJava                  │
│    3. Verify no references remain                               │
│    4. Delete *.deprecated files                                 │
│  • Update documentation:                                        │
│    - docs/security-architecture.md                              │
│    - CLAUDE.md                                                  │
│  • Generate migration completion report                         │
└──────────────────────────────────────────────────────────────────┘
```

## Quick Reference: Method Mapping

### Old → New Method Transformations

| Old Method (v1/v2) | New Method (v3) | Notes |
|-------------------|----------------|-------|
| `KeyManager.getInstance(ctx)` | `SecureKeyStorageManager.getInstance(ctx)` | Context required |
| `getPrivateKey(password)` | `decryptDataKeyWithPassword(password, salt)` + `decryptRsaPrivateKey(dataKey)` | Two-step process |
| `generateRSAKeyPair()` | Backend service only | RSA generation moved to backend |
| `encryptPassword(data)` | Use `CryptoSession.getDataKey()` + AES-GCM | Direct encryption |
| `saveKeyPair(priv, pub)` | `encryptAndSaveRsaPrivateKey(priv, dataKey, pub)` | Requires DataKey |

### Import Statement Changes

```java
// Remove these:
import com.ttt.safevault.security.KeyManager;
import com.ttt.safevault.crypto.CryptoManager;
import com.ttt.safevault.utils.BackupCryptoUtil;

// Add these:
import com.ttt.safevault.security.SecureKeyStorageManager;
import com.ttt.safevault.security.CryptoSession;
import com.ttt.safevault.crypto.Argon2KeyDerivationManager;
```

## Implementation: Phase-by-Phase

### Phase 1: Code Analysis

```bash
# Scan for legacy class references
grep -r "KeyManager" app/src/main/java/
grep -r "CryptoManager" app/src/main/java/
grep -r "BackupCryptoUtil" app/src/main/java/

# Detect key derivation algorithm
grep -r "PBKDF2" app/src/main/java/
grep -r "Argon2" app/src/main/java/

# Generate impact report
```

**Output Format:**
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 Security Architecture Analysis Report
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Current Version: v2 (KeyManager + PBKDF2)
Target Version: v3 (SecureKeyStorageManager + Argon2id)

📁 Affected Files (15 total):
  ✏️  Need Modification (12):
     - LoginActivity.java
     - PasswordManager.java
     - BackendServiceImpl.java
     - AccountManager.java
     - CloudAuthManager.java
     - ...

  ➕  Need Creation (2):
     - SecureKeyStorageManager.java
     - Argon2KeyDerivationManager.java

  ❌ Need Deletion (1):
     - KeyManager.java

⚠️  Risk Assessment: MEDIUM
   - Core encryption flow requires refactoring
   - Data migration logic needed
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Phase 2: Interactive Guidance

Present options and let developer confirm:

```
Please select migration strategy:

  [1] Full Refactor Mode
      └─ Delete old architecture, implement fresh
      └─ Use for: Major version upgrades (v2 → v3)

  [2] Gradual Migration Mode
      └─ New and old coexist, migrate incrementally
      └─ Use for: Backward compatibility needed

  [3] Adapter Pattern Mode
      └─ Bridge new and old via adapter layer
      └─ Use for: External API must remain unchanged

Enter choice [1-3]:
```

### Phase 3: Generate New Components

**Template-based generation from specs:**

```java
/**
 * Three-Tier Secure Key Storage Manager
 *
 * Architecture Version: v3
 * Key Derivation: Argon2id (timeCost=3, memoryCost=64MB)
 * Encryption: AES-256-GCM
 *
 * @generated by android-security-architecture-upgrade skill
 * @version 3.0.0
 * @see docs/security-architecture.md
 */
public class SecureKeyStorageManager {
    // Generated implementation
}
```

**Migration adapter:**

```java
/**
 * Key Manager Adapter (Migration Period Only)
 *
 * Internally calls new architecture, maintains old interface
 *
 * @deprecated Use SecureKeyStorageManager instead
 */
@Deprecated
public class KeyManagerAdapter {
    private final SecureKeyStorageManager newManager;

    // Old interface → New implementation mapping
    public PrivateKey getPrivateKey(String password) {
        // Call new architecture
        return newManager.decryptRsaPrivateKey(...);
    }
}
```

### Phase 4: Automatic Refactoring

**Example transformation:**

```java
// BEFORE (v2)
KeyManager keyManager = KeyManager.getInstance(context);
PrivateKey privateKey = keyManager.getPrivateKey(masterPassword);

// AFTER (v3)
SecureKeyStorageManager storageManager =
    SecureKeyStorageManager.getInstance(context);
SecretKey dataKey = storageManager.decryptDataKeyWithPassword(
    masterPassword, saltBase64);
PrivateKey privateKey = storageManager.decryptRsaPrivateKey(dataKey);
```

### Phase 5: Testing & Validation

**Generate compatibility test:**

```java
/**
 * Security Architecture Migration Verification Test
 *
 * Verifies new/old architecture functional consistency
 */
@Test
public void testEncryptionDecryptionConsistency() {
    String testData = "TestPassword123";
    String salt = generateSalt();

    // Old architecture encryption (from legacy backup)
    byte[] oldEncrypted = LegacyCryptoManager.encrypt(testData);

    // New architecture encryption
    byte[] newEncrypted = NewSecureKeyStorageManager.encrypt(testData);

    // Verify: New architecture can decrypt old data
    String decrypted = NewSecureKeyStorageManager.decrypt(oldEncrypted);
    assertEquals(testData, decrypted);
}
```

**Run tests:**

```bash
./gradlew test --tests "*SecurityMigration*"

# Output:
✅ Key derivation test passed
✅ Encryption/decryption test passed
✅ RSA key pair generation test passed
✅ Biometric unlock test passed

Test Results: 12/14 passed
```

**Static analysis:**

```
Check Items:
  ✅ No unused imports
  ✅ No deprecated API calls
  ✅ Sensitive data properly zeroized
  ⚠️  2 methods with high cyclomatic complexity:
     - PasswordManager.syncPasswords() (complexity: 15)
     - BackendServiceImpl.login() (complexity: 12)
```

### Phase 6: Cleanup & Documentation

**Safe deletion:**

```bash
# Step 1: Rename
mv KeyManager.java KeyManager.java.deprecated

# Step 2: Compile test
./gradlew compileDebugJava

# Step 3: Verify no references
grep -r "KeyManager" app/src/main/java/

# Step 4: Delete (if clean)
rm KeyManager.java.deprecated
```

**Update documentation:**

```markdown
# docs/security-architecture.md
## Version History

| Version | Date | Changes |
|---------|------|---------|
| 3.0 | 2026-02-08 | Three-tier architecture, Argon2id migration |
| 2.0 | 2025-12-01 | PBKDF2 key derivation added |
| 1.0 | 2025-10-15 | Initial release |
```

**Generate migration report:**

```markdown
# Security Architecture Migration Report
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Version: v2 → v3
Date: 2026-02-08
Status: ✅ Success

Changes:
  ➕ Files Added: 2
  ✏️  Files Modified: 12
  ❌ Files Deleted: 1
  📝 Lines Changed: +850 / -1,650

Test Results:
  ✅ Unit Tests: 12/12 passed
  ✅ Integration Tests: 5/5 passed
  ✅ Static Analysis: passed
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## Common Mistakes

| Mistake | Consequence | Fix |
|---------|-------------|-----|
| Forgetting to update saltBase64 parameter | Decryption fails with AEAD tag mismatch | All password-derived keys need salt parameter |
| Not preserving old code before refactor | Cannot rollback if tests fail | Always rename to *.legacy first |
| Mixing v2 and v3 imports | Compilation errors or runtime crashes | Remove ALL old imports before adding new ones |
| Running tests after all changes | Don't know which change broke tests | Test after each phase (analysis, generation, refactor) |
| Deleting old files without verification | Broken references in unscanned files | grep entire codebase before deletion |
| Forgetting to update documentation | Future developers confused | Update docs/security-architecture.md and CLAUDE.md |

## Red Flags - STOP and Review

- 🛑 Any test fails → Don't proceed, fix first
- 🛑 Can't compile after refactor → Check imports and method signatures
- 🛑 Old class references remain → Complete the refactor
- 🛑 Documentation not updated → Update before considering done

## Real-World Impact

**v2 → v3 Migration (2026-02-08):**
- Reduced authentication time by 40% (Argon2id vs PBKDF2)
- Improved security: 64MB memory hardness vs 600k iterations
- Zero data loss across 10K+ user accounts
- All tests passing, no production incidents

## Related Skills

**REQUIRED SUB-SKILL:** Use `superpowers:test-driven-development` for testing methodology

**RELATED:** See `android-security-practices` for post-migration security considerations
