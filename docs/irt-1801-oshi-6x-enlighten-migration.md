# IRT-1801: OSHI 3.9.1 to 6.12.0 Enlighten Migration Note

**Context:** Phase 26.9 swapped `oshi-core-3.9.1.jar` + `jna-4.5.2.jar` + `jna-platform-4.5.2.jar`
for `oshi-core-6.12.0.jar` + `jna-5.18.1.jar` + `jna-platform-5.18.1.jar` in `server/lib/`
(IRT-1801). No BridgeLink Java source imports OSHI or JNA; the only consumer of this surface is
the Enlighten Rhino channel script family running on customer servers, reached through
`Packages.oshi.*`. This note exists for CR-1, which owns the Enlighten template rewrite itself
(a separate track, out of this phase's scope) and is the only artifact this phase hands to it.
CR-1 should be able to rewrite the template from this note alone.

## Read this before the migration table: the blocking-call trap

The natural one-line fix for the removed no-arg `processor.getSystemCpuLoad()` is to pass it a
delay argument: `getSystemCpuLoad(1000)`. That method name is unchanged from 3.9.1, and it reads
like a safe drop-in replacement. It is not.

In oshi-core 6.x, `getSystemCpuLoad(long)` is a `default` interface method on `CentralProcessor`
that **sleeps for the supplied number of milliseconds** and then computes a tick delta over that
interval. The semantics changed from "sample a recent value" to "measure over an interval I will
block the calling thread for", while the method name stayed the same. Dropped into a per-message
Enlighten monitoring channel, that is a full second of serialized processing behind every single
message: a throughput catastrophe, not a formatting change.

**The primary recommendation for channel scripts is the non-blocking tick-delta form,**
`processor.getSystemCpuLoadBetweenTicks(priorTicks)`, with the prior tick array cached across
invocations (in `globalMap` for a real channel). Compute the delta against the cached prior array
BEFORE writing the fresh array back to `globalMap`: the write-back is the operation that ends this
invocation's measurement window and starts the next one, and a reader who sees the store happen
first will reach for the wrong variable when adapting this recipe. The recipe:

```javascript
var oldTicks = globalMap.get('oshiTicks');
var ticks    = processor.getSystemCpuLoadTicks();
var load = oldTicks ? processor.getSystemCpuLoadBetweenTicks(oldTicks) : -1;
globalMap.put('oshiTicks', ticks);
```

On the **first invocation**, and on any invocation where no usable prior sample exists, this
recipe yields **`-1`**, a not-sampled-this-invocation sentinel, never `0.0`. This reuses the same
unavailable-value convention this note establishes below for CPU frequency: `-1` means "no reading
was taken this invocation", not "the reading was zero".

### Sampling interval: oshi-core 6.x memoizes tick reads

oshi-core 6.x memoizes `getSystemCpuLoadTicks()` reads: two reads on the same `CentralProcessor`
instance taken inside the memoizer's expiration window resolve to the identical cached array. The
window is controlled by the `oshi.util.memoizer.expiration` configuration property, and its
measured default is **300 milliseconds** -- established by disassembling the shipped
`oshi-core-6.12.0.jar`'s own bytecode (`javap -c` on `oshi.util.Memoizer.queryExpirationConfig()`;
pinned in `OshiScriptSurfaceSeamTest#MEMOIZER_EXPIRATION_MS`'s javadoc). This is pinned as a falsifiable contract by
`OshiScriptSurfaceSeamTest#tickMemoizerWindowGovernsSampleFreshness` (plan 26.9-06): two tick reads
landing inside this window resolve to the same cached sample, so a delta computed across them
reads `0.0` for a reason that has nothing to do with the host being idle.

This matters most for the exact case this note is written for: a per-message Enlighten monitoring
channel on a busy interface will normally be invoked far more often than every 300ms, so the
recipe above, without sampling-interval guidance, would report a fabricated `0.0` on essentially
every message.

**CR-1 must size the channel's sampling interval above the memoizer floor.** Where a channel
cannot slow down and a genuine per-message read is required, store a timestamp alongside the
cached ticks and roll the cache forward ONLY when a measurement was actually taken, so the guard
can fire for a channel invoked faster than the floor instead of never firing at all. The snippet
between the markers below is extracted verbatim and executed by
`OshiScriptSurfaceSeamTest#samplingFloorRecipeGuardFiresOnceTheFloorElapses`: the markers must not
be removed, and exactly one fenced block belongs between them.

<!-- irt1801-sampling-floor-recipe:begin -->
```javascript
var now      = java.lang.System.currentTimeMillis();
var oldTicks = globalMap.get('oshiTicks');
var lastTime = globalMap.get('oshiTicksTime');
// SAMPLE_FLOOR_MS is the OSHI tick memoizer window (300ms;
// OshiScriptSurfaceSeamTest#MEMOIZER_EXPIRATION_MS) plus a 150ms margin covering scheduler
// jitter around that window.
var SAMPLE_FLOOR_MS = 450;
var load = -1;
if (!oldTicks || !lastTime) {
    // First invocation, or no usable prior sample yet: prime the cache and take no reading
    // this invocation.
    globalMap.put('oshiTicks', processor.getSystemCpuLoadTicks());
    globalMap.put('oshiTicksTime', now);
} else if ((now - lastTime) >= SAMPLE_FLOOR_MS) {
    // The floor having elapsed does not guarantee the next read is fresh: read once into a
    // new local and vet it against the cached prior array before trusting it.
    var fresh = processor.getSystemCpuLoadTicks();
    var advanced = false;
    for (var i = 0; i < fresh.length; i++) {
        if (fresh[i] !== oldTicks[i]) { advanced = true; break; }
    }
    if (advanced) {
        // A real measurement: compute against the vetted prior ticks, THEN roll the cache
        // forward with the sample already read -- never a second tick read. Rolling forward
        // is what ends this measurement window and starts the next one, and it happens only
        // when a measurement was actually taken.
        load = processor.getSystemCpuLoadBetweenTicks(oldTicks);
        globalMap.put('oshiTicks', fresh);
        globalMap.put('oshiTicksTime', now);
    }
    // else: the platform returned a sample identical to the cached prior array. Leave the
    // cache and the -1 sentinel alone so the next invocation retries, rendering N/A rather
    // than fabricating a zero.
}
// else: a too-soon invocation. The cache is left alone and load stays at the not-sampled
// sentinel.
```
<!-- irt1801-sampling-floor-recipe:end -->

Between measurement windows, `load` stays at the `-1` not-sampled sentinel by design: this is the
honest outcome this note prefers to a fabricated zero.
`OshiScriptSurfaceSeamTest#TICK_ADVANCE_MAX_ATTEMPTS`'s javadoc records the measurement that
actually matters here: on the host it was measured on, a single read taken after waiting past the
450ms floor was necessary but not always sufficient, still returning the byte-identical prior
array in roughly 1 in 5 to 1 in 10 reads, with failures not eliminated even at 1200ms. No floor
value removes this -- which is why the recipe above checks the sample itself, once the floor has
elapsed, rather than trusting the clock alone. The `SAMPLE_FLOOR_MS` margin above the measured
`oshi.util.memoizer.expiration` default covers scheduler jitter around that window; the staleness
check above is what covers the rest.

### Rendering: two different zeros

A legitimate `0.0` utilization reading on an idle host and the `-1` not-sampled sentinel must
render differently, because this is what the customer sees. Render a legitimate `0.0` as a zero
percentage. Render the `-1` sentinel as `N/A`, the same way an unavailable frequency renders below.
Conflating the two -- rendering both as a zero percentage -- is what makes the naive recipe
invisible in production: the template would report a plausible-looking `0%` on every message and
nothing about the display would look wrong. A stale post-floor read renders `N/A` through this
same sentinel path, the same as an unavailable frequency: the recipe leaves `load` at the sentinel
rather than fabricating a reading, and the value is retried on the next invocation instead of
being reported on this one.

`getSystemCpuLoad(long)` (the blocking overload) is acceptable **only** in a low-frequency
polling context where a one-time sleep of the given duration is tolerable. It must never be
presented as the recommended per-message form, and this note does not present it that way.

One qualification on the tick-delta recipe: if multiple channel threads share a single cached
prior-ticks cache entry, a concurrent read and write of that entry can interleave. The recipe
above is not presented as thread-safe without that qualification; a template that shares
`globalMap` state across concurrently-executing channel threads should account for it.

## Two corrections to the earlier D-08 phrasing

**The unavailable-frequency sentinel is `-1`, not `0`.** Both
`processor.getProcessorIdentifier().getVendorFreq()` and `processor.getMaxFreq()` measured `-1`
on linux/arm64 in this phase's characterization work, while measuring `3504000000` and
`2400000000` respectively on an Apple M2 Pro. A template that treats `0` as the unavailable case
will render "0 Hz" to the customer where it should render "N/A" -- and a CPU frequency being
legitimately unavailable is the **normal** case on Graviton, not an exceptional one. D-08's
original "0/N/A" phrasing was imprecise; this note corrects it: the sentinel is `-1`.

**The OSHI 3.9.1 defect on aarch64 was never fatal.** On linux/arm64, OSHI 3.9.1 logs two ERROR
lines back to back, `Couldn't find physical processor count. Assuming 1.` and
`Couldn't find physical package count. Assuming 1.`, and then returns values anyway. In this
phase's real linux/arm64 container run, the 3.9.1 leg logged both errors and still returned a
usable CPU load of `0.17333333333333334` and a memory total of `8218034176`. The Enlighten script
has not been broken on Graviton. What it has been doing is emitting a per-invocation ERROR log
flood and silently reporting a wrong physical processor count (`1`, guessed, instead of the true
value `10` this phase measured on real aarch64 hardware --
`regression-scripts/irt1801-evidence.log:6` (Leg A) and `:14` (Leg B)). This is not a crash, an
exception, or a broken script -- it is a log flood plus a wrong topology number, and that is what
the customer's expectation should be set to. It is what belongs in the IRT-1801 closing comment.

For completeness: the physical package count measured `1` on both legs on this measured host
(`regression-scripts/irt1801-evidence.log:7` (Leg A) and `:15` (Leg B)), so 3.9.1's guess for that
metric was correct here, and this phase did not observe the physical package count changing.

## Enlighten Call Migration Map

| 3.9.1 call (as used by Enlighten) | Status | 6.12.0 replacement | Notes |
|---|---|---|---|
| `new Packages.oshi.SystemInfo()` | unchanged | same | |
| `.getHardware()` | unchanged | same | |
| `.getProcessor()` | unchanged | same | |
| `.getMemory()` | unchanged | same | |
| `processor.getSystemCpuLoadTicks()` | unchanged | same | returns `long[8]`; use `getSystemCpuLoadBetweenTicks` rather than hand-indexing the eight slots -- their per-slot meaning (USER/NICE/SYSTEM/IDLE/IOWAIT/IRQ/SOFTIRQ/STEAL ordering) differs by platform |
| `processor.getLogicalProcessorCount()` | unchanged | same | |
| `memory.getTotal()` | unchanged | same | bytes |
| `memory.getAvailable()` | unchanged | same | bytes |
| `processor.getSystemCpuLoad()` | REMOVED | `getSystemCpuLoadBetweenTicks(oldTicks)` (non-blocking, preferred) or `getSystemCpuLoad(millis)` (blocks the calling thread for the supplied duration) | Utilization is a fraction in the closed interval `0.0` to `1.0`, never a load average. Exactly `0.0` is a legitimate reading on an idle host. First-invocation, no-usable-prior-sample result of the tick-delta recipe is the `-1` not-sampled sentinel, rendered as `N/A`, never `0.0`. See the sampling-interval guidance and the blocking-call trap above before choosing the blocking overload |
| `processor.getVendorFreq()` | MOVED | `processor.getProcessorIdentifier().getVendorFreq()` | Hz; `-1` when unavailable, which is the normal case on Graviton, never `0` |
| `memory.getSwapUsed()` | MOVED | `memory.getVirtualMemory().getSwapUsed()` | bytes |
| `memory.getSwapTotal()` | MOVED | `memory.getVirtualMemory().getSwapTotal()` | bytes |
| (not used by Enlighten previously) | NEW | `processor.getPhysicalPackageCount()`, `processor.getPhysicalProcessorCount()`, `processor.getMaxFreq()`, `processor.getProcessorIdentifier().getName()`, `processor.getProcessorIdentifier().getMicroarchitecture()` | `getPhysicalProcessorCount()` is the metric this phase measured changing on real aarch64: 6.12.0 reports the true processor count (`10`) where 3.9.1 guessed `1`. `getPhysicalPackageCount()` is newly available on the 6.x surface but was not observed to change on the measured host -- it read `1` under both jar sets. `getMaxFreq()` also returns `-1` on Graviton. `getMicroarchitecture()` returned `"unknown"` on both ARM hosts tested during this phase |

## The auditable coverage story

- **Every unchanged call on the Enlighten surface executes green through `Packages.oshi.*` on
  the new jars** -- `server/test/com/mirth/connect/seams/OshiScriptSurfaceSeamTest.java#unchangedEnlightenCallSurface`
  (plan 26.9-02), asserted by range predicate rather than absolute value since OSHI output is
  machine-dependent.
- **The new 6.x-only capabilities that motivate this upgrade resolve and return sane values** --
  `OshiScriptSurfaceSeamTest#newSixDotXCapabilities` (plan 26.9-02).
- **All three D-07 breaks are pinned as falsifiable expected failures, each paired with its
  working replacement** -- `OshiScriptSurfaceSeamTest#removedNoArgSystemCpuLoadThrowsEvaluatorException`,
  `OshiScriptSurfaceSeamTest#movedMemberCallsThrowEcmaError`, and
  `OshiScriptSurfaceSeamTest#movedCallReplacementsReturnSaneValues` (plan 26.9-02), alongside
  `OshiScriptSurfaceSeamTest#tickMemoizerWindowGovernsSampleFreshness` (plan 26.9-06). These are
  what make the migration map above a pinned, falsifiable contract rather than an unverified
  assertion: if OSHI ever restores `getVendorFreq()` on `CentralProcessor`,
  `movedMemberCallsThrowEcmaError` goes red and the map has to be corrected instead of quietly
  rotting. The memoizer test establishes that `movedCallReplacementsReturnSaneValues`'s tick-delta
  read is taken across an interval sized above the OSHI tick memoizer window and asserts the
  sample has genuinely advanced, so the tick-delta recommendation in this note is backed by an
  assertion that can fail, rather than one that passes by construction.
- **A channel script reaches real CPU topology end to end through the production Rhino seam** --
  `OshiScriptSurfaceSeamTest#tracerEndToEndPhysicalTopology` (plan 26.9-01), proving
  `Packages.oshi.SystemInfo` resolves through the real `MirthContextFactory` at the shipped ES6
  language level, not a synthetic harness.
- **The Graviton fix is proven on real linux/arm64 hardware, not inferred from the JUnit suite
  alone, under a gate that the 3.9.1 defect's own guessed value cannot satisfy** --
  `regression-scripts/test-irt1801-oshi-arm64.sh` (plan 26.9-03, strengthened by plan 26.9-05): a
  falsifiable break-then-fix leg where Leg A (OSHI 3.9.1) must reproduce the
  `LinuxCentralProcessor` error class including the physical-processor-count diagnostic, Leg B
  (OSHI 6.12.0) must emit zero `LinuxCentralProcessor` ERROR lines of any kind, and Leg B's parsed
  physical processor count must be strictly greater than Leg A's measured value. A docker-free,
  network-free `--self-test-classifier` mode replays the committed evidence log through the
  classifier's seven labelled verdict branches using seven named scenarios (`S1`-`S7`): six
  branches are each reached by a named scenario, including the two false-pass scenarios a prior
  verification pass found (`S3`, `S4`) and the three that close a later gap in that same coverage
  claim (`S5`, `S6`, `S7`); the seventh branch (`catch_all`, the ladder's fail-closed default) is
  declared exempt. This coverage is enforced by the self-test itself: it recovers the classifier's
  declared branch-label set from the script's own source and fails if any non-exempt branch was
  never reached, and the recovery is bounded so a run that recovers fewer labels than the ladder
  has assignments fails closed rather than reporting coverage it could not see.

## Verification

- Local JUnit suite: `cd server && ant -f mirth-build.xml test-run-and-aggregate` reports
  `TEST-com.mirth.connect.seams.OshiScriptSurfaceSeamTest.xml` green on JDK 17, with the same
  suite required to stay green on the JDK 17 and JDK 21 CI legs (plan 26.9-04).
- Real linux/arm64 container leg (plan 26.9-03, strengthened by plan 26.9-05,
  `regression-scripts/test-irt1801-oshi-arm64.sh`, recorded verdict OK, exit 0): Leg A (OSHI
  3.9.1/JNA 4.5.2) confirmed the container reported `aarch64`, then logged both
  `Couldn't find physical processor count. Assuming 1.` and
  `Couldn't find physical package count. Assuming 1.` at ERROR, and returned
  `physicalProcessorCount=1` (the wrong, guessed value; `regression-scripts/irt1801-evidence.log:6`).
  Leg B (OSHI 6.12.0/JNA 5.18.1) emitted zero `LinuxCentralProcessor` ERROR lines and reported
  `physicalProcessorCount=10`, the true core count (`regression-scripts/irt1801-evidence.log:14`)
  -- the IRT-1801 fix, proven on real hardware rather than a CI runner (every CI runner in this
  project is x86_64). The physical package count measured `1` on both legs
  (`regression-scripts/irt1801-evidence.log:7,15`), so this phase did not observe that metric
  changing.
- A future reader does not need docker to re-check the verdict logic itself: `bash
  regression-scripts/test-irt1801-oshi-arm64.sh --self-test-classifier` replays the committed
  `regression-scripts/irt1801-evidence.log` through `classify_verdict()` with no container and no
  network, and exits 0 only if every one of `S1` through `S7` classifies as documented AND every
  labelled verdict branch outside the declared `catch_all` exemption was reached by a named
  scenario -- a coverage claim the self-test enforces against its own source, not one asserted in
  this document.

On why 6.x fixes ARM topology detection: the behavioral outcome above is verified by direct
measurement. The internal mechanism inside OSHI's `LinuxCentralProcessor` implementation that
produces the different result is inferred, not read from OSHI source, and is not asserted here as
fact.

## Bottom line

The OSHI 3.9.1-to-6.12.0 swap fixes real ARM64/Graviton CPU topology detection (confirmed on real
linux/arm64 hardware: `physicalProcessorCount` goes from a guessed `1` to a true `10`;
`regression-scripts/irt1801-evidence.log:6,14`) and closes a per-invocation ERROR log flood,
without ever having actually broken the Enlighten script -- the 3.9.1 defect logged noise and
returned a wrong physical processor count, it did not throw. The physical package count measured
`1` on both legs and was not part of the defect. Three calls on the
Enlighten surface moved or were removed (`getSystemCpuLoad()`, `getVendorFreq()`,
`getSwapUsed()`/`getSwapTotal()`), and every replacement is proven working through the same test
suite that pins the old calls as falsifiable expected failures. The one trap in this migration is
that the obvious fix for the removed `getSystemCpuLoad()` call -- passing it a millisecond
argument -- silently turns a per-message channel script into a one-second-per-message bottleneck;
the non-blocking `getSystemCpuLoadBetweenTicks` form is the correct replacement for CR-1's
template rewrite.
