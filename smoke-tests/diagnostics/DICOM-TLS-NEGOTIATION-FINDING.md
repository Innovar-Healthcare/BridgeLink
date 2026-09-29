# DICOM TLS Negotiation Finding (Phase 18.4 Plan 01, Task 2)

**Status:** RUN — empirical result captured 2026-07-27.

**How this was run:** same rig as the Provider Finding (Task 1) — `DicomTlsProviderDiagnostic`
compiled and run directly against the vendored `dcm4che-net-2.0.29.jar`/
`dcm4che-tool-dcmsnd-2.0.29.jar`/`dcm4che-tool-dcmrcv-2.0.29.jar` jars under
`server/lib/extensions/dimse/` (no assembled distribution existed in this environment;
`dicom-tls-provider-diagnostic.sh` will re-run this automatically against a locally-built
distribution and overwrite this file). The diagnostic does **not** hard-pin the protocol
list to TLSv1.2-only — it installs the SAME default protocol list the production connector
installs (`MirthSSLUtil.DEFAULT_HTTPS_CLIENT_PROTOCOLS = {"TLSv1.3", "TLSv1.2"}`, via
`DcmSnd`/`DcmRcv.setTlsProtocol(...)`), then observes whether the legacy RSA-CBC cipher
suites still negotiate down to TLSv1.2 against that TLSv1.3-inclusive list.

**JVM:** OpenJDK 17.0.15 (Homebrew, arm64)

## IMPORTANT — a corrected finding, not what RESEARCH Pitfall 4 anticipated

Running this diagnostic surfaced a **more fundamental issue than the TLSv1.3-vs-legacy-cipher
question RESEARCH Pitfall 4/Assumption A2 anticipated**: dcm4che2's `NetworkConnection`
class has its own hardcoded, 2010-era default `tlsProtocol` field —
`{"TLSv1", "SSLv3", "SSLv2Hello"}` — completely independent of, and NOT inherited from,
the JVM's own TLS defaults. On JDK 17 (and, per RESEARCH's confirmed-unchanged
`jdk.tls.disabledAlgorithms` baseline, JDK 21/25 as well), `TLSv1`/`SSLv3` are disabled
outright — so leaving dcm4che2's own default untouched fails **every** leg immediately
with `SSLHandshakeException: No appropriate protocol (protocol is disabled or cipher
suites are inappropriate)`, regardless of aes/3des cipher choice. This is NOT a
TLSv1.3-preferring-list problem — it is dcm4che2 never being told about TLSv1.2 at all
unless the caller sets it explicitly.

**Why this doesn't affect production:** verified in `DICOMConfigurationUtil.java` — the
real connector code (`configureDcmSnd`/`configureDcmRcv`) unconditionally calls
`setTlsProtocol(MirthSSLUtil.getEnabledHttpsProtocols(...))` before `initTLS()` whenever
`tls != notls`, so it never relies on dcm4che2's ancient default. This diagnostic mirrors
that same override (`DEFAULT_PROTOCOLS = {"TLSv1.3", "TLSv1.2"}`) to test the question
RESEARCH actually intended: **given the connector's real, TLSv1.3-inclusive protocol
list, do the legacy RSA-CBC cipher suites still negotiate down to TLSv1.2?**

## Negotiated protocol + cipher suite per leg

### Run 1 — default `jdk.tls.disabledAlgorithms` (no D-04 3DES override applied)

```
AES_LEG:  protocol=TLSv1.2 cipherSuite=TLS_RSA_WITH_AES_128_CBC_SHA
3DES_LEG: FAILED (SocketException: Connection or outbound has closed)
```

- **aes leg: PASS.** With the connector's real protocol list installed
  (`{"TLSv1.3","TLSv1.2"}`), `TLS_RSA_WITH_AES_128_CBC_SHA` negotiates down to `TLSv1.2`
  cleanly, with no `handshake_failure`. This directly confirms RESEARCH Assumption A2 for
  the aes leg: JSSE gracefully falls back from TLSv1.3 to TLSv1.2 when the enabled cipher
  suite requires it.
- **3des leg: FAILS as expected without the D-04 override.** `3DES_EDE_CBC` sits on this
  JDK's `jdk.tls.disabledAlgorithms` (verified in the local JDK 17
  `conf/security/java.security`), so no common cipher suite exists between the two
  TLSv1.2-capable peers — the connection is abruptly closed
  (`SocketException: Connection or outbound has closed`) rather than a clean
  `SSLHandshakeException`/alert, but the root cause is the same disabled-algorithm
  condition RESEARCH anticipated.

### Run 2 — WITH the D-04 `java.security` overlay applied (`3DES_EDE_CBC` removed from
`jdk.tls.disabledAlgorithms`, all other entries unchanged, `-Djava.security.properties=`
single-`=` additive mode)

```
AES_LEG:  protocol=TLSv1.2 cipherSuite=TLS_RSA_WITH_AES_128_CBC_SHA
3DES_LEG: protocol=TLSv1.2 cipherSuite=SSL_RSA_WITH_3DES_EDE_CBC_SHA
```

With the exact overlay content RESEARCH Pattern 4 prescribes applied via
`-Djava.security.properties=<overlay>`, **both** legs now negotiate cleanly to `TLSv1.2`
with the exact expected cipher-suite constants.

## javax.net.debug ProtocolVersion grep

Both runs were executed under `-Djavax.net.debug=ssl,keymanager,ssl:handshake`; the
`SSLSession.getProtocol()`/`getCipherSuite()` values captured programmatically above are
the load-bearing evidence (the JDK 17 debug-log format on this JVM does not print a
grep-friendly literal `ProtocolVersion:` line for the negotiated value — the session
introspection was used instead, matching RESEARCH's own recommended reflection-based
approach over log-scraping, Pitfall 3).

## Conclusion

**PASS (aes) / requires D-04 contingency (3des) — exactly as RESEARCH anticipated, once
the connector's real default protocol list is installed instead of dcm4che2's own ancient
default.**

1. `tls=aes` (`TLS_RSA_WITH_AES_128_CBC_SHA`) negotiates down to `TLSv1.2` with **no**
   `java.security` override needed, on this JDK (17) and — per RESEARCH's confirmed-stable
   `jdk.tls.disabledAlgorithms` baseline across JDK 17/21 — expected to hold on JDK 21/25
   too. **No contingency needed for the aes channel.**
2. `tls=3des` (`SSL_RSA_WITH_3DES_EDE_CBC_SHA`) fails out-of-the-box on this JDK exactly as
   D-04 predicted, and is fully resolved by the documented **D-04 contingency**: apply the
   `java.security` overlay removing `3DES_EDE_CBC` from `jdk.tls.disabledAlgorithms`
   (RESEARCH Pattern 4) to **both** JVMs on every TLS leg (the harness driver JVM AND the
   Mirth server JVM). Verified empirically above — with the overlay applied, 3des
   negotiates to `TLSv1.2` / `SSL_RSA_WITH_3DES_EDE_CBC_SHA` cleanly.

**Action for Plan 04:** the aes channel needs no special protocol-list handling beyond
what the connector already does by default. The 3des channel MUST have the D-04
`java.security` overlay (`smoke-tests/fixtures/dicom-tls-3des.security`, RESEARCH Pattern 4)
wired to both the Mirth server JVM and the JUnit driver JVM, or its handshake will fail.
This is not a "maybe" fallback — it is a **required** wiring step for `tls=3des` coverage
to pass at all on JDK 17/21/25.
