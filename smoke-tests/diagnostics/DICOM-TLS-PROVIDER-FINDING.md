# DICOM TLS Provider Finding (Phase 18.4 Plan 01, Task 1)

**Status:** RUN — empirical result captured 2026-07-27.

**How this was run:** `DicomTlsProviderDiagnostic` was compiled and executed directly
against the vendored `dcm4che-net-2.0.29.jar` / `dcm4che-tool-dcmsnd-2.0.29.jar` /
`dcm4che-tool-dcmrcv-2.0.29.jar` jars under `server/lib/extensions/dimse/` (identical
content to what an assembled distribution ships at
`server/setup/extensions/dicom/lib/`; no assembled distribution existed in this
environment at Plan 01 execution time — `dicom-tls-provider-diagnostic.sh` itself checks
for `<dist>/extensions/dicom/lib` and will re-populate this file automatically the next
time it is run against a locally-built distribution). Command equivalent to what the
script performs (`keytool -genkeypair -storetype PKCS12` throwaway keystore + `javac`/
`java -Djavax.net.debug=ssl,keymanager,ssl:handshake`).

**JVM:** OpenJDK 17.0.15 (Homebrew, arm64)

## Security.getProviders() order

```
SUN v17
SunRsaSign v17
SunEC v17
SunJSSE v17
SunJCE v17
SunJGSS v17
SunSASL v17
XMLDSig v17
SunPCSC v17
JdkLDAP v17
JdkSASL v17
Apple v17
SunPKCS11 v17
```

No BouncyCastle (`BC`) provider is present in this list at all — it is never
JVM-globally registered, confirming RESEARCH Assumption A4 (repo-wide grep found zero
`Security.addProvider`/`insertProviderAt` calls outside test code).

## Handshake crypto primitive providers

```
CIPHER_PROVIDER: SunJCE
SIGNATURE_PROVIDER: SunRsaSign
```

(`Cipher.getInstance("AES/CBC/NoPadding")` and `Signature.getInstance("SHA256withRSA")`
— the same primitive families a `TLS_RSA_WITH_AES_128_CBC_SHA` handshake uses
internally — both resolve to plain JDK providers, not `"BC"`.)

## Negotiated cipher suite (aes leg)

```
AES_LEG: protocol=TLSv1.2 cipherSuite=TLS_RSA_WITH_AES_128_CBC_SHA
```

A live `SSLSocket` was confirmed on the association (`Association.getSocket()`
`instanceof SSLSocket`), and its `SSLSession` reports the provider-neutral cipher-suite
name above — cipher suite names don't identify the underlying JCA provider directly,
which is why the `Cipher`/`Signature` provider proxy above is the load-bearing evidence.

## Conclusion

**BouncyCastle does NOT serve the DICOM TLS handshake's Cipher/Signature primitives.**
The handshake runs entirely through the JDK's own default providers (`SunJSSE` for the
TLS record/handshake layer, `SunJCE` for the AES-CBC cipher, `SunRsaSign` for the RSA
signature/key-exchange operations). This empirically confirms RESEARCH Open Question 1 /
Assumption A4: **a green DICOM TLS test (this phase's own SC-1/SC-2/SC-3) does NOT, by
itself, guard the Phase 22/26 BouncyCastle-CVE regression for the DICOM transport's
handshake crypto specifically** — BC is used elsewhere in this codebase (the admin-HTTPS
keystore cert/key generation, `KeyEncryptor`/`Digester` property encryption) but never as
a globally-registered JCA/JSSE provider that the DICOM connector's plain
`KeyStore.getInstance(...)`/`SSLContext` calls would pick up.

**Recommendation for the phase SUMMARY (SC-3 caveat):** this phase's TLS coverage is a
correct and valuable transport-level regression tripwire (silent-plaintext-downgrade,
cert/keystore wiring breakage, cipher-suite/protocol negotiation breakage), but it does
**not** specifically exercise the BouncyCastle CVE seam for the DICOM transport, because
BC never participates in that handshake's low-level crypto as currently coded. A BC CVE
bump that changes only BC's own crypto primitives (and not the JDK's `SunJSSE`/`SunJCE`)
would not be caught by this test.
