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
   full mechanics.
7. Tears down via a `trap cleanup EXIT`: dumps the mirth.log tail on failure, copies the
   full log to `smoke-tests/out/mirth-<timestamp>.log`, sends SIGTERM to the server PID,
   waits, falls back to `kill -9`, deletes the temporary appdata directory, deletes the
   channel work directory and REST session cookie jar, and restores `mirth.properties`
   from its `.smoke-bak` copy.

A fresh `dir.appdata` per run means a fresh embedded Derby database every time (no
`db.lck`/stale-appdata poisoning across consecutive runs — see Pitfall 3 in
`18-RESEARCH.md`).

## Add a channel

The 10 committed fixtures in `smoke-tests/channels/` cover every stock connector type
(NET-01): `http-test.xml`, `tcp-mllp-test.xml`, `file-test.xml`, `jdbc-test.xml`,
`vm-test.xml`, `js-test.xml`, `smtp-test.xml`, `soap-test.xml`, `dicom-test.xml`,
`doc-writer-test.xml` (the last one carries two Document Writer destinations — PDF and
RTF — the Phase 22 OpenPDF/OpenRTF fidelity baseline, D-08).

To add an eleventh fixture:

1. **Author the channel XML** under `smoke-tests/channels/<name>-test.xml`. Follow the
   existing fixtures' conventions:
   - Fixed, human-assigned sequential channel ID (`00000011-0000-0000-0000-000000000011`
     — continue the sequence).
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
   consecutively with your new channel reaching STARTED alongside the existing 10.
5. **Wire assertions**: 18-07's pump/assert driver is where the actual HL7v2 message gets
   sent through the channel and the destination artifact is checked for the expected
   transformed content — this script only proves import/deploy/STARTED.

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
