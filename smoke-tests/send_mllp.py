#!/usr/bin/env python3
"""
send_mllp.py — MLLP frame sender for BridgeLink connector functional tests.

Usage:
    python3 send_mllp.py <host> <port> <hl7_message>

Where <hl7_message> uses literal \\n as the segment separator placeholder (converted to \\r).

Exit codes:
    0 — ACK received containing AA (success)
    1 — ACK missing AA or connection error (failure)

Example:
    python3 send_mllp.py localhost 9002 "MSH|^~\\&|LABNET|Acme Labs|||20090601||ORU^R01|001|D|2.2\\nPID|1|8890088|||Doe^John"
"""

import socket
import sys

# MLLP framing bytes (per HL7 MLLP specification)
VT = b'\x0b'   # Vertical Tab (0x0B) — start of MLLP block
FS = b'\x1c'   # File Separator (0x1C) — end of MLLP block
CR = b'\x0d'   # Carriage Return (0x0D) — end of MLLP block terminator

def main():
    if len(sys.argv) < 4:
        print("Usage: send_mllp.py <host> <port> <hl7_message>", file=sys.stderr)
        sys.exit(1)

    host = sys.argv[1]
    port = int(sys.argv[2])
    # Replace literal \n (shell-escaped segment separator) with \r (HL7 segment separator)
    msg = sys.argv[3].replace('\\n', '\r')

    # Frame the message: VT + payload bytes + FS + CR
    framed = VT + msg.encode('ascii') + FS + CR

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
