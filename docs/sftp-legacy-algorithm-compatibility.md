# SFTP Legacy Algorithm Compatibility Runbook

## Why connections to older SFTP servers may fail

The File connector's SFTP scheme is built on `com.github.mwiede:jsch`. Like every actively
maintained SSH2 client, jsch ships a **secure-by-default hardened algorithm set**: the key
exchange (kex), host-key/public-key, cipher, and MAC algorithms it will offer during
negotiation are a modern, curated subset — not the full historical list of every algorithm
jsch's engine is capable of speaking. This hardened default set is not new in the 26.9
jsch version bump; it has been the case since BridgeLink's original migration from the
unmaintained JCraft `jsch` fork to `com.github.mwiede:jsch` (see the "What did NOT change"
note below).

A minority of older or unpatched SFTP servers — appliances, legacy Linux distributions,
vendor systems that have not had their `sshd_config` updated in years — only speak
algorithms from **outside** this hardened default set (e.g. SHA-1-based key exchange, the
plain `ssh-rsa` host-key signature, CBC-mode ciphers, or the non-ETM `hmac-sha1` MAC).
When a BridgeLink File-connector SFTP channel with default configuration tries to connect
to such a server, the connection fails during algorithm negotiation with an exception
whose class name is:

```
com.jcraft.jsch.JSchAlgoNegoFailException: Algorithm negotiation fail
```

If you see `JSchAlgoNegoFailException` (or the message `Algorithm negotiation fail`) in
`mirth.log` for an SFTP channel, this runbook documents the supported, per-channel way to
restore connectivity to that specific legacy server — without weakening the secure
defaults for every other SFTP channel in the instance.

## Where to set the workaround

The File connector's SFTP scheme exposes an advanced **Configuration Settings** table in
the channel-editor UI. This maps directly onto the
`SftpSchemeProperties.configurationSettings` property — a `Map<String, String>` of
arbitrary key/value pairs that BridgeLink pipes straight through to jsch's
`session.setConfig(...)` call before the connection is opened
(`server/src/com/mirth/connect/connectors/file/filesystems/SftpConnection.java`).

This means any key jsch's `Session.setConfig()` understands can be set here, per channel,
per source or destination connector, with no code changes required. This is the supported
escape hatch for legacy-server compatibility — it is scoped to the single channel it is
configured on, so the rest of the instance's SFTP channels keep the hardened defaults.

To set it:

1. Open the channel's File Reader (source) or File Writer (destination) properties.
2. Select the **SFTP** scheme.
3. Open the **Configuration Settings** advanced table.
4. Add one row per key from the mapping table below, using the exact value shown.

## Algorithm-family mapping table

Each row below lists the excluded algorithm family, the concrete legacy algorithm a
customer is most likely to encounter, the exact `configurationSettings` key(s) that
control it, and the exact restore value. Every value below is copied **byte-identically**
from the proven-working smoke-test fixture
`smoke-tests/channels/file-sftp-legacy-workaround-test.xml` (see "Critical" section below
for why the full list — not just the legacy algorithm — must be used).

### 1. Key exchange (kex)

- **Legacy algorithm a customer hits:** `diffie-hellman-group14-sha1`
- **`configurationSettings` key:** `kex`
- **Restore value:**

```
diffie-hellman-group14-sha1,mlkem768x25519-sha256,curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha256
```

### 2. Host key / public key (server_host_key, PubkeyAcceptedAlgorithms)

- **Legacy algorithm a customer hits:** `ssh-rsa` (the plain SHA-1 RSA host-key/pubkey
  signature — not to be confused with `rsa-sha2-256`/`rsa-sha2-512`, which are already in
  jsch's defaults; see the caution below).
- **`configurationSettings` keys:** `server_host_key` AND `PubkeyAcceptedAlgorithms`
- **`server_host_key` restore value:**

```
ssh-rsa,ssh-ed25519-cert-v01@openssh.com,ecdsa-sha2-nistp256-cert-v01@openssh.com,ecdsa-sha2-nistp384-cert-v01@openssh.com,ecdsa-sha2-nistp521-cert-v01@openssh.com,rsa-sha2-512-cert-v01@openssh.com,rsa-sha2-256-cert-v01@openssh.com,ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256
```

- **`PubkeyAcceptedAlgorithms` restore value:**

```
ssh-rsa,ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256
```

**Before applying this override:** if the partner server's host key is RSA, first check
whether the server also negotiates `rsa-sha2-256`/`rsa-sha2-512` (RFC 8332, supported by
OpenSSH 7.2+) — those are already in jsch's default list and require no configuration
change. Only apply the `ssh-rsa` override above if the server truly offers nothing but the
legacy SHA-1 `ssh-rsa` signature.

### 3. Ciphers

- **Legacy algorithms a customer hits:** `3des-cbc`, `aes128-cbc` (CBC-mode ciphers)
- **`configurationSettings` keys:** `cipher.s2c` AND `cipher.c2s` (server-to-client and
  client-to-server directions must both be set)
- **Restore value (same value for both keys):**

```
3des-cbc,aes128-cbc,aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr
```

### 4. MAC

- **Legacy algorithm a customer hits:** `hmac-sha1` (the non-ETM, legacy MAC)
- **`configurationSettings` keys:** `mac.s2c` AND `mac.c2s` (both directions)
- **Restore value (same value for both keys):**

```
hmac-sha1,hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com,hmac-sha1-etm@openssh.com,hmac-sha2-256,hmac-sha2-512
```

## Critical: always append, never replace

Every value documented above is the **full comma-separated list** — the legacy algorithm
prepended to jsch's own modern default algorithms for that family, not the legacy
algorithm alone. Setting a `configurationSettings` value to a **bare legacy-only** string
(e.g. `kex = diffie-hellman-group14-sha1` with nothing else) does not merely "add back" the
legacy algorithm — it **replaces jsch's entire negotiation list for that family**, forcing
every connection on that channel (even to modern servers) down to the weakest algorithm
listed. This would silently downgrade the whole channel's security posture, not just
restore compatibility with one legacy partner.

Always copy the full value exactly as documented in this runbook (legacy algorithm first,
followed by jsch's modern defaults) so the channel can still negotiate the strongest
mutually-supported algorithm with any server it talks to, while remaining able to fall back
to the legacy algorithm only when a legacy server requires it.

## What did NOT change in the 26.9 jsch upgrade

BridgeLink 26.9 upgrades the bundled `com.github.mwiede:jsch` library from `0.2.18` to
`2.28.5`. This is a version-currency and CVE-hygiene upgrade, not an algorithm-hardening
change: the four legacy algorithm families documented above (`diffie-hellman-group14-sha1`
kex, `ssh-rsa` host-key/pubkey, `3des-cbc`/`aes128-cbc` ciphers, `hmac-sha1` MAC) were
**already excluded from jsch's default algorithm set at 0.2.18** — inherited from the
original JCraft-to-mwiede fork migration, long before this upgrade. Do not read this
upgrade, or this runbook, as "jsch 2.28.5 removed algorithms that 0.2.18 supported." Any
BridgeLink instance that needed a legacy-algorithm `configurationSettings` override before
this upgrade needs the identical override after it — see
`docs/release-notes-26.9.0.md` for the release-level summary of this change.
