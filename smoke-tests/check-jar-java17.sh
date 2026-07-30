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
#   0 - PASS: every base-path class in every jar is <= MAX_CLASS_MAJOR
#   1 - FAIL: at least one base-path class exceeds MAX_CLASS_MAJOR (offending entries printed)
#   2 - INCONCLUSIVE: a jar argument is missing/unreadable, python3 is unavailable, or no jars given
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
info "Scanning $# jar(s), MAX_CLASS_MAJOR=${MAX_CLASS_MAJOR} (Java $((MAX_CLASS_MAJOR - 44)))..."

set +e
SCAN_OUTPUT="$(MAX_CLASS_MAJOR="${MAX_CLASS_MAJOR}" python3 - "$@" <<'PYEOF'
import sys, zipfile, struct, collections, re, os
MAXOK = int(os.environ.get('MAX_CLASS_MAJOR', '61'))
VER = re.compile(r'^META-INF/versions/(\d+)/')
rc = 0
for p in sys.argv[1:]:
    z = zipfile.ZipFile(p)
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
    print(f"== {p}  Multi-Release: {mr}")
    print(f"   base-path majors: {dict(sorted(base.items()))} (total {sum(base.values())})")
    for t in sorted(tiers):
        reads = "read" if t <= (MAXOK - 44) else "ignored"
        print(f"   META-INF/versions/{t}: majors {dict(sorted(tiers[t].items()))} "
              f"({sum(tiers[t].values())} entries)  [{reads} by a Java {MAXOK-44} JVM]")
    print(f"   VERDICT: {'FAIL' if bad else 'PASS'}  base-path over major {MAXOK}: {len(bad)}")
    for n, mj in bad[:10]:
        print(f"     {n} -> {mj}")
    if bad:
        rc = 1
sys.exit(rc)
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
    ok "all base-path classes in all $# jar(s) are <= major ${MAX_CLASS_MAJOR}"
    VERDICT_EXIT=0
elif [[ ${SCAN_EXIT} -eq 1 ]]; then
    err "at least one base-path class exceeds major ${MAX_CLASS_MAJOR} -- see offending entries above"
    VERDICT_EXIT=1
else
    err "scan could not complete (python3 exit ${SCAN_EXIT}) -- INCONCLUSIVE"
    VERDICT_EXIT=2
fi

exit "${VERDICT_EXIT}"
