#!/bin/bash
# smoke-tests/check-dist-freshness.sh
# Phase 23 / CR-04: fail-closed reconciliation of the ASSEMBLED distribution against the
# SOURCE-TREE jars.
#
# Why this gate exists. Every harness verdict (run-smoke-test.sh, break-dependency.sh,
# check-manifest-classpath.sh) is produced against `server/setup/`, which is gitignored and is
# therefore pure local state. Before this script, the only assertion made about that tree was
# that `server-lib/mirth-server.jar` EXISTS. Nothing checked that it had been rebuilt after the
# source jars changed, so a green canary could be — and demonstrably was — produced against a
# distribution still carrying the PREVIOUS release's xstream / Rhino / BouncyCastle jars. A
# break-proof canary that swaps a mangled 1.4.21 fixture in place of a stale 1.4.20 jar still
# observes NoClassDefFoundError and still exits 0, proving nothing about the jar under test.
#
# What it asserts:
#   1. server/lib/*.jar        -> present AND byte-identical somewhere under
#                                 server/setup/server-lib (recursively; MirthLauncher walks it
#                                 recursively, and nested copies exist e.g. server-lib/donkey/).
#   2. client/lib/*.jar,       -> present by exact versioned filename anywhere under
#      manager/lib/*.jar          server/setup. Presence-only ON PURPOSE: client-lib and
#                                 extension jars are re-signed during assembly, so their bytes
#                                 legitimately differ from the source-tree copies. The signal
#                                 that matters for a version bump is that the NEW versioned
#                                 filename reached the distribution at all.
#
# Usage: smoke-tests/check-dist-freshness.sh
# Exit codes:
#   0 - FRESH: every source-tree jar is accounted for in the assembled distribution
#   2 - STALE / NOT BUILT: at least one jar is missing or differs (all offenders printed).
#       Never exit 1 — this is an infrastructure precondition, not a test verdict, and callers
#       classify a non-zero result as INCONCLUSIVE.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVER_SETUP="${REPO_ROOT}/server/setup"
SERVER_LIB="${SERVER_SETUP}/server-lib"
REBUILD_HINT="cd server && ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${YELLOW}INFO${NC}: $1"; }
ok()    { echo -e "${GREEN}OK${NC}: $1"; }
err()   { echo -e "${RED}ERROR${NC}: $1" >&2; }

if [[ ! -d "${SERVER_LIB}" ]]; then
    err "Assembled distribution not built: ${SERVER_LIB} does not exist. Build it first:
  ${REBUILD_HINT}"
    exit 2
fi

STALE=0
CHECKED=0

# --- 1. server/lib: presence + byte identity under server-lib ---------------------------------
while IFS= read -r src; do
    CHECKED=$((CHECKED + 1))
    dest="$(find "${SERVER_LIB}" -name "$(basename "${src}")" -print -quit 2>/dev/null || true)"
    if [[ -z "${dest}" ]]; then
        err "Assembled distribution is STALE: $(basename "${src}") missing under ${SERVER_LIB}"
        STALE=1
    elif ! cmp -s "${src}" "${dest}"; then
        err "Assembled distribution is STALE: ${dest} differs from ${src}"
        STALE=1
    fi
done < <(find "${REPO_ROOT}/server/lib" -maxdepth 1 -name '*.jar' | sort)

# --- 2. client/lib + manager/lib: presence anywhere under server/setup ------------------------
for tree in client manager; do
    [[ -d "${REPO_ROOT}/${tree}/lib" ]] || continue
    while IFS= read -r src; do
        CHECKED=$((CHECKED + 1))
        if [[ -z "$(find "${SERVER_SETUP}" -name "$(basename "${src}")" -print -quit 2>/dev/null || true)" ]]; then
            err "Assembled distribution is STALE: $(basename "${src}") (${tree}/lib) missing anywhere under ${SERVER_SETUP}"
            STALE=1
        fi
    done < <(find "${REPO_ROOT}/${tree}/lib" -maxdepth 1 -name '*.jar' | sort)
done

if [[ ${STALE} -eq 1 ]]; then
    err "The assembled distribution does not match the source tree — no harness verdict produced
       against it can be trusted. Rebuild first:
  ${REBUILD_HINT}
       (A 'differs from' report on a server-lib jar can also mean the distribution was assembled
       with jar signing enabled; the command above disables it.)"
    exit 2
fi

ok "assembled distribution reconciles with the source tree (${CHECKED} jar(s) checked)"
exit 0
