# Android Security Architecture Upgrade Skill

**Version:** 1.0.0
**Created:** 2026-02-08
**For:** SafeVault Android Password Manager

## What This Skill Does

Automates the migration of SafeVault's security storage architecture between versions, specifically from legacy architectures (v1/v2) to the current three-tier architecture (v3).

## Quick Start

### 1. Analyze Current Architecture

```bash
# Run the analysis script
cd E:/Android/SafeVault
bash .claude/skills/android-security-architecture-upgrade/analyze-architecture.sh
```

### 2. Invoke the Skill

When working with Claude Code:

```
I need to upgrade the security architecture from v2 to v3.
```

Or explicitly:

```
/android-security-architecture-upgrade
```

### 3. Follow the Interactive Prompts

The skill will guide you through:
- Analysis review
- Migration strategy selection
- Code generation
- Refactoring
- Testing
- Cleanup

## Files in This Skill

| File | Purpose |
|------|---------|
| `SKILL.md` | Main skill documentation for Claude Code |
| `analyze-architecture.sh` | Script to analyze current codebase |
| `test-template.java` | Template for migration verification tests |
| `migration-report-template.md` | Template for migration completion report |
| `README.md` | This file |

## Architecture Versions

### v1 (Deprecated)
- Key storage: Plain SharedPreferences
- Key derivation: None
- Encryption: RSA only

### v2 (Legacy)
- Key storage: KeyManager.java
- Key derivation: PBKDF2 (600,000 iterations)
- Encryption: AES-256-GCM

### v3 (Current)
- Key storage: SecureKeyStorageManager.java
- Key derivation: Argon2id (t=3, m=64MB)
- Encryption: AES-256-GCM
- Three-tier design: Root Keys → Data Key → RSA Keys

## Migration Strategies

### Full Refactor (Recommended)
- Delete old architecture
- Implement new from scratch
- Best for: Major version upgrades

### Gradual Migration
- New and old coexist
- Migrate incrementally
- Best for: Backward compatibility needed

### Adapter Pattern
- Bridge new and old
- Maintain external API
- Best for: Library dependencies

## Testing After Migration

```bash
# Run migration tests
./gradlew test --tests "*SecurityMigration*"

# Run all security tests
./gradlew test --tests "com.ttt.safevault.security.*"

# Run integration tests
./gradlew connectedAndroidTest
```

## Rollback Procedure

If migration fails:

1. Restore from git: `git checkout HEAD~1`
2. Or use backup: `cp *.legacy.java *.java`
3. Rebuild: `./gradlew clean assembleDebug`

## Safety Checklist

Before migrating:
- [ ] Commit current changes to git
- [ ] Create backup branch: `git checkout -b backup-before-migration`
- [ ] Run analysis script
- [ ] Review affected files

After migrating:
- [ ] All tests pass
- [ ] App builds successfully
- [ ] Manual testing completed
- [ ] Documentation updated

## Common Issues

### Issue: "UnsatisfiedLinkError: Argon2"
**Solution:** The argon2kt library needs proper native library loading.
Check that `argon2kt` dependency is in `app/build.gradle`.

### Issue: "AEADBadTagException"
**Solution:** Salt mismatch or wrong password.
Verify that saltBase64 parameter matches what was used during encryption.

### Issue: Tests pass but app crashes
**Solution:** Check ProGuard rules.
Add `-keep class com.ttt.safevault.crypto.** { *; }`

## Support

For issues or questions:
1. Check `docs/security-architecture.md` for architecture details
2. Review git history for past migrations
3. Consult `openspec/specs/crypto-security/` for specifications

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-02-08 | Initial skill creation |
