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

This produces `server/setup/` (`server-lib/mirth-server.jar`, `mirth-server-launcher.jar`,
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
- `--deploy-only`: boot, then import and deploy all 23 reference channel fixtures, poll
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

The registry has grown to 23 committed fixtures in `smoke-tests/channels/` (channel IDs
`00000001` through `00000023`). The original 12 (Phase 18, NET-01) cover every stock
connector type: `http-test.xml`, `tcp-mllp-test.xml`, `file-test.xml`, `jdbc-test.xml`,
`vm-test.xml`, `js-test.xml`, `smtp-test.xml`, `soap-test.xml`, `dicom-test.xml`,
`doc-writer-test.xml` (the last one carries two Document Writer destinations — PDF and
RTF — the Phase 22 OpenPDF/OpenRTF fidelity baseline, D-08), plus the two
`legacy-migration-*` fixtures — `legacy-migration-test.xml` (plan 18-09, channel root
versioned schema 3.6.0) and `legacy-migration-3-4-test.xml` (plan 18-10, channel root
versioned schema 3.4.0) — see "Proving the net" below for why this pair is structurally
different from the other ten and must never be "fixed" to the current schema version.
Phase 18.1 (NET-06, channel IDs `00000013`-`00000020`) added 8 more fixtures slicing the
HTTP connector's parameter surface into per-cluster channels — see "Parameter-coverage
pattern (18.1)" below for the slicing rule and the reusable pattern for the next connector.
Phase 18.2 (NET-07, channel IDs `00000021`-`00000023`) added 3 more fixtures folding the
IRT-828/831/832 Jetty regressions into the harness — see "Jetty regression coverage
(18.2)" below for the fixture purposes, the new test classes, and the deferred HTTPS gap:

- `http-listener-contextpath-test.xml` (`00000021`) — IRT-831 strict contextPath-prefix
  routing: an HTTP Listener with a non-empty source-level `<contextPath>/smokectx</contextPath>`
  plus a static resource nested under it, proving in-context/sub-path/outside/shared-prefix
  routing and static-resource-vs-channel precedence.
- `http-listener-largeresp-test.xml` (`00000022`) — IRT-832 Content-Length correctness on a
  channel-generated 100KB (102400-byte) response body (JavaScript Writer destination, default
  200 status).
- `http-listener-error500-test.xml` (`00000023`) — IRT-832 Content-Length correctness on a
  fixed `responseStatusCode=500` response with a small known body, proving the message still
  processes to SENT with zero errors even on a non-2xx response status.

`http-listener-response-test.xml` (the Phase 18.1 response-cluster fixture, channel
`00000013`) also gained two new `HttpStaticResource` siblings for IRT-828 coverage:
`/static/large` (CUSTOM, exactly 40960 bytes — the >30720-byte Content-Length regression
threshold) and `/static/file` (FILE, backed by `${STATIC_FILE_PATH}`, a pre-created
102400-byte file) — both additive; the pre-existing `/static/smoke` resource was left
byte-identical.

To add a 24th fixture:

1. **Author the channel XML** under `smoke-tests/channels/<name>-test.xml`. Follow the
   existing fixtures' conventions:
   - Fixed, human-assigned sequential channel ID (`00000024-0000-0000-0000-000000000024`
     — continue the sequence; IDs `00000001`-`00000023` are taken by the 12 Phase 18
     fixtures, the 8 Phase 18.1 HTTP parameter-coverage fixtures, and the 3 Phase 18.2
     Jetty regression fixtures).
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
   consecutively with your new channel reaching STARTED alongside the existing 23.
5. **Wire assertions**: 18-07's pump/assert driver is where the actual HL7v2 message gets
   sent through the channel and the destination artifact is checked for the expected
   transformed content — this script only proves import/deploy/STARTED.

## Parameter-coverage pattern (18.1)

Phase 18.1 (NET-06) extended the Phase 18 harness from "one fixture per connector type"
to "one fixture per parameter cluster" for a single connector (HTTP), taking the fixture
count from 12 to 20 (channel IDs `00000013` through `00000020`) without touching a single
byte of `http-test.xml` or the Phase 18 pump/stub test classes. This section is the
reusable shape: a later TCP/SMTP/JDBC parameter-coverage phase should be planned as "follow
this pattern", not re-derived from scratch.

### 1. Fixture slicing rule

- **One channel per parameter cluster, not one channel per connector.** Phase 18.1 shipped
  eight new fixtures for a single connector type: `http-listener-response-test.xml`
  (response headers/status/content-type/static-resource cluster), `http-datatype-xml-test.xml`
  (xmlBody+includeMetadata envelope cluster), `http-datatype-binary-recv-test.xml` /
  `http-datatype-binary-send-test.xml` (binary round-trip, split listener/sender),
  `http-listener-auth-basic-test.xml` / `http-listener-auth-digest-test.xml` (one fixture
  per auth type — never share an auth-type cluster across a channel), `http-sender-params-test.xml`
  (sender headers/query-params/content-type/non-preemptive auth-out), and
  `http-sender-timeout-test.xml` (isolated timeout case, see below).
- **The baseline connector fixture is never edited.** `http-test.xml` (channel `00000001`)
  stayed byte-for-byte untouched across all six plans (verified every plan via
  `git diff --stat smoke-tests/channels/http-test.xml` returning empty) — so a parameter
  regression reads as "custom HTTP params broke" (the new fixtures), never "the connector
  broke" (the baseline). Apply the same rule to the next connector's baseline fixture
  (`tcp-mllp-test.xml`, `smtp-test.xml`, `jdbc-test.xml`).
- **Expected-error cases get their OWN channel.** `http-sender-timeout-test.xml` is a
  single-message, `queueEnabled=false`/`retryCount=0` channel isolated from
  `http-sender-params-test.xml` specifically so an expected-error exemption (see §3) never
  shares a channel ID with a zero-error assertion. A shared channel cannot simultaneously
  assert "zero errors" (every other cluster's L1 check) and "exactly one expected error"
  (the timeout cluster's own check) without one assertion silently swallowing the other.

### 2. Recording-stub pattern

`RecordingHttpStub` (`smoke-tests/src/com/mirth/connect/smoketest/stubs/RecordingHttpStub.java`,
built on JDK built-in `com.sun.net.httpserver`, zero new jars — the same pattern as
`SoapStub`) exposes three contexts on one ephemeral port:

- **`/record`** — record-everything context. Every request (method, path, query, headers,
  body) is captured into a `RecordedRequest` and appended to a synchronized list, queryable
  via `getRequests(pathPrefix)`. This is the L2 artifact for sender-side fixtures that have
  no destination file to poll — the stub recording IS the wire-format proof.
- **`/auth`** — challenge context for auth-OUT assertions. Issues an explicit 401 +
  `WWW-Authenticate: Basic` challenge on the bare first request rather than relying on
  `com.sun.net.httpserver`'s built-in `BasicAuthenticator` — this is load-bearing, not
  cosmetic: a non-preemptive client (`usePreemptiveAuthentication=false`) sends request #1
  with NO `Authorization` header and only resends with credentials after seeing the 401
  challenge, so the stub must issue that challenge itself or the retry (and the header it
  carries) is never observed.
- **`/stall`** — timeout context. Sleeps for a configurable duration (constructor arg,
  default 5000ms in the real timeout fixture, a short 200ms in the self-test) before
  responding, forcing the client's `socketTimeout` to trip.
- **Wiring:** the stub's port is allocated **script-side** (`HTTP_STUB_PORT=$(free_port)` in
  `allocate_ports()`, exported, added to `ENVSUBST_ALLOWLIST`, forwarded to the JUnit driver
  as a `-D` sysproperty) but **bound driver-side** — `HttpParamsTest`'s own `@BeforeClass`
  constructs and starts the `RecordingHttpStub` on that port before any message is pumped,
  and stops it (LIFO, `server.stop(1)` — never `stop(0)`, so an in-flight `/stall` sleep
  isn't cut off mid-response) after the test class finishes.
- **Self-test first:** `StubSelfTest` exercises all three contexts (record/challenge/stall)
  with no live BridgeLink server running at all, before the stub is ever wired into a real
  channel — proving the stub's own behavior is correct in isolation before it becomes a
  dependency of the real assertion suite.

### 3. Expected-error exemption mechanics

The timeout cluster (`http-sender-timeout-test.xml`, D-06) was the one case in this phase
budgeted for a `fixtures/log-allowlist.txt` entry — but **no entry was actually added**.
Live verification (plan 18.1-05, run twice consecutively) found that `HttpDispatcher`'s
`logger.error("Error connecting to HTTP server.", t)` call does NOT surface in
`server/setup/logs/mirth.log` in this environment even though the timeout genuinely fires
(errorCount rises, exactly one `/stall` request is recorded, and the message's stored error
content contains `SocketTimeoutException`) — `mirth.log` contained only the four standard
server-startup INFO lines across both runs, zero ERROR lines of any kind. This resolved
RESEARCH's Assumption A3 as **false** for this codebase/logging-config combination.

**The mechanics still apply for the next connector cluster that budgets one** (some
connector's expected-error path may log differently):

- Every entry in `fixtures/log-allowlist.txt` MUST be preceded by a comment naming its
  owning channel and the decision that added it (e.g. `# http-sender-timeout-test
  (18.1-05, D-06): expected SocketTimeoutException on /stall dispatch`) — never an
  unattributed bare pattern.
- Make the pattern the **narrowest possible match** — the exact log line's distinguishing
  text (exception class name, connector-specific message), not a blanket `ERROR` or
  connector-name wildcard.
- **The safety argument:** each channel's own L1 assertion (`errorCount == 0` for every
  zero-error cluster) runs independently of the L3 log scan and is never touched by an
  allowlist entry scoped to a different channel's expected-error line. A narrowly-scoped
  allowlist entry therefore cannot hide a genuine regression in any OTHER channel — only the
  one channel whose exact expected-error line matches the pattern is exempted, and that
  channel's own zero-error assertion (there isn't one, because it's the expected-error
  channel — see §1) is replaced by the explicit `pollUntil(errorCount >= 1)` +
  wire-recording-count + message-content-substring checks documented in `HttpParamsTest`.
- **Derive from reality, never guess:** run the full harness first, capture the exact log
  line (if any) from a live `mirth.log`, and derive the pattern from that captured line. If,
  as happened here, no ERROR line appears at all, do not add a speculative entry — document
  the non-outcome (as this section does) rather than allowlisting something that will never
  match.

### 4. Wiring checklist for the next connector (TCP/SMTP/JDBC)

Ordered, following the exact sequence this phase used across six plans:

1. **Allocate ports** — add one `$(free_port)` call per new listener/stub port in
   `allocate_ports()` in `run-smoke-test.sh`, export it alongside the existing exports.
2. **Export → `ENVSUBST_ALLOWLIST`** — add every new `${VARNAME}` placeholder used by the
   new fixture XML to `ENVSUBST_ALLOWLIST` (centralized in one place in `run-smoke-test.sh`)
   — forgetting this step is a hard failure (envsubst leaves the literal `${...}` text in
   the imported XML, breaking port parsing).
3. **`CHANNEL_FILES` / `CHANNEL_IDS`** — append the new fixture's base filename and its
   channel ID (same index position in both arrays) using the next free sequential ID
   (18.1 continued `00000013`-`00000020`, 18.2 continued `00000021`-`00000023`; the next
   connector phase continues from `00000024`).
4. **`OUT_DIR` subdirs** — add a work-directory subdirectory in `allocate_work_dirs()` for
   any new File-Writer-style destination artifact the new fixture writes to.
5. **Listener probes** — add the new port to `wait_for_listener_ports()` ONLY if the probe
   is side-effect-free. Auth listeners are safe to probe (the security handler rejects an
   unauthenticated bare GET with 401 before any message is created — no statistics skew).
   Success-path listeners (a bare GET that would actually be parsed and processed as a real
   message) must be left unprobed, with an inline comment explaining why — probing them
   would create spurious statistics/artifacts that corrupt the L1/L2 assertions the real
   test later makes.
6. **`run_driver()` `-D` forwards** — forward every new port/path as a `-D` property to the
   JUnit driver invocation.
7. **`build.xml` property/sysproperty pairs** — add a matching `<property name="..."
   value=""/>` and `<sysproperty>` pair for each new `-D` so the forked JUnit JVM actually
   receives it.
8. **New `XxxParamsTest extends SmokeTestBase`** — create it beside the connector's existing
   pump/stub class (e.g. `TcpParamsTest` beside `NativePumpChannelsTest`'s MLLP coverage),
   with a LOCAL `requireProperty`-style replica reading the new cluster's `-D` properties in
   its own `@BeforeClass` — **never** add the new properties to
   `SmokeTestBase.baseSetUp()`'s required list, which would break every OTHER test class
   (`NativePumpChannelsTest`, `StubChannelsTest`) when run without them.
9. **Validate fixture XML shape via `--deploy-only`** — `bash smoke-tests/run-smoke-test.sh
   --deploy-only` before writing a single assertion. `InvalidChannel` (with a FATAL exit) is
   the validator for a hand-written `<properties class="...">` block that doesn't match the
   real class shape — cheaper to catch here than after the assertion driver is built.



## Break-dependency proof (NET-05)

`smoke-tests/break-dependency.sh` is the D-13/D-14 self-verifying broken-dependency proof:
it swaps EVERY jar matching `BREAK_LIB_GLOB` (default `xstream-*.jar`) found recursively under
`server/setup/server-lib` aside, drops in a deliberately incompatible fixture jar, runs the
harness expecting failure, restores the original jar(s) unconditionally (trap on EXIT), and
verifies the restore.

**When to run:** before any CVE-track jar bump (Phase 23 xstream/BC re-land, Phase 24 Derby,
Phase 25 mssql-jdbc, Phase 26 Jersey) — rehearses the exact validation ritual a risky
dependency swap needs before it can be trusted, and before any future re-attempt at the
xstream 1.4.21 re-land that was rolled back in v26.6.0 (commit `6a483ab9d`).

**Exit-code contract:**

| Exit | Meaning |
|------|---------|
| `0`  | OK — harness caught the broken dependency (behavioral catch at the configured `BREAK_EXPECT_FAILURE_CLASS` seam, default `import`, `SMOKE-FAILURE-CLASS: <class>`) |
| `1`  | SELF-TEST FAILED — harness passed with the broken jar in place; the net has a hole |
| `2`  | INCONCLUSIVE — harness failed, but not at the configured seam (e.g. boot/infrastructure failure) |

**Fixture jar selection:** `FIXTURE_JAR` defaults to `fixtures/xstream-1.4.21-mangled.jar`
(the D-25 armed default, see below) but is overridable via the `BREAK_FIXTURE_JAR`
environment variable, e.g.:

```bash
BREAK_FIXTURE_JAR=smoke-tests/fixtures/xstream-1.4.10.jar smoke-tests/break-dependency.sh
```

**Generalization knobs (Phase 23, D-26):** two additional environment variables generalize
the canary beyond xstream, both defaulting to today's xstream-only behavior so no caller is
broken by their introduction:

| Variable | Default | Purpose |
|----------|---------|---------|
| `BREAK_LIB_GLOB` | `xstream-*.jar` | The `find`-style glob used to enumerate jars under `server/setup/server-lib` to swap aside. Phases 24 (Derby)/25 (mssql-jdbc)/26 (Jersey) can point this at their own dependency's jar family without a script rewrite. |
| `BREAK_EXPECT_FAILURE_CLASS` | `import` | Which `SMOKE-FAILURE-CLASS:` marker the verdict classifier greps for. Must be one of the four legal values documented in "Failure-class markers" above: `import`, `assert`, `log`, `duration` — a new knob must not invite a fifth. |

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

**`xstream-1.4.10.jar` (the D-14-specified, 18-01-committed default fixture) — settled by
Phase 23 plan 02's rung-0 run (2026-07-30, JDK 21.0.12, clearing the Phase 16 Derby
preflight):** `BREAK_FIXTURE_JAR=smoke-tests/fixtures/xstream-1.4.10.jar` against the full
29-channel reference set (including `legacy-migration-3-4-test.xml`) recorded exit `1`
(SELF-TEST FAILED — harness passed cleanly, all 29 channels STARTED, 116 assertions
passed). This settles the question left open since plan 18-08/18-09: a `1.4.10` downgrade
genuinely does **not** reproduce the forward-upgrade regression class, confirmed
mechanically in `23-RESEARCH.md` §R3.1 (xstream `DomReader` is field- and API-identical
between 1.4.10 and 1.4.20; only 1.4.21 carries the GHI:#342 child-cache split). Verdict
tail:

```
HARNESS DURATION: 42s (limit 600s)
HARNESS PASSED (116 pass(es))
---------------------------------------
INFO: Harness exit code: 0
SELF-TEST FAILED: harness PASSED with broken xstream-*.jar — net has a hole
INFO: Restoring original xstream jar(s)...
OK: Restoration verified: all 1 original xstream jar(s) back in place, fixture jar removed.
```

**Rung 1 (an old BouncyCastle or Rhino jar) is SKIPPED per D-25** — not attempted. `BcSeamTest`
is designed green on both BC 1.78.1 and 1.84; and once Phase 23 plan 04's D-10 throw lands, a
Rhino 1.7.13 downgrade fails at *boot* (`JavaIterableIterator` absent from that jar), the wrong
stage for the import-seam verdict grep. Straight to rung 2.

**Status: NET-05 / SC-4 IS closed by plan 18-10, and RE-ARMED by Phase 23 plan 02 (D-25).**
Phase 23 lands xstream 1.4.21 as the shipped version, so the plan-18-10 fixture
(`fixtures/xstream-1.4.21.jar`) **can no longer break anything — it IS what ships.** Per D-25
the armed default is now a **deliberately-mangled copy**, `fixtures/xstream-1.4.21-mangled.jar`
(derived by removing `com/thoughtworks/xstream/io/xml/DomReader.class` — the exact superclass
`MirthDomReader` extends — from a copy of the shipped 1.4.21 jar via `zip -d`; see the
Provenance table below for the derivation recipe and locally-computed SHA-1), made the
**in-script default** at `break-dependency.sh`'s `FIXTURE_JAR` assignment (not a
`BREAK_FIXTURE_JAR` override) so the armed configuration cannot be run wrong by omission.

**Rung 2 live exit 0 (Phase 23 plan 02, 2026-07-30, JDK 21.0.12):**
`smoke-tests/break-dependency.sh` run with **no environment variable set** recorded a genuine,
self-verifying behavioral catch at the import seam — `NoClassDefFoundError:
com/thoughtworks/xstream/io/xml/DomReader` inside `MirthDomReader`'s own class-loading path.
Verdict tail:

```
HARNESS DURATION: 15s (limit 600s)
HARNESS FAILED (1 failure(s), 14 pass(es))
---------------------------------------
INFO: Harness exit code: 1
OK: harness caught the broken dependency (behavioral catch at the import seam)
--- import-stage failure excerpt ---
INFO: Importing 29 reference channels...
PASS: Imported http-test.xml
SMOKE-FAILURE-CLASS: import
<com.mirth.connect.client.core.ControllerException>
  <detailMessage>com.mirth.connect.donkey.util.xstream.SerializerException: java.lang.NoClassDefFoundError: com/thoughtworks/xstream/io/xml/DomReader</detailMessage>
  <cause class="com.mirth.connect.donkey.util.xstream.SerializerException">
    <detailMessage>java.lang.NoClassDefFoundError: com/thoughtworks/xstream/io/xml/DomReader</detailMessage>
    <cause class="java.lang.NoClassDefFoundError">
-------------------------------------
INFO: Restoring original xstream jar(s)...
OK: Restoration verified: all 1 original xstream jar(s) back in place, fixture jar removed.
```

After the run, `find server/setup/server-lib -name 'xstream-*.jar'` named only
`xstream-1.4.21.jar` — the trap-based restore left the runtime classpath clean; no mangled
jar survives on any runtime path.

This is the recorded D-06 hard-gate evidence for Phase 23's xstream 1.4.21 re-land: NET-05's
break-proof canary is proven live-armed at the exact moment its previous armed configuration
(the plan-18-10 fixture) stopped being able to fire.

The script itself is complete, correct, and self-verifying per every D-13/D-14/D-25/D-26
structural requirement (recursive jar discovery generalized via `BREAK_LIB_GLOB`, `BREAK_FIXTURE_JAR`
override with an armed in-script default, verdict classification generalized via
`BREAK_EXPECT_FAILURE_CLASS`, trap-based restore keyed on `$(basename "${FIXTURE_JAR}")` so no
fixture is ever left in the tree, restoration verification, three-way verdict classification) —
the exit code accurately reflects what actually happened, which is the property
`break-dependency.sh` is designed to prove.

## Jetty regression coverage (18.2)

Phase 18.2 (NET-07) folded six previously-standalone IRT-828/831/832/833/834/835 Jetty
regression checks (Content-Length correctness and contextPath routing, discovered against
released images) into this harness, adding two new sibling `*Test` classes rather than
extending `HttpParamsTest` or `WebServerRegressionTest`'s existing peers — each gets its own
JUnit report, auto-discovered by `build.xml`'s `batchtest`, no registration required.

- **`JettyRegressionTest`** (connector surface, three new fixtures `00000021`-`00000023`) —
  `contextPathRouting()` (IRT-831: strict prefix+slash contextPath matching, including the
  shared-prefix regression case, plus static-resource-under-context precedence),
  `responseContentLengthLarge()` and `errorResponseContentLength()` (IRT-832: Content-Length/
  gzip-tolerance correctness on a 100KB generated body and on a fixed 500-status response).
  `HttpParamsTest` itself also gained four IRT-828 assertions reusing the existing
  `http-listener-response-test.xml` fixture's two new static resources (small/large/gzip/FILE
  Content-Length correctness) — see "Add a channel" above for the fixture-level detail.
- **`WebServerRegressionTest`** (webserver surface — dials the already-booted HTTPS webserver
  directly, on `HTTPS_PORT`, reusing `RestClient`'s shared trust-all TLS client; no channel
  deploy) — covers IRT-834 (Swagger UI: index/bundle.js/css/`/api/server/version`
  Content-Length) and IRT-835 (WebStart JNLP: `/webstart`, bad-param 404, `.jnlp` alias
  Content-Length) in full, plus IRT-833 for its 404-missing-installer case only.

**IRT-833 disposition (D-A):** only the free 404 case (no `public_html/installers/`
directory present) is folded in-harness. Full IRT-833 coverage (asserting Content-Length on
an actual installer download) needs a real installer binary dropped into the distribution's
`public_html/installers/` directory *before* server boot — the harness boots a single shared
distribution with no such pre-boot file-drop step, and adding one was judged out of scope for
an in-harness fixture. That full-installer case continues to live in the standalone,
Docker-based `regression-scripts/test-irt833-installer-content-length.sh` (see
`regression-scripts/README.md` for the released-image vs in-harness-HEAD division of labor
this cross-references). IRT-834/835/831/832/828 are folded here in full; only IRT-833 keeps
a released-image-only companion script for its full case.

### Deferred: HTTPS-connector fixture (D-B)

An HTTPS Listener fixture (an HTTP Listener channel with the connector's own TLS enabled,
as opposed to the harness's admin/API `HTTPS_PORT`) is **INFEASIBLE with stock code** and was
deliberately NOT added in 18.2. `DefaultHttpConfiguration.configureReceiver()` builds a plain
Jetty `ServerConnector` with no TLS path at all — TLS-terminated HTTP Listener connectors
require a custom `HttpConfiguration` implementation, which is the commercial SSL-Manager
plugin's seam, not something stock BridgeLink code exposes. Building a stub TLS
`HttpConfiguration` solely for this harness was judged out of scope for a regression-coverage
phase.

A **sender-side** HTTPS case (an HTTP Sender dispatching to a TLS endpoint) is feasible in
principle but was also deferred — it is not blocked by the same seam, since the sender uses
the JVM's system-default `SSLContext` rather than a connector-level TLS configuration. If
pursued in a later phase, the design sketch is: **"HTTP Sender → in-JVM `HttpsServer` stub"**
— stand up a JDK built-in `com.sun.net.httpserver.HttpsServer` (the same zero-new-jars
pattern as `RecordingHttpStub`) inside the JUnit driver JVM, bound to an ephemeral port, with
an `SSLContext` built from a self-signed stub certificate. The load-bearing design detail:
that self-signed stub cert must be **injected into the server JVM's truststore** before the
HTTP Sender fixture dispatches to it — the harness's `MirthLauncher` JVM and the JUnit driver
JVM are separate processes, and the sender's outbound `SSLContext` (system-default) will
reject an untrusted self-signed cert with no explicit truststore wiring. This truststore-
injection step is the reason the sender-side case was deferred rather than folded alongside
the other five IRT tickets in this phase — it needs its own scoped design/verification pass,
not a quick addition to an existing fixture.

## Provenance

Binary artifacts committed to this directory are downloaded from Maven Central
(repo1.maven.org) and SHA-1 verified against the published `.sha1` sidecar before being
committed. No other new external dependencies are introduced by Phase 18 (see
`18-RESEARCH.md` Package Legitimacy Audit — all entries Approved). **Exception (Phase 23,
D-25):** `fixtures/xstream-1.4.21-mangled.jar` is a **derived** artifact, not downloaded from
Maven Central, so it has no upstream `.sha1` sidecar to verify against. Its row below gives
the exact derivation recipe (source jar + what was removed) and a **locally-computed** SHA-1
in place of an upstream one — it is never placed on any runtime classpath.

| Artifact | Version | SHA-1 (verified against repo1.maven.org) | Purpose |
|----------|---------|-------------------------------------------|---------|
| `testlib/greenmail-1.6.15.jar` | 1.6.15 | `cacc939fff36cabc9512644130504ef4f7ef53e8` | SMTP endpoint stub (NET-01 names 1.6.15 explicitly; D-07) |
| `testlib/greenmail-junit4-1.6.15.jar` | 1.6.15 | `19498174e9b8f832ff629fd568da45c34618a369` | JUnit 4 GreenMail rule/integration for the assertion driver |
| `fixtures/xstream-1.4.10.jar` | 1.4.10 | `dfecae23647abc9d9fd0416629a4213a3882b101` | Break-dependency fixture (D-13/D-14, plan 18-08) — a deliberately old, incompatible XStream jar. **Never placed on any runtime classpath**; lives under `fixtures/` only. Rung 0 negative confirmed (Phase 23 plan 02, D-25): paired with `legacy-migration-3-4-test.xml`, records exit 1 (SELF-TEST FAILED — does not reproduce the forward-upgrade regression class). |
| `fixtures/xstream-1.4.21.jar` | 1.4.21 | `65cb3e7f809b18b9aab43f2338ee5b320f72d7bd` | Break-dependency contingency fixture (D-13/D-14, plan 18-09) — the literal xstream version rolled back in v26.6.0 (commit `6a483ab9d`); plan 18-10 recorded a live exit-0 catch with it. **disarmed post-Phase-23** (D-08): xstream 1.4.21 is now the shipped version, so this fixture can no longer break anything — it IS what ships. Retained for the `22940c0f8`/`6a483ab9d` evidence trail; do not select it via `BREAK_FIXTURE_JAR` expecting a catch. |
| `fixtures/xstream-1.4.21-mangled.jar` | 1.4.21 (mangled) | `9de5fc65538a580289e5b4c97d956ca9d9f0f63a` (locally computed — **no upstream `.sha1` sidecar**; this is a derived artifact) | Break-dependency **armed default** (D-25, Phase 23 plan 02) — a byte-for-byte copy of `fixtures/xstream-1.4.21.jar` with `com/thoughtworks/xstream/io/xml/DomReader.class` removed via `zip -d smoke-tests/fixtures/xstream-1.4.21-mangled.jar 'com/thoughtworks/xstream/io/xml/DomReader.class'`, so `MirthDomReader`'s own superclass fails to link and the harness raises `NoClassDefFoundError` at the import seam (`SMOKE-FAILURE-CLASS: import`) unconditionally. This is the in-script `FIXTURE_JAR` default — no `BREAK_FIXTURE_JAR` override needed. **Never placed on any runtime classpath**; lives under `fixtures/` only. |

GreenMail's transitive `com.sun.mail:jakarta.mail` dependency is intentionally NOT
downloaded — the shipped `server/setup/server-lib/javax/javax.mail-1.6.2.jar` already
provides the `javax.mail.*` API at runtime, and adding a second mail jar would create a
duplicate-package classpath conflict (see Pitfall 5 in `18-RESEARCH.md`).

## Directory layout

```
smoke-tests/
├── run-smoke-test.sh          # boot/teardown orchestrator (this plan, 18-01)
├── break-dependency.sh        # NET-05/SC-4 break-proof canary (18-13/D-25/D-26)
├── check-jar-java17.sh        # MR-aware Java-17 loadability scan (Phase 23, D-17/D-18)
├── check-manifest-classpath.sh # manifest Class-Path reconciliation, RFC-822-aware (Phase 23, D-29.2)
├── README.md                  # this file
├── send_mllp.py               # MLLP frame sender (verbatim content copy from feature/26.6.x)
├── channels/                  # committed channel-XML fixtures (18-05)
├── src/                       # JUnit assertion driver + stub bootstrap classes (18-06/18-07)
├── testlib/                   # committed test-infra jars (GreenMail pair)
├── fixtures/                  # break-dependency fixture jar + log-allowlist.txt
└── out/                       # gitignored — mirth.log copies from harness runs
```
