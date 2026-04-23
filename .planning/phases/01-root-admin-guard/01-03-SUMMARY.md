---
phase: 01-root-admin-guard
plan: 03
subsystem: infra
tags: [java, security, root-check, IRT-584, no_new_privs, mirth-server]

# Dependency graph
requires:
  - phase: 01-root-admin-guard plan 01
    provides: RootCheckTest.java stub establishing evaluateRootCheck/readNoNewPrivs test contract
provides:
  - Secondary root/admin guard in Mirth.java run() — catches direct java -jar invocations bypassing MirthLauncher
  - RootCheckResult enum (BLOCK/WARN/OK) inside Mirth class
  - Package-private evaluateRootCheck(osName, userName, isWindowsAdmin, allowRoot) testable helper
  - Private isWindowsAdmin() via net session subprocess with 3s timeout, fail-open
  - Private checkRunningAsRoot() reading mirthProperties.getString and System.getProperty
  - Package-private readNoNewPrivs(Path) injectable for test path injection
  - Private checkNoNewPrivs() Linux-only advisory warning (never blocks startup)
  - ROOT_CHECK_ERROR_MSG and NO_NEW_PRIVS_WARNING constants
affects:
  - 01-05 (Wave 1 test fill-in — RootCheckTest removes @Ignore stubs and asserts against evaluateRootCheck instance method and readNoNewPrivs signatures delivered here)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Secondary safety net pattern: duplicate root guard in Mirth.run() catches direct java -jar invocations that bypass MirthLauncher primary guard"
    - "Injectable path pattern: readNoNewPrivs(Path) accepts injected path so tests can use temp files instead of /proc/self/status"
    - "Fail-open process detection: isWindowsAdmin() returns false on any exception or timeout — never blocks startup on non-admin uncertainty"
    - "JVM flag takes precedence: System.getProperty(server.allowRoot) checked before mirthProperties.getString fallback"

key-files:
  created: []
  modified:
    - server/src/com/mirth/connect/server/Mirth.java

key-decisions:
  - "evaluateRootCheck declared package-private (no access modifier) so RootCheckTest in same package can call it without reflection"
  - "readNoNewPrivs declared package-private (no access modifier) so RootCheckTest can inject temp file path"
  - "isWindowsAdmin() uses IOUtils.copy + getNullOutputStream() (Java 8 compatible) instead of OutputStream.nullOutputStream() (Java 11+)"
  - "checkNoNewPrivs() is warn-only and never blocks startup — T-1-03-02 disposed as accept per threat model"
  - "mirthProperties.getString() used (not getProperty()) — PropertiesConfiguration API requirement"
  - "logger.info() used on OK path — log4j Logger has info(); LoggerWrapper (MirthLauncher) does not, but Mirth.java uses log4j directly"

patterns-established:
  - "Root guard placement: inside if(initResources()) block, before TLS ephemeralDHKeySize setup — earliest safe point after properties are loaded"

requirements-completed:
  - ROOT-01
  - ROOT-02
  - ROOT-03
  - ROOT-05

# Metrics
duration: 2min
completed: 2026-04-23
---

# Phase 1 Plan 03: Mirth.java Secondary Root/Admin Guard Summary

**Secondary root/admin guard and no_new_privs advisory added to Mirth.java run() — RootCheckResult enum, evaluateRootCheck/isWindowsAdmin/checkRunningAsRoot/readNoNewPrivs/checkNoNewPrivs methods, and ROOT_CHECK_ERROR_MSG/NO_NEW_PRIVS_WARNING constants addressing ROOT-01, ROOT-02, ROOT-03, ROOT-05**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-23T18:53:26Z
- **Completed:** 2026-04-23T18:55:29Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Added full secondary root/admin guard to Mirth.java — catches direct `java -jar mirth-server.jar` invocations that bypass MirthLauncher's primary guard
- Added Linux no_new_privs advisory check via injectable /proc/self/status path — warn-only, never blocks startup, testable with temp file injection
- Verified clean compile: `ant compile` from server/ exits 0 (BUILD SUCCESSFUL, 15 warnings only — all pre-existing)

## Task Commits

1. **Task 1: Add RootCheckResult enum, constants, helpers, and call sites to Mirth.java** - `5ab7164a8` (feat)

## Files Created/Modified
- `server/src/com/mirth/connect/server/Mirth.java` - Added 138 lines: imports (BufferedReader, Files, Path, Paths, Arrays, TimeUnit), ROOT_CHECK_ERROR_MSG and NO_NEW_PRIVS_WARNING constants, RootCheckResult enum, evaluateRootCheck/isWindowsAdmin/checkRunningAsRoot/readNoNewPrivs/checkNoNewPrivs methods, call sites in run()

## Decisions Made
- Used `IOUtils.copy(process.getInputStream(), getNullOutputStream())` for stdout drain in isWindowsAdmin() — avoids OutputStream.nullOutputStream() which is Java 11+ only; getNullOutputStream() is already a static helper on Mirth class
- evaluateRootCheck and readNoNewPrivs declared without access modifier (package-private) to match the test contract established in Plan 01's RootCheckTest.java stub
- mirthProperties.getString() used throughout — PropertiesConfiguration API, not java.util.Properties.getProperty()

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None - compile succeeded on first attempt with no errors.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Mirth.java secondary guard is complete and compiled; RootCheckTest.java Wave 1 fill-in (plan 01-05) can now call `new Mirth().evaluateRootCheck(...)` and `new Mirth().readNoNewPrivs(path)` directly — both are package-private in com.mirth.connect.server
- Plan 01-02 (MirthLauncher primary guard) is the parallel wave 1 dependency; plan 01-05 requires both 01-02 and 01-03 to be complete before filling in test assertions

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. readNoNewPrivs(Path) reads /proc/self/status (kernel virtual file, read-only, no trust concern per T-1-03-03). isWindowsAdmin() spawns a subprocess but fails-open on any error. All threat model entries from plan frontmatter are addressed.

---
*Phase: 01-root-admin-guard*
*Completed: 2026-04-23*

## Self-Check: PASSED

- FOUND: server/src/com/mirth/connect/server/Mirth.java (modified)
- FOUND: commit 5ab7164a8 (feat(01-03): add secondary root/admin guard and no_new_privs advisory to Mirth.java)
- ant compile: BUILD SUCCESSFUL
- grep private enum RootCheckResult: line 150
- grep RootCheckResult evaluateRootCheck: line 154
- grep private void checkRunningAsRoot: line 188
- grep private boolean isWindowsAdmin: line 170
- grep void readNoNewPrivs: line 211
- grep private void checkNoNewPrivs: line 230
- grep checkRunningAsRoot();: line 278
- grep checkNoNewPrivs();: line 279
- ROOT_CHECK_ERROR_MSG occurrences: 2
- NO_NEW_PRIVS_WARNING occurrences: 2
- mirthProperties.getProperty occurrences: 0 (correct — getString() used)
- Privilege check passed logger.info: line 207
