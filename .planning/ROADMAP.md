# Roadmap: BridgeLink 26.3.1 — Security Hardening

## Overview

Two complementary security features, delivered as sequential phases. Phase 1 eliminates privilege escalation risk by refusing server startup under root or Administrator accounts — pure server-side Java, no UI touch. Phase 2 eliminates the trivial "admin" password from the full stack: REST API boundary, server-side checker, client UI validation, and existing-session enforcement via the grace-period login flow.

## Milestones

- 🚧 **26.3.1 Security Hardening** - Phases 1-2 (in progress)

## Phases

- [ ] **Phase 1: Root/Admin Guard** - Server refuses to start when running as root or Windows Administrator; operator override via mirth.properties
- [ ] **Phase 2: Admin Password Ban** - Block "admin" as a password at REST API, server internals, and client UI; force change for non-compliant existing passwords on login

## Phase Details

### Phase 1: Root/Admin Guard
**Goal**: Server refuses to start when running as root or Windows Administrator, with an operator escape hatch
**Depends on**: Nothing (first phase)
**Requirements**: ROOT-01, ROOT-02, ROOT-03, ROOT-04, ROOT-05
**Success Criteria** (what must be TRUE):
  1. Starting BridgeLink as the Linux/macOS root user causes startup to abort with a clear error message before any server threads are initialized
  2. Starting BridgeLink under a Windows Administrator account causes the same abort behavior on Windows
  3. An operator who sets `server.allowRoot = true` in `mirth.properties` can start the server as root without error
  4. A direct `java -jar` invocation (bypassing MirthLauncher) still aborts if running as root, because `Mirth.java startup()` performs the same check as a secondary safety net
  5. Normal (non-root) starts are unaffected — no error, no warning, no behavioral change
**Plans**: 5 plans

Plans:
- [x] 01-01-PLAN.md — Create RootCheckTest.java stub (Wave 0 test scaffold)
- [ ] 01-02-PLAN.md — Implement root-check primary guard in MirthLauncher.java (ROOT-01, ROOT-02, ROOT-03, ROOT-04)
- [ ] 01-03-PLAN.md — Implement root-check secondary guard + no_new_privs in Mirth.java (ROOT-01, ROOT-02, ROOT-03, ROOT-05)
- [ ] 01-04-PLAN.md — Add server.allowRoot = false to all three mirth.properties files (ROOT-03)
- [ ] 01-05-PLAN.md — Fill in all RootCheckTest.java assertions and verify ant test passes (ROOT-01, ROOT-02, ROOT-03, ROOT-05)

### Phase 2: Admin Password Ban
**Goal**: Users cannot set or keep "admin" as their password, enforced at every layer; existing users with non-compliant passwords are forced to change on next login
**Depends on**: Phase 1
**Requirements**: PASS-01, PASS-02, PASS-03, PASS-04
**Success Criteria** (what must be TRUE):
  1. A REST API call to set any user's password to "admin" returns HTTP 400 before reaching the controller layer
  2. A server-side call to `PasswordRequirementsChecker.doesPasswordMeetRequirements()` with "admin" returns false regardless of other policy settings
  3. In the Admin Client (UserEditPanel), typing "admin" into the new-password field shows an inline validation error before the user can submit
  4. A user whose stored password is "admin" is not denied login — they receive `SUCCESS_GRACE_PERIOD` and the existing ChangePasswordDialog opens automatically, forcing a change before reaching the main application
  5. A user whose stored password fails current complexity requirements (but is not "admin") also receives `SUCCESS_GRACE_PERIOD` on login and must update their password
**UI hint**: yes
**Plans**: TBD

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Root/Admin Guard | 1/5 | In Progress|  |
| 2. Admin Password Ban | 0/? | Not started | - |
