#!/usr/bin/env bash
#
# Runs TagFieldCheck against a JavaFX-bearing JDK. See README.md.
#
# Usage: ./run-tagfield-check.sh [/path/to/jdk-home]
#        Defaults to $JAVA_HOME. The JDK must bundle JavaFX (a "jdk+fx" build); a plain JDK
#        will fail at toolkit startup, which is a broken check, not a failed one.

set -euo pipefail

CHECK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLIENT_DIR="$(cd "${CHECK_DIR}/../.." && pwd)"

JDK_HOME="${1:-${JAVA_HOME:-}}"
if [[ -z "${JDK_HOME}" ]]; then
    echo "error: pass a JDK home as the first argument, or set JAVA_HOME." >&2
    exit 64
fi

# Azul ships both layouts: some bundles are flat, others nest under Contents/Home.
if [[ -x "${JDK_HOME}/Contents/Home/bin/java" ]]; then
    JDK_HOME="${JDK_HOME}/Contents/Home"
fi
if [[ ! -x "${JDK_HOME}/bin/java" ]]; then
    echo "error: no java executable under ${JDK_HOME}" >&2
    exit 64
fi

if [[ ! -d "${CLIENT_DIR}/classes" ]]; then
    echo "error: ${CLIENT_DIR}/classes not found. Build the client first:" >&2
    echo "  cd server && ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true" >&2
    exit 65
fi

CP="${CLIENT_DIR}/classes:${CLIENT_DIR}/lib/*"
OUT="${CHECK_DIR}/out"

echo "--- compiling the check"
rm -rf "${OUT}"
mkdir -p "${OUT}"
"${JDK_HOME}/bin/javac" -nowarn -cp "${CP}" -d "${OUT}" "${CHECK_DIR}/TagFieldCheck.java"

echo "--- running against ${JDK_HOME}"
set +e
"${JDK_HOME}/bin/java" -cp "${OUT}:${CP}" TagFieldCheck
status=$?
set -e

echo "--- exit status ${status}"
exit "${status}"
