#!/usr/bin/env bash
# Does a BridgeLink node reprocess a peer's in-flight messages when they share a server ID?
#
#   ./run-test.sh distinct     each node gets its own SERVER_ID   (the control)
#   ./run-test.sh shared       both nodes share one SERVER_ID    (the topology under test)
#   ./run-test.sh shared-raw   shared SERVER_ID, RAW storage     (the mitigation)
#
# Internal queues are off on both the source and destination in every arm. The distinct and
# shared arms use PRODUCTION storage so message recovery is enabled; shared-raw uses RAW,
# which disables it. Only SERVER_ID and the storage mode vary.
#
# Sequence: load node A until N messages are in flight against a deliberately slow
# destination, then start node B and deploy the same channel on it. That deploy is the
# scale-out event. Node B runs RecoveryTask on channel start; the question is whether it
# picks up node A's live work.
set -uo pipefail

MODE="${1:-}"
case "$MODE" in shared|distinct|shared-raw) ;; *) echo "usage: $0 shared|distinct|shared-raw"; exit 2 ;; esac

cd "$(dirname "$0")"
CH=0000ha01-0000-0000-0000-0000000ha001
N_MESSAGES="${N_MESSAGES:-10}"
HOLD="${HOLD_SECONDS:-240}"
A=https://127.0.0.1:8443
B=https://127.0.0.1:8444
# The template is the repo's own 26.6.0 smoke-test channel. Referenced, not copied, so it
# cannot drift from the real one.
TEMPLATE=../../smoke-tests/channels/http-listener-error500-test.xml
mkdir -p results
RESULT="results/result-${MODE}.json"

# shared-raw is the proposed mitigation: same shared ID, but RAW storage, which
# sets messageRecoveryEnabled=false and should remove the recovery collision entirely.
CHANNEL_FILE=channel.xml
python3 build-channel.py "$TEMPLATE" channel.xml PRODUCTION >/dev/null
if [[ "$MODE" == "distinct" ]]; then
  A_ID=aaaaaaaa-0000-0000-0000-00000000000a; B_ID=bbbbbbbb-0000-0000-0000-00000000000b
else
  A_ID=5ha4ed00-0000-0000-0000-00000000shar; B_ID=$A_ID
fi
if [[ "$MODE" == "shared-raw" ]]; then
  python3 build-channel.py "$TEMPLATE" channel-raw.xml RAW >/dev/null
  CHANNEL_FILE=channel-raw.xml
fi

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
api() { # api <base> <cookiejar> <method> <path> [extra curl args...]
  local base=$1 jar=$2 method=$3 path=$4; shift 4
  curl -sk -b "$jar" -X "$method" "${base}${path}" \
    -H 'X-Requested-With: XMLHttpRequest' -H 'Accept: application/json' "$@"
}
wait_api() { # wait_api <base> <label>
  local base=$1 label=$2
  for i in $(seq 1 60); do
    local c
    c=$(curl -sk -o /dev/null -w '%{http_code}' --max-time 4 "$base/api/server/version" \
        -H 'X-Requested-With: XMLHttpRequest' 2>/dev/null)
    [[ -n "$c" && "$c" != "000" ]] && { echo "  $label up after ~$((i*5))s"; return 0; }
    sleep 5
  done
  echo "  $label FAILED to come up"; return 1
}
login() { # login <base> <jar>
  curl -sk -c "$2" -X POST "$1/api/users/_login" -H 'X-Requested-With: XMLHttpRequest' \
    -d 'username=admin' -d 'password=admin' -o /dev/null -w '%{http_code}'
}

say "MODE=$MODE   A_ID=$A_ID   B_ID=$B_ID   messages=$N_MESSAGES   hold=${HOLD}s"

say "Resetting the stack (fresh database)"
docker compose down -v >/dev/null 2>&1
cat > .env <<EOF
BL_A_SERVER_ID=$A_ID
BL_B_SERVER_ID=$B_ID
HOLD_SECONDS=$HOLD
EOF
docker compose up -d postgres sink >/dev/null 2>&1
sleep 5

say "Starting node A"
docker compose up -d bl-a >/dev/null 2>&1
wait_api "$A" "node A" || exit 1
echo "  login A: $(login "$A" cookies-a.txt)"

say "Deploying the channel on node A"
code=$(api "$A" cookies-a.txt POST /api/channels -H 'Content-Type: application/xml' \
    --data-binary @"$CHANNEL_FILE" -o /dev/null -w '%{http_code}')
echo "  import HTTP $code"
[[ "$code" == "200" ]] || { echo "  import failed" >&2; exit 1; }
api "$A" cookies-a.txt POST "/api/channels/$CH/_deploy?returnErrors=true" \
    -o /dev/null -w '  deploy HTTP %{http_code}\n'
for i in $(seq 1 20); do
  api "$A" cookies-a.txt GET /api/channels/statuses | grep -q STARTED && { echo "  STARTED"; break; }
  sleep 3
done

curl -sS --max-time 5 http://127.0.0.1:9000/reset >/dev/null
say "Sending $N_MESSAGES messages into node A"
for i in $(seq 1 "$N_MESSAGES"); do
  (curl -sS --max-time "$HOLD" -X POST http://127.0.0.1:8081 -d "MSG-$i" >/dev/null 2>&1 &)
done

say "Waiting for messages to be in flight on node A"
for i in $(seq 1 40); do
  inflight=$(curl -sS --max-time 5 http://127.0.0.1:9000/report 2>/dev/null \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["in_flight_now"])' 2>/dev/null || echo 0)
  echo "  in flight: $inflight"
  [[ "$inflight" -ge "$N_MESSAGES" ]] && break
  sleep 3
done
BEFORE=$(curl -sS --max-time 5 http://127.0.0.1:9000/report | python3 -c 'import json,sys; print(json.load(sys.stdin)["total_deliveries"])')
echo "  deliveries before scale-out: $BEFORE"
# Without this, a failed import or a port collision yields BEFORE=0 and the run still prints
# "no duplicates observed" -- a false negative dressed as a result.
if [[ "$BEFORE" -ne "$N_MESSAGES" ]]; then
  echo "  SETUP FAILED: $BEFORE of $N_MESSAGES messages in flight; not a valid run" >&2
  exit 1
fi

say "SCALE-OUT: starting node B and deploying the same channel"
docker compose up -d bl-b >/dev/null 2>&1
wait_api "$B" "node B" || exit 1
echo "  login B: $(login "$B" cookies-b.txt)"
api "$B" cookies-b.txt POST "/api/channels/$CH/_deploy?returnErrors=true" \
    -o /dev/null -w '  deploy on B HTTP %{http_code}\n'

say "Letting node B's recovery run"
for i in $(seq 1 12); do sleep 5; done

say "RESULT"
curl -sS --max-time 10 http://127.0.0.1:9000/report > "$RESULT"
python3 - "$RESULT" "$BEFORE" "$MODE" <<'PY'
import json, sys
rep = json.load(open(sys.argv[1])); before = int(sys.argv[2]); mode = sys.argv[3]
extra = rep["extra_deliveries"]
print(f"  mode                     : {mode}")
print(f"  deliveries before B      : {before}")
print(f"  deliveries total         : {rep['total_deliveries']}")
print(f"  distinct message ids     : {rep['distinct_message_ids']}")
print(f"  ids delivered more than once: {rep['duplicated_message_ids']}")
print(f"  EXTRA (duplicate) deliveries: {extra}")
print(f"  by node                  : {rep['by_node']}")
if rep["duplicate_detail"]:
    print("  duplicates (message id -> delivering nodes):")
    for mid, nodes in sorted(rep["duplicate_detail"].items()):
        print(f"    {mid}: {nodes}")
print()
print("  VERDICT:", "DUPLICATE PROCESSING OBSERVED" if extra else "no duplicates observed")
PY

say "Did node B run recovery?"
# Two distinct lines matter and only one contains "message recovery": the RAW arm logs
# "...but message storage settings do not support recovery. Skipping recovery task."
docker compose logs bl-b 2>&1 | grep -iE "message recovery|recovery task" | tail -5 \
  || echo "  (no recovery log lines)"
echo
echo "Full JSON: $RESULT"
