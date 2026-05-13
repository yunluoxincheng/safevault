# SafeVault Android Rules

This project is currently a Java/XML Android app. These rules override older generic Android templates.

## Platform

- Language: Java 17.
- Minimum SDK: Android 10 / API 29.
- Target/compile SDK: 36.
- UI stack: XML layouts, ViewBinding, Material Components, ConstraintLayout, Navigation Component.

## Architecture

- Follow MVVM direction: `ui -> viewmodel -> model/service -> (network|security|crypto|data)`.
- Activities, Fragments, and Dialogs render UI and wire interactions.
- ViewModels coordinate UI state and user intents.
- Services/managers handle capability orchestration.
- Network code belongs in `network` APIs, Retrofit clients, interceptors, token handling, or dedicated service facades.

## Current Non-Goals

- Do not migrate to Jetpack Compose casually.
- Do not introduce Hilt casually; current project uses `ServiceLocator`.
- Do not introduce Kotlin or StateFlow without an approved OpenSpec change.

## Security

- Keep password, token, key, biometric, clipboard, and screenshot-prevention logic aligned with existing `security` and `crypto` packages.
- Do not log secrets or decrypted password values.
- Prefer existing managers such as `SecureKeyStorageManager`, `SecurityManager`, `SessionGuard`, `TokenManager`, `ShareEncryptionManager`, and `Argon2KeyDerivationManager`.

## UI

- Maintain existing Material/XML style unless a UI modernization proposal says otherwise.
- Keep UI text in resources when practical.
- Avoid putting complex business, crypto, persistence, or retry logic directly in Activities/Fragments.

## Verification

- Unit tests: from `android/`, run `./gradlew test`.
- Build check: from `android/`, run `./gradlew :app:assembleDebug`.
- Instrumented tests require a connected emulator/device and should be run only when relevant.
