#!/bin/sh
# smoke-tests/docs-license-gate.sh
# Phase 26.1 plan 03: PR #185 (IRT-1741) docs/license gate — "in lieu of a
# unit test" (D-03). #185 has NO runtime license Java path (LicenseClient.java
# / LicenseInfo.java are untouched); the real risk is Repudiation: a rebrand
# that silently breaks GitHub/licensee MPL auto-detection or drops the
# attribution NOTICE. This script makes both observable via shell/text +
# build assertions instead.
#
# Checks (run from the repo root):
#   1. Root LICENSE's first non-empty line is exactly the MPL 2.0 header —
#      the licensee/GitHub auto-detection fix (preamble stripped by #185).
#   2. Root NOTICE exists and carries the rebranded product name.
#   3. server/setup/docs/{LICENSE.txt,NOTICE.txt,README.txt} exist — the
#      dist-shipped copies (server/build.xml docs-copy target,
#      server/docs/* -> server/setup/docs/*). These are ONLY regenerated on
#      a fresh dist build (see 26.1-RESEARCH.md "Runtime State Inventory") —
#      run `cd server && ant -f mirth-build.xml -DdisableSigning=true
#      -Dskip.build.tests=true` before this script, or checks 3-4 will
#      correctly fail against a stale/absent server/setup/.
#   4. The shipped server/setup/docs/NOTICE.txt carries the rebranded
#      product name, proving the docs-copy ran against #185 content and not
#      a stale dist.
#
# Every check fails loudly (non-zero exit, one-line diagnostic to stderr) on
# any missing/mismatched item. There is no error-suppressing fallback that
# turns a missing file into a pass — a missing shipped-docs copy means the
# dist has not been (re)built, not that the gate is satisfied.
#
# Usage:
#   docs-license-gate.sh
#
# Exit codes:
#   0 - PASS (all four checks met); prints one VERDICT: PASS line.
#   1 - FAIL (first unmet check); prints one VERDICT: FAIL line to stderr
#       naming the check and the offending item, then stops.

set -eu

REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$REPO_ROOT"

EXPECTED_MPL_HEADER="Mozilla Public License Version 2.0"
REBRAND_MARKER="BridgeLink"

fail() {
  echo "VERDICT: FAIL $1" >&2
  exit 1
}

# --- Check 1: root LICENSE first non-empty line is the MPL header ---
if [ ! -f LICENSE ]; then
  fail "root LICENSE not found (expected at repo root: $REPO_ROOT/LICENSE)"
fi

LICENSE_FIRST_LINE=$(grep -m1 -v '^[[:space:]]*$' LICENSE || true)
if [ "$LICENSE_FIRST_LINE" != "$EXPECTED_MPL_HEADER" ]; then
  fail "root LICENSE first non-empty line is '$LICENSE_FIRST_LINE', expected '$EXPECTED_MPL_HEADER' (MPL auto-detection broken)"
fi

# --- Check 2: root NOTICE exists and carries the rebrand ---
if [ ! -f NOTICE ]; then
  fail "root NOTICE not found — attribution NOTICE missing"
fi

if ! grep -q "$REBRAND_MARKER" NOTICE; then
  fail "root NOTICE exists but does not contain '$REBRAND_MARKER' — attribution content missing/stale"
fi

# --- Check 3: shipped docs exist (dist-build output; fresh build required) ---
for f in LICENSE.txt NOTICE.txt README.txt; do
  if [ ! -f "server/setup/docs/$f" ]; then
    fail "server/setup/docs/$f not found — run a fresh dist build first (cd server && ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true)"
  fi
done

# --- Check 4: shipped NOTICE carries the rebrand (proves docs-copy ran against #185 content, not a stale dist) ---
if ! grep -q "$REBRAND_MARKER" server/setup/docs/NOTICE.txt; then
  fail "server/setup/docs/NOTICE.txt exists but does not contain '$REBRAND_MARKER' — dist is stale, rebuild before gating"
fi

echo "VERDICT: PASS docs-license-gate root LICENSE+NOTICE and shipped server/setup/docs/{LICENSE,NOTICE,README}.txt all present and rebranded"
exit 0
