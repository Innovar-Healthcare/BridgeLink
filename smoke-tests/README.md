# smoke-tests/

## Purpose

A boot-only smoke harness for `release/26.6.1` (IRT-2270). It boots the real assembled
BridgeLink distribution directly on the runner JVM, with embedded Derby, ephemeral ports, and
an auto-generated keystore, health-checks it over HTTPS, then tears down cleanly. This proves
the embedded-Derby boot path on both JDK 17 (expected clean abort at the JAVA-04 preflight) and
JDK 21 (successful boot), the two seams the Derby 10.17 CVE backport (IRT-2271) changed.

This is a pruned subset of the 26.9.x channel-deploy smoke harness.
Only the boot/health-check/teardown path is kept here; channel import/deploy, message
pump/assert, and every connector-specific fixture are 26.9-only and out of scope for this
security-only patch release.

## Prerequisites

Build the assembled distribution once before running the harness:

```bash
cd server
ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true
```

This produces `server/setup/` (`server-lib/mirth-server.jar`, `mirth-server-launcher.jar`,
`conf/mirth.properties`, etc.) - the harness never rebuilds it, only boots it.

**Rebuild it again after ANY change to a source-tree jar.** `server/setup/` is gitignored, so it
is pure local state: an assembled distribution left over from before a dependency bump still
carries the old jars, and every verdict produced against it is then about the old jars, not the
ones under test. `smoke-tests/check-dist-freshness.sh` reconciles `server/lib`, `client/lib`,
and `manager/lib` against the assembled tree and exits `2` with a rebuild hint on any mismatch.
It runs automatically in `run-smoke-test.sh`'s `preflight()`, and can also be run standalone:

```bash
bash smoke-tests/check-dist-freshness.sh
```

## Usage

```bash
bash smoke-tests/run-smoke-test.sh --boot-only
```

`--boot-only` stops after the health check succeeds and tears down immediately - this is the
only mode this branch's harness supports; the channel import/deploy path it would otherwise
reach is not part of this pruned copy. Exit code is 0 iff the boot and health check passed.

### What the script does

1. Preflight: confirms `server/setup/server-lib/mirth-server.jar` and
   `server/setup/conf/mirth.properties` exist, and runs `check-dist-freshness.sh` to confirm
   the assembled distribution matches the source-tree jars (fails fatally with a rebuild hint
   if not).
2. Allocates ephemeral ports and a scratch work directory.
3. Patches `server/setup/conf/mirth.properties` so the admin API binds to `127.0.0.1` only.
4. Launches `MirthLauncher` against the assembled `server/setup/`, redirecting its stdout and
   stderr to `smoke-tests/out/server-stdout.log`.
5. Polls `https://127.0.0.1:<https-port>/api/server/version` until it responds or the
   `--boot-only` health-check timeout is reached.
6. Tears down: stops the server process, restores `mirth.properties`, and removes the scratch
   work directory.

On JDK 17, the embedded-Derby preflight in `Mirth.java` aborts the boot with the message
`embedded Derby requires Java 21+ as of 26.6.1; upgrade Java or switch to an external database`
before the health check ever runs; `.github/workflows/smoke-harness.yml` wraps that leg in a
three-outcome expected-failure check rather than treating it as a plain pass/fail.
