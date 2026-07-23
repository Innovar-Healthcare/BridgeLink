#!/bin/bash
# smoke-tests/break-dependency.sh
# NET-05 / SC-4 self-verifying broken-dependency proof (D-13/D-14).
#
# Demonstrates that the harness catches the EXACT seam class that caused the v26.6.0
# xstream 1.4.21 rollback (management decision 2026-07-13, commit 6a483ab9d): deliberately
# downgrade the shipped xstream jar to an incompatible old version, run the harness
# EXPECTING failure, and verify the failure is a genuine BEHAVIORAL catch at the
# channel-import/InvalidChannel seam — not an unrelated infrastructure failure that would
# make the "proof" meaningless (Pitfall 10a/10b).
#
# Usage: smoke-tests/break-dependency.sh
# Exit codes (Pitfall 10b verdict classification):
#   0 - OK: harness caught the broken dependency (behavioral catch at the import seam)
#   1 - SELF-TEST FAILED: harness passed with a broken xstream jar — net has a hole
#   2 - INCONCLUSIVE: harness failed, but not at the dependency seam (infra failure)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVER_LIB="${REPO_ROOT}/server/setup/server-lib"
# BREAK_FIXTURE_JAR is overridable (plan 18-09): the default xstream-1.4.10.jar downgrade
# does NOT reproduce a functional break for this codebase (verified empirically in plan
# 18-08 and reconfirmed in 18-09 -- XStream's reflection-based serializer is highly
# backward-compatible for plain POJO graphs, and downgrading does not reproduce the
# forward-upgrade regression class). xstream-1.4.21.jar -- the literal v26.6.0 rollback
# version -- is the fixture that actually catches the DomReader child-caching regression
# against the legacy-migration-test.xml fixture (18-09 NET-05/SC-4 break-proof).
FIXTURE_JAR="${BREAK_FIXTURE_JAR:-${SCRIPT_DIR}/fixtures/xstream-1.4.10.jar}"
ASIDE_DIR="$(mktemp -d)/xstream-aside"
OUT_DIR="${SCRIPT_DIR}/out"
EVIDENCE_LOG="${OUT_DIR}/break-proof-harness-run.log"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${YELLOW}INFO${NC}: $1"; }
ok()    { echo -e "${GREEN}OK${NC}: $1"; }
err()   { echo -e "${RED}ERROR${NC}: $1" >&2; }

mkdir -p "${OUT_DIR}"

# ---------------------------------------------------------------------------
# Preflight
# ---------------------------------------------------------------------------
if [[ ! -f "${REPO_ROOT}/server/setup/server-lib/mirth-server.jar" ]]; then
    err "server/setup/server-lib/mirth-server.jar not found. Build the distribution first:
  cd server && ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true"
    exit 2
fi

if [[ ! -f "${FIXTURE_JAR}" ]]; then
    err "Break-dependency fixture not found: ${FIXTURE_JAR}"
    exit 2
fi

# Enumerate EVERY xstream jar RECURSIVELY under server-lib. MirthLauncher walks server-lib
# recursively (MirthLauncher.java directory walk) and a nested copy (e.g. donkey ships its own
# xstream-*.jar under server-lib/donkey/) would mask the break if left in place — never a
# top-level-only glob (Pitfall 10a), and never hardcode a version number (Phase 23 changes it).
ORIGINAL_JARS=()
while IFS= read -r line; do
    [[ -n "${line}" ]] && ORIGINAL_JARS+=("${line}")
done < <(find "${SERVER_LIB}" -name 'xstream-*.jar' | sort)

if [[ ${#ORIGINAL_JARS[@]} -eq 0 ]]; then
    err "No xstream-*.jar found recursively under ${SERVER_LIB} — nothing to break."
    exit 2
fi

info "Found ${#ORIGINAL_JARS[@]} xstream jar(s) to swap aside:"
for jar in "${ORIGINAL_JARS[@]}"; do
    echo "    ${jar}"
done

# Top-level (first, sorted) match is where the fixture jar gets copied in — this is the
# directory MirthLauncher's own top-level server-lib walk finds first.
TOP_LEVEL_JAR="${ORIGINAL_JARS[0]}"
TOP_LEVEL_DIR="$(dirname "${TOP_LEVEL_JAR}")"

# ---------------------------------------------------------------------------
# Swap: move every original jar aside (preserving its original path for restore), then copy
# the fixture into the top-level match's directory. ANY exit path restores everything (trap).
# ---------------------------------------------------------------------------
mkdir -p "${ASIDE_DIR}"

RESTORED=0
restore_jars() {
    if [[ ${RESTORED} -eq 1 ]]; then
        return 0
    fi
    RESTORED=1
    info "Restoring original xstream jar(s)..."
    rm -f "${TOP_LEVEL_DIR}/$(basename "${FIXTURE_JAR}")"
    local jar rel dest
    for jar in "${ORIGINAL_JARS[@]}"; do
        rel="${jar#"${SERVER_LIB}"/}"
        dest="${ASIDE_DIR}/${rel}"
        if [[ -f "${dest}" ]]; then
            mkdir -p "$(dirname "${jar}")"
            mv "${dest}" "${jar}"
        else
            err "Expected staged copy missing for restore: ${dest} (original: ${jar})"
        fi
    done
    rm -rf "$(dirname "${ASIDE_DIR}")"
}
trap restore_jars EXIT

for jar in "${ORIGINAL_JARS[@]}"; do
    rel="${jar#"${SERVER_LIB}"/}"
    dest="${ASIDE_DIR}/${rel}"
    mkdir -p "$(dirname "${dest}")"
    mv "${jar}" "${dest}"
done
cp "${FIXTURE_JAR}" "${TOP_LEVEL_DIR}/$(basename "${FIXTURE_JAR}")"
info "Swapped in ${FIXTURE_JAR} at ${TOP_LEVEL_DIR}/$(basename "${FIXTURE_JAR}")"

# ---------------------------------------------------------------------------
# Run: expect the harness to FAIL. Capture full output to a log file for the
# verdict classification and for SUMMARY evidence (D-13: executable property, not screenshot).
# ---------------------------------------------------------------------------
info "Running smoke-tests/run-smoke-test.sh with the broken xstream jar (expecting failure)..."
HARNESS_EXIT=0
bash "${SCRIPT_DIR}/run-smoke-test.sh" > "${EVIDENCE_LOG}" 2>&1 || HARNESS_EXIT=$?

echo "--- harness output (tail 60 lines) ---"
tail -n 60 "${EVIDENCE_LOG}" || true
echo "---------------------------------------"
info "Harness exit code: ${HARNESS_EXIT}"

# ---------------------------------------------------------------------------
# Verdict classification (Pitfall 10b)
# ---------------------------------------------------------------------------
VERDICT_EXIT=2

if [[ ${HARNESS_EXIT} -eq 0 ]]; then
    echo "SELF-TEST FAILED: harness PASSED with broken xstream — net has a hole"
    VERDICT_EXIT=1
elif grep -q 'SMOKE-FAILURE-CLASS: import' "${EVIDENCE_LOG}"; then
    ok "harness caught the broken dependency (behavioral catch at the XStream import seam)"
    echo "--- import-stage failure excerpt ---"
    grep -A 5 -B 2 'SMOKE-FAILURE-CLASS: import' "${EVIDENCE_LOG}" | head -60
    echo "-------------------------------------"
    VERDICT_EXIT=0
else
    FAILURE_CLASS="$(grep -o 'SMOKE-FAILURE-CLASS: [a-z]*' "${EVIDENCE_LOG}" | head -1 || true)"
    if [[ -z "${FAILURE_CLASS}" ]]; then
        FAILURE_CLASS="(no SMOKE-FAILURE-CLASS marker found — boot/infrastructure failure)"
    fi
    echo "INCONCLUSIVE: harness failed outside the dependency seam (${FAILURE_CLASS})"
    VERDICT_EXIT=2
fi

# ---------------------------------------------------------------------------
# Post-restore sanity (runs via the EXIT trap above BEFORE this point in shell exit
# processing is irrelevant here — we invoke it explicitly so verification happens before
# this script's own exit code is decided, and print confirmation either way).
# ---------------------------------------------------------------------------
restore_jars

STILL_MISSING=0
for jar in "${ORIGINAL_JARS[@]}"; do
    if [[ ! -f "${jar}" ]]; then
        err "Original jar not restored: ${jar}"
        STILL_MISSING=1
    fi
done

FIXTURE_LEFTOVER="$(find "${SERVER_LIB}" -name "$(basename "${FIXTURE_JAR}")" 2>/dev/null || true)"
if [[ -n "${FIXTURE_LEFTOVER}" ]]; then
    err "Fixture jar still present after restore: ${FIXTURE_LEFTOVER}"
    STILL_MISSING=1
fi

if [[ ${STILL_MISSING} -eq 0 ]]; then
    ok "Restoration verified: all $((${#ORIGINAL_JARS[@]})) original xstream jar(s) back in place, fixture jar removed."
else
    err "Restoration verification FAILED — tree may be left in a broken state."
    exit 2
fi

exit "${VERDICT_EXIT}"
