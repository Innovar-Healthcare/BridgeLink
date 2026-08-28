# server/test/fixtures

Test-only binary fixtures for `server/test/`. Modelled on `smoke-tests/README.md`'s
fixture inventory and provenance conventions.

## Inventory

| Artifact | Version | SHA-1 (verified against repo1.maven.org) | Purpose |
|----------|---------|-------------------------------------------|---------|
| `rhino-1.7.13.jar` | 1.7.13 | `e6b2e12dc79fbdc58d8bf62a583705a551ec37d6` | Deliberately vulnerable Rhino engine (23.1-05) -- the exact artifact Phase 23 replaced with `server/lib/rhino-1.7.15.1.jar`. Used only by `RhinoEngineVersionDifferentialTest` as one half of a two-jar behavioral-compatibility differential against the vendored, shipped engine. **Never placed on any runtime or ant classpath.** Resolved only by an explicit relative path (`server/test/fixtures/rhino-1.7.13.jar`) inside that one test class's isolated `URLClassLoader`. |

## Never-on-any-classpath rule

This fixture jar carries CVE-2025-66453 (an unbounded resource path in `toFixed`), the
CVE the Phase 23 bump fixed. It must never appear on:

- `server/lib`, `server/testlib`, or any of `client/lib`, `command/lib`, `manager/lib`,
  `donkey/lib` -- the directories the ant `testclasspath` fileset (`server/build.xml`
  `test-init`) and the production build's library filesets read.
- The assembled `server/setup` distribution tree.
- The running JVM's own `java.class.path`.

`RhinoEngineVersionDifferentialTest.vulnerableFixtureIsNotOnAnyProductionOrTestClasspath()`
asserts this automatically, and is proven able to fail (see that test's javadoc and the
23.1-05 SUMMARY for the one-shot falsifiability proof).

## Scanner containment

The `cve-scan.yml` workflow sets `SCAN_DIR` to `server/setup` and scans only that
assembled tree with Trivy and OWASP Dependency-Check. This fixture is outside both
scanners' reach by placement alone: `server/test/fixtures/` is not `server/lib` or
`server/testlib` (the two directories the ant `testclasspath` fileset covers), and
`server/build.xml`'s `test-compile` target copies only `**/*.xml` and `**/*.json` out of
the test tree into `test_classes` -- the jar itself reaches neither `test_classes`, nor
the packaged tests jar, nor `server/setup`.

A path-scoped OWASP Dependency-Check suppression is added anyway in
`config/dependency-check-suppression.xml`, as defense in depth in case `SCAN_DIR` ever
widens. No CVE id is added to `.trivyignore` for this fixture: that file suppresses by
CVE id globally, and doing so would also mask a genuine regression if a vulnerable Rhino
ever returned to `server/lib`. `.trivyignore` instead carries a dated explanatory comment
recording why no id was added there.

## Provenance

Downloaded from Maven Central
(`https://repo1.maven.org/maven2/org/mozilla/rhino/1.7.13/rhino-1.7.13.jar`) and SHA-1
verified against the published `.jar.sha1` sidecar before being committed. The value
above also matches, byte for byte, the copy of `rhino-1.7.13.jar` that shipped at
`server/lib/` before the Phase 23 bump to 1.7.15.1 -- the exact artifact this differential
harness compares against.
