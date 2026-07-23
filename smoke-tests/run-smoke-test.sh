#!/bin/bash
# smoke-tests/run-smoke-test.sh
# Boot/teardown orchestrator for the NET-01/02/05 end-to-end smoke harness (IRT-1491).
#
# Launches the real assembled BridgeLink distribution (server/setup/) directly on the
# runner JVM (D-01), with embedded Derby, ephemeral ports, and an auto-generated
# keystore, health-checks it over HTTPS, then tears down cleanly (D-03/D-04).
#
# Structured in named stages (preflight -> ports -> patch_properties -> launch_server ->
# health_check -> [later plans insert import/deploy/pump/assert stages here] -> teardown)
# so plans 18-05/18-06/18-07 can extend this script without redesigning process lifecycle.
#
# Usage: smoke-tests/run-smoke-test.sh [--db derby|mysql|postgres|mssql] [--boot-only] [--help]
#
# Prerequisite: build the distribution once —
#   cd server && ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true
#
# Satisfies: NET-01 (D-01/D-03), plan 18-01 must_haves (boot/teardown proven twice
# consecutively, 127.0.0.1-only bind, fresh keystore/appdata per run).
set -euo pipefail

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
DB_TYPE="derby"   # default (D-03: only derby is functional in Phase 18)
BOOT_ONLY=0

usage() {
    echo "Usage: $0 [--db derby|mysql|postgres|mssql] [--boot-only] [--help]"
    echo ""
    echo "  --db <backend>   Database backend (default: derby). Only 'derby' is functional"
    echo "                   in Phase 18 — other values are accepted but fail fast with a"
    echo "                   'not yet supported' message (see D-03 / Phase 24)."
    echo "  --boot-only      Stop after the health check succeeds; tear down immediately."
    echo "                   Later plans (18-05/18-07) insert import/deploy/pump/assert"
    echo "                   stages here when --boot-only is NOT passed."
    echo "  --help           Show this message and exit 0."
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --db)
            shift
            if [[ $# -eq 0 ]]; then
                echo "Error: --db requires a value (derby|mysql|postgres|mssql)" >&2
                exit 1
            fi
            DB_TYPE="$1"
            shift
            ;;
        --boot-only)
            BOOT_ONLY=1
            shift
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

case "$DB_TYPE" in
    derby|mysql|postgres|mssql) ;;
    *)
        echo "Error: unrecognized --db value '${DB_TYPE}'. Must be derby|mysql|postgres|mssql." >&2
        exit 1
        ;;
esac

# ---------------------------------------------------------------------------
# Derived variables
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVER_SETUP="${REPO_ROOT}/server/setup"
MIRTH_PROPS="${SERVER_SETUP}/conf/mirth.properties"
MIRTH_PROPS_BAK="${MIRTH_PROPS}.smoke-bak"
OUT_DIR="${SCRIPT_DIR}/out"
START_EPOCH=$(date +%s)
PASS_COUNT=0
FAIL_COUNT=0
SERVER_PID=""
APPDATA=""

# ---------------------------------------------------------------------------
# Color / status helpers (convention: migration-tests/run-migration-test.sh)
# ---------------------------------------------------------------------------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

pass()  { echo -e "${GREEN}PASS${NC}: $1"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail()  { echo -e "${RED}FAIL${NC}: $1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }
fatal() { echo -e "${RED}FATAL${NC}: $1"; FAIL_COUNT=$((FAIL_COUNT + 1)); exit 1; }
info()  { echo -e "${YELLOW}INFO${NC}: $1"; }
hr()    { echo "------------------------------------------------------------"; }

# ---------------------------------------------------------------------------
# Duration reporting — printed on every exit path (feeds D-04's <=10min target)
# ---------------------------------------------------------------------------
report_duration() {
    local end_epoch duration
    end_epoch=$(date +%s)
    duration=$((end_epoch - START_EPOCH))
    echo "HARNESS DURATION: ${duration}s"
}

# ---------------------------------------------------------------------------
# Stage: configure_db — D-03 pluggable --db parameter, derby-only for Phase 18
# ---------------------------------------------------------------------------
configure_db() {
    case "$DB_TYPE" in
        derby)
            info "Database backend: derby (embedded, fresh per-run appdata)"
            ;;
        mysql|postgres|mssql)
            fatal "--db ${DB_TYPE} is not yet supported in Phase 18, see D-03/Phase 24"
            ;;
    esac
}

# ---------------------------------------------------------------------------
# Stage: preflight — verify the assembled distribution exists (D-01 prerequisite)
# ---------------------------------------------------------------------------
preflight() {
    hr
    info "Preflight: DB_TYPE=${DB_TYPE}, BOOT_ONLY=${BOOT_ONLY}"

    if [[ ! -f "${SERVER_SETUP}/server-lib/mirth-server.jar" ]]; then
        fatal "server/setup/server-lib/mirth-server.jar not found. Build the distribution first:
  cd server && ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true"
    fi
    pass "server-lib/mirth-server.jar present"

    if [[ ! -f "${MIRTH_PROPS}" ]]; then
        fatal "server/setup/conf/mirth.properties not found. Build the distribution first:
  cd server && ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true"
    fi
    pass "server/setup/conf/mirth.properties present"

    if [[ ! -f "${SERVER_SETUP}/mirth-server-launcher.jar" ]]; then
        fatal "server/setup/mirth-server-launcher.jar not found. Build the distribution first:
  cd server && ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true"
    fi
    pass "mirth-server-launcher.jar present"

    mkdir -p "${OUT_DIR}"
}

# ---------------------------------------------------------------------------
# Stage: allocate_ports — bind-port-0 helper, ALL ports up front (Pitfall 8:
# randomize base per run, avoid TOCTOU by allocating everything before launch)
# ---------------------------------------------------------------------------
free_port() {
    python3 - <<'PYEOF'
import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PYEOF
}

allocate_ports() {
    hr
    info "Allocating ephemeral ports..."
    HTTP_PORT=$(free_port)
    HTTPS_PORT=$(free_port)
    # Reserved for later plans (channel listeners / endpoint stubs) — allocated now so
    # every stage this script grows into can rely on ALL ports being fixed up front.
    MLLP_PORT=$(free_port)
    HTTP_LISTENER_PORT=$(free_port)
    SMTP_PORT=$(free_port)
    SCP_PORT=$(free_port)
    SOAP_PORT=$(free_port)
    export HTTP_PORT HTTPS_PORT MLLP_PORT HTTP_LISTENER_PORT SMTP_PORT SCP_PORT SOAP_PORT
    pass "Ports allocated: HTTP=${HTTP_PORT} HTTPS=${HTTPS_PORT} MLLP=${MLLP_PORT} HTTP_LISTENER=${HTTP_LISTENER_PORT} SMTP=${SMTP_PORT} SCP=${SCP_PORT} SOAP=${SOAP_PORT}"
}

# ---------------------------------------------------------------------------
# Stage: patch_properties — sed -i.smoke-bak in place; restored in cleanup (Pitfall 3)
# T-18-01: shipped default http.host/https.host = 0.0.0.0 — a CI runner must not
# expose the admin API on all interfaces, so bind 127.0.0.1 only.
# ---------------------------------------------------------------------------
patch_properties() {
    hr
    info "Patching server/setup/conf/mirth.properties (backup: *.smoke-bak)..."

    APPDATA="$(mktemp -d)/appdata-$$"

    sed -i.smoke-bak \
        -e "s|^http.port *=.*|http.port = ${HTTP_PORT}|" \
        -e "s|^https.port *=.*|https.port = ${HTTPS_PORT}|" \
        -e "s|^dir.appdata *=.*|dir.appdata = ${APPDATA}|" \
        -e "s|^http.host *=.*|http.host = 127.0.0.1|" \
        -e "s|^https.host *=.*|https.host = 127.0.0.1|" \
        "${MIRTH_PROPS}"

    pass "mirth.properties patched: http.port=${HTTP_PORT} https.port=${HTTPS_PORT} dir.appdata=${APPDATA} http.host=127.0.0.1 https.host=127.0.0.1"
}

# ---------------------------------------------------------------------------
# Stage: launch_server — dev-launcher recipe (server/build.xml:1177-1183) with the
# authoritative 18-flag JVM options from server/docs/mcservice-java9+.vmoptions
# (NOT the build.xml test jvmargs list, which differs — Pitfall 1).
# ---------------------------------------------------------------------------
launch_server() {
    hr
    info "Launching MirthLauncher (cwd=${SERVER_SETUP})..."

    (
        cd "${SERVER_SETUP}"
        exec java \
            --add-modules=java.sql.rowset \
            --add-exports=java.base/com.sun.crypto.provider=ALL-UNNAMED \
            --add-exports=java.base/sun.security.provider=ALL-UNNAMED \
            --add-opens=java.base/java.lang=ALL-UNNAMED \
            --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
            --add-opens=java.base/java.math=ALL-UNNAMED \
            --add-opens=java.base/java.net=ALL-UNNAMED \
            --add-opens=java.base/java.security=ALL-UNNAMED \
            --add-opens=java.base/java.security.cert=ALL-UNNAMED \
            --add-opens=java.base/java.text=ALL-UNNAMED \
            --add-opens=java.base/java.util=ALL-UNNAMED \
            --add-opens=java.base/sun.security.pkcs=ALL-UNNAMED \
            --add-opens=java.base/sun.security.rsa=ALL-UNNAMED \
            --add-opens=java.base/sun.security.x509=ALL-UNNAMED \
            --add-opens=java.desktop/java.awt=ALL-UNNAMED \
            --add-opens=java.desktop/java.awt.color=ALL-UNNAMED \
            --add-opens=java.desktop/java.awt.font=ALL-UNNAMED \
            --add-opens=java.xml/com.sun.org.apache.xalan.internal.xsltc.trax=ALL-UNNAMED \
            -cp mirth-server-launcher.jar com.mirth.connect.server.launcher.MirthLauncher \
            > "${OUT_DIR}/server-stdout.log" 2>&1
    ) &
    SERVER_PID=$!

    pass "Server launched, PID=${SERVER_PID}"
}

# ---------------------------------------------------------------------------
# Stage: health_check — wait_for_bl analog (migration-tests convention), poll with
# timeout <=180s total, no fixed sleeps (Pitfall 7).
# ---------------------------------------------------------------------------
health_check() {
    hr
    local url="https://127.0.0.1:${HTTPS_PORT}/api/server/version"
    local max_attempts=60
    local attempt=0

    info "Waiting for BridgeLink at ${url}..."
    while [[ $attempt -lt $max_attempts ]]; do
        attempt=$((attempt + 1))

        if ! kill -0 "${SERVER_PID}" 2>/dev/null; then
            fail "Server process (PID ${SERVER_PID}) exited before becoming healthy"
            dump_log_tail
            return 1
        fi

        if curl -s -k --max-time 5 "${url}" -H "X-Requested-With: OpenAPI" -o /dev/null 2>/dev/null; then
            pass "BridgeLink is healthy (attempt ${attempt}/${max_attempts})"
            return 0
        fi

        sleep 3
    done

    fail "BridgeLink did not become healthy after $((max_attempts * 3))s"
    dump_log_tail
    return 1
}

dump_log_tail() {
    local log_file="${SERVER_SETUP}/logs/mirth.log"
    if [[ -f "${log_file}" ]]; then
        echo "--- mirth.log tail (last 50 lines) ---"
        tail -n 50 "${log_file}" || true
        echo "---------------------------------------"
    else
        info "No mirth.log found at ${log_file}"
    fi
}

# ---------------------------------------------------------------------------
# Stage: teardown / cleanup — trap on EXIT (Pitfall 3: unconditional, always runs)
# ---------------------------------------------------------------------------
cleanup() {
    hr
    info "Tearing down..."

    local log_file="${SERVER_SETUP}/logs/mirth.log"
    if [[ -f "${log_file}" ]]; then
        if [[ ${FAIL_COUNT} -gt 0 ]]; then
            dump_log_tail
        fi
        local ts
        ts=$(date -u +"%Y%m%dT%H%M%SZ")
        cp "${log_file}" "${OUT_DIR}/mirth-${ts}.log" 2>/dev/null || true
        info "Copied mirth.log to ${OUT_DIR}/mirth-${ts}.log"
    fi

    if [[ -n "${SERVER_PID}" ]] && kill -0 "${SERVER_PID}" 2>/dev/null; then
        info "Sending SIGTERM to server PID ${SERVER_PID}..."
        kill -TERM "${SERVER_PID}" 2>/dev/null || true
        local waited=0
        while kill -0 "${SERVER_PID}" 2>/dev/null && [[ ${waited} -lt 30 ]]; do
            sleep 1
            waited=$((waited + 1))
        done
        if kill -0 "${SERVER_PID}" 2>/dev/null; then
            info "Server still running after 30s, sending SIGKILL..."
            kill -KILL "${SERVER_PID}" 2>/dev/null || true
        fi
        wait "${SERVER_PID}" 2>/dev/null || true
        pass "Server process stopped"
    fi

    if [[ -n "${APPDATA}" && -d "${APPDATA}" ]]; then
        rm -rf "$(dirname "${APPDATA}")"
        info "Deleted temp appdata directory"
    fi

    if [[ -f "${MIRTH_PROPS_BAK}" ]]; then
        mv "${MIRTH_PROPS_BAK}" "${MIRTH_PROPS}"
        info "Restored mirth.properties from .smoke-bak"
    fi

    report_duration

    if [[ ${FAIL_COUNT} -gt 0 ]]; then
        echo -e "${RED}HARNESS FAILED${NC} (${FAIL_COUNT} failure(s), ${PASS_COUNT} pass(es))"
        exit 1
    else
        echo -e "${GREEN}HARNESS PASSED${NC} (${PASS_COUNT} pass(es))"
    fi
}

trap cleanup EXIT

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
configure_db
preflight
allocate_ports
patch_properties
launch_server

if ! health_check; then
    # FAIL_COUNT already incremented by health_check(); let cleanup trap handle exit.
    exit 1
fi

if [[ ${BOOT_ONLY} -eq 1 ]]; then
    info "--boot-only: stopping after health check, proceeding to teardown"
fi

# Later plans (18-05/18-06/18-07) insert channel import/deploy/pump/assert stages here,
# gated on `[[ ${BOOT_ONLY} -eq 0 ]]`. Phase 18-01 only proves boot -> health -> teardown.

exit 0
