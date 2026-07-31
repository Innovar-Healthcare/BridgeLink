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
# BREAK_FIXTURE_JAR is overridable. History (D-25): the xstream-1.4.10.jar downgrade does NOT
# reproduce a functional break for this codebase -- verified empirically in plan 18-08,
# reconfirmed in 18-09 (XStream's reflection-based serializer is highly backward-compatible
# for plain POJO graphs, and downgrading does not reproduce the forward-upgrade regression
# class), and settled a third time in Phase 23 plan 02's rung-0 run (BREAK_FIXTURE_JAR
# explicitly pointed at this same 1.4.10 jar against legacy-migration-3-4-test.xml --
# recorded exit 1, SELF-TEST FAILED, per smoke-tests/README.md). The plan 18-10 catch
# (BREAK_FIXTURE_JAR=smoke-tests/fixtures/xstream-1.4.21.jar -- the literal v26.6.0 rollback
# version) is now HISTORICAL ONLY: Phase 23 lands xstream 1.4.21 as the shipped version, so
# that fixture can no longer break anything (it IS what ships) -- see fixtures/xstream-1.4.21.jar's
# README annotation, disarmed post-Phase-23. Per D-25 the ARMED DEFAULT is now a deliberately
# mangled copy of the shipped 1.4.21 jar (com/thoughtworks/xstream/io/xml/DomReader.class
# removed, so MirthDomReader's own superclass fails to link -- guaranteed
# SMOKE-FAILURE-CLASS: import at the exact same seam), because an armed configuration that
# lives only in a README sentence is a canary that will be run wrong.
FIXTURE_JAR="${BREAK_FIXTURE_JAR:-${SCRIPT_DIR}/fixtures/xstream-1.4.21-mangled.jar}"
# BREAK_LIB_GLOB is overridable (D-26): generalizes the swap-aside glob beyond xstream so
# Phases 24/25/26 can reuse this script for their own dependency's break-proof canary without
# a script rewrite -- both knobs default to today's xstream-only values so behavior is
# unchanged unless a caller opts in.
BREAK_LIB_GLOB="${BREAK_LIB_GLOB:-xstream-*.jar}"
# BREAK_EXPECT_FAILURE_CLASS is overridable (D-26): generalizes the verdict-classification
# marker beyond the XStream import seam. Must be one of the four legal
# SMOKE-FAILURE-CLASS values (import/assert/log/duration) documented in this file's
# "Failure-class markers" section -- a new knob must not invite a fifth.
BREAK_EXPECT_FAILURE_CLASS="${BREAK_EXPECT_FAILURE_CLASS:-import}"
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

# Enumerate EVERY jar matching BREAK_LIB_GLOB RECURSIVELY under server-lib. MirthLauncher
# walks server-lib recursively (MirthLauncher.java directory walk) and a nested copy (e.g.
# donkey ships its own xstream-*.jar under server-lib/donkey/) would mask the break if left
# in place — never a top-level-only glob (Pitfall 10a), and never hardcode a version number
# (Phase 23 changes it) or a library family (D-26 -- BREAK_LIB_GLOB generalizes this).
ORIGINAL_JARS=()
while IFS= read -r line; do
    [[ -n "${line}" ]] && ORIGINAL_JARS+=("${line}")
done < <(find "${SERVER_LIB}" -name "${BREAK_LIB_GLOB}" | sort)

if [[ ${#ORIGINAL_JARS[@]} -eq 0 ]]; then
    err "No ${BREAK_LIB_GLOB} found recursively under ${SERVER_LIB} — nothing to break."
    exit 2
fi

info "Found ${#ORIGINAL_JARS[@]} jar(s) matching ${BREAK_LIB_GLOB} to swap aside:"
for jar in "${ORIGINAL_JARS[@]}"; do
    echo "    ${jar}"
done

# Top-level (first, sorted) match is where the fixture jar gets copied in — this is the
# directory MirthLauncher's own top-level server-lib walk finds first.
TOP_LEVEL_JAR="${ORIGINAL_JARS[0]}"
TOP_LEVEL_DIR="$(dirname "${TOP_LEVEL_JAR}")"

# The fixture must NOT share a basename with any jar it is swapped in for. README documents
# fixtures/xstream-1.4.21.jar as a selectable BREAK_FIXTURE_JAR value, and that basename is now
# identical to the jar Phase 23 ships. Under such a collision the restore-time `rm -f` targets the
# real shipped jar's path, and the post-restore leftover check matches the correctly restored
# ORIGINAL jar — turning a good run into a false "tree may be left in a broken state" that also
# discards the genuine verdict. Reject the collision up front, and from here on refer to the
# fixture's installed location by the exact recorded path, never by re-deriving it from a name.
FIXTURE_BASENAME="$(basename "${FIXTURE_JAR}")"
for jar in "${ORIGINAL_JARS[@]}"; do
    if [[ "$(basename "${jar}")" == "${FIXTURE_BASENAME}" ]]; then
        err "Fixture basename ${FIXTURE_BASENAME} collides with a shipped jar (${jar}); copy the fixture to a distinct name (e.g. ${FIXTURE_BASENAME%.jar}-broken.jar) before use."
        exit 2
    fi
done
FIXTURE_INSTALLED_PATH="${TOP_LEVEL_DIR}/${FIXTURE_BASENAME}"

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
    rm -f "${FIXTURE_INSTALLED_PATH}"
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
cp "${FIXTURE_JAR}" "${FIXTURE_INSTALLED_PATH}"
info "Swapped in ${FIXTURE_JAR} at ${FIXTURE_INSTALLED_PATH}"

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
    echo "SELF-TEST FAILED: harness PASSED with broken ${BREAK_LIB_GLOB} — net has a hole"
    VERDICT_EXIT=1
elif grep -q "SMOKE-FAILURE-CLASS: ${BREAK_EXPECT_FAILURE_CLASS}" "${EVIDENCE_LOG}"; then
    ok "harness caught the broken dependency (behavioral catch at the ${BREAK_EXPECT_FAILURE_CLASS} seam)"
    echo "--- ${BREAK_EXPECT_FAILURE_CLASS}-stage failure excerpt ---"
    grep -A 5 -B 2 "SMOKE-FAILURE-CLASS: ${BREAK_EXPECT_FAILURE_CLASS}" "${EVIDENCE_LOG}" | head -60
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

# Check the exact path the fixture was installed at — a basename search would also match the
# correctly restored original jar whenever the two share a name (rejected in preflight above).
if [[ -e "${FIXTURE_INSTALLED_PATH}" ]]; then
    err "Fixture jar still present after restore: ${FIXTURE_INSTALLED_PATH}"
    STILL_MISSING=1
fi

if [[ ${STILL_MISSING} -eq 0 ]]; then
    ok "Restoration verified: all $((${#ORIGINAL_JARS[@]})) original xstream jar(s) back in place, fixture jar removed."
else
    err "Restoration verification FAILED — tree may be left in a broken state."
    exit 2
fi

exit "${VERDICT_EXIT}"
