# Android Development Rules

## Architecture
Use MVVM architecture.

View -> ViewModel -> Repository

## UI
- Use Jetpack Compose
- UI logic must be in ViewModel

## State
- Use StateFlow
- Avoid mutable state in UI

## Networking
- Use Retrofit
- Use Repository layer for API calls

## Dependency Injection
- Use Hilt