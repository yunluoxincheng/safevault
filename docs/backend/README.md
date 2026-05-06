# Backend Documentation Routing

This folder is for backend documentation that is meaningful only in the full SafeVault repository context.

## Keep in `docs/backend/`

- Backend service/module ownership across the whole project
- Android-to-backend integration architecture
- Cross-component architecture decisions
- Repository-boundary decisions that involve both root and backend repositories

## Keep in `safevault-backend/docs/`

- Deployment/runbook docs executed from backend working directory
- Maven/Spring profile commands bound to backend repository workflows
- Docker Compose and backend-local environment operation details

## Keep in `docs/api/`

- API contracts, endpoint behavior, schema and DTO semantics
- Sync protocol and key-management contract behavior

## Canonical-over-duplicate Rule

If the same topic appears in multiple places, keep one canonical file and add a short pointer in the other location instead of duplicating content.

## Current Backend-Local Canonical Files

- `safevault-backend/docs/deployment/server-deployment.md`
- `safevault-backend/docs/modularization-plan.md`
