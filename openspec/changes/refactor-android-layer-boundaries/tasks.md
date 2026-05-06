## 1. Inventory
- [ ] 1.1 Map top-level Android packages and package-level dependency direction
- [ ] 1.2 Identify UI classes directly using `network`, `security`, `crypto`, or `data`
- [ ] 1.3 Classify direct dependencies as acceptable platform boundary, migration target, or needs deeper design
- [ ] 1.4 Record high-risk flows in `task.md`

## 2. Target Design
- [ ] 2.1 Define target package ownership for auth/account security
- [ ] 2.2 Define target package ownership for contact/friend flows
- [ ] 2.3 Define target package ownership for sharing flows
- [ ] 2.4 Define target package ownership for autofill and key migration system-boundary flows

## 3. Implementation Slices
- [ ] 3.1 Normalize only layer-boundary-related package placement or manifest-visible bootstrap inconsistencies found during inventory
- [ ] 3.2 Extract auth/login/register orchestration from UI into ViewModel/service boundaries
- [ ] 3.3 Extract account security orchestration from UI into manager/service boundaries
- [ ] 3.4 Extract contact/friend orchestration from UI into ViewModel/manager boundaries
- [ ] 3.5 Extract sharing orchestration from UI into ViewModel/manager boundaries
- [ ] 3.6 Review autofill and key migration classes for minimal safe boundary improvements
- [ ] 3.7 Remove temporary compatibility wrappers after all call sites move

## 4. Documentation
- [ ] 4.1 Update package boundary docs and `docs/directory-standards.md`
- [ ] 4.2 Update `task.md` after each meaningful slice
- [ ] 4.3 Update OpenSpec spec delta if the target boundary changes during review

## 5. Verification
- [ ] 5.1 Run `.\gradlew.bat test`
- [ ] 5.2 Run `.\gradlew.bat :app:assembleDebug`
- [ ] 5.3 Review login, registration, account security, sharing, contact, autofill, and key migration behavior
- [ ] 5.4 Confirm no backend API or database contract changed
