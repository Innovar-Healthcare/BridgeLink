---
phase: 01-root-admin-guard
verified: 2026-04-23T20:15:00Z
status: gaps_found
score: 4/5 must-haves verified
gaps:
  - truth: "Root check runs in MirthLauncher.java before the server thread starts (ROOT-04)"
    status: failed
    reason: "REQUIREMENTS.md marks ROOT-04 as [ ] Pending and 'Pending' in the traceability table. The implementation IS present in MirthLauncher.java (checkRunningAsRoot(mirthProperties) fires at line 173, before mirthThread.start()), but the requirements file has not been updated to reflect completion — creating a discrepancy between the requirement tracking state and the actual code."
    artifacts:
      - path: "server/src/com/mirth/connect/server/launcher/MirthLauncher.java"
        issue: "Code is correctly implemented; REQUIREMENTS.md checkbox and traceability table still show Pending/incomplete for ROOT-04"
      - path: ".planning/REQUIREMENTS.md"
        issue: "ROOT-04 shows [ ] (unchecked) and 'Pending' in traceability table — not updated after implementation"
    missing:
      - "Update REQUIREMENTS.md: change ROOT-04 from '- [ ]' to '- [x]' and update traceability table status from 'Pending' to 'Complete'"
  - truth: "server/setup/conf/mirth.properties contains server.allowRoot = false"
    status: failed
    reason: "The server/setup/ directory does not exist on disk. Plan 04 required this file but it is absent. The SUMMARY noted it used 'git add -f' due to .gitignore, but the directory is not present at all."
    artifacts:
      - path: "server/setup/conf/mirth.properties"
        issue: "File and parent directory do not exist — 'ls server/setup/' returns DIR NOT FOUND"
    missing:
      - "Determine whether server/setup/conf/mirth.properties was deleted, never created, or was always in .gitignore and is not expected to be present in the working tree. If the installer template file is required, create it; if the directory is legitimately absent in this repo structure, add an override to accept the deviation."
---

# Phase 1: Root Admin Guard Verification Report

**Phase Goal:** Add two-layer startup guards that abort the JVM when BridgeLink runs as root (Linux/macOS) or Windows Administrator. Operator override via server.allowRoot property. no_new_privs advisory check.
**Verified:** 2026-04-23T20:15:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Server refuses to start when running as root/Linux (ROOT-01) | VERIFIED | `evaluateRootCheck("Linux","root",false,false)` returns BLOCK; `checkRunningAsRoot()` calls `System.exit(1)` on BLOCK result in both MirthLauncher (line 130) and Mirth (line 203) |
| 2 | Server refuses to start when running as Windows Administrator (ROOT-02) | VERIFIED | `evaluateRootCheck("Windows 10","SYSTEM",true,false)` returns BLOCK; isWindowsAdmin() via `net session` subprocess; fail-open on exception/timeout |
| 3 | Operator override via `server.allowRoot = true` in mirth.properties (ROOT-03) | VERIFIED | `server.allowRoot = false` present in `server/conf/mirth.properties` (line 70) and `server/mirth.properties` (line 65); JVM flag -Dserver.allowRoot takes precedence; WARN logged when override active |
| 4 | Root check runs in MirthLauncher.java before server thread starts (ROOT-04) | PARTIAL | Code IS correctly placed at line 173 before `mirthThread.start()`. However REQUIREMENTS.md still shows ROOT-04 as `[ ]` Pending — requirements tracking not updated |
| 5 | Root check runs in Mirth.java `run()` after `initResources()` as secondary guard (ROOT-05) | VERIFIED | `checkRunningAsRoot()` and `checkNoNewPrivs()` called at lines 278-279, inside `if (initResources())` block, before TLS setup |

**Score:** 4/5 truths verified (ROOT-04 partial — code correct, requirements tracking stale)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `server/src/com/mirth/connect/server/launcher/MirthLauncher.java` | Primary root guard before server thread | VERIFIED | `public enum RootCheckResult`, `public static evaluateRootCheck()`, `private static isWindowsAdmin()`, `private static checkRunningAsRoot()`, call at line 173 before `mirthThread.start()` |
| `server/src/com/mirth/connect/server/Mirth.java` | Secondary root guard after initResources() | VERIFIED | `enum RootCheckResult` (package-private), `evaluateRootCheck()` (package-private), `checkRunningAsRoot()`, `checkNoNewPrivs()`, `readNoNewPrivs(Path)` (package-private), `ROOT_CHECK_ERROR_MSG`, `NO_NEW_PRIVS_WARNING`; calls at lines 278-279 |
| `server/test/com/mirth/connect/server/RootCheckTest.java` | 16-test JUnit 4 suite, no @Ignore | VERIFIED | 16 `@Test` methods, 0 `@Ignore` annotations, 13 `assertEquals` calls, `@BeforeClass` Guice mock for Mirth instantiation |
| `server/conf/mirth.properties` | Runtime config with allowRoot property | VERIFIED | `server.allowRoot = false` at line 70 with security comment at line 69 |
| `server/mirth.properties` | Dev-time config with allowRoot property | VERIFIED | `server.allowRoot = false` at line 65 with security comment at line 64 |
| `server/setup/conf/mirth.properties` | Installer template with allowRoot property | MISSING | Directory `server/setup/` does not exist on disk |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `MirthLauncher.main()` | `MirthLauncher.checkRunningAsRoot(mirthProperties)` | Direct call at line 173 | WIRED | Call appears after mirth.properties try/catch, before ManifestFile construction and `mirthThread.start()` |
| `MirthLauncher.checkRunningAsRoot()` | `MirthLauncher.evaluateRootCheck()` | Delegates to testable helper | WIRED | Line 126 |
| `MirthLauncher.checkRunningAsRoot()` | `System.exit(1)` | On BLOCK result | WIRED | Line 130 |
| `Mirth.run()` | `Mirth.checkRunningAsRoot()` | Inside if(initResources()) at lines 278 | WIRED | Before TLS setup at correct insertion point |
| `Mirth.run()` | `Mirth.checkNoNewPrivs()` | Immediately after checkRunningAsRoot() at line 279 | WIRED | Linux-only, never blocks startup |
| `Mirth.checkRunningAsRoot()` | `System.exit(1)` | On BLOCK result | WIRED | Line 203 |
| `Mirth.readNoNewPrivs(Path)` | `/proc/self/status` via `Files.newBufferedReader(procSelfStatus)` | Injectable path | WIRED | Line 212 |
| `server/conf/mirth.properties` | `MirthLauncher.checkRunningAsRoot()` | `mirthProperties.getProperty("server.allowRoot","false")` | WIRED | Line 120 |
| `server/conf/mirth.properties` | `Mirth.checkRunningAsRoot()` | `mirthProperties.getString("server.allowRoot","false")` | WIRED | Line 193 |

### Data-Flow Trace (Level 4)

Not applicable — this phase produces startup guard logic, not data-rendering components. No dynamic data rendering to trace.

### Behavioral Spot-Checks

| Behavior | Evidence | Status |
|----------|----------|--------|
| `evaluateRootCheck("Linux","root",false,false)` returns BLOCK | Test `testLauncher_blocksWhenLinuxRootAndNotAllowed` asserts this; code path verified by inspection | PASS |
| `evaluateRootCheck("Windows 10","SYSTEM",true,false)` returns BLOCK | Test `testLauncher_blocksWhenWindowsAdminAndNotAllowed` asserts this | PASS |
| allowRoot override produces WARN not BLOCK | Test `testLauncher_warnWhenLinuxRootAndAllowed` asserts WARN; `testLauncher_jvmFlagOverrideAllowsRoot` covers JVM property path | PASS |
| readNoNewPrivs handles IOException gracefully | Test `testReadNoNewPrivs_gracefulOnIoException` passes non-existent path; method catches Exception | PASS |
| ant test-run BUILD SUCCESSFUL with 16/16 passing | SUMMARY 01-05 reports tests=16, errors=0, failures=0, skipped=0 | REPORTED PASS (human to re-run if desired) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| ROOT-01 | 01-02, 01-03, 01-05 | Server refuses to start as root on Linux/macOS | SATISFIED | BLOCK path wired in both MirthLauncher and Mirth; 4 tests cover Linux root scenarios |
| ROOT-02 | 01-02, 01-03, 01-05 | Server refuses to start as Windows Administrator | SATISFIED | `isWindowsAdmin()` + BLOCK path in both classes; 2 tests cover Windows admin scenarios |
| ROOT-03 | 01-02, 01-03, 01-04, 01-05 | Operator override via server.allowRoot | SATISFIED | Property in conf/mirth.properties and mirth.properties; JVM flag precedence implemented; WARN logged on override |
| ROOT-04 | 01-02 | Root check in MirthLauncher before server thread starts | PARTIAL | Code correctly implemented at line 173 before `mirthThread.start()`; REQUIREMENTS.md tracking still shows Pending/unchecked |
| ROOT-05 | 01-03 | Root check in Mirth.run() after initResources() | SATISFIED | Calls at lines 278-279, inside if(initResources()), before TLS setup |

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| None found | — | — | All implementation methods have substantive bodies; no TODO/FIXME/placeholder text; no empty return stubs in security-critical paths |

### Human Verification Required

None for automated checks. Optional re-run of test suite to independently confirm:

**Test re-run (optional)**
- **Test:** `cd server && ant test-run` from the project root
- **Expected:** BUILD SUCCESSFUL, TEST-com.mirth.connect.server.RootCheckTest.xml reports tests=16, errors=0, failures=0, skipped=0
- **Why optional:** SUMMARY documents this result but it was not re-executed during this verification session

### Gaps Summary

Two gaps found:

**Gap 1 — Requirements tracking stale for ROOT-04 (low severity — code is correct)**
`REQUIREMENTS.md` still shows ROOT-04 as `[ ]` (unchecked) with status "Pending" in the traceability table. The actual implementation in `MirthLauncher.java` is correct: `checkRunningAsRoot(mirthProperties)` fires at line 173 before `mirthThread.start()`. The gap is documentation-only — the code satisfies the requirement but the tracking artifact was not updated after Plan 02 completed.

Fix: In `.planning/REQUIREMENTS.md`, change `- [ ] **ROOT-04**` to `- [x] **ROOT-04**` and update the traceability table row from `Pending` to `Complete`.

**Gap 2 — server/setup/conf/mirth.properties absent (medium severity — installer template missing)**
The `server/setup/` directory does not exist on disk. Plan 04 required `server.allowRoot = false` to be added to this installer template file, and the SUMMARY claimed it was done via `git add -f`. The directory is absent from the working tree entirely. This means the installer template (used to generate fresh installs) does not include the security property. Whether this is a git-tracking issue, a gitignore exclusion, or a structural absence needs human determination.

---

_Verified: 2026-04-23T20:15:00Z_
_Verifier: Claude (gsd-verifier)_
