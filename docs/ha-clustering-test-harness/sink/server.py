#!/usr/bin/env python3
"""
Slow destination endpoint for the BridgeLink shared-server-ID test.

Two jobs:
  1. Record every delivery (message id + which node sent it + timestamp).
  2. Hold the connection open for HOLD_SECONDS, so the message stays in flight
     inside BridgeLink. That in-flight window is what a peer node's RecoveryTask
     can see when the nodes share a server ID.

Endpoints:
  GET /deliver?id=<messageId>&node=<hostname>   record, then sleep, then 200
  GET /report                                    JSON of everything recorded
  GET /reset                                     clear recorded state
  GET /health                                    liveness
"""
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

HOLD_SECONDS = float(os.environ.get("HOLD_SECONDS", "120"))
PORT = int(os.environ.get("PORT", "9000"))

_lock = threading.Lock()
_deliveries = []      # every delivery, in arrival order
_in_flight = 0
_started = time.time()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _json(self, payload, code=200):
        body = json.dumps(payload, indent=2).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _text(self, text, code=200):
        body = text.encode()
        self.send_response(code)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        global _in_flight
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)

        if parsed.path == "/health":
            return self._text("ok")

        if parsed.path == "/reset":
            with _lock:
                _deliveries.clear()
            return self._text("reset")

        if parsed.path == "/report":
            with _lock:
                deliveries = list(_deliveries)
                inflight = _in_flight

            counts = {}
            for d in deliveries:
                counts[d["id"]] = counts.get(d["id"], 0) + 1

            duplicated = {k: v for k, v in counts.items() if v > 1}
            # Which node delivered each duplicated id, in order.
            dup_detail = {
                mid: [d["node"] for d in deliveries if d["id"] == mid]
                for mid in duplicated
            }
            return self._json({
                "total_deliveries": len(deliveries),
                "distinct_message_ids": len(counts),
                "duplicated_message_ids": len(duplicated),
                "extra_deliveries": sum(v - 1 for v in duplicated.values()),
                "in_flight_now": inflight,
                "duplicate_detail": dup_detail,
                "by_node": _tally(deliveries, "node"),
                "deliveries": deliveries,
            })

        if parsed.path == "/deliver":
            mid = (qs.get("id") or ["?"])[0]
            node = (qs.get("node") or ["?"])[0]
            with _lock:
                first = all(d["id"] != mid for d in _deliveries)
                _deliveries.append({
                    "id": mid,
                    "node": node,
                    "at": round(time.time() - _started, 3),
                    "first": first,
                })
                if first:
                    _in_flight += 1
                seq = len(_deliveries)
            kind = "FIRST" if first else "DUPLICATE"
            print(f"[sink] #{seq} {kind} id={mid} node={node}", flush=True)

            # Hold only the FIRST delivery of each message. That is what keeps the
            # original in flight on node A so the peer's recovery can see it.
            #
            # Duplicates return immediately, on purpose: if they were held too, each
            # recovered message would block the (serial) RecoveryTask for the full hold
            # and the run could never drain. Holding them would measure our own sink,
            # not BridgeLink.
            if first:
                time.sleep(HOLD_SECONDS)
                with _lock:
                    _in_flight -= 1
            return self._text("delivered")

        return self._text("not found", 404)

    def log_message(self, *args):
        pass  # we do our own logging


def _tally(rows, key):
    out = {}
    for r in rows:
        out[r[key]] = out.get(r[key], 0) + 1
    return out


if __name__ == "__main__":
    print(f"[sink] listening on {PORT}, holding each delivery {HOLD_SECONDS}s", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
