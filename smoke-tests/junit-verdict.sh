#!/bin/sh
# smoke-tests/junit-verdict.sh
# Phase 26.1 plan 01: per-class JUnit4 XML verdict helper.
#
# Why this exists. `ant test-run` batchtests every **/*Test.class with
# haltonfailure="false" and JUnit's own Assume.assumeXxx() guards report a
# skipped test as part of a "green" run — so the ant exit code alone cannot
# prove a NAMED test actually ran and passed (see 26.1-RESEARCH.md
# "Anti-Patterns to Avoid" / Pitfalls 1-2). The verdict must come from the
# per-class JUnit XML report's <testsuite> attributes, never from ant's
# terminal exit status.
#
# Usage:
#   junit-verdict.sh <report-xml-path> [--no-skip]
#
# Reads the <testsuite tests="N" failures="N" errors="N" skipped="N" ...>
# attributes from the given TEST-<fqcn>.xml report and requires:
#   tests > 0 AND failures == 0 AND errors == 0
# With --no-skip, additionally requires skipped == 0.
#
# Exit codes:
#   0 - PASS (all required conditions met); prints one VERDICT: PASS line.
#   1 - FAIL (missing file, unparsable attrs, or a condition unmet); prints
#       one VERDICT: FAIL line to stderr naming the fqcn and the offending
#       attribute. Never silently passes.

set -eu

usage() {
  echo "Usage: $0 <report-xml-path> [--no-skip]" >&2
}

REPORT="${1:-}"
NO_SKIP=0
if [ "${2:-}" = "--no-skip" ]; then
  NO_SKIP=1
fi

if [ -z "$REPORT" ]; then
  usage
  echo "VERDICT: FAIL <unknown> missing required <report-xml-path> argument" >&2
  exit 1
fi

# Derive the fully-qualified class name from the filename convention
# TEST-<fqcn>.xml, even if the file turns out not to exist -- so a missing-
# file diagnostic still names what was being looked for.
FQCN=$(basename "$REPORT" .xml | sed 's/^TEST-//')

if [ ! -f "$REPORT" ]; then
  echo "VERDICT: FAIL $FQCN report file not found: $REPORT" >&2
  exit 1
fi

# Extract the opening <testsuite ...> tag (attributes may appear in any
# order; the element may or may not self-close). Anchor on a following
# space or `>` so an aggregate <testsuites ...> root (whose roll-up totals
# are not this named class's counts) is never matched (WR-02).
TESTSUITE_TAG=$(grep -oE '<testsuite[ >][^>]*>' "$REPORT" | head -1 || true)
if [ -z "$TESTSUITE_TAG" ]; then
  echo "VERDICT: FAIL $FQCN no <testsuite> element found in: $REPORT" >&2
  exit 1
fi

extract_attr() {
  # $1 = attribute name; reads $TESTSUITE_TAG
  printf '%s' "$TESTSUITE_TAG" | sed -n 's/.*[^a-zA-Z]'"$1"'="\([^"]*\)".*/\1/p'
}

TESTS=$(extract_attr tests)
FAILURES=$(extract_attr failures)
ERRORS=$(extract_attr errors)
SKIPPED=$(extract_attr skipped)
# JUnit XML schema does not require a `skipped` attribute on <testsuite>;
# several formatters omit it. Default to 0 so an otherwise-green report
# without it does not spuriously FAIL (WR-01). It is only hard-required to
# be numeric when it will actually be enforced, below.
SKIPPED="${SKIPPED:-0}"

require_numeric() {
  # $1 = attribute name, $2 = value
  case "$2" in
    ''|*[!0-9]*)
      echo "VERDICT: FAIL $FQCN could not parse numeric '$1' attribute from testsuite in: $REPORT" >&2
      exit 1
      ;;
  esac
}

require_numeric tests "$TESTS"
require_numeric failures "$FAILURES"
require_numeric errors "$ERRORS"
if [ "$NO_SKIP" -eq 1 ]; then
  require_numeric skipped "$SKIPPED"
fi

if [ "$TESTS" -eq 0 ]; then
  echo "VERDICT: FAIL $FQCN tests=0 (no tests executed)" >&2
  exit 1
fi

if [ "$FAILURES" -ne 0 ]; then
  echo "VERDICT: FAIL $FQCN failures=$FAILURES (expected 0)" >&2
  exit 1
fi

if [ "$ERRORS" -ne 0 ]; then
  echo "VERDICT: FAIL $FQCN errors=$ERRORS (expected 0)" >&2
  exit 1
fi

if [ "$NO_SKIP" -eq 1 ] && [ "$SKIPPED" -ne 0 ]; then
  echo "VERDICT: FAIL $FQCN skipped=$SKIPPED (--no-skip requires skipped=0)" >&2
  exit 1
fi

echo "VERDICT: PASS $FQCN tests=$TESTS failures=0 errors=0 skipped=$SKIPPED"
exit 0
