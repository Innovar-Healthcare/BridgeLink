# smoke-tests/

## Purpose

An end-to-end smoke harness (IRT-1491 / NET-01, NET-02, NET-05; roadmap D-02) that boots
the real assembled BridgeLink distribution directly on the runner JVM — embedded Derby,
ephemeral ports, an auto-generated keystore — health-checks it, and tears down cleanly.
Later plans in Phase 18 extend this foundation to deploy a reference channel set covering
every stock connector type, pump representative HL7v2 messages through them, and assert
delivery/transformation/error-free logs (NET-02), plus a self-verifying broken-dependency
demonstration (NET-05).

This directory deliberately mirrors the `migration-tests/` convention (script + fixtures +
README) but is a separate top-level tree — Phase 19 is recovering `migration-tests/` in
parallel, and file overlap between in-flight phases must be avoided.

## Prerequisites

Build the assembled distribution once before running the harness:

```bash
cd server
ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true
```

This produces `server/setup/` (`server-lib/mirth-server.jar`, `mirth-launcher.jar`,
`conf/mirth.properties`, etc.) — the harness never rebuilds it, only boots it.

## Usage

```bash
smoke-tests/run-smoke-test.sh [--db derby|mysql|postgres|mssql] [--boot-only] [--deploy-only] [--help]
```

- `--db` (default `derby`): selects the harness database backend. Only `derby` is
  functional in Phase 18 (D-03) — the other three values are accepted and routed to a
  `configure_db()` stub that fails fast with a "not yet supported in Phase 18, see
  D-03/Phase 24" message. This keeps the flag's shape stable for Phase 24, when Derby
  10.17 lands and the JDK-17 leg flips to an external DB.
- `--boot-only`: stop after the health check succeeds and tear down immediately — proves
  the boot/teardown machinery in isolation (18-01), skipping the import/deploy stage.
- `--deploy-only`: boot, then import and deploy all 10 reference channel fixtures, poll
  them to STARTED, then tear down (no message pump/assert driver — that's 18-06/18-07).
  Mutually exclusive with `--boot-only`.
- `--help`: print usage and exit 0.

Exit code is 0 iff every stage that ran passed. The script prints a
`HARNESS DURATION: <n>s` line on every exit path (feeds the ≤10-minute-per-leg target,
D-04).

### What the script does

1. Preflight: confirms `server/setup/server-lib/mirth-server.jar` and
   `server/setup/conf/mirth.properties` exist (fails fatally with a rebuild hint if not).
2. Allocates ephemeral ports (HTTP, HTTPS, MLLP, HTTP listener, SMTP, SCP, SOAP — plus a
   derived `SOAP_URL`) and a per-run channel work directory (`IN_DIR`/`OUT_DIR`/
   `SQLITE_PATH`) under a fresh `mktemp -d`.
3. Patches a copy of `server/setup/conf/mirth.properties` in place (`sed -i.smoke-bak`):
   `http.port`, `https.port`, a fresh `dir.appdata` under a per-run temp directory, and
   binds `http.host`/`https.host` to `127.0.0.1` (the shipped default is `0.0.0.0` — a CI
   runner must never expose the admin API on all interfaces, see Threat T-18-01).
4. Launches `com.mirth.connect.server.launcher.MirthLauncher` from `server/setup` with the
   authoritative 18-flag JVM options list from `server/docs/mcservice-java9+.vmoptions`
   (NOT the build.xml test jvmargs, which differ).
5. Health-checks `GET https://127.0.0.1:<HTTPS_PORT>/api/server/version` with
   `X-Requested-With: OpenAPI`, polling with a timeout (no fixed sleeps).
6. **(skipped by `--boot-only`) Import/deploy stage** — see "Add a channel" below for the
   full mechanics. Ends by clearing all channel statistics (`clear_statistics()`) — the
   readiness probe in step 6 above (`wait_for_listener_ports()`) sends a real HTTP GET
   straight at the HTTP Listener channel's own port, which the channel treats as (and
   fails to parse as) a message; clearing statistics here gives the driver a clean slate.
7. **(skipped by `--boot-only`/`--deploy-only`) Driver stage** — `ant -f smoke-tests/build.xml
   test-run`, invoked with every harness port/path as a `-D` property (forwarded to the
   forked JUnit JVM as sysproperties by `build.xml`). Runs `NativePumpChannelsTest` (HTTP,
   MLLP, File, VM, JS) and `StubChannelsTest` (SMTP, SOAP, JDBC, DICOM, Document Writer),
   each asserting D-08's three levels per channel: L1 (SENT count, zero ERROR-status via
   REST statistics), L2 (destination-artifact content, never byte-exact), L3 below. A driver
   failure prints `SMOKE-FAILURE-CLASS: assert` plus the junit-reports summary, but does not
   abort the script — L3 and teardown still run so every stage gets a chance to report.
8. **(skipped by `--boot-only`/`--deploy-only`) L3 log scan** — scans
   `server/setup/logs/mirth.log` for `^ERROR|ERROR \[` lines, filtered through
   `fixtures/log-allowlist.txt` (comment lines and blanks stripped before use — see "Add a
   channel" below and T-18-16). Any surviving line prints
   `SMOKE-FAILURE-CLASS: log` and fails the run. Note: `mirth.log` lives at a **fixed path**
   (log4j2's RollingFile appender, not per-run `dir.appdata`) and would otherwise
   accumulate across every invocation — `preflight()` archives and truncates it at the
   start of every run so L3 only ever sees the current run's output and two consecutive
   runs are judged independently.
9. Tears down via a `trap cleanup EXIT`: dumps the mirth.log tail on failure, copies the
   full log to `smoke-tests/out/mirth-<timestamp>.log`, sends SIGTERM to the server PID,
   waits, falls back to `kill -9`, deletes the temporary appdata directory, deletes the
   channel work directory and REST session cookie jar, and restores `mirth.properties`
   from its `.smoke-bak` copy. Prints `HARNESS DURATION: <n>s (limit 600s)` on every exit
   path and fails with `SMOKE-FAILURE-CLASS: duration` if the harness itself (boot through
   teardown, excluding the one-time distribution build) exceeded the D-04 600s ceiling.

A fresh `dir.appdata` per run means a fresh embedded Derby database every time (no
`db.lck`/stale-appdata poisoning across consecutive runs — see Pitfall 3 in
`18-RESEARCH.md`).

### Failure-class markers

Every failure the harness can detect prints one of four `SMOKE-FAILURE-CLASS:` markers,
so CI logs/grep-based triage can tell at a glance which stage broke:

| Marker | Stage | Meaning |
|--------|-------|---------|
| `SMOKE-FAILURE-CLASS: import` | import/deploy | A channel failed to import, degraded to `InvalidChannel` on deploy, or never reached STARTED |
| `SMOKE-FAILURE-CLASS: assert` | driver | The JUnit pump/assert driver (`NativePumpChannelsTest`/`StubChannelsTest`) reported a test failure or error |
| `SMOKE-FAILURE-CLASS: log` | L3 log scan | `mirth.log` contains an ERROR line not covered by `fixtures/log-allowlist.txt` |
| `SMOKE-FAILURE-CLASS: duration` | duration gate | The harness run exceeded the D-04 600-second ceiling |

### Artifacts (`smoke-tests/out/`, gitignored)

Every run leaves behind (for CI artifact upload, 18-08):

- `mirth-<timestamp>.log` — the full server log from that run
- `mirth-preexisting-<timestamp>.log` — only appears if a prior run's log was found still
  present at boot (interrupted run that skipped cleanup); archived, never silently dropped
- `junit-reports-<timestamp>/` — the driver's JUnit XML reports (one file per test class)
- `server-stdout.log` — raw stdout/stderr from the launched `MirthLauncher` process

### D-04 duration expectation

The harness is expected to complete in low tens of seconds locally (boot + import/deploy +
driver + teardown) — well under the 600-second (10-minute) D-04 ceiling. The one-time `ant
mirth-build.xml` distribution build (~1-2 minutes) is a separate prerequisite step and is
never included in the measured duration.

## Add a channel

The 12 committed fixtures in `smoke-tests/channels/` cover every stock connector type
(NET-01): `http-test.xml`, `tcp-mllp-test.xml`, `file-test.xml`, `jdbc-test.xml`,
`vm-test.xml`, `js-test.xml`, `smtp-test.xml`, `soap-test.xml`, `dicom-test.xml`,
`doc-writer-test.xml` (the last one carries two Document Writer destinations — PDF and
RTF — the Phase 22 OpenPDF/OpenRTF fidelity baseline, D-08), plus the two
`legacy-migration-*` fixtures — `legacy-migration-test.xml` (plan 18-09, channel root
versioned schema 3.6.0) and `legacy-migration-3-4-test.xml` (plan 18-10, channel root
versioned schema 3.4.0) — see "Proving the net" below for why this pair is structurally
different from the other ten and must never be "fixed" to the current schema version.

To add a thirteenth fixture:

1. **Author the channel XML** under `smoke-tests/channels/<name>-test.xml`. Follow the
   existing fixtures' conventions:
   - Fixed, human-assigned sequential channel ID (`00000013-0000-0000-0000-000000000013`
     — continue the sequence; `...0011` and `...0012` are taken by the two
     `legacy-migration-*` fixtures).
   - `<description>` ends with "Test-only; never deploy to production."
   - Every ephemeral port/path is a `${VARNAME}` placeholder, never a hardcoded value.
   - A real transformer step (JavaScript Step, `com.mirth.connect.plugins.javascriptstep.JavaScriptStep`)
     that extracts or sets a `channelMap` variable (e.g. `patientName`) referenced by the
     destination template, so L2 content assertions (18-07) have something concrete to
     match. **Every element implementing `Migratable` — connector properties, transformer/
     filter, data-type properties, and transformer *steps* — needs an explicit
     `version="26.6.0"` attribute.** Omitting it defaults the element to version `3.0.0`
     and triggers the full migration chain against a live object, which can silently
     mutate or duplicate fields (discovered the hard way while building `js-test.xml` —
     see the plan 18-05 SUMMARY Deviations section).
   - Do NOT hand-write `<properties class="...">` blocks from scratch — copy the block
     structure from the nearest existing fixture with the same connector type, or derive
     it from the real `*Properties.java` source (field declaration order = XML element
     order for XStream's default reflection-based marshalling).
   - Generate the channel against a real running instance before committing: build the
     distribution, boot manually (or borrow this script's boot stages), import your draft
     over `POST /channels`, and `GET /channels/{id}` back — if the description reads
     "This channel is invalid. Verify all required extensions are loaded correctly.", the
     properties block does not match the real class shape.
2. **Add any new placeholder variable** to `ENVSUBST_ALLOWLIST` in `run-smoke-test.sh`
   (search for that variable name — it is centralized in one place) and to the port/dir
   allocator (`allocate_ports`/`allocate_work_dirs`) if it needs a fresh ephemeral
   value per run. Never widen this to bare `envsubst` (T-18-10 — arbitrary environment
   expansion into imported XML is a tampering/injection risk).
3. **Register the fixture** in `run-smoke-test.sh`: append the base filename (no
   extension) to `CHANNEL_FILES` and the matching channel ID to `CHANNEL_IDS` (same
   index position in both arrays — the script pairs them positionally for status/STARTED
   polling and error messages).
4. **Verify**: `smoke-tests/run-smoke-test.sh --deploy-only` should exit 0 twice
   consecutively with your new channel reaching STARTED alongside the existing 12.
5. **Wire assertions**: 18-07's pump/assert driver is where the actual HL7v2 message gets
   sent through the channel and the destination artifact is checked for the expected
   transformed content — this script only proves import/deploy/STARTED.

## Proving the net (NET-05 / SC-4 / break-dependency.sh)

`smoke-tests/break-dependency.sh` is the D-13/D-14 self-verifying broken-dependency proof:
it swaps EVERY `xstream-*.jar` found recursively under `server/setup/server-lib` aside, drops
in a deliberately incompatible fixture jar, runs the harness expecting failure, restores the
original jar(s) unconditionally (trap on EXIT), and verifies the restore.

**When to run:** before any CVE-track jar bump (Phase 23 xstream/BC re-land, Phase 24 Derby,
Phase 25 mssql-jdbc, Phase 26 Jersey) — rehearses the exact validation ritual a risky
dependency swap needs before it can be trusted, and before any future re-attempt at the
xstream 1.4.21 re-land that was rolled back in v26.6.0 (commit `6a483ab9d`).

**Exit-code contract:**

| Exit | Meaning |
|------|---------|
| `0`  | OK — harness caught the broken dependency (behavioral catch at the XStream import seam, `SMOKE-FAILURE-CLASS: import`) |
| `1`  | SELF-TEST FAILED — harness passed with the broken jar in place; the net has a hole |
| `2`  | INCONCLUSIVE — harness failed, but not at the import/dependency seam (e.g. boot/infrastructure failure) |

**Fixture jar selection:** `FIXTURE_JAR` defaults to `fixtures/xstream-1.4.10.jar` but is
overridable via the `BREAK_FIXTURE_JAR` environment variable, e.g.:

```bash
BREAK_FIXTURE_JAR=smoke-tests/fixtures/xstream-1.4.21.jar smoke-tests/break-dependency.sh
```

**Verified result (plan 18-10, JDK 26.0.1 — locally available JDK clearing the Phase 16
Derby preflight, 2026-07-23): exit `0` (harness caught the broken dependency) on the FIRST
authorized fixture tried, `BREAK_FIXTURE_JAR=smoke-tests/fixtures/xstream-1.4.21.jar` paired
with the new `legacy-migration-3-4-test.xml` (channel root versioned schema 3.4.0, plan
18-10's 12th fixture). No fallback to the `1.4.10` default fixture and no transformer/filter
escalation were needed. Verdict tail:**

```
INFO: Harness exit code: 1
OK: harness caught the broken dependency (behavioral catch at the XStream import seam)
--- import-stage failure excerpt ---
SMOKE-FAILURE-CLASS: import
INVALID CHANNEL (import degraded): legacy-migration-3-4-test (00000012-0000-0000-0000-000000000012)
-------------------------------------
INFO: Restoring original xstream jar(s)...
OK: Restoration verified: all 1 original xstream jar(s) back in place, fixture jar removed.
```

Notably, in the SAME broken-jar run, `legacy-migration-test.xml` (the 3.6.0-rooted fixture,
already `>= 3.5.0`) imported, migrated, deployed, and reached STARTED cleanly — while
`legacy-migration-3-4-test.xml` (channel root at schema 3.4.0) degraded to `InvalidChannel`
and was absent from `GET /channels/statuses`. This is the exact differential the gap
analysis below predicted: only a channel root versioned `< 3.5.0` forces
`Channel.migrate3_5_0()` to run, and only that mutation-then-reload sequence trips on the
broken xstream jar.

**Root cause history — why `legacy-migration-test.xml` alone (plan 18-09) could not catch
this regression class:** `MigratableConverter.migrateElement()` only invokes
`Migratable.migrate3_5_0(element)` — the exact method commit `22940c0f8` names as removing
`codeTemplateLibraries` and adding `exportData` (the DOM mutation that the
`22940c0f8`/`6a483ab9d` regression's reload bug affected) — when
`MigrationUtil.compareVersions(elementVersion, "3.5.0") < 0`, i.e. only for elements
versioned **strictly older than 3.5.0**. `legacy-migration-test.xml` (like the
`XStreamSeamTest` fixture it was extracted from) is versioned `3.6.0` — already `>= 3.5.0` —
so `migrate3_5_0()` never ran for it, and the specific DOM-mutation-then-reload sequence the
regression affected was never exercised. `3.6.0` was the only fixture plan 18-09's
`<action>` authorized (`XStreamSeamTest.knownOldFormatChannelXmlDeserializes` proves it
migrates cleanly on the shipped xstream, avoiding the separate, pre-existing
`ImportConverter3_0_0` NPE that 18-08 hit with a hand-crafted pre-3.0.0-format channel) — a
fixture versioned `< 3.5.0` was out of plan 18-09's authorized scope, and plan 18-09 recorded
an honest negative (`1.4.10` downgrade does not reproduce a functional break either — see
below) pending exactly this follow-up.

**Why `legacy-migration-3-4-test.xml` (plan 18-10) exists and closes the gap:** its `<channel>`
root is versioned schema 3.4.0 — chosen `>= 3.0.0` so the `ImportConverter3_0_0` NPE boundary
is never entered, and `< 3.5.0` so `Channel.migrate3_5_0()` runs unconditionally on every
import. Every nested element (sourceConnector, transformer, filter, properties, etc.)
deliberately stays at schema 3.6.0 — `Transformer.migrate3_5_0()`/`Filter.migrate3_5_0()`
call `removeChild("steps")`/`removeChild("rules")` and would NPE on the post-3.5-shaped
`<elements/>` bodies if their own version attributes were downgraded too. The channel-root
migration alone is sufficient to reproduce the exact `22940c0f8`/`6a483ab9d` seam.

**`xstream-1.4.10.jar` (the D-14-specified, 18-01-committed default fixture)** was not
needed for this run — the `1.4.21` fixture (Step 1 of the plan's authorized sequence)
caught the regression on the first attempt. Per plan 18-08/18-09's prior findings,
`1.4.10` (a downgrade) is not expected to reproduce a forward-upgrade regression class;
this remains documented but was not re-tested since `1.4.21` already produced the
required evidence.

**Status: NET-05 / SC-4 IS closed by plan 18-10.** `break-dependency.sh` with
`BREAK_FIXTURE_JAR=smoke-tests/fixtures/xstream-1.4.21.jar` now records a genuine,
self-verifying behavioral catch (`SMOKE-FAILURE-CLASS: import`) at the exact seam the
v26.6.0 xstream rollback (commit `22940c0f8`, reverted in `6a483ab9d`) broke, with
`legacy-migration-3-4-test.xml` as the trip-wire. This is the recorded SC-4 evidence
gating Phase 23's xstream 1.4.21 re-land.

The script itself is complete, correct, and self-verifying per every D-13/D-14 structural
requirement (recursive jar discovery, generalized `BREAK_FIXTURE_JAR` override, trap-based
restore keyed on `$(basename "${FIXTURE_JAR}")` so no fixture is ever left in the tree,
restoration verification, three-way verdict classification) — the exit code accurately
reflects what actually happened, which is the property `break-dependency.sh` is designed to
prove.

## Provenance

Binary artifacts committed to this directory are downloaded from Maven Central
(repo1.maven.org) and SHA-1 verified against the published `.sha1` sidecar before being
committed. No other new external dependencies are introduced by Phase 18 (see
`18-RESEARCH.md` Package Legitimacy Audit — all entries Approved).

| Artifact | Version | SHA-1 (verified against repo1.maven.org) | Purpose |
|----------|---------|-------------------------------------------|---------|
| `testlib/greenmail-1.6.15.jar` | 1.6.15 | `cacc939fff36cabc9512644130504ef4f7ef53e8` | SMTP endpoint stub (NET-01 names 1.6.15 explicitly; D-07) |
| `testlib/greenmail-junit4-1.6.15.jar` | 1.6.15 | `19498174e9b8f832ff629fd568da45c34618a369` | JUnit 4 GreenMail rule/integration for the assertion driver |
| `fixtures/xstream-1.4.10.jar` | 1.4.10 | `dfecae23647abc9d9fd0416629a4213a3882b101` | Break-dependency fixture (D-13/D-14, plan 18-08) — a deliberately old, incompatible XStream jar. **Never placed on any runtime classpath**; lives under `fixtures/` only. |
| `fixtures/xstream-1.4.21.jar` | 1.4.21 | `65cb3e7f809b18b9aab43f2338ee5b320f72d7bd` | Break-dependency contingency fixture (D-13/D-14, plan 18-09) — the literal xstream version rolled back in v26.6.0 (commit `6a483ab9d`). Select via `BREAK_FIXTURE_JAR=smoke-tests/fixtures/xstream-1.4.21.jar`. **Never placed on any runtime classpath**; lives under `fixtures/` only. |

GreenMail's transitive `com.sun.mail:jakarta.mail` dependency is intentionally NOT
downloaded — the shipped `server/setup/server-lib/javax/javax.mail-1.6.2.jar` already
provides the `javax.mail.*` API at runtime, and adding a second mail jar would create a
duplicate-package classpath conflict (see Pitfall 5 in `18-RESEARCH.md`).

## Directory layout

```
smoke-tests/
├── run-smoke-test.sh      # boot/teardown orchestrator (this plan, 18-01)
├── README.md              # this file
├── send_mllp.py           # MLLP frame sender (verbatim content copy from feature/26.6.x)
├── channels/              # committed channel-XML fixtures (18-05)
├── src/                   # JUnit assertion driver + stub bootstrap classes (18-06/18-07)
├── testlib/               # committed test-infra jars (GreenMail pair)
├── fixtures/              # break-dependency fixture jar + log-allowlist.txt
└── out/                   # gitignored — mirth.log copies from harness runs
```
