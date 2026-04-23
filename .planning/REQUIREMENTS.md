# Requirements: BridgeLink 26.3.1

**Defined:** 2026-04-23
**Core Value:** Clinical message routing must be reliable and secure — every security gap that lets an attacker compromise the integration layer is a patient data risk.

## v1 Requirements

### Root/Admin Guard (IRT-584)

- [x] **ROOT-01**: Server refuses to start when JVM runs as root on Linux/macOS
- [x] **ROOT-02**: Server refuses to start when JVM runs as Windows Administrator
- [x] **ROOT-03**: Operator can override the root block by setting `server.allowRoot = true` in `mirth.properties`
- [x] **ROOT-04**: Root check runs in `MirthLauncher.java` before the server thread starts (primary layer)
- [x] **ROOT-05**: Root check runs in `Mirth.java` `startup()` after `initResources()` as a secondary safety net for direct `java -jar` invocations

### Password Ban (IRT-580)

- [ ] **PASS-01**: REST API rejects "admin" as a new or updated password at `UserServlet.checkUserPassword()` and `updateUserPassword()` with HTTP 400
- [ ] **PASS-02**: `PasswordRequirementsChecker.doesPasswordMeetRequirements()` rejects "admin" server-side (defense in depth)
- [ ] **PASS-03**: Client `UserEditPanel` shows an inline validation error when the user enters "admin" as a new password, before calling the server
- [ ] **PASS-04**: On login, users whose stored password is "admin" or fails current complexity requirements receive `SUCCESS_GRACE_PERIOD` status, triggering the existing `ChangePasswordDialog` flow automatically

## Future Requirements

### Windows/Linux installer enforcement (IRT-578, IRT-579)

- **INST-01**: Windows fresh install: installer screen blocks "admin" as admin password, writes `default.firstLogin.password` to mirth.properties
- **INST-02**: Windows upgrade: installer warns if admin password is "admin" before proceeding
- **INST-03**: Startup bootstrap reads `default.firstLogin.password`, writes to DB, then removes property
- **INST-04**: Bad-admin detection on upgrade: sets `bad_admin_credential` DB flag, creates `recovery.txt`, blocks startup
- **INST-05**: Recovery flow: reads corrected password from `recovery.txt`, validates, clears DB flag
- **INST-06**: Linux fresh install: operator sets `default.firstLogin.password` in mirth.properties before first start

## Out of Scope

| Feature | Reason |
|---------|--------|
| install4j installer screen changes | Requires separate installer config work — deferred to next milestone |
| Linux/CLI fresh-install enforcement (IRT-579) | Deferred — no installer hook available, operator-only workflow |
| Claude AI channel summarizer | Separate feature track, not security |
| Windows deploy pipeline | Ops/infra, not security |
| OIDC enhancements | No active ticket in this milestone |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| ROOT-01 | Phase 1 | Complete |
| ROOT-02 | Phase 1 | Complete |
| ROOT-03 | Phase 1 | Complete |
| ROOT-04 | Phase 1 | Complete |
| ROOT-05 | Phase 1 | Complete |
| PASS-01 | Phase 2 | Pending |
| PASS-02 | Phase 2 | Pending |
| PASS-03 | Phase 2 | Pending |
| PASS-04 | Phase 2 | Pending |

**Coverage:**
- v1 requirements: 9 total
- Mapped to phases: 9
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-23*
*Last updated: 2026-04-23 after initial definition*
