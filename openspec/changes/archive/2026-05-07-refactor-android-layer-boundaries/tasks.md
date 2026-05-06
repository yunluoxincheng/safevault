## 1. Inventory
- [x] 1.1 Map top-level Android packages and package-level dependency direction
- [x] 1.2 Identify UI classes directly using `network`, `security`, `crypto`, or `data`
- [x] 1.3 Classify direct dependencies as acceptable platform boundary, migration target, or needs deeper design
- [x] 1.4 Record high-risk flows in `task.md`

## 2. Target Design
- [x] 2.1 Define target package ownership for auth/account security
- [x] 2.2 Define target package ownership for contact/friend flows
- [x] 2.3 Define target package ownership for sharing flows
- [x] 2.4 Define target package ownership for autofill and key migration system-boundary flows

## 3. Implementation Slices
- [x] 3.1 Normalize only layer-boundary-related package placement or manifest-visible bootstrap inconsistencies found during inventory
- [x] 3.2 Extract auth/login/register orchestration from UI into ViewModel/service boundaries
- [x] 3.3 Extract account security orchestration from UI into manager/service boundaries
- [x] 3.4 Extract contact/friend orchestration from UI into ViewModel/manager boundaries
- [x] 3.5 Extract sharing orchestration from UI into ViewModel/manager boundaries
- [x] 3.6 Review autofill and key migration classes for minimal safe boundary improvements
- [x] 3.7 Remove temporary compatibility wrappers after all call sites move

## 4. Documentation
- [x] 4.1 Update package boundary docs and `docs/directory-standards.md`
- [x] 4.2 Update `task.md` after each meaningful slice
- [x] 4.3 Update OpenSpec spec delta if the target boundary changes during review

## 5. Verification
- [x] 5.1 Run `.\gradlew.bat test`
- [x] 5.2 Run `.\gradlew.bat :app:assembleDebug`
- [x] 5.3 Review login, registration, account security, sharing, contact, autofill, and key migration behavior
- [x] 5.4 Confirm no backend API or database contract changed

## Notes
- 2026-05-07: KeyMigrationActivity boundary-focused pass removed direct UI access to `SecureKeyStorageManager` by routing key-status and key-detail reads via `KeyMigrationService`.
- 2026-05-07: KeyMigration start decision (`session/password/need-auth/not-available`) is now resolved by `KeyMigrationService` and consumed by UI as a decision object.
- 2026-05-07: KeyMigration error handling now uses `MigrationErrorType` classification from `KeyMigrationService`; UI only maps category to message/retry entry.
- 2026-05-07: `3.6` is closed with intentional minimal retention: single-button retry trigger remains in UI, while retry eligibility is decided by service error classification.
- 2026-05-07: user manual verification confirmed key migration failure-path and cancel-path behavior; `5.3` is closed.
- 2026-05-07: sharing slice closed by removing `ShareActivity` UI-side cloud-share orchestration (packet/build/request flow) and routing share creation through ViewModel + `BackendService`/manager boundary; UI keeps interaction, auth entry, and safety-number verification display logic.
