# mssql-jdbc Encryption & Compatibility Runbook

BridgeLink 26.9 upgrades the bundled Microsoft SQL Server JDBC driver
(`mssql-jdbc`) from `8.4.1.jre8` to `12.10.2.jre11` and retires the co-shipped
`jTDS` driver entirely. This runbook covers everything a customer needs to
migrate cleanly: the encryption-default change and its escape hatch, the
channel-URL migration for customers who used jTDS, the loss of Sybase support,
and the internal server-database migration for installs that run their own
backing store on SQL Server. See `docs/release-notes-26.9.0.md` for the
release-level summary of this change.

## 1. The `encrypt=true` default and the certificate-validation failure

### Why connections to self-signed or untrusted-cert SQL Servers may fail

mssql-jdbc has defaulted its `encrypt` connection property to `true` and
validated the server's TLS certificate **since driver version 10.2** — this is
not new behavior introduced by the 26.9 upgrade to 12.10.2. If your SQL Server
presents a self-signed certificate, or a certificate that does not chain to a
trusted root, or whose Common Name/SAN does not match the hostname used to
connect, an encrypted JDBC connection with no further configuration will now
fail with an exception whose signature looks like:

```
com.microsoft.sqlserver.jdbc.SQLServerException: The driver could not establish
a secure connection to SQL Server by using Secure Sockets Layer (SSL)
encryption... PKIX path building failed... unable to find valid certification
path to requested target
```

or, for a hostname/certificate-name mismatch:

```
com.microsoft.sqlserver.jdbc.SQLServerException: The driver could not establish
a secure connection to SQL Server by using Secure Sockets Layer (SSL)
encryption: Failed to validate the server name in a certificate...
```

If you see `could not establish a secure connection`, `PKIX`, or `Failed to
validate the server name` in `mirth.log` for a Database Reader or Database
Writer connector, this section documents the supported, per-connection way to
restore connectivity to that specific SQL Server — without disabling
encryption or certificate validation for every other JDBC connection in the
instance.

**Do not read this as "the 26.9 upgrade removed support for self-signed SQL
Servers."** The secure-by-default posture is intentional and unchanged by this
upgrade; only the driver version (and the CVE fixes that come with it) moved.

### The escape hatch

Append the following to the JDBC connection URL used by the affected Database
Reader or Database Writer connector:

```
;trustServerCertificate=true
```

For example, a URL that previously read:

```
jdbc:sqlserver://myserver:1433;databaseName=mydb
```

becomes:

```
jdbc:sqlserver://myserver:1433;databaseName=mydb;trustServerCertificate=true
```

This value is copied **byte-identically** from the proven-working smoke-test
fixture `smoke-tests/channels/jdbc-mssql-selfsigned-workaround-test.xml` — the
same live-verified break-then-fix pair that proves the secure default actually
rejects a self-signed server (the negative leg) and that this exact value
restores connectivity (the workaround leg).

An equivalent, broader alternative is `;encrypt=false`, which disables
encryption entirely rather than merely relaxing certificate validation. Prefer
`trustServerCertificate=true` unless you have a specific reason to disable
encryption outright.

**Caution:** `trustServerCertificate=true` disables certificate validation for
that connection — it accepts *any* certificate the server presents, including
one from an attacker performing a man-in-the-middle attack on the network
path. This is a deliberate, legacy-compatibility opt-out, not the recommended
posture. Only apply it to the specific connection(s) that need to reach a
self-signed or otherwise untrusted-cert SQL Server, and prefer, where
possible, installing a certificate the client can actually validate (a
CA-issued certificate, or your organization's internal CA root added to the
JVM's trust store) over disabling validation. Do not set this as a
connector-wide or instance-wide default "to be safe" — that would silently
weaken every JDBC MSSQL connection in the instance, not just the one that
needs it.

## 2. Customer channel URL migration: jTDS to mssql-jdbc

BridgeLink 26.9 **fully removes** the bundled jTDS driver
(`net.sourceforge.jtds.jdbc.Driver`, `jtds-1.3.1.jar`) — it is no longer
offered in the driver dropdown, and the jar is no longer shipped. Any channel
whose Database Reader or Database Writer was configured to use jTDS must be
migrated to mssql-jdbc **before** upgrading, or that channel will fail to
deploy with a `ClassNotFoundException` for
`net.sourceforge.jtds.jdbc.Driver` after the upgrade.

There is no automated rewrite of channel-stored jTDS connection strings —
review each affected channel and update it manually:

### Driver class

| Before (jTDS) | After (mssql-jdbc) |
|---|---|
| `net.sourceforge.jtds.jdbc.Driver` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` |

### Connection URL grammar

jTDS and mssql-jdbc use different URL grammars — this is not a simple
find-and-replace of the scheme prefix:

```
Before (jTDS):     jdbc:jtds:sqlserver://host:port/dbname
After (mssql-jdbc): jdbc:sqlserver://host:port;databaseName=dbname
```

Note the differences: the path-style `/dbname` suffix becomes a
semicolon-delimited `;databaseName=dbname` property, and the `:jtds:` segment
is dropped entirely. If the channel's URL also carried jTDS-specific
properties (e.g. `instance=`, `domain=` for Windows/NTLM auth, or
`ssl=require`), review the
[mssql-jdbc connection property reference](https://learn.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties)
for the equivalent mssql-jdbc property name — most have a direct analog, but
none are auto-translated.

Remember: mssql-jdbc's `encrypt=true` default applies to every migrated
connection, so a channel connecting to a self-signed or untrusted-cert SQL
Server will also need the escape hatch from Section 1 above appended to its
newly-migrated URL.

## 3. Sybase support has been dropped

The retired jTDS driver was BridgeLink's **only** supported Sybase JDBC
option (the dropdown entry was literally labeled "SQL Server/Sybase (jTDS)").
mssql-jdbc is a SQL-Server-only driver — it does not speak the Sybase/TDS
dialect jTDS's Sybase mode used.

**If you have any channel connecting to a Sybase database, that connectivity
is lost by this upgrade.** There is no in-place replacement driver bundled
with 26.9. If you have a genuine Sybase integration need, do not upgrade that
channel's environment until you have sourced and validated a maintained
third-party Sybase JDBC driver independently — this is explicitly out of
scope for the 26.9 CVE-elimination work and is tracked separately.

## 4. Internal server database (`mcserver`) migration

This section applies **only** to installs where BridgeLink's own internal
backing database — the `mcserver` configuration in `mirth.properties`, not a
customer integration channel — is configured with `database = sqlserver`.
Installs using Derby, MySQL, or PostgreSQL as the internal database are
unaffected by this section.

Before this upgrade, an internal SQL Server backing store used jTDS
(`net.sourceforge.jtds.jdbc.Driver`) with a `jdbc:jtds:sqlserver://...` URL.
After this upgrade, BridgeLink's internal default for `database = sqlserver`
is mssql-jdbc (`com.microsoft.sqlserver.jdbc.SQLServerDriver`). This is a
**manual pre-upgrade step**, not an automated migration — you must update your
install's `mirth.properties` (and any pinned `database.driver` override)
**before** starting the server on 26.9:

1. Update `database.url` from the jTDS grammar to the mssql-jdbc grammar
   (Section 2 above) — e.g.:

   ```
   Before: database.url = jdbc:jtds:sqlserver://dbhost:1433/mirthdb
   After:  database.url = jdbc:sqlserver://dbhost:1433;databaseName=mirthdb
   ```

2. If `mirth.properties` pins `database.driver` explicitly to the jTDS class
   name, update it to `com.microsoft.sqlserver.jdbc.SQLServerDriver`. If it is
   left unset (the common case), BridgeLink's internal default mapping now
   resolves to mssql-jdbc automatically once `database = sqlserver` is set.

3. **`encrypt=true` now applies to the server's own connection to its
   internal database, too** — not just customer channels. If your internal
   SQL Server backing store presents a self-signed or otherwise
   untrusted-cert certificate, append the same
   `;trustServerCertificate=true` escape hatch from Section 1 to
   `database.url`, or the server itself will fail to start with the same
   certificate-validation failure documented above.

4. BridgeLink's persisted driver list (`dbdrivers.xml`) is migrated
   automatically on first startup after the upgrade — a server-side migrator
   strips the retired jTDS entry from the persisted list — but this driver-list
   migration is separate from, and does not replace, the manual
   `mirth.properties` steps above. The migrator only cleans up the driver
   dropdown; it does not rewrite your `database.url` or `database.driver`
   settings.

If you skip these manual steps, the server will fail to start with a
`ClassNotFoundException` for `net.sourceforge.jtds.jdbc.Driver` (if
`database.driver` was pinned) or a connection failure against the
old-grammar URL.
