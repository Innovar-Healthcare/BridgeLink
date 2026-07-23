#!/bin/bash
# smoke-tests/run-smoke-test.sh
# Boot/teardown orchestrator for the NET-01/02/05 end-to-end smoke harness (IRT-1491).
#
# Launches the real assembled BridgeLink distribution (server/setup/) directly on the
# runner JVM (D-01), with embedded Derby, ephemeral ports, and an auto-generated
# keystore, health-checks it over HTTPS, then tears down cleanly (D-03/D-04).
#
# Structured in named stages (preflight -> ports -> patch_properties -> launch_server ->
# health_check -> import_deploy [18-05] -> pump/assert [18-06/18-07] -> teardown) so later
# plans can extend this script without redesigning process lifecycle.
#
# Usage: smoke-tests/run-smoke-test.sh [--db derby|mysql|postgres|mssql] [--boot-only]
#                                       [--deploy-only] [--help]
#
# Prerequisite: build the distribution once —
#   cd server && ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true
#
# Satisfies: NET-01 (D-01/D-03), plan 18-01 must_haves (boot/teardown proven twice
# consecutively, 127.0.0.1-only bind, fresh keystore/appdata per run). Plan 18-05 adds the
# import/deploy stage (D-05/D-06): all 10 reference channel fixtures are imported over
# REST, deployed in parallel, and polled to STARTED, with FATAL InvalidChannel detection
# (the NET-05 break-proof detection mechanism).
set -euo pipefail

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
DB_TYPE="derby"   # default (D-03: only derby is functional in Phase 18)
BOOT_ONLY=0
DEPLOY_ONLY=0

usage() {
    echo "Usage: $0 [--db derby|mysql|postgres|mssql] [--boot-only] [--deploy-only] [--help]"
    echo ""
    echo "  --db <backend>   Database backend (default: derby). Only 'derby' is functional"
    echo "                   in Phase 18 — other values are accepted but fail fast with a"
    echo "                   'not yet supported' message (see D-03 / Phase 24)."
    echo "  --boot-only      Stop after the health check succeeds; tear down immediately."
    echo "                   Skips the import/deploy stage and everything after it."
    echo "  --deploy-only    Boot + import + deploy all 10 reference channels, then tear"
    echo "                   down (no message pump/assert driver — that's 18-06/18-07)."
    echo "                   Mutually exclusive with --boot-only."
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
        --deploy-only)
            DEPLOY_ONLY=1
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

if [[ ${BOOT_ONLY} -eq 1 && ${DEPLOY_ONLY} -eq 1 ]]; then
    echo "Error: --boot-only and --deploy-only are mutually exclusive." >&2
    exit 1
fi

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
HARNESS_LOG_DIR="${SCRIPT_DIR}/out"
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

    mkdir -p "${HARNESS_LOG_DIR}"
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
    # SOAP_URL is derived, not a raw port — the Web Service Sender fixture (soap-test.xml)
    # substitutes ${SOAP_URL} directly (D-07: Endpoint.publish stub target).
    SOAP_URL="http://127.0.0.1:${SOAP_PORT}/smoketest"
    export HTTP_PORT HTTPS_PORT MLLP_PORT HTTP_LISTENER_PORT SMTP_PORT SCP_PORT SOAP_PORT SOAP_URL
    pass "Ports allocated: HTTP=${HTTP_PORT} HTTPS=${HTTPS_PORT} MLLP=${MLLP_PORT} HTTP_LISTENER=${HTTP_LISTENER_PORT} SMTP=${SMTP_PORT} SCP=${SCP_PORT} SOAP=${SOAP_PORT} (SOAP_URL=${SOAP_URL})"
}

# ---------------------------------------------------------------------------
# Stage: allocate_work_dirs — per-run channel I/O work tree (IN_DIR/OUT_DIR/
# SQLITE_PATH), created under a fresh mktemp -d so two consecutive runs never
# collide and teardown can safely rm -rf it (Pitfall: never touch smoke-tests/
# committed fixtures — this is scratch space only).
# ---------------------------------------------------------------------------
CHANNEL_WORK_DIR=""

allocate_work_dirs() {
    hr
    info "Allocating channel import/deploy work directory..."
    CHANNEL_WORK_DIR="$(mktemp -d)"
    IN_DIR="${CHANNEL_WORK_DIR}/in"
    OUT_DIR="${CHANNEL_WORK_DIR}/out"
    SQLITE_PATH="${CHANNEL_WORK_DIR}/smoke-test.db"

    # Per-channel subdirectories the fixtures' placeholders resolve into.
    mkdir -p "${IN_DIR}/file" \
             "${OUT_DIR}/http" "${OUT_DIR}/mllp" "${OUT_DIR}/file" "${OUT_DIR}/vm" \
             "${OUT_DIR}/js" "${OUT_DIR}/doc"

    export IN_DIR OUT_DIR SQLITE_PATH
    pass "Channel work dir allocated: ${CHANNEL_WORK_DIR} (IN_DIR/OUT_DIR/SQLITE_PATH exported)"
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
            > "${HARNESS_LOG_DIR}/server-stdout.log" 2>&1
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
# Stage: import_deploy — channel fixture import/deploy (plan 18-05, D-05/D-06)
#
# All curl calls carry `X-Requested-With: OpenAPI` (CSRF guard, load-bearing —
# requests without it are rejected). Cookie jar is per-run and deleted in
# cleanup(). envsubst uses an EXPLICIT variable allowlist (T-18-10) — never
# bare envsubst, which would expand arbitrary environment content into
# REST-imported XML.
# ---------------------------------------------------------------------------
API=""
COOKIE_JAR=""
CHANNEL_FILES=(http-test tcp-mllp-test file-test jdbc-test vm-test js-test smtp-test soap-test dicom-test doc-writer-test)
CHANNEL_IDS=(
    "00000001-0000-0000-0000-000000000001"
    "00000002-0000-0000-0000-000000000002"
    "00000003-0000-0000-0000-000000000003"
    "00000004-0000-0000-0000-000000000004"
    "00000005-0000-0000-0000-000000000005"
    "00000006-0000-0000-0000-000000000006"
    "00000007-0000-0000-0000-000000000007"
    "00000008-0000-0000-0000-000000000008"
    "00000009-0000-0000-0000-000000000009"
    "00000010-0000-0000-0000-000000000010"
)
# Explicit envsubst allowlist — exactly the ${VARNAME} placeholders the committed
# fixtures use. ${DICOMMESSAGE} is a Mirth-internal template variable resolved by
# the server itself and MUST NOT appear here (envsubst would blank it out).
ENVSUBST_ALLOWLIST='${HTTP_LISTENER_PORT} ${MLLP_PORT} ${SMTP_PORT} ${SCP_PORT} ${SOAP_URL} ${SQLITE_PATH} ${IN_DIR} ${OUT_DIR}'

bl_login() {
    info "Logging in to ${API}..."
    local http_code body
    body=$(mktemp)
    http_code=$(curl -s -k -c "${COOKIE_JAR}" \
        -X POST "${API}/users/_login" \
        -H "X-Requested-With: OpenAPI" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "username=admin&password=admin" \
        -o "${body}" -w "%{http_code}")
    if [[ "${http_code}" != "200" ]]; then
        cat "${body}"; rm -f "${body}"
        fatal "Login failed (HTTP ${http_code})"
    fi
    rm -f "${body}"
    pass "Authenticated to ${API}"
}

get_server_version() {
    BL_VERSION=$(curl -s -k "${API}/server/version" -H "X-Requested-With: OpenAPI" | tr -d '"' | tr -d '\r\n')
    if [[ -z "${BL_VERSION}" ]]; then
        fatal "Could not determine server version from ${API}/server/version"
    fi
    info "Server version: ${BL_VERSION}"
}

# Substitutes placeholders (explicit allowlist) into a per-run work dir, then
# rewrites the channel/connector version="..." attributes to the ACTUAL running
# server version (fixtures are committed at the current dev version; this sed
# pass makes imports version-proof even when the dev version drifts — the exact
# proven-in-production mechanism from test-irt832 lines 476-500).
substitute_fixtures() {
    hr
    info "Substituting channel fixture placeholders into work dir..."
    mkdir -p "${CHANNEL_WORK_DIR}/channels"

    local name src dst
    for name in "${CHANNEL_FILES[@]}"; do
        src="${SCRIPT_DIR}/channels/${name}.xml"
        dst="${CHANNEL_WORK_DIR}/channels/${name}.xml"

        if [[ ! -f "${src}" ]]; then
            fatal "Missing committed fixture: ${src}"
        fi

        envsubst "${ENVSUBST_ALLOWLIST}" < "${src}" > "${dst}.tmp"
        sed -e 's/version="[0-9][0-9.]*"/version="'"${BL_VERSION}"'"/g' "${dst}.tmp" > "${dst}"
        rm -f "${dst}.tmp"
    done

    pass "All ${#CHANNEL_FILES[@]} fixtures substituted and version-rewritten (server/version=${BL_VERSION})"
}

import_channels() {
    hr
    info "Importing ${#CHANNEL_FILES[@]} reference channels..."
    local name xmlfile http_code body
    for name in "${CHANNEL_FILES[@]}"; do
        xmlfile="${CHANNEL_WORK_DIR}/channels/${name}.xml"
        body=$(mktemp)
        http_code=$(curl -s -k -b "${COOKIE_JAR}" \
            -X POST "${API}/channels" \
            -H "X-Requested-With: OpenAPI" \
            -H "Content-Type: application/xml" \
            --data-binary "@${xmlfile}" \
            -o "${body}" -w "%{http_code}")
        if [[ "${http_code}" != "200" && "${http_code}" != "201" ]]; then
            echo "SMOKE-FAILURE-CLASS: import"
            cat "${body}"; rm -f "${body}"
            fatal "Import failed for ${name}.xml (HTTP ${http_code})"
        fi
        rm -f "${body}"
        pass "Imported ${name}.xml"
    done
}

# Deploys all channels in one call (D-06: parallel, protects the 10-minute budget).
# Accepts 200 OR 204 — the _deploy endpoint returns 204 No Content on success
# (test-irt832 line 133); asserting 200-only fails healthy deploys.
deploy_channels() {
    hr
    info "Deploying all ${#CHANNEL_IDS[@]} channels..."
    local set_body id http_code body
    set_body="<set>"
    for id in "${CHANNEL_IDS[@]}"; do
        set_body+="<string>${id}</string>"
    done
    set_body+="</set>"

    body=$(mktemp)
    http_code=$(curl -s -k -b "${COOKIE_JAR}" \
        -X POST "${API}/channels/_deploy?returnErrors=true" \
        -H "X-Requested-With: OpenAPI" \
        -H "Content-Type: application/xml" \
        -d "${set_body}" \
        -o "${body}" -w "%{http_code}")
    if [[ "${http_code}" != "200" && "${http_code}" != "204" ]]; then
        echo "SMOKE-FAILURE-CLASS: import"
        cat "${body}"; rm -f "${body}"
        fatal "Deploy request failed (HTTP ${http_code})"
    fi
    rm -f "${body}"
    pass "Deploy request accepted (HTTP ${http_code})"
}

# FATAL InvalidChannel detection (divergence from the test-irt832 analog, which only
# warns — Pitfall 2 here is FATAL because this is the exact NET-05 break-proof
# mechanism). A channel that degraded to InvalidChannel on import is present in
# GET /channels but ABSENT from GET /channels/statuses?...&includeUndeployed=true.
# Never grep the channel GET body for "invalidChannel" — ChannelConverter.marshal
# can reconstruct output resembling the original XML, a false negative.
verify_no_invalid_channels() {
    hr
    info "Verifying no channel degraded to InvalidChannel on import..."

    # Deploy registration can lag briefly behind the _deploy response (the endpoint
    # returns as soon as the request is ACCEPTED, not necessarily once every channel's
    # dashboard status entry is registered) — poll a few times before declaring a
    # channel truly absent, so this check doesn't race deploy_channels() and produce
    # a false FATAL. This is a presence check only; wait_for_started() does the real
    # STARTED-state polling afterward.
    local all_statuses id name i attempt
    local attempt=0
    local max_attempts=5

    while [[ ${attempt} -lt ${max_attempts} ]]; do
        all_statuses=$(curl -s -k -b "${COOKIE_JAR}" \
            "${API}/channels/statuses?includeUndeployed=true" \
            -H "X-Requested-With: OpenAPI" -H "Accept: application/xml")

        # Bash builtin substring match (NOT `echo ... | grep -q`) — `all_statuses` can
        # be tens of KB across 10 channels' full dashboard status blocks, and grep -q
        # exits as soon as it finds a match, closing its end of the pipe while echo is
        # still writing. Under `set -o pipefail` that SIGPIPE makes the pipeline report
        # failure even when grep DID match, causing false "absent" verdicts here — the
        # exact bug that produced spurious FATAL InvalidChannel failures during this
        # plan's own verification runs. The builtin `[[ == *pattern* ]]` never forks a
        # pipe, so it cannot exhibit this failure mode.
        local missing=0
        for id in "${CHANNEL_IDS[@]}"; do
            [[ "${all_statuses}" == *"${id}"* ]] || missing=$((missing + 1))
        done

        if [[ ${missing} -eq 0 ]]; then
            break
        fi

        attempt=$((attempt + 1))
        sleep 3
    done

    i=0
    for id in "${CHANNEL_IDS[@]}"; do
        name="${CHANNEL_FILES[$i]}"
        if [[ "${all_statuses}" == *"${id}"* ]]; then
            pass "Channel ${name} (${id}) present in statuses"
        else
            echo "SMOKE-FAILURE-CLASS: import"
            echo "INVALID CHANNEL (import degraded): ${name} (${id})"
            dump_log_tail
            fail "Channel ${name} (${id}) absent from statuses — InvalidChannel degradation"
        fi
        i=$((i + 1))
    done

    if [[ ${FAIL_COUNT} -gt 0 ]]; then
        fatal "One or more channels degraded to InvalidChannel on import (see SMOKE-FAILURE-CLASS: import above)"
    fi
}

# Polls each channel to STARTED, ≤60s per channel, no fixed sleeps (Pitfall 7).
# A STOPPED channel gets one _start attempt (test-irt832's fallback pattern),
# then polling continues.
wait_for_started() {
    hr
    info "Waiting for all channels to reach STARTED..."
    local id name attempts state started_once statfile
    local i=0
    statfile=$(mktemp)
    for id in "${CHANNEL_IDS[@]}"; do
        name="${CHANNEL_FILES[$i]}"
        i=$((i + 1))
        attempts=0
        started_once=0

        while [[ ${attempts} -lt 30 ]]; do
            # curl writes to a file (not a live pipe) before grep/sed read it — avoids
            # the SIGPIPE/pipefail false-negative class fixed in verify_no_invalid_channels
            # above (grep -o here can match multiple <state> tags — channel + source +
            # destination — and a downstream `head -1` closing early would race the
            # writer under `set -o pipefail`).
            curl -s -k -b "${COOKIE_JAR}" \
                "${API}/channels/statuses?channelId=${id}&includeUndeployed=true" \
                -H "X-Requested-With: OpenAPI" -H "Accept: application/xml" \
                -o "${statfile}"
            state=$(grep -o '<state>[A-Z_]*</state>' "${statfile}" | head -1 | sed -e 's/<state>//' -e 's/<\/state>//')

            if [[ "${state}" == "STARTED" ]]; then
                pass "Channel ${name} (${id}) is STARTED"
                break
            fi

            if [[ "${state}" == "STOPPED" && ${started_once} -eq 0 ]]; then
                info "  Channel ${name} is STOPPED — attempting _start..."
                curl -s -k -b "${COOKIE_JAR}" -X POST "${API}/channels/${id}/_start" \
                    -H "X-Requested-With: OpenAPI" -o /dev/null || true
                started_once=1
            fi

            sleep 2
            attempts=$((attempts + 1))
        done

        if [[ "${state}" != "STARTED" ]]; then
            echo "SMOKE-FAILURE-CLASS: import"
            dump_log_tail
            rm -f "${statfile}"
            fatal "Channel ${name} (${id}) did not reach STARTED within 60s (last state: ${state:-unknown})"
        fi
    done
    rm -f "${statfile}"
}

# STARTED does NOT guarantee the listener socket is accepting connections yet
# (test-irt832 wait_for_listener, lines 101-119, exists for exactly this reason).
# 18-07's pump stage depends on this guarantee — pumping before the port binds
# is a flake source. Poll-with-timeout <=60s, no fixed sleeps.
wait_for_listener_ports() {
    hr
    info "Waiting for socket-source listener ports to accept connections..."

    local attempts=0 code
    info "  HTTP Listener (port ${HTTP_LISTENER_PORT})..."
    while [[ ${attempts} -lt 20 ]]; do
        code=$(curl -s --max-time 3 --connect-timeout 2 -o /dev/null -w "%{http_code}" \
            "http://127.0.0.1:${HTTP_LISTENER_PORT}/" 2>/dev/null || echo "000")
        if [[ "${code}" != "000" ]]; then
            pass "  HTTP Listener port ${HTTP_LISTENER_PORT} responding (HTTP ${code})"
            break
        fi
        sleep 3
        attempts=$((attempts + 1))
    done
    if [[ "${code}" == "000" ]]; then
        fatal "HTTP Listener port ${HTTP_LISTENER_PORT} did not respond within 60s"
    fi

    attempts=0
    info "  TCP/MLLP Listener (port ${MLLP_PORT})..."
    while [[ ${attempts} -lt 20 ]]; do
        if python3 -c "
import socket, sys
s = socket.socket()
s.settimeout(2)
try:
    s.connect(('127.0.0.1', ${MLLP_PORT}))
    s.close()
    sys.exit(0)
except Exception:
    sys.exit(1)
" 2>/dev/null; then
            pass "  TCP/MLLP Listener port ${MLLP_PORT} accepting connections"
            break
        fi
        sleep 3
        attempts=$((attempts + 1))
    done
    if [[ ${attempts} -ge 20 ]]; then
        fatal "TCP/MLLP Listener port ${MLLP_PORT} did not accept connections within 60s"
    fi
}

import_deploy() {
    API="https://127.0.0.1:${HTTPS_PORT}/api"
    COOKIE_JAR="$(mktemp)"

    bl_login
    get_server_version
    substitute_fixtures
    import_channels
    deploy_channels
    verify_no_invalid_channels
    wait_for_started
    wait_for_listener_ports

    pass "Import/deploy stage complete: all ${#CHANNEL_IDS[@]} channels STARTED"
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
        cp "${log_file}" "${HARNESS_LOG_DIR}/mirth-${ts}.log" 2>/dev/null || true
        info "Copied mirth.log to ${HARNESS_LOG_DIR}/mirth-${ts}.log"
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

    if [[ -n "${CHANNEL_WORK_DIR}" && -d "${CHANNEL_WORK_DIR}" ]]; then
        rm -rf "${CHANNEL_WORK_DIR}"
        info "Deleted channel import/deploy work directory"
    fi

    if [[ -n "${COOKIE_JAR}" && -f "${COOKIE_JAR}" ]]; then
        rm -f "${COOKIE_JAR}"
        info "Deleted REST session cookie jar"
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
allocate_work_dirs
patch_properties
launch_server

if ! health_check; then
    # FAIL_COUNT already incremented by health_check(); let cleanup trap handle exit.
    exit 1
fi

if [[ ${BOOT_ONLY} -eq 1 ]]; then
    info "--boot-only: stopping after health check, proceeding to teardown"
    exit 0
fi

import_deploy

if [[ ${DEPLOY_ONLY} -eq 1 ]]; then
    info "--deploy-only: stopping after import/deploy, proceeding to teardown"
fi

# Later plans (18-06/18-07) insert the message pump/assert driver here, gated on
# `[[ ${DEPLOY_ONLY} -eq 0 ]]`. Plan 18-05 proves boot -> health -> import -> deploy
# -> STARTED -> teardown.

exit 0
