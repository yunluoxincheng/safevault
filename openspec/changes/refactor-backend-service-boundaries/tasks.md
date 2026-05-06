## 1. Inventory
- [ ] 1.1 Map controllers to services and DTOs
- [ ] 1.2 Map services to repositories, entities, security helpers, and WebSocket publishers
- [ ] 1.3 Identify oversized services or classes with mixed responsibilities
- [ ] 1.4 Record Flyway migration baseline and confirm no old migrations are modified

## 2. Architecture Direction
- [ ] 2.1 Document strengthened layered architecture as the approved direction
- [ ] 2.2 Document package ownership and dependency rules for existing backend layers
- [ ] 2.3 Mark modular-monolith package migration as out of scope for this change
- [ ] 2.4 Update backend package documentation and `safevault-backend/docs/` as needed

## 3. Implementation Slices
- [ ] 3.1 Refactor low-risk service helpers without changing API behavior
- [ ] 3.2 Refactor auth and registration service boundaries
- [ ] 3.3 Refactor email verification and token/event service boundaries
- [ ] 3.4 Refactor vault and private-key service boundaries
- [ ] 3.5 Refactor contact/friend/share service boundaries
- [ ] 3.6 Refactor WebSocket notification orchestration if it is scattered
- [ ] 3.7 Remove temporary compatibility wrappers after call sites move

## 4. Documentation
- [ ] 4.1 Update `docs/directory-standards.md`
- [ ] 4.2 Update backend local documentation when package ownership changes
- [ ] 4.3 Update `task.md` after each meaningful slice

## 5. Verification
- [ ] 5.1 Run `.\mvnw.cmd test` from `safevault-backend/`
- [ ] 5.2 Confirm REST paths and DTO shapes remain stable
- [ ] 5.3 Confirm no existing Flyway migration file was changed
- [ ] 5.4 Confirm root Git and backend Git status separately
