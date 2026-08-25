#!/bin/bash
# smoke-tests/break-postgres-driver.sh
# Phase 26.8 (D-08 Tier 2) self-verifying postgres-connection break-proof.
#
# Demonstrates that the smoke-postgres leg (smoke-tests/run-smoke-test.sh --db postgres,
# landed in 26.8-02) fails loudly, not silently, when the driver cannot authenticate against
# the database. Unlike smoke-tests/break-dependency.sh (which swaps in a mangled jar and
# needs SMOKE_SKIP_DIST_FRESHNESS=1 to bypass the dist-freshness gate), this driver injects a
# deliberately wrong database.password via the dormant SMOKE_PG_BREAK knob added to
# run-smoke-test.sh's patch_properties() in this same plan -- no dist mangling, no freshness
# suppression, the assembled distribution is never touched.
#
# The healthy postgres:16-alpine container still boots correctly; only the credentials the
# harness patches into mirth.properties are wrong, so the injected fault lands exactly at the
# driver/connection seam (a deterministic SCRAM / password-authentication-failed signature),
# not at boot or container setup.
#
# Note on the SMOKE-FAILURE-CLASS marker: break-dependency.sh's verdict classifier keys on
# grep -qF "SMOKE-FAILURE-CLASS: ${BREAK_EXPECT_FAILURE_CLASS}", a marker only ever emitted by
# run-smoke-test.sh's post-boot scan stages (scan_mirth_log/scan_postgres_driver_errors), which
# run AFTER import_deploy. Empirically confirmed this plan (live run, 2026-08-24): a bad
# database.password makes health_check() fail and the harness exit 1 BEFORE those scan stages
# ever run, so no SMOKE-FAILURE-CLASS marker is emitted for this fault. This classifier
# therefore keys directly on the pgjdbc/postgres fault-signature literals below, matched
# against both the captured harness output (which includes health_check()'s own
# dump_log_tail() excerpt) and mirth.log, rather than on that marker.
#
# Usage: smoke-tests/break-postgres-driver.sh
# Exit codes (mirrors break-dependency.sh's verdict classification):
#   0 - OK: the harness caught the bad-credentials fault (behavioral catch at the
#           driver/connection seam)
#   1 - SELF-TEST FAILED: the harness passed with bad credentials -- net has a hole
#   2 - INCONCLUSIVE: the harness failed, but not at the driver/connection seam
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="${SCRIPT_DIR}/out"
EVIDENCE_LOG="${OUT_DIR}/break-postgres-driver-run.log"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${YELLOW}INFO${NC}: $1"; }
ok()    { echo -e "${GREEN}OK${NC}: $1"; }
err()   { echo -e "${RED}ERROR${NC}: $1" >&2; }

mkdir -p "${OUT_DIR}"

# Verified pgjdbc / postgres server fault signatures (see 26.8-RESEARCH.md "Verified pgjdbc
# error signatures"). A bad-password fault is expected to surface as either the
# server-relayed "password authentication failed" FATAL, or (less commonly, if the server
# demands SCRAM before the bad password is even evaluated) a SCRAM-related message. The
# unreachable-DB / connect-timeout signatures are listed for documentation parity with
# run-smoke-test.sh's scan_postgres_driver_errors() but are not expected to match this
# specific fault (they belong to a different injected-fault shape, not exercised here).
PG_FAULT_SIGNATURES=(
    "SCRAM"
    "password authentication failed"
    "No suitable driver"
    "ClassNotFoundException: org.postgresql.Driver"
    "The connection attempt failed."
)

info "Running smoke-tests/run-smoke-test.sh --db postgres with SMOKE_PG_BREAK=1 (expecting failure)..."
HARNESS_EXIT=0
SMOKE_PG_BREAK=1 bash "${SCRIPT_DIR}/run-smoke-test.sh" --db postgres > "${EVIDENCE_LOG}" 2>&1 || HARNESS_EXIT=$?

echo "--- harness output (tail 80 lines) ---"
tail -n 80 "${EVIDENCE_LOG}" || true
echo "---------------------------------------"
info "Harness exit code: ${HARNESS_EXIT}"

# ---------------------------------------------------------------------------
# mirth.log location: run-smoke-test.sh copies the live log into
# smoke-tests/out/mirth-<timestamp>.log during cleanup(), and the original path is
# server/setup/logs/mirth.log while the server is still up. Both are checked so the
# signature scan below sees the log regardless of when cleanup() ran relative to this check.
# ---------------------------------------------------------------------------
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MIRTH_LOG_LIVE="${REPO_ROOT}/server/setup/logs/mirth.log"
MIRTH_LOG_ARCHIVED=""
if ls "${OUT_DIR}"/mirth-*.log > /dev/null 2>&1; then
    # Most recent archived copy (cleanup() timestamps with date -u +%Y%m%dT%H%M%SZ, which
    # sorts lexicographically in chronological order).
    MIRTH_LOG_ARCHIVED="$(ls -t "${OUT_DIR}"/mirth-*.log | head -1)"
fi

# ---------------------------------------------------------------------------
# Verdict classification (mirrors break-dependency.sh:225-243)
# ---------------------------------------------------------------------------
VERDICT_EXIT=2
MATCHED_SIGNATURE=""
MATCHED_SOURCE_PATH=""
MATCHED_SOURCE_LABEL=""

if [[ ${HARNESS_EXIT} -eq 0 ]]; then
    echo "SELF-TEST FAILED: harness PASSED with bad postgres credentials (SMOKE_PG_BREAK=1) -- net has a hole"
    VERDICT_EXIT=1
else
    for sig in "${PG_FAULT_SIGNATURES[@]}"; do
        if grep -qF "${sig}" "${EVIDENCE_LOG}"; then
            MATCHED_SIGNATURE="${sig}"
            MATCHED_SOURCE_PATH="${EVIDENCE_LOG}"
            MATCHED_SOURCE_LABEL="${EVIDENCE_LOG} (harness stdout/stderr)"
            break
        fi
        if [[ -f "${MIRTH_LOG_LIVE}" ]] && grep -qF "${sig}" "${MIRTH_LOG_LIVE}"; then
            MATCHED_SIGNATURE="${sig}"
            MATCHED_SOURCE_PATH="${MIRTH_LOG_LIVE}"
            MATCHED_SOURCE_LABEL="${MIRTH_LOG_LIVE}"
            break
        fi
        if [[ -n "${MIRTH_LOG_ARCHIVED}" ]] && grep -qF "${sig}" "${MIRTH_LOG_ARCHIVED}"; then
            MATCHED_SIGNATURE="${sig}"
            MATCHED_SOURCE_PATH="${MIRTH_LOG_ARCHIVED}"
            MATCHED_SOURCE_LABEL="${MIRTH_LOG_ARCHIVED}"
            break
        fi
    done

    if [[ -n "${MATCHED_SIGNATURE}" ]]; then
        ok "harness caught the bad-credentials fault (matched '${MATCHED_SIGNATURE}' in ${MATCHED_SOURCE_LABEL})"
        echo "--- matching excerpt ---"
        grep -F -A 3 -B 3 "${MATCHED_SIGNATURE}" "${MATCHED_SOURCE_PATH}" | head -30
        echo "------------------------"
        VERDICT_EXIT=0
    else
        echo "INCONCLUSIVE: harness failed, but no known pg driver/auth fault signature was found in the evidence log or mirth.log"
        VERDICT_EXIT=2
    fi
fi

# ---------------------------------------------------------------------------
# Post-run sanity: run-smoke-test.sh's own cleanup() trap tears down the postgres
# container and restores mirth.properties from *.smoke-bak on every exit path
# (including the injected-fault failure above). Verify that actually happened --
# never let a leftover container or a dirty mirth.properties hide behind a good verdict.
# ---------------------------------------------------------------------------
LEFTOVER=0
if docker ps -a --format '{{.Names}}' 2>/dev/null | grep -q '^smoke-postgres-'; then
    err "A smoke-postgres-* container is still present after the break-proof run:"
    docker ps -a --format '{{.Names}}\t{{.Status}}' | grep '^smoke-postgres-' || true
    LEFTOVER=1
else
    ok "No leftover smoke-postgres-* container (cleanup() teardown verified)"
fi

MIRTH_PROPS="${REPO_ROOT}/server/setup/conf/mirth.properties"
if [[ -f "${MIRTH_PROPS}.smoke-bak" ]]; then
    err "mirth.properties.smoke-bak still present -- cleanup() restore did not complete: ${MIRTH_PROPS}.smoke-bak"
    LEFTOVER=1
fi

# Only server/setup/conf/mirth.properties is touched (patched + restored) by
# run-smoke-test.sh; server/mirth.properties is a separate, unrelated template file
# stamped by the ant build itself (a pre-existing build-residue concern documented in
# CLAUDE.md, orthogonal to this harness run) and must not be checked here.
DIRTY_TREE="$(cd "${REPO_ROOT}" && git status --short -- server/setup/conf/mirth.properties 2>/dev/null || true)"
if [[ -n "${DIRTY_TREE}" ]]; then
    err "git status is not clean after the break-proof run:"
    echo "${DIRTY_TREE}"
    LEFTOVER=1
else
    ok "git status clean on server/setup/conf/mirth.properties after the break-proof run"
fi

if [[ ${LEFTOVER} -eq 1 ]]; then
    err "Post-run sanity FAILED -- tree/container may be left in a broken state (verdict was ${VERDICT_EXIT})."
    # Never let a cleanup hiccup downgrade a genuine SELF-TEST FAILED (exit 1) verdict --
    # that is the single most serious outcome this script can report.
    if [[ ${VERDICT_EXIT} -eq 1 ]]; then
        exit 1
    fi
    exit 2
fi

exit "${VERDICT_EXIT}"
