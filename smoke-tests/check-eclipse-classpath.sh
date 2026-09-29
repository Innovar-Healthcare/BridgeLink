#!/bin/bash
# smoke-tests/check-eclipse-classpath.sh
# Phase 23 / WR-06: Eclipse .classpath reconciliation.
#
# Why this gate exists. D-29.2's check-manifest-classpath.sh reconciles ASSEMBLED-jar manifest
# Class-Path attributes, but nothing covered the Eclipse project descriptors -- so `client/.classpath`
# accumulated sixteen `kind="lib"` entries pointing at jar versions that no longer exist (log4j
# 2.17.2, guava 28.2-jre, jetty-util 9.4.x, slf4j 1.7.30, ...), silently breaking the build path of
# every developer who imports the project. A dangling Eclipse entry never fails the Ant build, so
# only an explicit check finds it.
#
# Project-relative paths resolve under <project-dir>. Absolute entries are Eclipse
# workspace-relative and start with a PROJECT NAME (e.g. /Server/lib/..., /Donkey/lib/...), which
# is mapped to its directory in this repo via the <name> in each project's .project file.
#
# Usage: smoke-tests/check-eclipse-classpath.sh [<project-dir>...]     (default: client)
#
# SCOPE (deliberate): the default target is `client` only -- the descriptor Phase 23 touched and
# reconciled in full. The other Eclipse projects (server, donkey, command, manager, generator,
# webadmin) carry LARGE pre-existing drift accumulated over many releases (hundreds of entries
# still naming jars from the 3.x era). Reconciling those is a separate, deliberate piece of work;
# turning them on here would produce a gate that is red on arrival and therefore ignored. Pass a
# project directory explicitly to check it, e.g.:
#
#   bash smoke-tests/check-eclipse-classpath.sh manager
#   bash smoke-tests/check-eclipse-classpath.sh client donkey server
# Exit codes:
#   0 - PASS: every kind="lib" entry resolves (build-output entries under server/setup are
#       reported as SKIPPED, not failed -- server/setup is gitignored build output that does not
#       exist before `ant -f mirth-build.xml` has run)
#   1 - FAIL: at least one entry is dangling ("DANGLING: <project>: <path>" printed)
#   2 - INCONCLUSIVE: a named project directory has no .classpath, or no projects were found
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${YELLOW}INFO${NC}: $1"; }
ok()    { echo -e "${GREEN}OK${NC}: $1"; }
err()   { echo -e "${RED}ERROR${NC}: $1" >&2; }

# --- Build the Eclipse-project-name -> directory map from the .project files themselves --------
declare -a PROJECT_NAMES=()
declare -a PROJECT_DIRS=()
while IFS= read -r projectFile; do
    # The FIRST <name> in a .project is the project name; later ones are builder ids, so
    # head -1 (not a greedy whole-file regex, which would pick the last).
    name="$(grep -o '<name>[^<]*</name>' "${projectFile}" | head -1 | sed 's/<name>//;s/<\/name>//')"
    [[ -n "${name}" ]] || continue
    PROJECT_NAMES+=("${name}")
    PROJECT_DIRS+=("$(dirname "${projectFile}")")
done < <(find "${REPO_ROOT}" -maxdepth 2 -name '.project' | sort)

resolve_workspace_path() {
    # /<ProjectName>/rest/of/path -> <project dir>/rest/of/path
    local p="$1" first rest i
    first="${p#/}"
    rest="${first#*/}"
    first="${first%%/*}"
    for i in "${!PROJECT_NAMES[@]}"; do
        if [[ "${PROJECT_NAMES[$i]}" == "${first}" ]]; then
            echo "${PROJECT_DIRS[$i]}/${rest}"
            return 0
        fi
    done
    echo ""   # unknown project name
}

# --- Target projects --------------------------------------------------------------------------
declare -a TARGETS=()
if [[ $# -gt 0 ]]; then
    for arg in "$@"; do
        # Accept both an absolute path and a repo-relative project name.
        case "${arg}" in
            /*) TARGETS+=("${arg}") ;;
            *)  TARGETS+=("${REPO_ROOT}/${arg}") ;;
        esac
    done
else
    TARGETS=("${REPO_ROOT}/client")   # see SCOPE in the header
fi

if [[ ${#TARGETS[@]} -eq 0 ]]; then
    err "No Eclipse projects with a .classpath found under ${REPO_ROOT}"
    exit 2
fi

EXAMINED=0
SKIPPED=0
MISSING=0

for proj in "${TARGETS[@]}"; do
    if [[ ! -f "${proj}/.classpath" ]]; then
        err "No .classpath in ${proj}"
        exit 2
    fi
    projName="$(basename "${proj}")"
    while IFS= read -r entry; do
        [[ -n "${entry}" ]] || continue
        EXAMINED=$((EXAMINED + 1))
        case "${entry}" in
            /*) target="$(resolve_workspace_path "${entry}")" ;;
            *)  target="${proj}/${entry}" ;;
        esac
        if [[ -z "${target}" ]]; then
            echo "DANGLING: ${projName}: ${entry} (no Eclipse project of that name in this repo)"
            MISSING=1
            continue
        fi
        # server/setup is gitignored build output: absent before a distribution build, so an entry
        # pointing into it is not evidence of version drift.
        if [[ "${target}" == "${REPO_ROOT}/server/setup/"* && ! -e "${target}" ]]; then
            echo "SKIPPED (build output, run the distribution build to populate): ${projName}: ${entry}"
            SKIPPED=$((SKIPPED + 1))
            continue
        fi
        if [[ ! -e "${target}" ]]; then
            echo "DANGLING: ${projName}: ${entry}"
            MISSING=1
        fi
    done < <(grep -o 'kind="lib" path="[^"]*"' "${proj}/.classpath" | sed 's/.*path="//;s/"$//')
done

info "kind=\"lib\" entries examined: ${EXAMINED} (skipped build-output entries: ${SKIPPED})"

if [[ ${MISSING} -eq 0 ]]; then
    ok "every Eclipse .classpath library entry resolves"
    exit 0
fi

err "at least one Eclipse .classpath library entry is dangling -- see DANGLING lines above.
       Retarget it at the jar actually present in the project's lib directory (an Eclipse build
       path never fails the Ant build, so nothing else will tell you)."
exit 1
