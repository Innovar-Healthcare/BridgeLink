#!/usr/bin/env python3
"""
send_mllp.py — MLLP frame sender for BridgeLink connector functional tests.

Usage:
    python3 send_mllp.py <host> <port> <hl7_message> [--encoding ascii|latin-1]

Where <hl7_message> uses literal \\n as the segment separator placeholder (converted to \\r).

The optional --encoding flag selects how the message string is encoded before MLLP
framing. Default is ascii, matching every existing caller byte-for-byte. Pass
--encoding latin-1 (ISO-8859-1) to frame a payload containing a byte above the 0x7F
ASCII boundary (for example 0xE9, the accented "e" used by IRT-2217's non-ASCII
default-charset smoke leg) -- ascii encoding raises UnicodeEncodeError on such bytes.

Exit codes:
    0 — ACK received containing AA (success)
    1 — ACK missing AA or connection error (failure)

Example:
    python3 send_mllp.py localhost 9002 "MSH|^~\\&|LABNET|Acme Labs|||20090601||ORU^R01|001|D|2.2\\nPID|1|8890088|||Doe^John"
    python3 send_mllp.py localhost 9002 "PID|1||X||D\\xe9e^Test" --encoding latin-1
"""

import socket
import sys

# MLLP framing bytes (per HL7 MLLP specification)
VT = b'\x0b'   # Vertical Tab (0x0B) — start of MLLP block
FS = b'\x1c'   # File Separator (0x1C) — end of MLLP block
CR = b'\x0d'   # Carriage Return (0x0D) — end of MLLP block terminator

DEFAULT_ENCODING = 'ascii'
SUPPORTED_ENCODINGS = ('ascii', 'latin-1')


def usage_error():
    print("Usage: send_mllp.py <host> <port> <hl7_message> [--encoding ascii|latin-1]", file=sys.stderr)
    sys.exit(1)


def parse_args(argv):
    """Parse the host/port/message positionals plus an optional --encoding flag.

    Returns (host, port, msg, encoding). Positional order and count stay identical
    to the original 3-argument contract when --encoding is not supplied, so every
    existing caller (e.g. NativePumpChannelsTest.pumpMllp) is unaffected.
    """
    encoding = DEFAULT_ENCODING
    positional = []
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg == '--encoding':
            if i + 1 >= len(argv):
                usage_error()
            encoding = argv[i + 1]
            i += 2
            continue
        positional.append(arg)
        i += 1

    if len(positional) < 3:
        usage_error()

    if encoding not in SUPPORTED_ENCODINGS:
        print("Unsupported --encoding %r (expected one of %s)" % (encoding, SUPPORTED_ENCODINGS), file=sys.stderr)
        sys.exit(1)

    host = positional[0]
    port = int(positional[1])
    # Replace literal \n (shell-escaped segment separator) with \r (HL7 segment separator)
    msg = positional[2].replace('\\n', '\r')
    return host, port, msg, encoding


def main():
    host, port, msg, encoding = parse_args(sys.argv[1:])

    # Frame the message: VT + payload bytes + FS + CR
    framed = VT + msg.encode(encoding) + FS + CR

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(10)
        s.connect((host, port))
        s.sendall(framed)
        ack = s.recv(4096)

    # Decode ACK — BridgeLink auto-generates a valid HL7v2 AA ACK
    ack_str = ack.decode('ascii', errors='replace')
    if 'AA' in ack_str:
        print('ACK_OK')
        sys.exit(0)
    else:
        print('ACK_FAIL: ' + repr(ack_str[:200]))
        sys.exit(1)


if __name__ == '__main__':
    main()
