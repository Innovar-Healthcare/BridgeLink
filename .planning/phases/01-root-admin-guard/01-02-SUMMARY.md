---
phase: 01-root-admin-guard
plan: 02
subsystem: security
tags: [java, startup-guard, root-check, IRT-584, system-exit, windows-admin, net-session]

# Dependency graph
requires:
  - phase: 01-root-admin-guard/01-01
    provides: RootCheckTest.java stub with @Ignore Wave 0 placeholders declaring evaluateRootCheck static signature
provides:
  - MirthLauncher.java with RootCheckResult enum, evaluateRootCheck() public static helper, isWindowsAdmin() Windows detection, checkRunningAsRoot() caller, call site in main()
  - Primary (pre-thread) root/Administrator startup guard firing after mirth.properties load, before addManifestToClasspath
affects:
  - 01-05 (Wave 1 test fill-in — can now remove @Ignore from MirthLauncher.evaluateRootCheck tests and write assertions)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Testable startup guard pattern: evaluateRootCheck() accepts (osName, userName, isWindowsAdmin, allowRoot) as explicit params — no System.getProperty() calls inside the decision method, enabling unit tests to pass controlled values without system property mutation"
    - "Fail-open Windows admin detection: isWindowsAdmin() returns false on any exception or timeout (3s destroyForcibly); prevents DoS via hung net session subprocess"
    - "Java 8 compatible subprocess drain: byte[] buf read loop instead of OutputStream.nullOutputStream() (Java 11+)"

key-files:
  created: []
  modified:
    - server/src/com/mirth/connect/server/launcher/MirthLauncher.java

key-decisions:
  - "evaluateRootCheck declared public static (not package-private) — RootCheckTest.java is in com.mirth.connect.server package, which is a different package from com.mirth.connect.server.launcher; public access required for cross-package test access"
  - "isWindowsAdmin uses Arrays.asList ProcessBuilder form for Java 8 compatibility with varargs ProcessBuilder(String...) which also works; drain loop used instead of nullOutputStream() (Java 11+)"
  - "checkRunningAsRoot call placed after mirth.properties try/catch, before ManifestFile construction — earliest point where mirthProperties is populated and logger is available"

patterns-established:
  - "Startup guard enum pattern: BLOCK/WARN/OK returned from testable helper; caller owns System.exit(1) — keeps helper free of side effects for unit testing"

requirements-completed:
  - ROOT-01
  - ROOT-02
  - ROOT-03
  - ROOT-04

# Metrics
duration: 10min
completed: 2026-04-23
---

# Phase 01 Plan 02: MirthLauncher Root/Admin Guard Summary

**RootCheckResult enum, evaluateRootCheck() public static helper, isWindowsAdmin() Windows net-session check, and checkRunningAsRoot() caller added to MirthLauncher.java as IRT-584 primary startup guard**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-04-23T18:44:00Z
- **Completed:** 2026-04-23T18:54:35Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Added all 6 implementation elements to MirthLauncher.java: ROOT_CHECK_ERROR_MSG constant, RootCheckResult enum, evaluateRootCheck() public static helper, isWindowsAdmin() subprocess helper, checkRunningAsRoot() caller, and call site in main()
- checkRunningAsRoot(mirthProperties) fires after mirth.properties try/catch at line ~173, before ManifestFile construction — earliest possible point after logger initialization
- evaluateRootCheck() declared public static so RootCheckTest.java (in com.mirth.connect.server package) can call it across package boundaries without reflection
- ant compile exits 0 — BUILD SUCCESSFUL with 15 pre-existing deprecation warnings (unrelated to new code)

## Task Commits

1. **Task 1: Add root/admin guard to MirthLauncher** - `f75b603d0` (feat)

## Files Created/Modified
- `server/src/com/mirth/connect/server/launcher/MirthLauncher.java` - Added 87 lines: 3 new imports, ROOT_CHECK_ERROR_MSG constant, RootCheckResult enum, evaluateRootCheck(), isWindowsAdmin(), checkRunningAsRoot(), and call in main()

## Decisions Made
- evaluateRootCheck() declared `public static` rather than package-private: RootCheckTest.java lives in `com.mirth.connect.server` (different package from `com.mirth.connect.server.launcher`), so public is required for direct call in tests
- Java 8 compatible drain loop used in isWindowsAdmin() instead of `OutputStream.nullOutputStream()` (Java 11+) — matches the plan spec and confirmed safe by RESEARCH.md pitfall documentation
- imports added in correct project order (java.* block before javax.*): `java.io.InputStream`, `java.util.Arrays`, `java.util.concurrent.TimeUnit`

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered
None — compilation succeeded on first attempt with no errors.

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- MirthLauncher.java has the full primary root guard — Wave 1 plan 01-05 can now fill in the @Ignore test stubs for MirthLauncher.evaluateRootCheck() by calling the public static method directly with controlled (osName, userName, isWindowsAdmin, allowRoot) parameters
- No blockers for plans 01-03 (Mirth.java secondary guard) or 01-04 (mirth.properties property additions)

## Threat Surface Scan
No new network endpoints, auth paths, file access patterns, or schema changes introduced. The implementation only reads existing system properties (os.name, user.name, server.allowRoot) and spawns a subprocess on Windows. These surfaces are fully covered by the plan's threat model (T-1-02-01 through T-1-02-05).

---
*Phase: 01-root-admin-guard*
*Completed: 2026-04-23*

## Self-Check: PASSED

- FOUND: server/src/com/mirth/connect/server/launcher/MirthLauncher.java
- FOUND: commit f75b603d0
- VERIFIED: enum RootCheckResult present at line 73
- VERIFIED: public static RootCheckResult evaluateRootCheck present at line 77
- VERIFIED: private static boolean isWindowsAdmin present at line 93
- VERIFIED: private static void checkRunningAsRoot present at line 115
- VERIFIED: checkRunningAsRoot(mirthProperties) call present at line 173
- VERIFIED: ROOT_CHECK_ERROR_MSG count = 2 (declaration + usage)
- VERIFIED: System.exit(1) present
- VERIFIED: logger.info count = 0 (no info() calls)
- VERIFIED: nullOutputStream count = 0 (Java 8 safe drain loop used)
- VERIFIED: ant compile BUILD SUCCESSFUL
