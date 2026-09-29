#!/usr/bin/env python3
"""
Derive the test channel from a known-good 26.6.0 smoke-test channel.

Starting from a real exported channel rather than hand-writing XStream XML matters:
XStream does not run constructors, so any element omitted here would deserialize to
null/false rather than a sensible default.

Shape of the result:
  source      HTTP Listener, 0.0.0.0:8081, respondAfterProcessing=true
              (respondAfterProcessing=false is what turns the SOURCE queue on, and
              this test requires all internal queues off)
  destination JavaScript Writer, queueEnabled=false, that reports the delivery to the
              sink and then blocks on the sink's slow response, holding the message
              in flight
  storage     PRODUCTION (message recovery enabled -- the condition under test)
"""
import os
import re
import sys

src, dst = sys.argv[1], sys.argv[2]
STORAGE = sys.argv[3] if len(sys.argv) > 3 else "PRODUCTION"
x = open(src).read()

SCRIPT = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "destination.js")).read().strip()


subs = [
    # Identity
    (r"<id>00000023-0000-0000-0000-000000000023</id>",
     "<id>0000ha01-0000-0000-0000-0000000ha001</id>"),
    (r"<name>HTTP Error 500 Response Test Channel</name>",
     "<name>HA Shared Server ID Test</name>"),
    (r"<description>.*?</description>",
     "<description>Measures duplicate processing when two nodes share a server ID. "
     "Internal queues off, PRODUCTION storage. Test-only.</description>"),

    # Source listener: reachable from outside the container, on a known port
    (r"<host>127\.0\.0\.1</host>", "<host>0.0.0.0</host>"),
    (r"<port>\$\{HTTP_ERROR500_PORT\}</port>", "<port>8081</port>"),

    # Enough concurrency that several messages are genuinely in flight at once
    (r"<processingThreads>1</processingThreads>", "<processingThreads>20</processingThreads>"),
    (r"<threadCount>1</threadCount>", "<threadCount>20</threadCount>"),

    # PRODUCTION keeps message recovery enabled; that is the behaviour under test
    (r"<messageStorageMode>DEVELOPMENT</messageStorageMode>",
     f"<messageStorageMode>{STORAGE}</messageStorageMode>"),

    # The destination: report, then block
    (r"<script><!\[CDATA\[return \"IRT-832 smoke test error response body\.\";\]\]></script>",
     "<script><![CDATA[" + SCRIPT + "]]></script>"),
]

for pat, rep in subs:
    new, n = re.subn(pat, rep, x, flags=re.DOTALL)
    if n != 1:
        sys.exit(f"FAIL: pattern matched {n} times (expected 1): {pat[:70]}")
    x = new

# Guardrails: the whole experiment is void if any of these silently drift.
assert "<queueEnabled>false</queueEnabled>" in x, "destination queue must be off"
assert "<respondAfterProcessing>true</respondAfterProcessing>" in x, "source queue must be off"
assert f"<messageStorageMode>{STORAGE}</messageStorageMode>" in x
assert "${" not in x, "unsubstituted placeholder left in channel"

open(dst, "w").write(x)
print(f"wrote {dst}")
print(f"  storage={STORAGE}  sourceQueue=off  destQueue=off  listener=0.0.0.0:8081")
