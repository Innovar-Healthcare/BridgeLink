#!/bin/bash
# smoke-tests/check-jar-java17.sh
# Phase 23 / D-17 / D-18: MR-aware Java-17 class-file loadability scan.
#
# Asserts every BASE-PATH class file in a jar is loadable by a Java 17 JVM (major <= 61),
# while REPORTING (never failing on) META-INF/versions/N multi-release tiers. A naive "no
# class above major 61" scan would FAIL any Multi-Release BouncyCastle bcprov jar BridgeLink
# has shipped and run on Java 17 (both the pre-Phase-23 and the current release) --
# because such jars carry base-irrelevant entries at higher class majors under
# META-INF/versions/N (23-RESEARCH.md Pitfall 3). A JVM only ever reads a versions/N tier
# where N <= its own feature version, so those entries are correctly invisible to Java 17 and
# must not be scored against the floor.
#
# Usage: smoke-tests/check-jar-java17.sh <jar> [<jar>...]
# Exit codes:
#   0 - PASS: every base-path class in every jar is <= MAX_CLASS_MAJOR, and every jar actually
#       contributed at least one base-path class entry (an empty scan can never PASS)
#   1 - FAIL: at least one base-path class exceeds MAX_CLASS_MAJOR (offending entries printed).
#       A real FAIL takes precedence over an INCONCLUSIVE jar in the same run: the violation is
#       hard evidence and must not be downgraded to "could not tell" (the per-jar INCONCLUSIVE
#       lines are still printed, and a NOTE names how many jars went unscanned).
#   2 - INCONCLUSIVE: no jars given, python3 unavailable, MAX_CLASS_MAJOR not a positive integer,
#       or a jar is missing/unreadable/not a zip/carries no base-path class entries at all --
#       i.e. the gate could not actually evaluate the jar. Never reported as FAIL.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# MAX_CLASS_MAJOR=61 is Java 17 (feature version = major - 44). Overridable so Phases 24-26
# can retarget the platform floor without editing this script (D-17/D-18).
MAX_CLASS_MAJOR="${MAX_CLASS_MAJOR:-61}"
OUT_DIR="${SCRIPT_DIR}/out"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${YELLOW}INFO${NC}: $1"; }
ok()    { echo -e "${GREEN}OK${NC}: $1"; }
err()   { echo -e "${RED}ERROR${NC}: $1" >&2; }

mkdir -p "${OUT_DIR}"
REPORT_LOG="${OUT_DIR}/check-jar-java17.log"

# ---------------------------------------------------------------------------
# Preflight
# ---------------------------------------------------------------------------
if [[ $# -eq 0 ]]; then
    err "No jar arguments given. Usage: smoke-tests/check-jar-java17.sh <jar> [<jar>...]"
    exit 2
fi

if ! command -v python3 &>/dev/null; then
    err "python3 not found on PATH -- required for MR-aware class-file scanning."
    exit 2
fi

for jar in "$@"; do
    if [[ ! -f "${jar}" ]]; then
        err "Jar not found or unreadable: ${jar}"
        exit 2
    fi
done

# ---------------------------------------------------------------------------
# Scan: verified logic from 23-RESEARCH.md sec R2.3 (the exact code that produced
# sec R1.1's results this session) -- do not re-derive.
# ---------------------------------------------------------------------------
# Validate the override BEFORE the arithmetic below: a non-numeric value would otherwise blow up
# in $((...)) here and in int() inside the scan, and an uncaught Python exception exits 1 --
# which the verdict block would mistranslate into a fabricated class-version violation.
if ! [[ "${MAX_CLASS_MAJOR}" =~ ^[0-9]+$ ]]; then
    err "MAX_CLASS_MAJOR must be a positive integer (got: ${MAX_CLASS_MAJOR})"
    exit 2
fi

info "Scanning $# jar(s), MAX_CLASS_MAJOR=${MAX_CLASS_MAJOR} (Java $((MAX_CLASS_MAJOR - 44)))..."

set +e
SCAN_OUTPUT="$(MAX_CLASS_MAJOR="${MAX_CLASS_MAJOR}" python3 - "$@" <<'PYEOF'
import sys, zipfile, struct, collections, re, os
try:
    MAXOK = int(os.environ.get('MAX_CLASS_MAJOR', '61'))
except ValueError:
    print("INCONCLUSIVE: MAX_CLASS_MAJOR is not an integer")
    sys.exit(2)
VER = re.compile(r'^META-INF/versions/(\d+)/')
any_fail = False
any_inconclusive = 0
for p in sys.argv[1:]:
    # A truncated / non-zip / unreadable jar must be INCONCLUSIVE, never a class-version FAIL:
    # an uncaught exception here exits 1, which the caller would print as "base-path class
    # exceeds major N" for a jar that was never scanned at all.
    try:
        with zipfile.ZipFile(p) as z:
            mr = False
            try:
                mf = z.read('META-INF/MANIFEST.MF').decode('utf8', 'replace')
                mr = bool(re.search(r'(?im)^Multi-Release:\s*true', mf))
            except KeyError:
                pass
            base = collections.Counter()
            tiers = collections.defaultdict(collections.Counter)
            bad = []
            for n in z.namelist():
                if not n.endswith('.class'):
                    continue
                m = VER.match(n)
                with z.open(n) as f:
                    h = f.read(8)
                if len(h) < 8 or h[:4] != b'\xca\xfe\xba\xbe':
                    continue
                major = struct.unpack('>HH', h[4:8])[1]
                if m:
                    tiers[int(m.group(1))][major] += 1      # REPORT only, never fail (D-17a)
                else:
                    base[major] += 1
                    if major > MAXOK:
                        bad.append((n, major))
    except (zipfile.BadZipFile, OSError) as e:
        print(f"== {p}  INCONCLUSIVE: unreadable / not a zip: {e}")
        any_inconclusive += 1
        continue
    print(f"== {p}  Multi-Release: {mr}")
    print(f"   base-path majors: {dict(sorted(base.items()))} (total {sum(base.values())})")
    for t in sorted(tiers):
        reads = "read" if t <= (MAXOK - 44) else "ignored"
        print(f"   META-INF/versions/{t}: majors {dict(sorted(tiers[t].items()))} "
              f"({sum(tiers[t].values())} entries)  [{reads} by a Java {MAXOK-44} JVM]")
    # Fail closed on an empty base path: a sources/javadoc jar, a pom-only artifact, an
    # all-META-INF/versions jar, a stub, or a wrong path would otherwise print PASS because
    # "no base-path class exceeds the floor" is vacuously true of the empty set (D-17/D-18).
    if sum(base.values()) == 0:
        print(f"   VERDICT: INCONCLUSIVE  no base-path class entries scanned in {p}")
        any_inconclusive += 1
        continue
    print(f"   VERDICT: {'FAIL' if bad else 'PASS'}  base-path over major {MAXOK}: {len(bad)}")
    for n, mj in bad[:10]:
        print(f"     {n} -> {mj}")
    if bad:
        any_fail = True
if any_inconclusive:
    print(f"NOTE: {any_inconclusive} jar(s) could not be evaluated (see INCONCLUSIVE lines above)")
# A genuine class-version violation outranks an unscannable jar in the same run: exit 1 keeps
# the hard evidence, and the NOTE above preserves the "not everything was scanned" signal.
if any_fail:
    sys.exit(1)
sys.exit(2 if any_inconclusive else 0)
PYEOF
)"
SCAN_EXIT=$?
set -e

echo "${SCAN_OUTPUT}" | tee "${REPORT_LOG}"

# ---------------------------------------------------------------------------
# Verdict
# ---------------------------------------------------------------------------
VERDICT_EXIT=2
if [[ ${SCAN_EXIT} -eq 0 ]]; then
    ok "all base-path classes in all $# jar(s) are <= major ${MAX_CLASS_MAJOR} (every jar contributed at least one base-path class)"
    VERDICT_EXIT=0
elif [[ ${SCAN_EXIT} -eq 1 ]]; then
    err "at least one base-path class exceeds major ${MAX_CLASS_MAJOR} -- see offending entries above"
    VERDICT_EXIT=1
else
    err "at least one jar could not be evaluated (unreadable / not a zip / no base-path class entries) -- INCONCLUSIVE, see the scan output above"
    VERDICT_EXIT=2
fi

exit "${VERDICT_EXIT}"
