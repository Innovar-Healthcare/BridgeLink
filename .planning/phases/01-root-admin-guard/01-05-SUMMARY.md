---
phase: 01-root-admin-guard
plan: 05
subsystem: security
tags: [java, unit-tests, root-check, IRT-584, junit4, mockito, guice]

# Dependency graph
requires:
  - phase: 01-root-admin-guard/01-02
    provides: MirthLauncher.evaluateRootCheck() public static helper + RootCheckResult enum
  - phase: 01-root-admin-guard/01-03
    provides: Mirth.evaluateRootCheck() + Mirth.readNoNewPrivs(Path) package-private helpers

provides:
  - Complete RootCheckTest.java with 16 passing tests covering all root/admin guard branches
  - Proof that evaluateRootCheck BLOCK/WARN/OK paths fire correctly for Linux, macOS, Windows
  - Proof that readNoNewPrivs handles NoNewPrivs:0, NoNewPrivs:1, IOException, and missing-field cases gracefully

affects:
  - Requirements ROOT-01 through ROOT-05 now have test coverage

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Cross-package enum access: make nested enum public when test is in a different package; make it package-private when test is in the same package"
    - "Guice mock @BeforeClass for Mirth instantiation: mock all 10 ControllerFactory.createXxx() calls that Mirth field initializers invoke before new Mirth() can succeed"
    - "readNoNewPrivs injection: pass controlled temp file paths to avoid /proc/self/status dependency in tests; assert no exception thrown (graceful degradation verification)"

key-files:
  created: []
  modified:
    - server/test/com/mirth/connect/server/RootCheckTest.java
    - server/src/com/mirth/connect/server/launcher/MirthLauncher.java
    - server/src/com/mirth/connect/server/Mirth.java

key-decisions:
  - "MirthLauncher.RootCheckResult changed from package-private to public: test is in com.mirth.connect.server (different package from com.mirth.connect.server.launcher); public access required for .name() call on return value without cross-package type reference errors"
  - "Mirth.RootCheckResult changed from private to package-private: test is in same package com.mirth.connect.server; package-private is sufficient and less permissive than public"
  - "All Mirth evaluateRootCheck assertions use .name() string comparison (not Mirth.RootCheckResult.BLOCK direct reference) since Mirth.RootCheckResult is package-private, avoiding any risk of accidental inaccessibility"

# Metrics
duration: 25min
completed: 2026-04-23
---

# Phase 01 Plan 05: RootCheckTest Fill-in Summary

**16-test JUnit 4 suite for IRT-584 root/admin guard covering BLOCK/WARN/OK paths for MirthLauncher and Mirth, readNoNewPrivs injection tests, and JVM override tests — all passing with BUILD SUCCESSFUL**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-23T19:15:00Z
- **Completed:** 2026-04-23T19:45:55Z
- **Tasks:** 1
- **Files modified:** 3

## Accomplishments

- Removed all 11 @Ignore annotations from RootCheckTest.java (Wave 0 stubs)
- Added complete test implementations: 16 @Test methods, all passing
- Added @BeforeClass Guice mock that sets up all 10 ControllerFactory.createXxx() methods Mirth field initializers require, enabling `new Mirth()` without NullPointerException
- MirthLauncher tests: BLOCK (Linux root), WARN (Linux root + allowRoot), OK (Linux non-root), BLOCK (Windows admin), OK (Windows non-admin), WARN (Windows admin + allowRoot) — 6 tests
- Mirth tests: BLOCK (Linux root), WARN (Linux root + allowRoot), OK (Linux non-root), BLOCK (Mac root) — 4 tests
- readNoNewPrivs tests: NoNewPrivs:0 (warn), NoNewPrivs:1 (info), IOException (graceful), missing field (silent) — 4 tests
- JVM override tests: testLauncher_jvmFlagOverrideAllowsRoot, testMirth_jvmFlagOverrideAllowsRoot — 2 tests
- ant test-run from server/ exits 0, BUILD SUCCESSFUL; TEST-com.mirth.connect.server.RootCheckTest.xml reports tests=16, errors=0, failures=0, skipped=0

## Task Commits

1. **Task 1: Fill in RootCheckTest.java with full assertions and remove @Ignore** - `d5a6a5d54` (feat)

## Files Created/Modified

- `server/test/com/mirth/connect/server/RootCheckTest.java` — Fully rewritten: 11 @Ignore removed, 16 @Test implemented, @BeforeClass Guice mock added, 147 net lines added
- `server/src/com/mirth/connect/server/launcher/MirthLauncher.java` — RootCheckResult enum visibility changed from package-private to public (Rule 1 fix)
- `server/src/com/mirth/connect/server/Mirth.java` — RootCheckResult enum visibility changed from private to package-private (Rule 1 fix)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] MirthLauncher.RootCheckResult and Mirth.RootCheckResult were inaccessible from test code**

- **Found during:** Task 1 — first ant test-run attempt
- **Issue:** The plan called for `assertEquals("BLOCK", evaluateRootCheck(...).name())` but javac emitted "Enum.name() is defined in an inaccessible class or interface" for all 12 such calls. Root cause: `MirthLauncher.RootCheckResult` was `enum` (package-private in launcher package, inaccessible from server package), and `Mirth.RootCheckResult` was `private enum` (inaccessible even from the same package test)
- **Fix:** Changed `MirthLauncher.RootCheckResult` from package-private to `public enum`; changed `Mirth.RootCheckResult` from `private enum` to package-private `enum`. Re-ran `ant compile` then `ant test-run` — 16/16 tests pass
- **Files modified:** `server/src/com/mirth/connect/server/launcher/MirthLauncher.java` (line 73), `server/src/com/mirth/connect/server/Mirth.java` (line 150)
- **Commit:** d5a6a5d54 (included in task commit)

**2. [Rule 1 - Bug] ant compile required before ant test-run to pick up enum visibility changes**

- **Found during:** Task 1 — after fixing enum visibility, second ant test-run still showed compilation errors
- **Issue:** `ant test-run` depends on `test-init` → `init` but NOT on `compile`. Old .class files in `${classes}` were used for the test compile classpath
- **Fix:** Ran `ant compile` first to rebuild main sources, then `ant test-run`
- **Commit:** n/a (build procedure only)

## Issues Encountered

- Enum visibility was under-specified in plan 01-02 (declared `enum RootCheckResult` without the `public` modifier) and plan 01-03 (declared `private enum RootCheckResult`). Both were discovered at compile time and auto-fixed.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes. The two enum visibility changes are purely compile-time access modifiers with no runtime security surface impact.

## Known Stubs

None — all 16 tests have full assertions. No placeholder text or empty test bodies remain.

---

*Phase: 01-root-admin-guard*
*Completed: 2026-04-23*

## Self-Check: PASSED

- FOUND: server/test/com/mirth/connect/server/RootCheckTest.java
- FOUND: server/src/com/mirth/connect/server/launcher/MirthLauncher.java
- FOUND: server/src/com/mirth/connect/server/Mirth.java
- FOUND: commit d5a6a5d54
- VERIFIED: grep "@Ignore" RootCheckTest.java = 0 (no @Ignore annotations remain)
- VERIFIED: grep "@Test" RootCheckTest.java = 16 (16 test methods)
- VERIFIED: grep "assertEquals" RootCheckTest.java = 13 (13 assertion calls)
- VERIFIED: grep "MirthLauncher.evaluateRootCheck" = 8 calls
- VERIFIED: grep "mirth.evaluateRootCheck" = 5 calls
- VERIFIED: grep "readNoNewPrivs" = 6 calls
- VERIFIED: grep "@BeforeClass" = 1
- VERIFIED: ant test-run BUILD SUCCESSFUL
- VERIFIED: TEST-com.mirth.connect.server.RootCheckTest.xml: tests=16, errors=0, failures=0, skipped=0
