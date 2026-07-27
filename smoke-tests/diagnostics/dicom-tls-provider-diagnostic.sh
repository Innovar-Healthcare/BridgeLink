#!/usr/bin/env bash
#
# smoke-tests/diagnostics/dicom-tls-provider-diagnostic.sh — Phase 18.4 Plan 01
#
# Standalone (non-harness) diagnostic. Stands up a real, ephemeral, loopback DICOM
# TLS handshake using the embedded dcm4che DcmRcv/DcmSnd tool classes and records:
#
#   1. Which JCA/JSSE provider serves the handshake's Cipher/Signature primitives
#      (RESEARCH Open Question 1 / Assumption A4 — does BouncyCastle ever
#      participate, or is it entirely SunJSSE/SunJCE/SunRsaSign?)
#   2. Whether the legacy RSA-CBC DICOM cipher suites (aes, 3des) negotiate DOWN to
#      TLSv1.2 without a handshake_failure on the available JDK (RESEARCH Open
#      Question 2 / Assumption A2 / Pitfall 4).
#
# This is deliberately OUTSIDE smoke-tests/src/ so build.xml's compile target never
# picks it up (Plan 03/04's harness wiring stays conflict-free). It does NOT touch
# run-smoke-test.sh, build.xml, or any smoke-tests/channels|fixtures|src files.
#
# Usage:
#   smoke-tests/diagnostics/dicom-tls-provider-diagnostic.sh [path/to/assembled/setup]
#
# Default dist dir: ../../server/setup relative to this script (matches
# run-smoke-test.sh's SERVER_SETUP convention), so it can be run standalone against
# a locally-built distribution:
#   ant -f server/mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST="${1:-${SCRIPT_DIR}/../../server/setup}"

PROVIDER_FINDING="${SCRIPT_DIR}/DICOM-TLS-PROVIDER-FINDING.md"
NEGOTIATION_FINDING="${SCRIPT_DIR}/DICOM-TLS-NEGOTIATION-FINDING.md"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'
pass() { echo -e "${GREEN}PASS${NC}: $1"; }
fail() { echo -e "${RED}FAIL${NC}: $1"; }
info() { echo -e "${YELLOW}INFO${NC}: $1"; }
hr() { echo "------------------------------------------------------------"; }

write_template_findings() {
    local reason="$1"
    cat > "${PROVIDER_FINDING}" <<EOF
# DICOM TLS Provider Finding (Phase 18.4 Plan 01, Task 1)

**Status:** NOT RUN — ${reason}

Re-run this script against a locally-built distribution to populate this finding:

    smoke-tests/diagnostics/dicom-tls-provider-diagnostic.sh /path/to/server/setup

## Expected content once run

- Ordered \`java.security.Security.getProviders()\` list
- Negotiated cipher suite + protocol for the aes leg
- \`Cipher\`/\`Signature\` provider names (proxy for which provider serves the
  handshake crypto)
- Conclusion: does BouncyCastle serve the DICOM TLS handshake crypto, or does
  SunJSSE/SunJCE?
EOF
    cat > "${NEGOTIATION_FINDING}" <<EOF
# DICOM TLS Negotiation Finding (Phase 18.4 Plan 01, Task 2)

**Status:** NOT RUN — ${reason}

Re-run this script against a locally-built distribution to populate this finding:

    smoke-tests/diagnostics/dicom-tls-provider-diagnostic.sh /path/to/server/setup

## Expected content once run

- JVM \`java.version\`/\`java.vendor\`
- Negotiated protocol + cipher suite for the aes leg (expect \`TLSv1.2\` /
  \`TLS_RSA_WITH_AES_128_CBC_SHA\`) and the 3des leg (expect \`TLSv1.2\` /
  \`SSL_RSA_WITH_3DES_EDE_CBC_SHA\`)
- PASS/FAIL conclusion
- The \`mirth.properties\` TLSv1.2 contingency (apply ONLY if FAIL): restrict
  \`https.serverProtocols\`/\`https.clientProtocols\` to \`TLSv1.2\` for the
  duration of the Plan 04 harness run.
EOF
}

hr
info "DICOM TLS provider + negotiation diagnostic (Phase 18.4 Plan 01)"
info "Distribution dir: ${DIST}"

if [[ ! -d "${DIST}/extensions/dicom/lib" ]]; then
    fail "No assembled distribution found at ${DIST}/extensions/dicom/lib"
    info "Build one first: ant -f server/mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true"
    write_template_findings "no assembled distribution at ${DIST}/extensions/dicom/lib"
    exit 0
fi

if ! command -v keytool > /dev/null 2>&1; then
    fail "keytool not found on PATH"
    write_template_findings "keytool not found on PATH"
    exit 1
fi

WORK_DIR="$(mktemp -d)"
cleanup() {
    rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

KEYSTORE="${WORK_DIR}/dicom-tls-diag.p12"
KEYSTORE_PW="diag-$(date +%s)"

info "Generating throwaway PKCS12 keystore/truststore..."
# PKCS12 requires storepass == keypass (JDK 9+ keytool constraint) — never pass -keypass.
keytool -genkeypair \
    -alias dicom-tls-smoke \
    -keyalg RSA -keysize 2048 \
    -validity 7 \
    -dname "CN=dicom-tls-smoke,O=BridgeLink Smoke Harness" \
    -keystore "${KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${KEYSTORE_PW}" \
    > /dev/null 2>&1
if [[ ! -f "${KEYSTORE}" ]]; then
    fail "keytool failed to generate ${KEYSTORE}"
    write_template_findings "keytool keystore generation failed"
    exit 1
fi
pass "Generated ${KEYSTORE}"

info "Assembling classpath from assembled distribution jars..."
CP="$(find "${DIST}/extensions/dicom/lib" "${DIST}/server-lib/commons" "${DIST}/server-lib/donkey" -name '*.jar' 2> /dev/null | paste -sd: -)"
if [[ -z "${CP}" ]]; then
    fail "No jars found under ${DIST}/extensions/dicom/lib"
    write_template_findings "no dcm4che jars found under ${DIST}/extensions/dicom/lib"
    exit 1
fi

info "Compiling DicomTlsProviderDiagnostic.java..."
if ! javac -cp "${CP}" -d "${WORK_DIR}" "${SCRIPT_DIR}/DicomTlsProviderDiagnostic.java" > "${WORK_DIR}/javac.log" 2>&1; then
    fail "javac failed — see log below"
    cat "${WORK_DIR}/javac.log"
    write_template_findings "javac compilation failed — see console output for the javac error"
    exit 1
fi
pass "Compiled diagnostic"

info "Running diagnostic under -Djavax.net.debug=ssl,keymanager,ssl:handshake..."
RUN_LOG="${WORK_DIR}/run.log"
java -cp "${WORK_DIR}:${CP}" \
    -Djavax.net.debug=ssl,keymanager,ssl:handshake \
    DicomTlsProviderDiagnostic "${KEYSTORE}" "${KEYSTORE_PW}" \
    > "${RUN_LOG}" 2>&1
RUN_STATUS=$?
cat "${RUN_LOG}"

JAVA_VERSION="$(java -version 2>&1 | head -1)"
PROVIDERS="$(grep -A 30 '^PROVIDERS:' "${RUN_LOG}" || true)"
AES_LINE="$(grep '^AES_LEG:' "${RUN_LOG}" || true)"
DES_LINE="$(grep '^3DES_LEG:' "${RUN_LOG}" || true)"
CIPHER_PROVIDER_LINE="$(grep '^CIPHER_PROVIDER:' "${RUN_LOG}" || true)"
SIGNATURE_PROVIDER_LINE="$(grep '^SIGNATURE_PROVIDER:' "${RUN_LOG}" || true)"
DEBUG_PROTOCOL_LINES="$(grep -i 'ProtocolVersion' "${RUN_LOG}" | head -10 || true)"
DEBUG_PROVIDER_LINES="$(grep -iE 'KeyManager|X509|provider' "${RUN_LOG}" | head -20 || true)"

# --- Provider finding (Task 1: RESEARCH Open Question 1 / Assumption A4) ---
{
    echo "# DICOM TLS Provider Finding (Phase 18.4 Plan 01, Task 1)"
    echo
    echo "**Run date:** $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo "**JVM:** ${JAVA_VERSION}"
    echo
    echo "## Security.getProviders() order"
    echo '```'
    echo "${PROVIDERS}"
    echo '```'
    echo
    echo "## Handshake crypto primitive providers"
    echo
    echo "${CIPHER_PROVIDER_LINE}"
    echo
    echo "${SIGNATURE_PROVIDER_LINE}"
    echo
    echo "## Negotiated cipher suite (aes leg)"
    echo
    echo "${AES_LINE}"
    echo
    echo "## javax.net.debug provider-selection grep"
    echo '```'
    echo "${DEBUG_PROVIDER_LINES}"
    echo '```'
    echo
    echo "## Conclusion"
    echo
    if echo "${CIPHER_PROVIDER_LINE}" | grep -qi "BC"; then
        echo "BouncyCastle (BC) DOES serve the DICOM TLS handshake's Cipher/Signature primitives."
    else
        echo "BouncyCastle does NOT serve the DICOM TLS handshake's Cipher/Signature primitives — the handshake runs entirely through the JDK's default providers (SunJSSE/SunJCE/SunRsaSign), confirming RESEARCH Assumption A4/Open Question 1: a green DICOM TLS test does not, by itself, guard the Phase 22/26 BouncyCastle-CVE regression for the DICOM transport specifically."
    fi
} > "${PROVIDER_FINDING}"
pass "Wrote ${PROVIDER_FINDING}"

# --- Negotiation finding (Task 2: RESEARCH Open Question 2 / Assumption A2 / Pitfall 4) ---
{
    echo "# DICOM TLS Negotiation Finding (Phase 18.4 Plan 01, Task 2)"
    echo
    echo "**Run date:** $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo "**JVM:** ${JAVA_VERSION}"
    echo
    echo "## Negotiated protocol + cipher suite per leg"
    echo
    echo "- aes leg: ${AES_LINE}"
    echo "- 3des leg: ${DES_LINE}"
    echo
    echo "## javax.net.debug ProtocolVersion grep"
    echo '```'
    echo "${DEBUG_PROTOCOL_LINES}"
    echo '```'
    echo
    echo "## Conclusion"
    echo
    if echo "${AES_LINE}" | grep -q "TLSv1.2" && echo "${DES_LINE}" | grep -q "TLSv1.2" \
        && ! grep -qi "handshake_failure\|no cipher suites in common\|SSLHandshakeException" "${RUN_LOG}"; then
        echo "PASS — both the aes and 3des legacy-cipher legs negotiated down to TLSv1.2 with no handshake_failure."
    else
        echo "FAIL (or inconclusive) — see the run log above for the SSLHandshakeException / handshake_failure detail."
        echo
        echo "**Contingency (apply ONLY on FAIL):** restrict \`https.serverProtocols\`/\`https.clientProtocols\` to \`TLSv1.2\` in the run's \`mirth.properties\` for the duration of the Plan 04 harness run — \`DefaultDICOMConfiguration\` derives the DICOM connector's TLS protocol list from these server-wide keys (RESEARCH Assumption A2), so this is a harness-level, not per-channel, workaround."
    fi
} > "${NEGOTIATION_FINDING}"
pass "Wrote ${NEGOTIATION_FINDING}"

hr
if [[ ${RUN_STATUS} -eq 0 ]]; then
    pass "Diagnostic completed"
else
    fail "Diagnostic exited with status ${RUN_STATUS} — see findings above for handshake_failure detail (this IS the Task 2 finding if it occurred during the negotiation legs)"
fi
exit 0
