---
name: android-debugging-fixes
description: Use when encountering functional issues in Android apps like crashes, ANRs, unexpected behavior, or when preparing to use specific feature-fix skills. Apply BEFORE using android-biometric-fixes, android-network-sync-fixes, android-email-verification-fixes, or android-encryption-fixes
---

# Android Debugging Fixes

## Overview

Systematic debugging methodology for Android applications. This skill provides the foundation for debugging any Android functional issue before applying feature-specific fixes.

**Core principle:** Understand the problem completely before attempting fixes. Symptom patches create more bugs.

**REQUIRED PREREQUISITE:** You MUST understand `superpowers:systematic-debugging` before using this skill.

## When to Use

Use this skill FIRST when:
- App crashes (Force Close)
- ANR (Application Not Responding)
- Features don't work as expected
- Before using any specific feature-fix skill

Use this skill ESPECIALLY when:
- You're tempted to "just try a quick fix"
- Multiple attempted fixes haven't worked
- The issue seems random or intermittent

## Android Debugging Tool Chain

### 1. Logcat Analysis

**Basic filtering:**
```bash
# Filter by tag
adb logcat -s TAG_NAME

# Filter by priority
adb logcat *:E                    # Errors only
adb logcat *:W                    # Warnings and above

# Filter by package
adb logcat --pid=$(adb shell pidof com.package.name)

# Clear and follow
adb logcat -c && adb logcat
```

**What to look for:**
- `FATAL EXCEPTION` - Crash location
- `AndroidRuntime:` - Uncaught exceptions
- `System.err:` - Print stack traces
- Your app's log tags - Custom debug logs

### 2. ADB Commands

```bash
# Device status
adb devices
adb shell getprop ro.build.version.sdk

# App process info
adb shell ps -A | grep your.package
adb shell kill <pid>               # Force stop app

# File operations
adb pull /sdcard/file.txt .       # Pull file from device
adb push file.txt /sdcard/         # Push file to device
adb shell rm /sdcard/file.txt      # Delete file

# Screen capture
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png

# Package info
adb shell pm list packages
adb shell pm dump com.package.name | grep -A 10 "version"
```

### 3. Layout Inspector (UI Issues)

**When to use:** Views not displaying, layouts broken, wrong dimensions

**Access:** Android Studio → Tools → Layout Inspector

**What to check:**
- View hierarchy matches expected structure
- View dimensions are correct
- Visibility states (VISIBLE/GONE/INVISIBLE)
- Constraint connections

### 4. Android Profiler (Performance)

**When to use:** App is slow, laggy, or consuming too much memory

**Access:** Android Studio → View → Tool Windows → Profiler

**What to check:**
- CPU usage spikes during operations
- Memory leaks (memory continuously grows)
- Network request timing
- Database query performance

### 5. StrictMode Detection

**Enable in Application.onCreate():**
```java
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyLog()
            .build());

    StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .penaltyLog()
            .build());
}
```

**Look for:** StrictMode violations in logcat indicating main thread I/O

## Problem Classification

| Issue Type | Diagnosis Method | Primary Tools |
|------------|------------------|---------------|
| **Crash (FC)** | Stack trace from logcat | logcat, debugger |
| **ANR** | Analyze /data/anr/traces.txt | ADB, file analysis |
| **Logic Error** | Step-through debugging | Debugger, logs |
| **Performance** | Profiler data | Android Profiler |
| **UI Issue** | View hierarchy inspection | Layout Inspector |
| **Network** | Request/response analysis | logcat, Charles/Proxy |

## Android-Specific Debugging Flow

```
┌─────────────────────────────────────────────────────────────┐
│  Step 1: Reproduce the Issue Consistently                   │
│  - Clear logcat: adb logcat -c                              │
│  - Trigger the bug                                          │
│  - Capture logs: adb logcat > bugreport.txt                 │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  Step 2: Check Manifest Configuration                       │
│  - Permissions declared?                                    │
│  - Components (Activities/Services/Receivers) declared?     │
│  - Exported attributes correct?                             │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  Step 3: Check Dependency Versions                          │
│  - Gradle dependencies version conflicts?                   │
│  - Support library versions consistent?                     │
│  - Play services version compatible?                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  Step 4: Check Threading Issues                             │
│  - Network on main thread?                                  │
│  - Database on main thread?                                 │
│  - UI updates from background thread?                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  Step 5: Check Lifecycle Issues                             │
│  - Activity/Fragment lifecycle order                        │
│  - ViewModel scope correct?                                 │
│  - LiveData observers active?                               │
│  - Context references leak?                                 │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  Step 6: Isolate the Component                              │
│  - Create minimal reproducible example                      │
│  - Test in isolation from rest of app                       │
│  - Binary search through recent changes                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  Step 7: Apply Fix and Verify                               │
│  - Make ONE change at a time                                │
│  - Test the fix                                             │
│  - Run existing tests                                       │
│  - Test on different Android versions/API levels            │
└─────────────────────────────────────────────────────────────┘
```

## Common Android Issue Patterns

### 1. NullPointerException on View

**Cause:** Accessing view before `findViewById` or ViewBinding inflates it

**Check:**
- View accessed after `setContentView()`?
- View accessed after Fragment's `onViewCreated()`?
- View null check before use?

### 2. NetworkOnMainThreadException

**Cause:** Network operation on main thread

**Fix:** Move to background thread (Coroutines, RxJava, AsyncTask)

### 3. IllegalStateException: Fragment already added

**Cause:** Adding same fragment instance multiple times

**Check:**
- Fragment transaction committed?
- Using fragment instance correctly?
- `findFragmentByTag()` before adding?

### 4. SecurityException: Permission Denial

**Cause:** Missing runtime permission request

**Fix:** Check `ContextCompat.checkSelfPermission()` and request if needed

### 5. Activity has leaked window

**Cause:** Dialog/Toast shown after Activity destroyed

**Fix:** Dismiss dialogs in `onDestroy()`, check `isFinishing()`

## Fix Verification Checklist

Before claiming a fix works:

- [ ] Issue no longer occurs (manual test)
- [ ] No new errors in logcat
- [ ] No ANRs generated
- [ ] Existing unit tests pass
- [ ] Existing instrumented tests pass
- [ ] Tested on minimum API level (API 29)
- [ ] Tested on target API level (API 36)
- [ ] Tested on at least one physical device
- [ ] No StrictMode violations
- [ ] No memory leaks (verified with Profiler)

## Red Flags - STOP and Follow Process

If you catch yourself thinking:
- "Just try changing X and see if it works"
- "Skip the logcat analysis, I know what's wrong"
- "Multiple changes at once is faster"
- "It's probably a framework bug"
- "Testing on emulator is enough"

**ALL of these mean: STOP. Return to Step 1.**

## Encountering Unknown Issues?

When this skill doesn't cover your issue:

1. **Search official documentation**
   - Android Developers reference
   - AOSP source code
   - API diff reports

2. **Search community resources**
   - Stack Overflow
   - GitHub Issues
   - Android issue tracker

3. **Apply systematic debugging** from `superpowers:systematic-debugging`

4. **Document the solution** and consider updating this skill

## Related Skills

After using this general debugging skill, use the appropriate feature-specific skill:
- **android-biometric-fixes** - Biometric authentication issues
- **android-network-sync-fixes** - Network and synchronization problems
- **android-email-verification-fixes** - Email verification issues
- **android-encryption-fixes** - Encryption/decryption problems

## Real-World Impact

From Android debugging sessions:
- Systematic approach: 15-45 minutes per bug
- Random fix attempts: 2-4 hours of thrashing
- First-time fix rate: 90% vs 35%
- Regression rate: Near zero vs frequent
