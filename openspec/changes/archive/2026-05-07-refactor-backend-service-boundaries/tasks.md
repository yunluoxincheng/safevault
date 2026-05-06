## 1. Inventory
- [x] 1.1 Map controllers to services and DTOs
- [x] 1.2 Map services to repositories, entities, security helpers, and WebSocket publishers
- [x] 1.3 Identify oversized services or classes with mixed responsibilities
- [x] 1.4 Record Flyway migration baseline and confirm no old migrations are modified

## 2. Architecture Direction
- [x] 2.1 Document strengthened layered architecture as the approved direction
- [x] 2.2 Document package ownership and dependency rules for existing backend layers
- [x] 2.3 Mark modular-monolith package migration as out of scope for this change
- [x] 2.4 Update backend package documentation and `safevault-backend/docs/` as needed

## 3. Implementation Slices
- [x] 3.1 Refactor low-risk service helpers without changing API behavior
- [x] 3.2 Refactor auth and registration service boundaries
- [x] 3.3 Refactor email verification and token/event service boundaries
- [x] 3.4 Refactor vault and private-key service boundaries
- [x] 3.5 Refactor contact/friend/share service boundaries
- [x] 3.6 Refactor WebSocket notification orchestration if it is scattered
- [x] 3.7 Remove temporary compatibility wrappers after call sites move

## 4. Documentation
- [x] 4.1 Update `docs/directory-standards.md`
- [x] 4.2 Update backend local documentation when package ownership changes
- [x] 4.3 Update `task.md` after each meaningful slice

## 5. Verification
- [x] 5.1 Run `.\mvnw.cmd test` from `safevault-backend/`
- [x] 5.2 Confirm REST paths and DTO shapes remain stable
- [x] 5.3 Confirm no existing Flyway migration file was changed
- [x] 5.4 Confirm root Git and backend Git status separately
