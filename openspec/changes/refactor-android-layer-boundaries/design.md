# Design: Refactor Android Layer Boundaries

## Overview
This change incrementally aligns the Android app with the existing Java/XML MVVM style. It does not introduce a new framework. It narrows UI classes to rendering and interaction wiring while moving feature orchestration into ViewModels and existing or new focused managers.

## Current Pressure Points
Observed examples include UI classes that reference `RetrofitClient`, `TokenManager`, `SecureKeyStorageManager`, Room database types, biometric managers, and share crypto helpers directly. Some of these dependencies are acceptable at system-boundary Activities, such as autofill or biometric prompts, but many should be mediated by ViewModels or service/manager facades.

## Target Boundaries
Android dependency direction remains:

`ui -> viewmodel -> model/service -> (network|security|crypto|data)`

Responsibilities:
- `ui`: view binding, adapters, dialogs, navigation, permission prompts, and lifecycle-safe rendering.
- `viewmodel`: UI state, user intents, validation flow, async orchestration, and error surfaces.
- `service` and `service/manager`: capability-level workflows such as auth, contact sync, sharing, key migration, and account security.
- `network`: Retrofit APIs, interceptors, token transport, WebSocket transport.
- `security` and `crypto`: UI-independent security policy and cryptographic operations where possible.
- `data`: Room database, DAO, and local persistence boundary.

## Migration Slices
Preferred order:
1. Read-only inventory and dependency map.
2. Low-risk package/file consistency fixes only when they unblock layer-boundary work or correct manifest-visible bootstrap placement discovered in inventory.
3. Auth and account security UI extraction.
4. Contact/friend flow extraction.
5. Sharing flow extraction.
6. Autofill and key migration review, because they touch Android system boundaries and sensitive storage.

Each slice should preserve behavior and avoid unrelated visual changes.

## Compatibility Strategy
- Prefer adding small facades or manager methods before moving many call sites.
- Keep public method names stable when possible.
- Use deprecated compatibility wrappers only when they reduce risk during a package move, and remove them in a later cleanup task.
- Avoid changing serialized models, database entities, API DTOs, navigation IDs, resource names, and manifest-visible class names unless specifically required by a slice.

## Testing Strategy
- Run `.\gradlew.bat test` after non-trivial Java refactors.
- Run `.\gradlew.bat :app:assembleDebug` for package moves, manifest-visible classes, resources, navigation, or ViewBinding changes.
- Add or adjust unit tests for extracted ViewModels/managers when the slice contains meaningful behavior.
- Manually review flows that deal with master password, biometrics, token persistence, sharing payloads, and autofill.

## Security Notes
- Do not log secrets, tokens, private keys, master passwords, verification codes, salts, tags, or decrypted password fields.
- Do not weaken `FLAG_SECURE`, biometric gates, token invalidation, clipboard clearing, or KeyStore behavior.
- Existing security managers should remain the source of truth unless a separate security proposal authorizes a behavior change.
