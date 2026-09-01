# Shared server ID: does a peer node reprocess in-flight messages?

A controlled experiment for the BridgeLink HA question: **can two nodes share a server ID
if internal channel queues are off?**

The claim under test was that internal queues are the only thing scoped by server ID, so
turning them off makes a shared ID safe. Message recovery is scoped the same way, is not
gated on queues, and runs on **every channel start** rather than only after a crash. This
harness measures whether that actually produces duplicate processing, and how much.

## What it does

Two BridgeLink nodes against one PostgreSQL, plus a deliberately slow destination.

1. Deploy a channel on node A. Source queue off, destination queue off, storage `PRODUCTION`
   (so message recovery is enabled, which is the default for any real channel).
2. Push N messages into node A. The destination reports each delivery to the sink, then
   blocks on the sink's withheld response. The messages are now genuinely in flight:
   `PROCESSED = FALSE`, mid-send, exactly as they are during normal operation.
3. Start node B and deploy the same channel. **This is the scale-out event.** A channel
   start runs `RecoveryTask`.
4. Count how many message IDs the sink saw more than once.

The only variable between arms is `SERVER_ID`.

| Arm | `SERVER_ID` | Expectation |
|---|---|---|
| `distinct` (control) | different per node | no duplicates; B's recovery matches nothing |
| `shared` | identical on both | B recovers A's live in-flight messages |

## Running it

```bash
./run-test.sh distinct
./run-test.sh shared
```

Each arm tears the stack down and recreates the database, so the arms cannot contaminate
each other. Results land in `results/`. The committed ones are from the run recorded below.

Tunables: `N_MESSAGES` (default 10), `HOLD_SECONDS` (default 240).

## Reading the result

`extra_deliveries` is the finding: deliveries beyond one per distinct message ID. `by_node`
attributes them, keyed by container hostname, so a duplicate delivered by node B against a
message node A received is visible directly.

Corroborate it against node B's own log:

```bash
docker compose logs bl-b | grep -i "message recovery"
```

`Starting message recovery ... Incomplete unfinished messages found` on node B, for messages
node A received, is the mechanism in BridgeLink's own words.

## Details that are load-bearing

- **The keystore is shared via `MP_KEYSTORE_PATH` onto a common volume, and
  `MP_KEYSTORE_STOREPASS` / `MP_KEYSTORE_KEYPASS` are pinned to the same values on both
  nodes.** Sharing the file alone is not enough: each node otherwise generates its own
  password and the second fails with *"Keystore was tampered with, or password was
  incorrect."* This is the shared-keystore requirement showing up in practice, and it is
  independent of the server ID question.
- **`MP_SERVER_STARTUPDEPLOY=false`** on both nodes, so channel deploys happen when the
  script says rather than at boot. That makes the scale-out event a discrete, timed step.
- **`respondAfterProcessing` stays `true`.** Setting it false is what enables the *source*
  queue, and this test requires all internal queues off.
- **The destination uses a raw `java.net.Socket`, not `URL.openConnection()`.** Under JDK 17
  module rules Rhino cannot reach `sun.net.www.protocol.http.HttpURLConnection`.
- **The sink holds only the first delivery of each message ID.** Duplicates return
  immediately. `RecoveryTask` is serial, so holding duplicates too would block it for the
  full hold per message and the run would never drain; that would measure the sink rather
  than BridgeLink.
- **The channel is derived from this repo's own 26.6.0 smoke-test channel**
  (`smoke-tests/channels/http-listener-error500-test.xml`) rather than hand-written, and it is
  referenced in place rather than copied here so the two cannot drift. XStream does not run
  constructors, so any omitted element would deserialize to `null`/`false` instead of a
  sensible default.

## Scope

Measures duplicate *processing* on a scale-out with a shared server ID. It does not measure
throughput, failover timing, or Work Queue behaviour, and it does not test `RAW` storage
(where recovery is disabled outright and the result should by inspection be zero).

## Results (2026-09-01, BridgeLink 26.6.0, 10 messages, 240s hold)

| Arm | Server IDs | Storage | Deliveries | Duplicated IDs | Recovery on node B |
|---|---|---|---|---|---|
| `distinct` | different | PRODUCTION | 10 / 10 | 0 | did not run |
| `shared` | identical | PRODUCTION | **20** / 10 | **10** | `Successfully recovered 10 out of 10 messages` |
| `shared-raw` | identical | RAW | 10 / 10 | 0 | `message storage settings do not support recovery. Skipping recovery task.` |

**A shared server ID with PRODUCTION storage duplicated the entire in-flight set**, not a sample
of it: all ten messages were delivered twice, once by each node, on a single scale-out.

**RAW storage removes it.** Node B still *found* the peer's incomplete messages, which confirms
the shared-ID visibility is real, and declined to act on them because recovery is disabled in
that mode.

So the shared-identity pattern is safe only with both settings together: internal channel queues
off **and** RAW storage. Queues off alone is not sufficient, which was the original claim under
test.

### What this does not establish

- Work Queue was not installed. The pattern pairs a shared ID with Work Queue for distribution;
  this harness tested only the recovery collision.
- RAW also commits asynchronously. The durability cost of that was not measured here and would
  need a database-crash test.
- Only queues, recovery and statistics were examined for server-ID scoping. That is not proven
  to be the complete set.
- Single run per arm, one message rate, one destination latency. The effect was total rather than
  marginal, so repetition was not pursued, but no distribution was characterised.
