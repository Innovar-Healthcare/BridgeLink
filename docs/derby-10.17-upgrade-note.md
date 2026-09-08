# Derby 10.17.1.0 Upgrade Note (internal)

**Status:** 10.17.1.0 ships in 26.9 (IRT-1489, CVE-03). This note supersedes the
"Resolution / Future Path" section of
[`docs/derby-10.17-java17-incompatibility.md`](./derby-10.17-java17-incompatibility.md),
which documents the earlier revert-to-10.16.1.1 decision; that revert no longer
reflects what ships. Cross-link that note rather than duplicating it -- it already
carries the class-version-63 compile error and the CVE-2022-46337 analysis in full.

This note is internal only. Customer-facing release-notes wording and the support
matrix are Phase 28 scope (D-06); do not lift prose from here into a customer-facing
document without that phase's review.

## 1. Soft vs. full upgrade -- the shipped default is soft, and rollback-safe

BridgeLink's `mirth.properties` ships `upgrade=false` by default (PR #151/IRT-776).
With that default, a Derby backend on disk in the OLDER 10.16 on-disk format boots
against the newer 10.17.1.0 engine **without rewriting the on-disk format**. This is
the soft path:

- The engine opens the existing database at its current format level.
- No on-disk structure changes; a 10.16-format database stays a 10.16-format
  database.
- **Rollback-safe:** because the format is untouched, an older 10.16.1.1 engine can
  still open the same database afterward if an operator needs to roll back the Derby
  jar version.

This is the path every existing BridgeLink install takes by default when it picks up
the 10.17.1.0 jars in a normal upgrade -- no format change, no one-way risk, no
special operator action required.

## 2. Full format upgrade (`upgrade=true`) is one-way -- no downgrade

Setting `upgrade=true` performs a **full Derby format upgrade** to 10.17's on-disk
format. This is opt-in, and it is **not reversible**:

- Once the database is rewritten to the 10.17 format, it **cannot be downgraded**
  back to a form a 10.16 (or earlier) engine can open.
- An install that opts into `upgrade=true` and completes the format upgrade has
  permanently traded rollback capability for the newer format.

This is exactly why plan 02's JAVA-04 preflight matters: it aborts an embedded-Derby
boot on a sub-21 JVM **before** any upgrade attempt can begin, so an operator cannot
accidentally start (and partially complete) a one-way format upgrade on a JVM that
cannot sustain the resulting 10.17 engine. The preflight is a guard against triggering
the irreversible path under the wrong runtime, not a mitigation of the irreversibility
itself -- the irreversibility is inherent to the full format upgrade and is not
something this phase (or any phase) removes.

## 3. Java-21 runtime requirement for embedded Derby 10.17 (v63)

Derby 10.17.1.0's `derbytools` jar is compiled to class-file version 63 (Java 19
minimum bytecode target; the Derby project's own release notes call out a Java SE 21+
floor for this line). Consequences:

- **Embedded Derby deployments require Java 21+ at runtime.** A JDK-17 process cannot
  load the v63 Derby classes; the reinstated JAVA-04 preflight aborts such a boot with
  a clear message rather than letting an opaque `UnsupportedClassVersionError` surface
  later.
- **JDK 17 remains supported for external-database deployments only** (MySQL,
  PostgreSQL, MSSQL/jTDS, etc.) -- those backends never load the Derby classes, so the
  Java-21 floor applies to the embedded-Derby tier specifically, not to BridgeLink as
  a whole.
- The **build toolchain** stays on JDK 17 / `--release 17` regardless (see the sibling
  incompatibility note for the D-01 reflection seam that makes this possible) --
  the Java-21 requirement described here is a **deployment-runtime** constraint for
  embedded Derby, distinct from the JDK-17 compile-time floor the project's build
  still targets.

## 4. Verification -- the harness upgrade-in-place Derby leg

The migration harness's upgrade-in-place Derby leg (plan 04) **is** the verification
of the 10.16-to-10.17 path described in section 1: it seeds a Derby-backed install on
an older release, stops it cleanly (releasing Derby's `db.lck`), then boots the newer
release's engine against the same on-disk database and asserts the data survives the
soft-upgrade boot. That harness run is the falsifiable evidence for the claims in this
note, not a manual or asserted-only description.

## Cross-references

- [`docs/derby-10.17-java17-incompatibility.md`](./derby-10.17-java17-incompatibility.md)
  -- the class-version-63 compile error and the original CVE-2022-46337 exploitability
  analysis. Its "Resolution / Future Path" section (revert to 10.16.1.1; "upgrade
  toolchain to Java 19+") is now historically scoped: this note supersedes it for the
  10.17.1.0 shipping decision.
- Phase 20 (Platform Compatibility Decision Gate) -- the decision record ratifying
  Derby 10.17.1.0 + the Java-21 floor for embedded Derby.
- Plan 24-01 Task 1 (D-01) -- the reflective `ij.runScript` seam that keeps the JDK-17
  build compiling with the v63 jars present.
- Plan 24-01 Task 2 (`24-CVE-CLOSURE.md`) -- the CVE-2022-46337 closure record for
  this jar bump.
