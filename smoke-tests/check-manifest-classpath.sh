#!/bin/bash
# smoke-tests/check-manifest-classpath.sh
# Phase 23 / D-29.2: manifest Class-Path reconciliation, with RFC-822 line unfolding.
#
# Every Class-Path entry in an assembled jar's manifest must resolve to a real file relative
# to <base-dir>. Catches the silent-breakage class in 23-RESEARCH.md sec R5.4: a manifest
# Class-Path miss is a runtime NoClassDefFoundError, never a build failure, and the JAR spec
# folds manifest lines at 72 bytes -- a naive `grep Class-Path` reads ~3% of a 2400-char
# value and silently passes. This script RFC-822-unfolds continuation lines first (details
# and the concrete manager-launcher invocation are in smoke-tests/README.md and 23-PLAN.md).
#
# Usage: smoke-tests/check-manifest-classpath.sh <jar> <base-dir>
# Exit codes:
#   0 - PASS: every Class-Path entry resolves under <base-dir>
#   1 - FAIL: at least one entry does not resolve ("DANGLING: <entry>" printed)
#   2 - INCONCLUSIVE: jar/base-dir missing, unzip unavailable, or no Class-Path attribute
#       (an absent attribute is INCONCLUSIVE, never a silent PASS)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_DIR="${SCRIPT_DIR}/out"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${YELLOW}INFO${NC}: $1"; }
ok()    { echo -e "${GREEN}OK${NC}: $1"; }
err()   { echo -e "${RED}ERROR${NC}: $1" >&2; }

mkdir -p "${OUT_DIR}"
REPORT_LOG="${OUT_DIR}/check-manifest-classpath.log"

# ---------------------------------------------------------------------------
# Preflight
# ---------------------------------------------------------------------------
if [[ $# -ne 2 ]]; then
    err "Usage: smoke-tests/check-manifest-classpath.sh <jar> <base-dir>"
    exit 2
fi
JAR="$1"
BASE="$2"

if ! command -v unzip &>/dev/null; then
    err "unzip not found on PATH -- required to read the jar manifest."
    exit 2
fi

if [[ ! -f "${JAR}" ]]; then
    err "Jar not found: ${JAR}"
    exit 2
fi

if [[ ! -d "${BASE}" ]]; then
    err "Base dir not found: ${BASE}"
    exit 2
fi

# ---------------------------------------------------------------------------
# Extract manifest and unfold RFC-822 continuation lines (23-RESEARCH.md sec R7b, verified).
# On the Class-Path: line, strip the key and start accumulating; on each subsequent line
# beginning with a single space, strip EXACTLY ONE leading space and append; stop at the
# first line that does neither.
# ---------------------------------------------------------------------------
MANIFEST="$(unzip -p "${JAR}" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r')" || {
    err "Could not read META-INF/MANIFEST.MF from ${JAR}"
    exit 2
}

if [[ -z "${MANIFEST}" ]]; then
    err "META-INF/MANIFEST.MF is empty or missing in ${JAR}"
    exit 2
fi

CP="$(echo "${MANIFEST}" | awk '
    /^Class-Path:/ { f=1; sub(/^Class-Path: */, ""); printf "%s", $0; next }
    f && /^ / { sub(/^ /, ""); printf "%s", $0; next }
    f { exit }
')"

if [[ -z "${CP}" ]]; then
    err "Manifest carries no Class-Path attribute (or it is empty) -- INCONCLUSIVE, not a PASS."
    exit 2
fi

# ---------------------------------------------------------------------------
# Reconcile every whitespace-separated entry against the filesystem. Print the full report
# before deciding the verdict (mirrors break-dependency.sh:160's discipline).
# ---------------------------------------------------------------------------
{
    echo "== ${JAR}  (base-dir: ${BASE})"
} | tee "${REPORT_LOG}"

EXAMINED=0
MISSING=0
for entry in ${CP}; do
    EXAMINED=$((EXAMINED + 1))
    if [[ ! -f "${BASE}/${entry}" ]]; then
        echo "DANGLING: ${entry}" | tee -a "${REPORT_LOG}" >&2
        MISSING=1
    fi
done

{
    echo "   entries examined: ${EXAMINED}"
    echo "   VERDICT: $([[ ${MISSING} -eq 0 ]] && echo PASS || echo FAIL)"
} | tee -a "${REPORT_LOG}"

if [[ ${MISSING} -eq 0 ]]; then
    ok "all ${EXAMINED} Class-Path entries resolve under ${BASE}"
    exit 0
else
    err "at least one Class-Path entry is dangling -- see DANGLING lines above"
    exit 1
fi
