# BridgeLink 26.9.0 Release Notes

## 🔐 SFTP / jsch Transport Upgrade (IRT-1541, CVE-12)

The File connector's SFTP transport now runs on a current release of
`com.github.mwiede:jsch`, closing out staleness/CVE-hygiene exposure on the
previously bundled version and maximizing distance from future advisories.

| Library | Before | After |
|---------|--------|-------|
| jsch (com.github.mwiede) | 0.2.18 | 2.28.5 |

- The File connector's SFTP scheme (`SftpConnection`, `SftpSchemeProperties`) is a
  drop-in upgrade - no API changes, no channel reconfiguration required for
  existing SFTP channels connecting to modern servers.
- jsch retains its **secure-by-default hardened algorithm set**, which excludes a
  handful of weak legacy algorithm families: `diffie-hellman-group14-sha1` key
  exchange, the plain `ssh-rsa` host-key/pubkey signature, the `3des-cbc`/`aes128-cbc`
  ciphers, and the non-ETM `hmac-sha1` MAC. **This hardened posture is unchanged by
  the 2.28.5 upgrade** - these families were already excluded from jsch's defaults
  at 0.2.18 (inherited from the original JCraft-to-mwiede fork migration); the
  version bump itself only adds algorithms (post-quantum kex, OpenSSH certificate
  host keys) and reorders cipher preference (AES-GCM now preferred over AES-CTR).
  It does not remove anything a 0.2.18-based channel relied on.
- **Legacy-server impact:** SFTP channels connecting to older or unpatched servers
  that offer *only* algorithms from the excluded families above may fail
  negotiation with `com.jcraft.jsch.JSchAlgoNegoFailException: Algorithm
  negotiation fail`. This is expected - it is the same secure-by-default behavior
  the connector has always had, not a new regression introduced by this upgrade.
- **Workaround:** each affected channel's per-channel `configurationSettings`
  (the SFTP scheme's advanced Configuration Settings table, piped straight to
  jsch's `session.setConfig()`) can restore connectivity to a specific legacy
  server without weakening the defaults for any other channel.
- For the exact `configurationSettings` key/value pairs to use for each excluded
  algorithm family, see the full configuration runbook:
  **[`docs/sftp-legacy-algorithm-compatibility.md`](./sftp-legacy-algorithm-compatibility.md)**.

---

## 🔐 mssql-jdbc Driver Upgrade + jTDS Retirement (CVE-04)

The JDBC connector's Microsoft SQL Server driver is upgraded to a current,
actively-maintained release, and the bundled `jTDS` driver is retired
entirely.

| Library | Before | After |
|---------|--------|-------|
| mssql-jdbc | 8.4.1.jre8 | 12.10.2.jre11 |

- **jTDS is retired.** `jtds-1.3.1.jar` and its vendored TLS source patch are
  fully removed from every shipped location, and the "SQL Server/Sybase
  (jTDS)" driver entry is removed from both the driver dropdown
  (`DriverInfo`) and the runtime-authoritative `dbdrivers.xml`. **Sybase
  support is dropped along with it** - jTDS was BridgeLink's only Sybase
  JDBC option, and mssql-jdbc does not speak the Sybase/TDS dialect. If any
  channel in your environment connects to Sybase, that connectivity does not
  survive this upgrade.
- **`encrypt=true` default, unchanged since mssql-jdbc 10.2.** mssql-jdbc has
  validated the server's TLS certificate by default since driver version
  10.2 - this is not new behavior introduced by the 12.10.2 upgrade. A
  connection to a SQL Server presenting a self-signed or otherwise
  untrusted certificate will fail with a
  `could not establish a secure connection` / PKIX / server-name-validation
  error unless the connection string opts in to
  `;trustServerCertificate=true` (or `;encrypt=false`).
- **Customer channel URL migration required.** Any channel previously
  configured with the jTDS driver
  (`net.sourceforge.jtds.jdbc.Driver`, `jdbc:jtds:sqlserver://host:port/db`)
  must be migrated to mssql-jdbc
  (`com.microsoft.sqlserver.jdbc.SQLServerDriver`,
  `jdbc:sqlserver://host:port;databaseName=db`) **before** upgrading, or the
  channel will fail to deploy with a `ClassNotFoundException` after the
  upgrade. There is no automated rewrite of channel-stored connection
  strings.
- **Internal `mcserver` database migration (SQL-Server-backed installs
  only).** Installs running BridgeLink's own internal backing store on SQL
  Server (`database = sqlserver` in `mirth.properties`) must manually update
  `database.url` (and any pinned `database.driver`) to the mssql-jdbc form
  **before** starting the server on 26.9 - this is a manual pre-upgrade step,
  not an automated migration. The persisted driver list is migrated
  automatically on first startup (a server-side migrator strips the retired
  jTDS entry), but that driver-list cleanup does not rewrite your
  `mirth.properties` connection settings.
- For the full migration runbook - the exact escape-hatch key/value, the
  jTDS-to-mssql-jdbc URL grammar migration, and the internal database
  migration steps - see:
  **[`docs/mssql-jdbc-encryption-compatibility.md`](./mssql-jdbc-encryption-compatibility.md)**.

---

## Windows / Default-Encoding Upgrade Safety (JEP 400, IRT-2217)

Starting with Java 18, JEP 400 changed the JVM's platform default charset
from the host's locale-driven encoding to UTF-8 on every operating system.
Any connector whose Encoding is left at "Default" (`DEFAULT_ENCODING`)
follows that platform default: on a pre-Java-18 Windows server it resolved to
windows-1252, and on Java 18 and later it resolves to UTF-8.

**Embedded Derby installs must be on Java 21 before you upgrade.** The
installer media bundle no JRE and accept a host JVM of Java 17 through 21.
Embedded Derby 10.17 in this release requires Java 21. On an older JVM the
server exits before touching the database, logging:

```
embedded Derby requires Java 21+ as of 26.9; upgrade Java or switch to an external database
```

An install on an external database (MySQL, PostgreSQL, SQL Server) may stay on
Java 17. Moving a Windows install from Java 17 to Java 21 is what exposes it
to the charset change above.

**Affected connectors:** TCP/MLLP, File (text mode), Database (byte columns)
and SMTP -- the connectors whose Encoding field is commonly left at "Default".
HTTP connectors ship an explicit UTF-8 default rather than "Default", so they
are affected only where an administrator changed that field: to "Default" on a
Listener or a Sender, or to "NONE" on a Sender whose remote response omits a
charset.

**What else changed in this release.** Two changes beyond the JVM default,
both of which alter how "Default" resolves:

- TCP/MLLP and HTTP connectors on "Default" now honor the server-wide encoding
  property, which the File, Database and SMTP connectors always did (IRT-1780).
  If `ca.uhn.hl7v2.llp.charset` is already set in your `mirth.properties`,
  check its value before upgrading: those connectors previously ignored it and
  will now follow it, on any Java version.
- `server.defaultencoding` is new, as the documented name for that same value.
  It takes precedence over the legacy alias when both are set (IRT-1913).

Beyond those, `CharsetUtils`'s resolution order is unchanged: the server-wide
property when set, otherwise the JVM platform default.

**How to tell whether this affects you.** At every startup the server compares
the JVM default charset against the host's native encoding and writes a WARN
to `mirth.log` when they differ (IRT-1914). In the log from the most recent
startup, search for:

```
JVM default charset is ... but the host (native) encoding is ...
```

`mirth.log` rolls at 500KB, so check it soon after the restart. Read the
absence of that warning narrowly. It means the two values agree on this host,
or a server-wide encoding is already pinned. It does not clear you for either
change listed above, and it compares against the current host rather than
whatever the previous install ran on.

**Remedy.** To keep the pre-Java-18 host encoding for `DEFAULT_ENCODING`
connectors, set in `conf/mirth.properties`:

```
server.defaultencoding = windows-1252
```

The legacy alias `ca.uhn.hl7v2.llp.charset` is still honored, but
`server.defaultencoding` takes precedence when both are set. As an
alternative to a server-wide setting, pin the Encoding field on each affected
connector individually instead of relying on "Default".

**What this setting does not cover.** Channel scripts calling `FileUtil.read`
or `FileUtil.write` without an explicit charset, and the Document Writer's RTF
output, follow the JVM default directly; `server.defaultencoding` does not
govern them. Pass an explicit charset in those scripts.

---

## Security - Inherited Mirth XXE and SQL Injection CVEs (IRT-2262, ICSMA-26-253-01)

- **CVE-2026-78224 (XSLT Transformer Step no longer resolves external
  entities/stylesheets).** The XSLT Transformer Step's generated transform
  script now restricts the underlying `TransformerFactory` from resolving
  external DTDs and external stylesheets. The runtime effect differs by
  reference type: a source XML or XSLT template that declares an external
  DTD or an external general entity (a `SYSTEM` DOCTYPE) now fails the XSLT step,
  with the error logged at the ERROR level - older CDA/HL7v3 feeds
  carrying a `<!DOCTYPE ... SYSTEM "http://...">` will now fail the step,
  and operators should expect and investigate that failure rather than a
  resolved reference. A stylesheet that reaches an external reference through
  the stylesheet side - an `xsl:import`/`xsl:include` of an external href, or
  a `document()` call with a `SYSTEM`/URL argument - likewise now fails the
  step at the ERROR level rather than resolving the reference. A stylesheet
  that legitimately loads a lookup table via `document()` will need that data
  supplied another way. Internal, self-contained transforms (no external
  DTD/entity/stylesheet reference) are unaffected.

- **CVE-2026-82578 (XML batch processing with the XPath split option no
  longer resolves external entities/DTDs).** Inbound batch XML is now parsed
  through a hardened parser before the XPath split query runs. A batch whose
  DOCTYPE previously pulled in an external entity or an external DTD will no
  longer fetch it - the split proceeds without resolving that reference. A
  batch that declares only an internal DTD subset (no external reference)
  still parses and splits normally.

- **CVE-2026-82583 (Database Connector Get Tables API reduces the
  `selectLimit` SQL injection vector to a single validated SELECT
  statement).** The Database Connector's Get Tables metadata API
  (`POST /connectors/jdbc/_getTables`) now validates the table/schema
  identifiers and the `selectLimit` query template before using them to
  retrieve column metadata. The caller-supplied `selectLimit` is executed
  only when it matches a recognized safe single-table SELECT metadata-
  probe shape - the shipped `LIMIT`, `TOP`, `WHERE ROWNUM`, and
  `FETCH FIRST` forms over `SELECT * FROM ?` - and a matching template
  runs under a five-second query timeout and a one-row cap. Anything that
  does not match a recognized safe shape falls back to the existing
  injection-free JDBC metadata path instead of being executed. This closes
  the arbitrary and stacked-statement SQL execution vector, including
  single-statement side effects (file write, large-object export/import,
  SSRF) and blind or time-based inference within an otherwise well-formed
  SELECT statement.

  **Observable behavior change.** A custom `selectLimit` that does not
  match one of the recognized safe shapes is no longer executed as-is;
  column metadata for that table is retrieved through the generic JDBC
  metadata path instead. The default `selectLimit`
  (`SELECT * FROM ? LIMIT 1`) and the other shipped per-dialect shapes are
  unaffected, and now run bounded by a five-second query timeout and a
  one-row cap.

---

## Security - BouncyCastle 1.86 (IRT-2441, CVE-2026-8763, CVE-2026-13506)

BouncyCastle is upgraded from 1.84 to 1.86 in the server, Administrator
client and CLI libraries.

| Library | Before | After |
|---------|--------|-------|
| bcprov-jdk18on | 1.84 | 1.86 |
| bcpkix-jdk18on | 1.84 | 1.86 |
| bcutil-jdk18on | 1.84 | 1.86 |

- **CVE-2026-8763 (critical).** An X.509 name-constraints bypass (a
  trailing dot on a DNS name) in certificate-path validation performed
  through BouncyCastle, fixed upstream in 1.85.
- **CVE-2026-13506 (high).** Denial of service from deeply nested ASN.1
  structures under lazy parsing, fixed upstream in 1.85.
- **Stricter ASN.1 time decoding.** Since 1.85, BouncyCastle rejects
  structurally malformed ASN.1 UTCTime and GeneralizedTime values when
  decoding. That reaches channel scripts and plugins that parse or
  verify signatures, certificates or CRLs with BouncyCastle, not only
  CMS. A zone-less UTCTime (`YYMMDDHHMMSS` without the trailing `Z`)
  can be re-admitted by adding
  `-Dorg.bouncycastle.asn1.allow_zoneless_utctime=true` to the server's
  JVM options. That admits only that one shape; every other malformed
  value is still rejected. BridgeLink does not ship the option enabled,
  and nothing in the product sets it.
- **BouncyCastle's own PKCS12 keystore.** Since 1.85, BouncyCastle's
  PKCS12 implementation, reached only through an explicit
  `KeyStore.getInstance("PKCS12", "BC")`, writes keystores with a
  default PBE iteration count of 600,000 instead of 51,200. Storing and
  loading such a keystore therefore costs roughly twelve times more.
  1.86 adds `org.bouncycastle.pkcs12.store_it_count` to set the
  write-side count. This affects channel scripts and plugins that
  request BouncyCastle's PKCS12 explicitly. BridgeLink's own keystore
  (`keystore.type`, JCEKS by default or PKCS12) is served by the JDK's
  providers, not BouncyCastle, and is unaffected.
- **PBKDF2 iteration cap (CVE-2026-17508).** BouncyCastle 1.86 rejects
  a raw JCA PBKDF2 derivation above 10,000,000 iterations (the
  `org.bouncycastle.pbe.max_iteration_count` system property, default
  10,000,000). BridgeLink hashes administrator passwords with PBKDF2 at
  the `digest.iterations` setting (default 600,000, unaffected).

  **Upgrade impact:** a deployment that set `digest.iterations` above
  10,000,000 will find every administrator login failing after the
  upgrade. Before upgrading, add
  `-Dorg.bouncycastle.pbe.max_iteration_count` set to at least its
  `digest.iterations` value to the server's JVM options. Do not lower
  `digest.iterations` as a fix: stored password hashes do not record
  their iteration count, so lowering it makes every existing password
  fail to verify. Raising the cap also widens the bound
  CVE-2026-17508 places on untrusted PBKDF2 input, so keep it no
  higher than needed.
- **Removed legacy APIs.** 1.86 removes the deprecated
  `org.bouncycastle.pqc.crypto` ML-DSA, ML-KEM and SLH-DSA classes and
  the legacy Rainbow, Picnic, FrodoKEM and CMCE implementations. A
  channel script that imported the ML-DSA, ML-KEM or SLH-DSA classes
  must move to the standardized classes under `org.bouncycastle.crypto`;
  Rainbow and Picnic have no replacement. BridgeLink itself uses none
  of them.

**Upgrade impact:** deployments on default settings need no action.

---

## Database - 26.6.1 Migration Rung and Fail-Loud Unknown-Version Startup (IRT-2329)

A database created or last upgraded by `release/26.6.1` (`SCHEMA_INFO.VERSION =
"26.6.1"`) now upgrades cleanly to 26.9.0. Previously, 26.9.0 did not recognize
the `26.6.1` schema-version string, and startup silently coerced the unrecognized
version to `V0` and replayed the entire legacy migration ladder against an
already-populated schema - a data-integrity hazard that could abort startup
outright.

- **New `v26_6_1` schema-version rung.** `26.6.1` is schema-identical to
  `26.6.0` (it added only a no-op migrator upstream, no database delta), so
  the new rung runs no migration logic of its own. `Migrate26_9_0` is
  unchanged and unaffected by the new rung - it still only rewrites the
  persisted JDBC driver list (mssql-jdbc/jTDS retirement, CVE-04) and does
  not branch on the starting schema version.
- **Fail-loud on an unrecognized persisted schema version.** This release
  also hardens `ServerMigrator` (IRT-2295 scope item 3): if `SCHEMA_INFO`
  holds a version string the server does not recognize, startup now aborts
  immediately with a `MigrationException` naming the offending string,
  instead of silently replaying the migration ladder from the beginning. A
  database with no `SCHEMA_INFO` row at all (a fresh install) is unaffected
  - that case still initializes normally.

**Upgrade impact:** deployments already running `release/26.6.1` upgrade to
26.9.0 with no manual database intervention required.

---
