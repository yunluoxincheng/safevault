# Change: Refactor Project Structure Documentation

## Why
Current repository structure is rich but lacks explicit package-boundary contracts at code level. This increases onboarding cost and raises the risk of cross-layer coupling.

## What Changes
- Add package-level structure contracts for Android and backend root packages.
- Add a dedicated structure reorganization document under `docs/`.
- Add OpenSpec requirement delta for maintainable structure governance.

## Impact
- Affected specs: `project-structure` (new capability-level governance spec delta)
- Affected code: package metadata files only (`package-info.java`), no runtime logic changed
