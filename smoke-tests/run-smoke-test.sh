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
# import/deploy stage (D-05/D-06): all 12 reference channel fixtures are imported over
# REST, deployed in parallel, and polled to STARTED, with FATAL InvalidChannel detection
# (the NET-05 break-proof detection mechanism). Plan 18-09 adds the 11th fixture
# (legacy-migration-test.xml, a genuinely legacy schema-3.6.0 export) so every run also
# exercises Channel.migrateX()/MigratableConverter/MirthDomReader -- the exact seam behind
# the v26.6.0 xstream rollback. Plan 18-10 adds the 12th fixture
# (legacy-migration-3-4-test.xml, a schema-3.4.0 channel root) which forces
# Channel.migrate3_5_0() -- the exact DOM-mutation-then-reload seam the regression broke --
# closing the NET-05/SC-4 break-proof gap. Plans 18.1-02/03/04 add 8 more fixtures
# (NET-06, HTTP connector parameter coverage): response/xmlBody/binary-recv clusters,
# source auth (Basic/Digest), and sender-params/sender-timeout/binary-send -- bringing
# the total to 20 reference channels.
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
    echo "  --deploy-only    Boot + import + deploy all 20 reference channels, then tear"
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
# 25.1-03 (SC-3, IRT-1541): the live mirth.log path, forwarded to the JUnit driver so
# SftpParamsTest's legacy-negative leg can assert the class-specific JSchAlgoNegoFailException
# signature directly. Verified from a live run (Rule 1 correction — NOT a poll-time failure
# as originally assumed): FileReceiver.onStart() eagerly opens a connection at DEPLOY time,
# so the algorithm-negotiation failure throws synchronously as a channel-START failure
# (StartException) BEFORE the channel ever reaches STARTED and before any message could be
# dispatched — there is no per-message ERROR-status statistics entry to read via
# getErrorCount() for this leg (that REST-level signal used elsewhere in this harness
# assumes a channel that successfully started). The log content is the only class-specific
# evidence available for this leg.
MIRTH_LOG_PATH="${SERVER_SETUP}/logs/mirth.log"
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
# Duration reporting — printed on every exit path, and enforces the D-04 <=10-minute-per-run
# ceiling (measured on the harness alone: boot-start -> teardown-complete, excluding the
# one-time `ant mirth-build.xml` distribution build — Pitfall 11, that build is a separate,
# out-of-band prerequisite step never timed here).
# ---------------------------------------------------------------------------
DURATION_LIMIT_SECONDS=600

report_duration() {
    local end_epoch duration
    end_epoch=$(date +%s)
    duration=$((end_epoch - START_EPOCH))
    echo "HARNESS DURATION: ${duration}s (limit ${DURATION_LIMIT_SECONDS}s)"
    if [[ ${duration} -gt ${DURATION_LIMIT_SECONDS} ]]; then
        echo "SMOKE-FAILURE-CLASS: duration"
        fail "Harness duration ${duration}s exceeded the ${DURATION_LIMIT_SECONDS}s D-04 ceiling"
    fi
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

    # Rule 1 fix (plan 18-07): server/setup/logs/mirth.log lives at a FIXED path (log4j2's
    # RollingFile appender, not per-run dir.appdata) and simply keeps appending across every
    # harness invocation — unlike Derby's appdata, it is never fresh per run. The L3 scan
    # (scan_mirth_log) reads this file's ENTIRE current content on every run, so a stale ERROR
    # line from a PAST run (already fixed in code) would still fail today's run, and two
    # consecutive runs could never be judged independently (D-04's "twice consecutively" bar).
    # Archive any leftover log from an interrupted prior run (matching cleanup()'s own
    # archive-before-wipe pattern) then start this run with a clean file.
    local existing_log="${SERVER_SETUP}/logs/mirth.log"
    if [[ -f "${existing_log}" ]]; then
        mkdir -p "${SERVER_SETUP}/logs"
        cp "${existing_log}" "${HARNESS_LOG_DIR}/mirth-preexisting-$(date -u +"%Y%m%dT%H%M%SZ").log" 2>/dev/null || true
        : > "${existing_log}"
    fi
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
    # 18.3-01: DICOM round-trip channel (NET-08) — new Mirth DICOM Listener source port
    # and a dedicated DcmRcv SCP stub port (kept separate from SCP_PORT, Pitfall 7).
    DICOM_LISTENER_PORT=$(free_port)
    DICOM_ROUNDTRIP_SCP_PORT=$(free_port)
    # 18.4-03: DICOM TLS round-trip channels (NET-09) — dedicated Listener/SCP ports per
    # cipher (aes/3des), kept separate from the plaintext DICOM ports above (Pitfall 7).
    DICOM_TLS_AES_LISTENER_PORT=$(free_port)
    DICOM_TLS_AES_SCP_PORT=$(free_port)
    DICOM_TLS_3DES_LISTENER_PORT=$(free_port)
    DICOM_TLS_3DES_SCP_PORT=$(free_port)
    # 18.1-02: HTTP connector parameter-coverage fixtures (NET-06).
    HTTP_RESPONSE_PORT=$(free_port)
    HTTP_XMLBODY_PORT=$(free_port)
    HTTP_BINARY_PORT=$(free_port)
    # 18.1-03: HTTP source-auth (Basic/Digest) fixtures (D-07/NET-06).
    HTTP_AUTH_BASIC_PORT=$(free_port)
    HTTP_AUTH_DIGEST_PORT=$(free_port)
    # 18.1-04: recording HTTP stub target port for the sender-params/sender-timeout
    # fixtures (D-02/D-06/D-09/NET-06). Allocated script-side (envsubst needs it at
    # fixture-import time); bound driver-side by RecordingHttpStub in plan 18.1-05.
    HTTP_STUB_PORT=$(free_port)
    # 18.2: Jetty regression fixtures (NET-07).
    HTTP_CTXPATH_PORT=$(free_port)
    HTTP_LARGE_PORT=$(free_port)
    HTTP_ERROR500_PORT=$(free_port)
    # SOAP_URL is derived, not a raw port — the Web Service Sender fixture (soap-test.xml)
    # substitutes ${SOAP_URL} directly (D-07: Endpoint.publish stub target).
    SOAP_URL="http://127.0.0.1:${SOAP_PORT}/smoketest"
    export HTTP_PORT HTTPS_PORT MLLP_PORT HTTP_LISTENER_PORT SMTP_PORT SCP_PORT SOAP_PORT SOAP_URL \
        HTTP_RESPONSE_PORT HTTP_XMLBODY_PORT HTTP_BINARY_PORT HTTP_AUTH_BASIC_PORT HTTP_AUTH_DIGEST_PORT \
        HTTP_STUB_PORT HTTP_CTXPATH_PORT HTTP_LARGE_PORT HTTP_ERROR500_PORT \
        DICOM_LISTENER_PORT DICOM_ROUNDTRIP_SCP_PORT \
        DICOM_TLS_AES_LISTENER_PORT DICOM_TLS_AES_SCP_PORT DICOM_TLS_3DES_LISTENER_PORT DICOM_TLS_3DES_SCP_PORT
    pass "Ports allocated: HTTP=${HTTP_PORT} HTTPS=${HTTPS_PORT} MLLP=${MLLP_PORT} HTTP_LISTENER=${HTTP_LISTENER_PORT} SMTP=${SMTP_PORT} SCP=${SCP_PORT} SOAP=${SOAP_PORT} (SOAP_URL=${SOAP_URL}) HTTP_RESPONSE=${HTTP_RESPONSE_PORT} HTTP_XMLBODY=${HTTP_XMLBODY_PORT} HTTP_BINARY=${HTTP_BINARY_PORT} HTTP_AUTH_BASIC=${HTTP_AUTH_BASIC_PORT} HTTP_AUTH_DIGEST=${HTTP_AUTH_DIGEST_PORT} HTTP_STUB=${HTTP_STUB_PORT} HTTP_CTXPATH=${HTTP_CTXPATH_PORT} HTTP_LARGE=${HTTP_LARGE_PORT} HTTP_ERROR500=${HTTP_ERROR500_PORT} DICOM_LISTENER=${DICOM_LISTENER_PORT} DICOM_ROUNDTRIP_SCP=${DICOM_ROUNDTRIP_SCP_PORT} DICOM_TLS_AES_LISTENER=${DICOM_TLS_AES_LISTENER_PORT} DICOM_TLS_AES_SCP=${DICOM_TLS_AES_SCP_PORT} DICOM_TLS_3DES_LISTENER=${DICOM_TLS_3DES_LISTENER_PORT} DICOM_TLS_3DES_SCP=${DICOM_TLS_3DES_SCP_PORT}"
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
             "${OUT_DIR}/js" "${OUT_DIR}/doc" \
             "${OUT_DIR}/http-response" "${OUT_DIR}/http-xmlbody" "${OUT_DIR}/http-binary" \
             "${OUT_DIR}/http-auth-basic" "${OUT_DIR}/http-auth-digest" \
             "${OUT_DIR}/http-ctxpath"

    # 18.2: pre-create the IRT-828 FILE static resource (NET-07). The server JVM reads
    # this path directly at deploy/request time — no container/bind-mount in this harness
    # (Pitfall 4). Content is irrelevant; size (102400 bytes) is what plan 18.2-03 asserts.
    STATIC_FILE_PATH="${CHANNEL_WORK_DIR}/static/large-file.bin"
    mkdir -p "$(dirname "${STATIC_FILE_PATH}")"
    head -c 102400 /dev/zero > "${STATIC_FILE_PATH}"
    export STATIC_FILE_PATH

    export IN_DIR OUT_DIR SQLITE_PATH
    pass "Channel work dir allocated: ${CHANNEL_WORK_DIR} (IN_DIR/OUT_DIR/SQLITE_PATH exported, STATIC_FILE_PATH=${STATIC_FILE_PATH})"
}

# ---------------------------------------------------------------------------
# Stage: generate_dicom_tls_keystore — 18.4-03 (NET-09, D-02 corrected): mints ONE
# shared self-signed PKCS12 keystore/truststore per run via keytool (there is no
# pre-existing keytool machinery in this harness to reuse — the server's own
# "keystore per run" is its in-process BC JCEKS admin-HTTPS cert, a different
# mechanism entirely). The same file is used as BOTH keyStore and trustStore on
# every TLS peer (Listener, Sender, SCU driver, SCP stub). Lives under
# CHANNEL_WORK_DIR so the existing cleanup() rm -rf tears it down with everything
# else. PKCS12 requires the store password and key password to match (JDK 9+
# keytool constraint) — never pass a separate key-password flag (Pitfall 5).
# ---------------------------------------------------------------------------
generate_dicom_tls_keystore() {
    hr
    info "Generating shared DICOM TLS PKCS12 keystore/truststore..."
    DICOM_TLS_KEYSTORE="${CHANNEL_WORK_DIR}/dicom-tls-shared.p12"
    DICOM_TLS_KEYSTORE_PW="smoketest-$(date +%s)"

    keytool -genkeypair \
        -alias dicom-tls-smoke \
        -keyalg RSA -keysize 2048 \
        -validity 7 \
        -dname "CN=dicom-tls-smoke,O=BridgeLink Smoke Harness" \
        -keystore "${DICOM_TLS_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${DICOM_TLS_KEYSTORE_PW}" \
        > /dev/null 2>&1

    export DICOM_TLS_KEYSTORE DICOM_TLS_KEYSTORE_PW
    pass "Generated shared PKCS12 keystore: ${DICOM_TLS_KEYSTORE}"
}

# ---------------------------------------------------------------------------
# Stage: generate_sftp_fixtures — 25.1-02 (SC-2, IRT-1541): mints a pinned modern
# OpenSSH (atmoz/sftp) container plus a throwaway ed25519 client keypair and a
# runtime-captured known_hosts fixture, so the File connector's jsch 2.28.5 SFTP
# transport can be exercised end-to-end for password auth, key auth, and
# known-hosts host-key verification (D-06). Modeled on generate_dicom_tls_keystore()
# above: all generated file material lives under CHANNEL_WORK_DIR so the existing
# cleanup() rm -rf tears it down; the container itself needs its own explicit
# `docker rm -f` in cleanup() (a container is not a filesystem path).
# ---------------------------------------------------------------------------
SFTP_CONTAINER_NAME=""
SFTP_IMAGE_TAG="atmoz/sftp:alpine"

generate_sftp_fixtures() {
    hr
    info "Generating modern SFTP fixtures (container + keypair + known_hosts)..."

    # Docker-availability preflight (RESEARCH Environment Availability / T-25.1-02c):
    # fail fast with an actionable message instead of a cryptic mid-run failure.
    if ! docker info > /dev/null 2>&1; then
        fatal "Docker is not available (docker info failed). The modern-SFTP smoke leg (SC-2) requires a running Docker daemon:
  - macOS: open -a Docker (Docker Desktop) and wait for it to finish starting
  - Linux: sudo systemctl start docker
Re-run smoke-tests/run-smoke-test.sh once Docker is available."
    fi

    SFTP_MODERN_PORT=$(free_port)

    local sftp_work_dir="${CHANNEL_WORK_DIR}/sftp"
    SFTP_UPLOAD_DIR="${sftp_work_dir}/upload"
    mkdir -p "${SFTP_UPLOAD_DIR}"
    # atmoz/sftp's create-sftp-user only chown's a dir it creates itself; a bind-mounted
    # dir already exists, so it is skipped and never chowned to the container's "smoke"
    # user. This is a throwaway per-run work dir (CHANNEL_WORK_DIR, never a committed
    # fixture), so world-writable is an acceptable trade to keep both host and container
    # UIDs able to read/write it without pre-computing a matching numeric UID.
    chmod 777 "${SFTP_UPLOAD_DIR}"

    # Throwaway client keypair — never committed (T-25.1-02b), lives under
    # CHANNEL_WORK_DIR and is torn down by cleanup()'s existing rm -rf.
    SFTP_KEY_PATH="${sftp_work_dir}/id_ed25519"
    ssh-keygen -t ed25519 -N '' -f "${SFTP_KEY_PATH}" -C "smoke-harness" > /dev/null

    # Pinned tag (never :latest, T-25.1-SC) — resolve and record the concrete digest at
    # execution time (rather than hardcoding one), so the pin is visible in run output
    # without needing to bump the script every time the upstream image is rebuilt.
    docker pull "${SFTP_IMAGE_TAG}" > /dev/null
    local sftp_image_digest
    sftp_image_digest=$(docker inspect --format '{{index .RepoDigests 0}}' "${SFTP_IMAGE_TAG}" 2>/dev/null || echo "unknown")
    info "Modern SFTP image: ${SFTP_IMAGE_TAG} (${sftp_image_digest})"

    SFTP_CONTAINER_NAME="smoke-sftp-modern-$$"
    docker run -d \
        --name "${SFTP_CONTAINER_NAME}" \
        -p "127.0.0.1:${SFTP_MODERN_PORT}:22" \
        -v "${SFTP_UPLOAD_DIR}:/home/smoke/upload" \
        -v "${SFTP_KEY_PATH}.pub:/home/smoke/.ssh/keys/id_ed25519.pub:ro" \
        "${SFTP_IMAGE_TAG}" \
        smoke:smokepass:::upload \
        > /dev/null

    # Capture the container's host key into a runtime known_hosts fixture once sshd is
    # accepting connections — ssh-keyscan itself doubles as the readiness probe here
    # (retried on a poll loop, Pitfall 7: no fixed sleep before the first attempt).
    SFTP_KNOWN_HOSTS_PATH="${sftp_work_dir}/known_hosts"
    local attempts=0
    while [[ ${attempts} -lt 30 ]]; do
        if ssh-keyscan -p "${SFTP_MODERN_PORT}" -T 3 127.0.0.1 > "${SFTP_KNOWN_HOSTS_PATH}" 2>/dev/null \
                && [[ -s "${SFTP_KNOWN_HOSTS_PATH}" ]]; then
            break
        fi
        sleep 2
        attempts=$((attempts + 1))
    done
    if [[ ! -s "${SFTP_KNOWN_HOSTS_PATH}" ]]; then
        fatal "Modern SFTP container did not accept connections within 60s (ssh-keyscan never produced a host key)"
    fi

    export SFTP_MODERN_PORT SFTP_KEY_PATH SFTP_KNOWN_HOSTS_PATH SFTP_UPLOAD_DIR
    pass "Modern SFTP fixtures ready: container=${SFTP_CONTAINER_NAME} port=${SFTP_MODERN_PORT} keyPath=${SFTP_KEY_PATH} knownHosts=${SFTP_KNOWN_HOSTS_PATH} uploadDir=${SFTP_UPLOAD_DIR}"
}

# ---------------------------------------------------------------------------
# Stage: check_sftp_legacy_fixture — 25.1-03 (SC-3, IRT-1541): conditional legacy-algorithm
# leg, gated on SFTP_LEGACY_PORT being pre-exported by an EXTERNAL driver
# (regression-scripts/test-irt1541-jsch-sftp-upgrade.sh) BEFORE this script is invoked. This
# harness does NOT boot the legacy server itself — that lives in the driver's own
# docker-compose.test-irt1541.yml, since the legacy server is deliberately weak (algorithm
# negotiation should FAIL against it by default) and standing it up unconditionally inside
# every ordinary harness run would be a needless Docker/CI cost for a leg only the
# break-then-fix driver ever exercises. Unset (the normal case) => the two legacy channel
# fixtures are never added to CHANNEL_FILES/CHANNEL_IDS below, and SftpParamsTest's two
# legacy @Test methods self-skip via Assume.assumeTrue — a plain run-smoke-test.sh is
# completely unaffected.
# ---------------------------------------------------------------------------
SFTP_LEGACY_LEG_ACTIVE=0
check_sftp_legacy_fixture() {
    hr
    if [[ -n "${SFTP_LEGACY_PORT:-}" ]]; then
        SFTP_LEGACY_LEG_ACTIVE=1
        info "SFTP_LEGACY_PORT=${SFTP_LEGACY_PORT} detected (external driver) — legacy-algorithm fixtures ACTIVE"
    else
        info "SFTP_LEGACY_PORT not set — legacy-algorithm fixtures SKIPPED (only exercised via regression-scripts/test-irt1541-jsch-sftp-upgrade.sh)"
    fi
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
            -Djava.security.properties="${SCRIPT_DIR}/fixtures/dicom-tls-3des.security" \
            --add-modules=java.sql.rowset \
            --add-exports=java.base/com.sun.crypto.provider=ALL-UNNAMED \
            --add-exports=java.base/sun.security.provider=ALL-UNNAMED \
            --add-opens=java.base/java.lang=ALL-UNNAMED \
            --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
            --add-opens=java.base/java.math=ALL-UNNAMED \
            --add-opens=java.base/java.net=ALL-UNNAMED \
            --add-opens=java.base/java.security=ALL-UNNAMED \
            --add-opens=java.base/java.security.cert=ALL-UNNAMED \
            --add-opens=java.sql/java.sql=ALL-UNNAMED \
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
CHANNEL_FILES=(http-test tcp-mllp-test file-test jdbc-test vm-test js-test smtp-test soap-test dicom-test doc-writer-test legacy-migration-test legacy-migration-3-4-test http-listener-response-test http-datatype-xml-test http-datatype-binary-recv-test http-listener-auth-basic-test http-listener-auth-digest-test http-sender-params-test http-sender-timeout-test http-datatype-binary-send-test http-listener-contextpath-test http-listener-largeresp-test http-listener-error500-test dicom-roundtrip-test dicom-tls-aes-roundtrip-test dicom-tls-3des-roundtrip-test file-sftp-modern-test file-sftp-keyauth-test file-sftp-knownhosts-test)
# 25.1-03 (SC-3, IRT-1541): the two legacy-algorithm fixtures are appended ONLY when
# SFTP_LEGACY_PORT is pre-exported by the external break-then-fix driver — an ordinary
# run-smoke-test.sh invocation has no legacy server to dial, so these must stay out of the
# unconditional array (unlike every fixture above) or import_deploy()'s STARTED-state poll
# and wait_for_started() would have no target to reach for them.
if [[ -n "${SFTP_LEGACY_PORT:-}" ]]; then
    CHANNEL_FILES+=(file-sftp-legacy-negative-test file-sftp-legacy-workaround-test)
fi
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
    "00000011-0000-0000-0000-000000000011"
    "00000012-0000-0000-0000-000000000012"
    "00000013-0000-0000-0000-000000000013"
    "00000014-0000-0000-0000-000000000014"
    "00000015-0000-0000-0000-000000000015"
    "00000016-0000-0000-0000-000000000016"
    "00000017-0000-0000-0000-000000000017"
    "00000018-0000-0000-0000-000000000018"
    "00000019-0000-0000-0000-000000000019"
    "00000020-0000-0000-0000-000000000020"
    "00000021-0000-0000-0000-000000000021"
    "00000022-0000-0000-0000-000000000022"
    "00000023-0000-0000-0000-000000000023"
    "00000024-0000-0000-0000-000000000024"
    "00000025-0000-0000-0000-000000000025"
    "00000026-0000-0000-0000-000000000026"
    "00000027-0000-0000-0000-000000000027"
    "00000028-0000-0000-0000-000000000028"
    "00000029-0000-0000-0000-000000000029"
)
if [[ -n "${SFTP_LEGACY_PORT:-}" ]]; then
    CHANNEL_IDS+=(
        "00000030-0000-0000-0000-000000000030"
        "00000031-0000-0000-0000-000000000031"
    )
fi
# Explicit envsubst allowlist — exactly the ${VARNAME} placeholders the committed
# fixtures use. ${DICOMMESSAGE} is a Mirth-internal template variable resolved by
# the server itself and MUST NOT appear here (envsubst would blank it out).
# 18.1-02 adds HTTP_RESPONSE_PORT/HTTP_XMLBODY_PORT/HTTP_BINARY_PORT (NET-06 fixtures).
# 18.1-03 adds HTTP_AUTH_BASIC_PORT/HTTP_AUTH_DIGEST_PORT (D-07 auth fixtures). The
# Digest fixture's literal <opaque>smokeopaque</opaque> value deliberately contains no
# ${...} token, so ${UUID} (the DigestHttpAuthProperties class default) never appears
# in committed fixture XML and never needs (or risks) allowlisting (Pitfall 6).
# 18.1-04 adds HTTP_STUB_PORT (sender-params/sender-timeout fixtures target the
# recording HTTP stub's /auth and /stall contexts, D-02/D-06/D-09/NET-06).
# 18.2 adds HTTP_CTXPATH_PORT/HTTP_LARGE_PORT/HTTP_ERROR500_PORT (Jetty regression
# fixtures 00000021-23, NET-07) and STATIC_FILE_PATH (the IRT-828 FILE static resource
# appended to http-listener-response-test.xml, pre-created by allocate_work_dirs()).
# 18.3-01 adds DICOM_LISTENER_PORT/DICOM_ROUNDTRIP_SCP_PORT (dicom-roundtrip-test.xml,
# NET-08 round-trip fixture — channel 00000024).
# 18.4-03 adds DICOM_TLS_KEYSTORE/DICOM_TLS_KEYSTORE_PW (the shared PKCS12 keystore from
# generate_dicom_tls_keystore()) and DICOM_TLS_AES_LISTENER_PORT/DICOM_TLS_AES_SCP_PORT/
# DICOM_TLS_3DES_LISTENER_PORT/DICOM_TLS_3DES_SCP_PORT (dicom-tls-aes-roundtrip-test.xml/
# dicom-tls-3des-roundtrip-test.xml, NET-09 mutual-TLS round-trip fixtures — channels
# 00000025/00000026).
# 25.1-02 adds SFTP_MODERN_PORT/SFTP_KEY_PATH/SFTP_KNOWN_HOSTS_PATH/SFTP_UPLOAD_DIR (the
# modern SFTP fixtures from generate_sftp_fixtures(): pinned atmoz/sftp container port,
# throwaway client key path, runtime-captured known_hosts path, host-side upload dir) for
# file-sftp-modern-test.xml/file-sftp-keyauth-test.xml/file-sftp-knownhosts-test.xml
# (SC-2, channels 00000027/00000028/00000029). Only SFTP_MODERN_PORT/SFTP_KEY_PATH/
# SFTP_KNOWN_HOSTS_PATH currently appear inside those fixtures' XML text — SFTP_UPLOAD_DIR
# is allowlisted for parity/future fixtures but envsubst is a no-op for names absent from
# the source file, so listing it here is harmless.
# 25.1-03 adds SFTP_LEGACY_PORT (SC-3, IRT-1541): the external break-then-fix driver's legacy
# atmoz/sftp server port, referenced by file-sftp-legacy-negative-test.xml/
# file-sftp-legacy-workaround-test.xml (channels 00000030/00000031). Harmless (envsubst no-op)
# in an ordinary run where these two fixtures are never added to CHANNEL_FILES.
ENVSUBST_ALLOWLIST='${HTTP_LISTENER_PORT} ${MLLP_PORT} ${SMTP_PORT} ${SCP_PORT} ${SOAP_URL} ${SQLITE_PATH} ${IN_DIR} ${OUT_DIR} ${HTTP_RESPONSE_PORT} ${HTTP_XMLBODY_PORT} ${HTTP_BINARY_PORT} ${HTTP_AUTH_BASIC_PORT} ${HTTP_AUTH_DIGEST_PORT} ${HTTP_STUB_PORT} ${HTTP_CTXPATH_PORT} ${HTTP_LARGE_PORT} ${HTTP_ERROR500_PORT} ${STATIC_FILE_PATH} ${DICOM_LISTENER_PORT} ${DICOM_ROUNDTRIP_SCP_PORT} ${DICOM_TLS_KEYSTORE} ${DICOM_TLS_KEYSTORE_PW} ${DICOM_TLS_AES_LISTENER_PORT} ${DICOM_TLS_AES_SCP_PORT} ${DICOM_TLS_3DES_LISTENER_PORT} ${DICOM_TLS_3DES_SCP_PORT} ${SFTP_MODERN_PORT} ${SFTP_KEY_PATH} ${SFTP_KNOWN_HOSTS_PATH} ${SFTP_UPLOAD_DIR} ${SFTP_LEGACY_PORT}'

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

        # legacy-migration-* fixtures (legacy-migration-test.xml, schema 3.6.0, plan 18-09;
        # legacy-migration-3-4-test.xml, schema 3.4.0 channel root, plan 18-10) are
        # DELIBERATELY excluded from both the envsubst pass (neither uses allowlisted
        # placeholders) and the version-rewrite sed below (NET-05/SC-4). Each fixture's
        # entire value is being a genuinely old-schema export -- rewriting either one's
        # version attributes to the live server version would silently re-open the NET-05
        # break-proof hole, since every OTHER fixture is pinned to the current schema
        # version and never exercises Channel.migrateX()/MigratableConverter/
        # MirthDomReader. The 3-4 variant specifically drives Channel.migrate3_5_0(), the
        # NET-05/SC-4 regression seam. Straight-copy only.
        if [[ "${name}" == legacy-migration-* ]]; then
            cp "${src}" "${dst}"
            continue
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
#
# 25.1-03 (SC-3, IRT-1541): channel 00000030 (file-sftp-legacy-negative-test.xml) is
# DESIGNED to fail its onStart() connection attempt (D-07's falsifiable negative proof —
# FileReceiver.onStart() eagerly opens a connection at deploy time, throwing
# JSchAlgoNegoFailException synchronously, not merely logging a poll-time warning).
# EngineServlet.deployChannels(returnErrors=true) aggregates ALL channels' task results via
# ErrorTaskHandler.isErrored() and throws for the ENTIRE batch request if even ONE channel
# errors — even though every OTHER channel's deploy task still completes normally in the
# same batch (deploy tasks run independently; only the aggregated HTTP response is
# affected). Deploying 00000030 in its OWN separate returnErrors=false call keeps its
# expected failure from being misreported as a harness-wide FATAL.
deploy_channels() {
    hr
    info "Deploying all ${#CHANNEL_IDS[@]} channels..."
    local set_body id http_code body
    local legacy_negative_id=""
    local deploy_ids=()
    for id in "${CHANNEL_IDS[@]}"; do
        if [[ "${id}" == "00000030-0000-0000-0000-000000000030" ]]; then
            legacy_negative_id="${id}"
        else
            deploy_ids+=("${id}")
        fi
    done

    set_body="<set>"
    for id in "${deploy_ids[@]}"; do
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
    pass "Deploy request accepted (HTTP ${http_code}) for ${#deploy_ids[@]} channels"

    if [[ -n "${legacy_negative_id}" ]]; then
        info "Deploying legacy-negative fixture (${legacy_negative_id}) separately with returnErrors=false — its connection failure is EXPECTED (D-07)."
        body=$(mktemp)
        http_code=$(curl -s -k -b "${COOKIE_JAR}" \
            -X POST "${API}/channels/_deploy?returnErrors=false" \
            -H "X-Requested-With: OpenAPI" \
            -H "Content-Type: application/xml" \
            -d "<set><string>${legacy_negative_id}</string></set>" \
            -o "${body}" -w "%{http_code}")
        rm -f "${body}"
        if [[ "${http_code}" != "200" && "${http_code}" != "204" ]]; then
            echo "SMOKE-FAILURE-CLASS: import"
            fatal "Legacy-negative fixture deploy REQUEST itself failed unexpectedly (HTTP ${http_code}; expected 200/204 even though the underlying SFTP connection attempt fails)"
        fi
        pass "Legacy-negative fixture deploy request accepted (HTTP ${http_code}) — connection failure expected on start"
    fi
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

        # 25.1-03 (SC-3, IRT-1541): channel 00000030 is DESIGNED to never reach STARTED —
        # its onStart() connection attempt fails by design (D-07 negative proof). Waiting
        # 60s for a state it will never reach would just waste harness budget every run;
        # SftpParamsTest's legacyDefaultFailsAlgoNego @Test is the actual assertion for
        # this channel's behavior, not this generic STARTED-state poll.
        if [[ "${id}" == "00000030-0000-0000-0000-000000000030" ]]; then
            info "  Skipping STARTED-wait for ${name} (${id}) — expected to never start (D-07 negative leg)"
            continue
        fi

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

    # 18.1-02: probe HTTP_RESPONSE_PORT only (RESEARCH Pitfall 5/11 — this probe runs
    # before clear_statistics() so its ERROR-status parse artifact is wiped; a bare GET
    # against the response-cluster fixture hits the parse-ERROR path which hardcodes a
    # 500 response (HttpReceiver.sendErrorResponse), so accept ANY response code here,
    # never assert 202 on a probe). HTTP_XMLBODY_PORT and HTTP_BINARY_PORT are
    # DELIBERATELY NOT probed: an empty probe message would process successfully on
    # those two channels (XML/RAW datatypes don't reject empty content the way HL7v2
    # does) and WRITE a destination artifact, corrupting the L2 file assertions the
    # driver depends on. Their deploy health is already covered by wait_for_started()'s
    # STARTED-state poll above.
    attempts=0
    info "  HTTP Response Listener (port ${HTTP_RESPONSE_PORT})..."
    while [[ ${attempts} -lt 20 ]]; do
        code=$(curl -s --max-time 3 --connect-timeout 2 -o /dev/null -w "%{http_code}" \
            "http://127.0.0.1:${HTTP_RESPONSE_PORT}/" 2>/dev/null || echo "000")
        if [[ "${code}" != "000" ]]; then
            pass "  HTTP Response Listener port ${HTTP_RESPONSE_PORT} responding (HTTP ${code})"
            break
        fi
        sleep 3
        attempts=$((attempts + 1))
    done
    if [[ "${code}" == "000" ]]; then
        fatal "HTTP Response Listener port ${HTTP_RESPONSE_PORT} did not respond within 60s"
    fi

    # 18.1-03: probe both auth listener ports. Auth listeners are probe-SAFE
    # (RESEARCH — Pitfall 5 update): the Jetty 12 EE8 ConstraintSecurityHandler
    # rejects an unauthenticated request with 401 BEFORE any message is dispatched to
    # the channel, so a bare unauthenticated probe creates no message and needs no
    # clear_statistics() cleanup. Treat any non-"000" response (typically 401) as "port
    # is up" — never assert a specific status code here.
    attempts=0
    info "  HTTP Auth Basic Listener (port ${HTTP_AUTH_BASIC_PORT})..."
    while [[ ${attempts} -lt 20 ]]; do
        code=$(curl -s --max-time 3 --connect-timeout 2 -o /dev/null -w "%{http_code}" \
            "http://127.0.0.1:${HTTP_AUTH_BASIC_PORT}/" 2>/dev/null || echo "000")
        if [[ "${code}" != "000" ]]; then
            pass "  HTTP Auth Basic Listener port ${HTTP_AUTH_BASIC_PORT} responding (HTTP ${code})"
            break
        fi
        sleep 3
        attempts=$((attempts + 1))
    done
    if [[ "${code}" == "000" ]]; then
        fatal "HTTP Auth Basic Listener port ${HTTP_AUTH_BASIC_PORT} did not respond within 60s"
    fi

    attempts=0
    info "  HTTP Auth Digest Listener (port ${HTTP_AUTH_DIGEST_PORT})..."
    while [[ ${attempts} -lt 20 ]]; do
        code=$(curl -s --max-time 3 --connect-timeout 2 -o /dev/null -w "%{http_code}" \
            "http://127.0.0.1:${HTTP_AUTH_DIGEST_PORT}/" 2>/dev/null || echo "000")
        if [[ "${code}" != "000" ]]; then
            pass "  HTTP Auth Digest Listener port ${HTTP_AUTH_DIGEST_PORT} responding (HTTP ${code})"
            break
        fi
        sleep 3
        attempts=$((attempts + 1))
    done
    if [[ "${code}" == "000" ]]; then
        fatal "HTTP Auth Digest Listener port ${HTTP_AUTH_DIGEST_PORT} did not respond within 60s"
    fi

    # 18.2: HTTP_CTXPATH_PORT, HTTP_LARGE_PORT, and HTTP_ERROR500_PORT must NOT be
    # probed with an HTTP request — a bare GET on any of the three Jetty-regression
    # fixtures (00000021/22/23) creates a real message / writes a destination
    # artifact, corrupting the L1/L2 assertions plan 18.2-03's driver depends on.
    # A bare TCP connect-then-close (the MLLP_PORT pattern above) IS side-effect-free
    # for all three: no HTTP request is parsed, so no message is created and no
    # statistics/artifacts are skewed. JettyRegressionTest dials these ports one-shot
    # with no connect-retry, so probe here to close the STARTED-vs-socket-bound race.
    local p
    for p in "${HTTP_CTXPATH_PORT}" "${HTTP_LARGE_PORT}" "${HTTP_ERROR500_PORT}"; do
        attempts=0
        info "  Jetty-regression listener (port ${p})..."
        while [[ ${attempts} -lt 20 ]]; do
            if python3 -c "
import socket, sys
s = socket.socket()
s.settimeout(2)
try:
    s.connect(('127.0.0.1', ${p}))
    s.close()
    sys.exit(0)
except Exception:
    sys.exit(1)
" 2>/dev/null; then
                pass "  Jetty-regression listener port ${p} accepting connections"
                break
            fi
            sleep 3
            attempts=$((attempts + 1))
        done
        if [[ ${attempts} -ge 20 ]]; then
            fatal "Jetty-regression listener port ${p} did not accept connections within 60s"
        fi
    done

    # 18.3-01: DICOM Listener readiness probe (NET-08, dicom-roundtrip-test.xml, channel
    # 00000024). Modeled EXACTLY on the MLLP bare-TCP-connect probe above (socket.connect
    # + immediate close, no bytes sent) — NOT the HTTP probe. A bare connect+close never
    # reaches DICOMReceiver/MirthDcmRcv.onCStoreRQ() (that requires a full A-ASSOCIATE-RQ
    # handshake), so it delivers no C-STORE and creates no spurious message.
    attempts=0
    info "  DICOM Listener (port ${DICOM_LISTENER_PORT})..."
    while [[ ${attempts} -lt 20 ]]; do
        if python3 -c "
import socket, sys
s = socket.socket()
s.settimeout(2)
try:
    s.connect(('127.0.0.1', ${DICOM_LISTENER_PORT}))
    s.close()
    sys.exit(0)
except Exception:
    sys.exit(1)
" 2>/dev/null; then
            pass "  DICOM Listener port ${DICOM_LISTENER_PORT} accepting connections"
            break
        fi
        sleep 3
        attempts=$((attempts + 1))
    done
    if [[ ${attempts} -ge 20 ]]; then
        fatal "DICOM Listener port ${DICOM_LISTENER_PORT} did not accept connections within 60s"
    fi

    # 18.4-03: DICOM TLS Listener readiness probes (NET-09, dicom-tls-aes-roundtrip-test.xml/
    # dicom-tls-3des-roundtrip-test.xml, channels 00000025/00000026). Same bare-TCP-connect
    # pattern as the plaintext DICOM probe above — these ports are TLS-only, so a bare TCP
    # connect+close confirms the listener bound WITHOUT attempting a plaintext DICOM
    # association (that would just fail the TLS handshake, which is fine, but sending any
    # bytes here is unnecessary and the plain connect/close is sufficient and side-effect-free).
    for p in "${DICOM_TLS_AES_LISTENER_PORT}" "${DICOM_TLS_3DES_LISTENER_PORT}"; do
        attempts=0
        info "  DICOM TLS Listener (port ${p})..."
        while [[ ${attempts} -lt 20 ]]; do
            if python3 -c "
import socket, sys
s = socket.socket()
s.settimeout(2)
try:
    s.connect(('127.0.0.1', ${p}))
    s.close()
    sys.exit(0)
except Exception:
    sys.exit(1)
" 2>/dev/null; then
                pass "  DICOM TLS Listener port ${p} accepting connections"
                break
            fi
            sleep 3
            attempts=$((attempts + 1))
        done
        if [[ ${attempts} -ge 20 ]]; then
            fatal "DICOM TLS Listener port ${p} did not accept connections within 60s"
        fi
    done
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
    clear_statistics

    pass "Import/deploy stage complete: all ${#CHANNEL_IDS[@]} channels STARTED"
}

# Rule 1 fix (plan 18-07): wait_for_listener_ports() above sends a real plain HTTP GET
# straight at the http-test channel's own listener port (its readiness probe) — Mirth's HTTP
# Receiver treats ANY request landing on that port as a message to process, and an empty GET
# body fails HL7v2 parsing, silently recording one ERROR-status message before the JUnit
# driver ever pumps anything. Clearing statistics for every deployed channel here — right
# after import/deploy finishes, right before the driver's L1 (zero-ERROR) assertions run —
# gives 18-07's per-channel three-level assertions a clean slate that reflects only the
# driver's own pumped messages, not this stage's own infrastructure side effect.
clear_statistics() {
    hr
    info "Clearing channel statistics (removes the wait_for_listener_ports() HTTP readiness-probe artifact before the driver runs)..."
    local http_code body
    body=$(mktemp)
    http_code=$(curl -s -k -b "${COOKIE_JAR}" \
        -X POST "${API}/channels/_clearAllStatistics" \
        -H "X-Requested-With: OpenAPI" \
        -o "${body}" -w "%{http_code}")
    if [[ "${http_code}" != "200" && "${http_code}" != "204" ]]; then
        cat "${body}"; rm -f "${body}"
        fatal "Clearing channel statistics failed (HTTP ${http_code})"
    fi
    rm -f "${body}"
    pass "Channel statistics cleared"
}

# ---------------------------------------------------------------------------
# Stage: driver — JUnit pump/assert driver (plan 18-06/18-07, D-02/D-08).
#
# `ant -f smoke-tests/build.xml test-run` is invoked with every harness port/path as a `-D`
# Ant property; build.xml's test-run target forwards each as a JVM sysproperty to the forked
# JUnit process, which SmokeTestBase reads (NativePumpChannelsTest/StubChannelsTest). The ant
# invocation is guarded by an `if` so `set -e` does not abort the script on a driver failure —
# we need to print the SMOKE-FAILURE-CLASS marker and junit summary before failing loudly.
# ---------------------------------------------------------------------------
run_driver() {
    hr
    info "Running JUnit pump/assert driver (ant -f smoke-tests/build.xml test-run)..."

    local driver_log
    driver_log="$(mktemp)"

    if ant -f "${SCRIPT_DIR}/build.xml" test-run \
        -Dsmoke.setup.dir="${SERVER_SETUP}" \
        -DHTTPS_PORT="${HTTPS_PORT}" \
        -DHTTP_LISTENER_PORT="${HTTP_LISTENER_PORT}" \
        -DMLLP_PORT="${MLLP_PORT}" \
        -DSMTP_PORT="${SMTP_PORT}" \
        -DSOAP_URL="${SOAP_URL}" \
        -DSCP_PORT="${SCP_PORT}" \
        -DSQLITE_PATH="${SQLITE_PATH}" \
        -DIN_DIR="${IN_DIR}" \
        -DOUT_DIR="${OUT_DIR}" \
        -DHTTP_RESPONSE_PORT="${HTTP_RESPONSE_PORT}" \
        -DHTTP_XMLBODY_PORT="${HTTP_XMLBODY_PORT}" \
        -DHTTP_BINARY_PORT="${HTTP_BINARY_PORT}" \
        -DHTTP_AUTH_BASIC_PORT="${HTTP_AUTH_BASIC_PORT}" \
        -DHTTP_AUTH_DIGEST_PORT="${HTTP_AUTH_DIGEST_PORT}" \
        -DHTTP_STUB_PORT="${HTTP_STUB_PORT}" \
        -DHTTP_CTXPATH_PORT="${HTTP_CTXPATH_PORT}" \
        -DHTTP_LARGE_PORT="${HTTP_LARGE_PORT}" \
        -DHTTP_ERROR500_PORT="${HTTP_ERROR500_PORT}" \
        -DDICOM_LISTENER_PORT="${DICOM_LISTENER_PORT}" \
        -DDICOM_ROUNDTRIP_SCP_PORT="${DICOM_ROUNDTRIP_SCP_PORT}" \
        -DDICOM_TLS_KEYSTORE="${DICOM_TLS_KEYSTORE}" \
        -DDICOM_TLS_KEYSTORE_PW="${DICOM_TLS_KEYSTORE_PW}" \
        -DDICOM_TLS_AES_LISTENER_PORT="${DICOM_TLS_AES_LISTENER_PORT}" \
        -DDICOM_TLS_AES_SCP_PORT="${DICOM_TLS_AES_SCP_PORT}" \
        -DDICOM_TLS_3DES_LISTENER_PORT="${DICOM_TLS_3DES_LISTENER_PORT}" \
        -DDICOM_TLS_3DES_SCP_PORT="${DICOM_TLS_3DES_SCP_PORT}" \
        -DSFTP_MODERN_PORT="${SFTP_MODERN_PORT}" \
        -DSFTP_KEY_PATH="${SFTP_KEY_PATH}" \
        -DSFTP_KNOWN_HOSTS_PATH="${SFTP_KNOWN_HOSTS_PATH}" \
        -DSFTP_UPLOAD_DIR="${SFTP_UPLOAD_DIR}" \
        -DSFTP_LEGACY_PORT="${SFTP_LEGACY_PORT:-}" \
        -DSFTP_LEGACY_UPLOAD_DIR="${SFTP_LEGACY_UPLOAD_DIR:-}" \
        -DMIRTH_LOG_PATH="${MIRTH_LOG_PATH}" \
        > "${driver_log}" 2>&1; then
        pass "JUnit pump/assert driver passed"
    else
        echo "SMOKE-FAILURE-CLASS: assert"
        info "Driver output (last 150 lines):"
        tail -n 150 "${driver_log}" || true
        if [[ -d "${SCRIPT_DIR}/junit-reports" ]]; then
            info "junit-reports/ summary:"
            grep -h "<testsuite " "${SCRIPT_DIR}/junit-reports"/*.xml 2>/dev/null || true
        fi
        rm -f "${driver_log}"
        fail "JUnit pump/assert driver failed — see junit-reports/ and the output above"
        copy_junit_reports
        return 1
    fi

    rm -f "${driver_log}"
    copy_junit_reports
}

# Copies junit-reports/*.xml alongside mirth.log in smoke-tests/out/ for CI artifact upload
# (18-08). A no-op (with an info line, not a failure) if the driver never produced reports.
copy_junit_reports() {
    if [[ -d "${SCRIPT_DIR}/junit-reports" ]] && compgen -G "${SCRIPT_DIR}/junit-reports/*.xml" > /dev/null; then
        local ts dest
        ts=$(date -u +"%Y%m%dT%H%M%SZ")
        dest="${HARNESS_LOG_DIR}/junit-reports-${ts}"
        mkdir -p "${dest}"
        cp "${SCRIPT_DIR}/junit-reports"/*.xml "${dest}/" 2>/dev/null || true
        info "Copied junit-reports to ${dest}/"
    else
        info "No junit-reports/*.xml found to copy (driver may not have run)"
    fi
}

# ---------------------------------------------------------------------------
# Stage: L3 log scan — mirth.log ERROR-line scan filtered through the allowlist (D-08's third
# assertion level). Comment lines (leading '#') and blank lines in log-allowlist.txt are
# stripped into a temp pattern file before use, so a justification comment can never
# accidentally act as a matching pattern (T-18-16 — grep-gate hygiene).
# ---------------------------------------------------------------------------
scan_mirth_log() {
    hr
    info "L3: scanning mirth.log for ERROR lines not covered by the allowlist..."

    local log_file="${SERVER_SETUP}/logs/mirth.log"
    if [[ ! -f "${log_file}" ]]; then
        info "No mirth.log found at ${log_file} — skipping L3 scan"
        return 0
    fi

    local allowlist_patterns
    allowlist_patterns="$(mktemp)"
    grep -v '^#' "${SCRIPT_DIR}/fixtures/log-allowlist.txt" | grep -v '^[[:space:]]*$' > "${allowlist_patterns}" || true

    local error_lines
    error_lines="$(mktemp)"
    grep -E '^ERROR|ERROR \[' "${log_file}" > "${error_lines}" || true

    local surviving
    if [[ -s "${allowlist_patterns}" ]]; then
        surviving="$(grep -v -f "${allowlist_patterns}" "${error_lines}" || true)"
    else
        surviving="$(cat "${error_lines}")"
    fi

    rm -f "${allowlist_patterns}" "${error_lines}"

    if [[ -n "${surviving}" ]]; then
        echo "SMOKE-FAILURE-CLASS: log"
        echo "--- Unallowlisted ERROR lines in mirth.log ---"
        echo "${surviving}"
        echo "-----------------------------------------------"
        fail "mirth.log contains ERROR lines not covered by fixtures/log-allowlist.txt"
        return 1
    fi

    pass "L3 log scan clean (no unallowlisted ERROR lines in mirth.log)"
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

    # 25.1-02: unconditional, trap-safe removal of the modern SFTP container (T-25.1-02b/SC-2)
    # — a container is not a filesystem path, so it needs its own explicit teardown call
    # separate from the CHANNEL_WORK_DIR rm -rf above.
    if [[ -n "${SFTP_CONTAINER_NAME}" ]]; then
        docker rm -f "${SFTP_CONTAINER_NAME}" > /dev/null 2>&1 || true
        info "Removed modern SFTP container ${SFTP_CONTAINER_NAME}"
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
generate_dicom_tls_keystore
generate_sftp_fixtures
check_sftp_legacy_fixture
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
    exit 0
fi

# Driver (plan 18-06/18-07, D-02/D-08) + L3 log scan (D-08's third assertion level) — both use
# fail() (record-and-continue), not fatal() (abort-immediately), so a failure in either stage
# still lets the other run and lets teardown/duration-reporting happen before the final
# PASS/FAIL banner (cleanup() below checks FAIL_COUNT).
run_driver || true
scan_mirth_log || true

exit 0
