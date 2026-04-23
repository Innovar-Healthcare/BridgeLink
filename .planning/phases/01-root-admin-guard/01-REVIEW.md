---
phase: 01-root-admin-guard
reviewed: 2026-04-23T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - server/src/com/mirth/connect/server/launcher/MirthLauncher.java
  - server/src/com/mirth/connect/server/Mirth.java
  - server/test/com/mirth/connect/server/RootCheckTest.java
  - server/conf/mirth.properties
  - server/mirth.properties
findings:
  critical: 1
  warning: 3
  info: 3
  total: 7
status: issues_found
---

# Phase 01: Code Review Report — Root/Admin Guard (IRT-584)

**Reviewed:** 2026-04-23
**Depth:** standard
**Files Reviewed:** 5 (server/setup/conf/mirth.properties does not exist — skipped)
**Status:** issues_found

## Summary

The IRT-584 root/administrator guard implementation is structurally sound. The core logic (`evaluateRootCheck`, `checkRunningAsRoot`, `readNoNewPrivs`) is correct, the fail-open semantics are consistently applied, and the subprocess drain in both classes avoids Java 11+ APIs. The `@BeforeClass` Guice mock in `RootCheckTest` covers all ten `createXxx()` calls matching `Mirth`'s field initializers, no `@Ignore` annotations are present, and System.setProperty cleanup uses try/finally correctly.

Two issues require attention before merge: `server/mirth.properties` (the local dev copy) contains non-default keystore passwords and is tracked in git — this is the critical finding. A warning-level logic gap exists in the JVM-flag override tests, which test `evaluateRootCheck()` directly rather than the `checkRunningAsRoot()` property-reading path, making those tests misleading. Two additional warnings cover a duplicated `ROOT_CHECK_ERROR_MSG` constant and a misplaced import that violates project ordering rules.

---

## Critical Issues

### CR-01: Plaintext keystore credentials in tracked file (server/mirth.properties)

**File:** `server/mirth.properties:29-30`
**Issue:** `server/mirth.properties` contains non-default keystore passwords (`nfHbaNFacIhQ` / `tdW6edezNbmd`) in plaintext and appears to be tracked by git. These are distinct from the template defaults in `server/conf/mirth.properties`, indicating they were generated or modified locally. Committing this file leaks credentials into version history and could be scraped from the repo.
**Fix:** Add `server/mirth.properties` to `.gitignore` (this file is a runtime working copy, not a template). Rotate the keystore if this has already been pushed to a shared branch.

```
# .gitignore addition
server/mirth.properties
```

If the file must be tracked for CI purposes, replace the password values with placeholder tokens and supply real values via environment secrets at deploy time.

---

## Warnings

### WR-01: ROOT_CHECK_ERROR_MSG duplicated across MirthLauncher and Mirth

**File:** `server/src/com/mirth/connect/server/launcher/MirthLauncher.java:54-69` and `server/src/com/mirth/connect/server/Mirth.java:109-124`
**Issue:** The `ROOT_CHECK_ERROR_MSG` string constant is defined identically in both classes. If the message needs to be updated (e.g., a URL changes, a Linux command changes) it must be edited in two places and they will drift.
**Fix:** Define the constant once in `MirthLauncher` as `public static final` and reference it from `Mirth`:

```java
// In MirthLauncher.java — change to:
public static final String ROOT_CHECK_ERROR_MSG = "...";

// In Mirth.java — replace the private copy with:
private static final String ROOT_CHECK_ERROR_MSG = MirthLauncher.ROOT_CHECK_ERROR_MSG;
```

---

### WR-02: JVM-flag override tests do not exercise the property-reading path

**File:** `server/test/com/mirth/connect/server/RootCheckTest.java:181-212`
**Issue:** `testLauncher_jvmFlagOverrideAllowsRoot` and `testMirth_jvmFlagOverrideAllowsRoot` set `System.setProperty("server.allowRoot", "true")` but then call `evaluateRootCheck(...)` directly, passing `allowRoot=true` as a hard-coded argument. The system property is never read by the test — the tests do not verify that `checkRunningAsRoot()` actually reads the JVM flag and promotes it to `allowRoot=true`. The tests would pass even if the `sysProp` branch in `checkRunningAsRoot()` were deleted.
**Fix:** Either rename the tests to reflect what they actually test (`testEvaluateRootCheck_warnWhenAllowRootTrue`), or add a test that calls `checkRunningAsRoot()` via reflection or by making it package-private, confirming the system property is read. Since `checkRunningAsRoot()` calls `System.exit(1)` on BLOCK, testing it requires mocking `System.exit` (via a `SecurityManager` or PowerMock), so the simpler fix is an honest rename:

```java
// Rename:
public void testLauncher_warnWhenAllowRootTruePassedDirectly()
public void testMirth_warnWhenAllowRootTruePassedDirectly()
```

---

### WR-03: Misplaced import violates project import-ordering rules

**File:** `server/src/com/mirth/connect/server/launcher/MirthLauncher.java:22`
**Issue:** `java.io.InputStream` is imported on line 22, after the `java.util.*` group (lines 19-21) and interleaved with other `java.util.*` imports. Per the project's documented import ordering rules in MEMORY.md, all `java.io.*` imports must appear before `java.util.*` imports. Eclipse/VSCode Java LS applies these rules and misorderings can cause cascading "cannot be resolved" errors in the IDE.
**Fix:** Move `java.io.InputStream` to join the other `java.io.*` imports at the top of the import block, alongside lines 12-15:

```java
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;   // <-- move here, remove from line 22
```

---

## Info

### IN-01: server/setup/conf/mirth.properties does not exist

**File:** `server/setup/conf/mirth.properties` (absent)
**Issue:** The review scope listed this file, but the `server/setup/` directory does not exist in the working tree. If this is an installer template directory that gets created during the build/packaging step, the `server.allowRoot` property will be absent from any `mirth.properties` bundled by the installer. New installs would then default to `getProperty("server.allowRoot", "false")` which is safe (defaults to block), but the property would not appear in the config file as a documented knob for operators.
**Fix:** Confirm whether an installer template exists elsewhere (e.g., in `donkey/` or a packaging module). If a setup template is generated, add `server.allowRoot = false` with the security comment to that template as well.

---

### IN-02: Mirth.java readNoNewPrivs — NumberFormatException edge case is caught but silently discarded

**File:** `server/src/com/mirth/connect/server/Mirth.java:217`
**Issue:** `Integer.parseInt(line.substring("NoNewPrivs:".length()).trim())` will throw `NumberFormatException` if the kernel ever produces an unexpected value (e.g., a kernel with a different format). This is caught by `catch (Exception e)` on line 225 and logged at DEBUG, so it will not crash the server. This is correct and acceptable behavior — flagged only for awareness.
**Fix:** No code change required. The current behavior (catch-and-debug-log) is appropriate for an advisory check. Document with a comment if desired:

```java
} catch (Exception e) {
    // Includes NumberFormatException for malformed NoNewPrivs values.
    // Advisory only — failure here does not prevent startup.
    logger.debug("no_new_privs check skipped...");
}
```

---

### IN-03: server/conf/mirth.properties contains the IRT-577 default keystore passwords

**File:** `server/conf/mirth.properties:33-34`
**Issue:** The template config ships with `keystore.storepass = 81uWxplDtB` and `keystore.keypass = 81uWxplDtB`. These are the exact values that IRT-577 is supposed to detect and warn about. This is intentional — the template must have default values — but it is worth confirming that the IRT-577 default-password detection logic keys on this specific string so that any operator who copies the template without rotating the keystore triggers the warning. This is an informational note, not a bug.
**Fix:** No change required. Verify in `DefaultConfigurationController.isUsingDefaultKeystorePassword()` that the detection string matches `"81uWxplDtB"`.

---

_Reviewed: 2026-04-23_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
