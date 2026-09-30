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

**Action required for SQL Server users: SQL Server certificates are now
checked by default.** BridgeLink 26.9 upgrades the Microsoft SQL Server JDBC
driver from 8.4.1 to 12.10.2, which fixes CVE-2025-59250. From driver version
10.2 onward, Microsoft changed the default for the `encrypt` connection
setting from `false` to `true`, and the driver now checks the server's
certificate whenever the connection is encrypted. If you are upgrading from
BridgeLink 26.6 or earlier, SQL Server connections that did not set `encrypt`
were either unencrypted or encrypted without a certificate check. After the
upgrade they are encrypted and the certificate is checked. If your SQL Server
uses a self-signed certificate, a certificate from a CA the Java runtime
doesn't trust, or one whose name doesn't match the hostname in the URL, those
connections will fail. The error will contain "could not establish a secure
connection", "PKIX path building failed", or "Failed to validate the server
name". This can affect three places:

- **BridgeLink's own database** (`database.url` in `mirth.properties`): the
  server will not start. Installs that used the jTDS default for this
  database must rewrite the URL anyway; see the internal `mcserver` item
  below.
- **Database Reader and Database Writer connectors**: the server starts, but
  those channels will error.
- **Scripts that open their own SQL Server connections.**

BridgeLink does not change any connection URLs during the upgrade. Please
review them before you upgrade. The best fix is a SQL Server certificate that
BridgeLink can validate: one issued by a public CA, or by your internal CA
with its root added to the Java trust store. If that isn't possible, add
`;trustServerCertificate=true` to the affected URL. This keeps encryption on
but skips the certificate check. Adding `;encrypt=false` is not enough on its
own: if your SQL Server forces encryption, the connection is still encrypted
and the certificate is still checked. URLs that already set `encrypt=true` are
not affected. URLs that set `encrypt=false` are affected if the server forces
encryption.

- **jTDS is retired.** `jtds-1.3.1.jar` and its vendored TLS source patch are
  fully removed from every shipped location, and the "SQL Server/Sybase
  (jTDS)" driver entry is removed from both the driver dropdown
  (`DriverInfo`) and the runtime-authoritative `dbdrivers.xml`. **Sybase
  support is dropped along with it** - jTDS was BridgeLink's only Sybase
  JDBC option, and mssql-jdbc does not speak the Sybase/TDS dialect. If any
  channel in your environment connects to Sybase, that connectivity does not
  survive this upgrade.
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

## Linux Installers Run the Service as a Dedicated User (IRT-2333)

BridgeLink refuses to run as root. Earlier Linux installers (`.sh` and RPM)
registered the `blservice` systemd unit with no user, so a default install
started the server as root and it exited at first boot.

In this release both Linux installers run the service as a system user:
`bridgelink` for the RPM, and `bridgelink` or the account you choose in the
`.sh` installer.

- The installer creates the user if it does not exist and gives it ownership
  of the installation directory.
- It sets the account in a systemd drop-in,
  `/etc/systemd/system/blservice.service.d/10-bridgelink-user.conf`.
- The `.sh` installer asks which account to use. For an unattended install,
  set `unix_service_user` (for example `-Vunix_service_user=svc-bl`). Running
  as root is still possible with
  `"-Vunix_service_account_type=root (not recommended)"` (keep the quotes, the
  value contains spaces) or the same line in a response file. The service then
  starts only when `server.allowRoot = true` is set in `conf/mirth.properties`.
- On upgrade, the account the service already runs as is kept, including one
  you set yourself with `systemctl edit blservice`.
- Uninstalling removes the drop-in and leaves the user in place.

**The drop-in sets `NoNewPrivileges=true`.** Channel scripts can no longer
gain privileges through `sudo` or setuid programs. If a channel needs that,
do not edit the installer's drop-in: it is rewritten on every upgrade. Run
`systemctl edit blservice` instead, add `NoNewPrivileges=false` under
`[Service]`, save, and restart the service. That override is kept across
upgrades. An install that already runs under a `User=` drop-in of your own
does not get the installer's drop-in, so it does not get this setting.

---

## Windows Installer Runs the Service as a Virtual Account (IRT-2332)

BridgeLink refuses to run as an administrator, and Local System is one.
Earlier Windows installers defaulted to Local System and recommended it, so a
default install ended with a service that never started (`mirth.log` shows
`BridgeLink is running as root/Administrator` and nothing listens on 8443).

In this release the Windows installer runs `blservice` as
`NT SERVICE\blservice` by default. This is the service's own virtual account:
Windows manages it, it has no password, there is no user to create, and it is
not an administrator. The installer gives it write access to the installation
directory, which the server needs for `logs\` and `appdata\`.

- **Custom account** is still offered, for SQL Server Windows authentication,
  UNC file shares or other domain resources. It is granted the "Log on as a
  service" right and write access to the installation directory, as before.
- **Local System (not recommended)** is still offered. The service then starts
  only when `server.allowRoot = true` is set in `conf/mirth.properties`.
  Otherwise the installer does not start it and says why on the last screen
  and in the installer log.
- **On upgrade:**
  - A custom account is kept. Leave the password blank to keep the stored one.
    This now holds for unattended (`-q`) upgrades too: earlier installers
    registered `blservice` again during an unattended upgrade, which reset
    it to Local System and discarded the stored password, so the service
    failed to log on and did not start.
  - A service on Local System moves to the virtual account, unless
    `server.allowRoot = true` is set in `conf/mirth.properties`. That repairs
    a 26.6.x install that never started. A `-Dserver.allowRoot=true` line in
    `blservice.vmoptions` alone does not keep Local System; set it in
    `conf/mirth.properties`.
  - A service on Local Service or Network Service also moves to the virtual
    account.
- **Unattended installs:** the default needs no response-file line. For a
  custom account set `service_account_type=Custom account...`,
  `service_account_user` and `service_account_password`. In a response file,
  write a backslash in the user name twice (`service_account_user=.\\svc-bl`),
  because the file uses Java properties escaping. A `service_account_type=Local
  System` line from an older response file is read as the old default and
  gives the virtual account. To choose Local System on purpose, use
  `service_account_type=Local System (not recommended)`.

The virtual account reaches the network as the computer account. If a channel
reads a UNC share or connects to SQL Server with Windows authentication, grant
the computer account access, or install with a custom account.

---

## Unattended Windows Installs Install WebAdmin (IRT-2364)

An unattended Windows install (`-q`) used to skip WebAdmin without saying so,
while the response file it wrote still recorded `installWebAdmin=true`. It now
runs the WebAdmin installer silently and waits for it to finish, so the run
ends with the `BridgeLinkWebAdmin` service installed and listening on 8444.

- To skip WebAdmin on purpose, pass `-VinstallWebAdmin=false` or put
  `installWebAdmin=false` in the response file. The installer log then says
  `WebAdmin skipped`.
- If the WebAdmin installer fails or takes longer than 15 minutes, the
  installer log says `WebAdmin was not installed:` with the reason. The
  WebAdmin installer's own log is `webadmin-setup.log` in the installation
  directory.
- When WebAdmin was skipped or could not be installed, the installer no
  longer leaves `installWebAdmin=true` in effect for the rest of the run.

---

## RPM Upgrades Keep Your conf/ Files (IRT-2320)

Upgrading with `rpm -U` no longer overwrites your configuration. Earlier RPMs
replaced every file in `conf/` with the packaged template. That lost your
edits and the keystore password the server generated on first start, so after
the upgrade the server reported "started" but did not listen on HTTPS (8443).

This release's RPM marks the six conf files as configuration files: `mirth.properties`,
`dynamic-lookup.properties`, `log4j2.properties`, `log4j2-cli.properties`,
`mirth-cli-config.properties` and `dbdrivers.xml`. The launcher JVM option files
(`blservice.vmoptions`, `blserver.vmoptions`, `blcommand.vmoptions`) are kept
the same way, so a changed heap size (`-Xmx`) survives the upgrade. This also
protects an upgrade from 26.6.x.

- A file you have changed is kept. If the new template differs from the one
  your file came from, the template is written next to it as `<name>.rpmnew`,
  for example `conf/mirth.properties.rpmnew`. Compare the two if you want to
  pick up template changes; the new `mirth.properties` keys are already added
  to your file for you. Delete the `.rpmnew` file when you are done. If you
  edited `blcommand.vmoptions`, merge in the new `--add-opens` lines from
  `blcommand.vmoptions.rpmnew` so the CLI runs with the same options as a
  fresh install.
- A file you never changed is replaced with the new template.
- `rpm -e` keeps a changed file as `<name>.rpmsave` instead of deleting it.
  The server's keystore and database in `appdata/` are also left in place. If
  you reinstall afterwards, the new install starts on the packaged
  `mirth.properties`, which cannot open the old keystore, so HTTPS does not
  come up. Copy `conf/mirth.properties.rpmsave` back to
  `conf/mirth.properties` and run `systemctl restart blservice`.

The `.sh` installer already kept `conf/` and is unchanged.

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
  upgrade. The failure is silent: every login is rejected as an
  incorrect username or password, nothing is written to the server
  log, and each attempt still counts toward account lockout. Setting
  or changing a password does fail with a visible encryption error,
  because that path throws instead of silently returning false. An
  operator seeing only "incorrect credentials" is likely to start a
  credential reset rather than check the JVM options, so check
  `digest.iterations` first if every administrator is suddenly locked
  out after this upgrade. Before upgrading, add
  `-Dorg.bouncycastle.pbe.max_iteration_count` set to at least its
  `digest.iterations` value to the server's JVM options file
  (`mcservice-java9+.vmoptions`). Do not lower `digest.iterations` as
  a fix: stored password hashes do not record their iteration count,
  so lowering it makes every existing password fail to verify.
  Raising the cap also widens the bound CVE-2026-17508 places on
  untrusted PBKDF2 input, so keep it no higher than needed.
- **Removed legacy APIs.** 1.86 removes the deprecated
  `org.bouncycastle.pqc.crypto` ML-DSA, ML-KEM and SLH-DSA classes and
  the legacy Rainbow, Picnic, FrodoKEM and CMCE implementations. A
  channel script that imported the ML-DSA, ML-KEM or SLH-DSA classes
  must move to the standardized classes under `org.bouncycastle.crypto`;
  Rainbow and Picnic have no replacement. BridgeLink itself uses none
  of them.

**Upgrade impact:** deployments on default settings need no action.

---

## S3 File Connector - Default Credential Chain Fixed (IRT-2573)

On 26.9.0 builds before this fix, an S3-mode File Reader or File Writer set to use the
default credential provider chain (for example an EC2 instance role) failed: Test Read
returned an error and deployed channels failed at runtime. The AWS SDK for Java v2 shipped
with BridgeLink is upgraded to fix this.

| Library | Before | After |
|---------|--------|-------|
| AWS SDK for Java v2 | 2.15.28 | 2.55.8 |

- **Advanced S3 Settings region list.** The Administrator's region dropdown now lists the
  newer AWS regions (53, up from 31).
- **Static access keys and temporary credentials** work as before.

**Upgrade impact:** if you added AWS SDK v2 service jars of your own (for example SQS, SNS or
Secrets Manager clients) to `custom-lib` or to a plugin, replace them with version 2.55.8.
Service jars built for 2.15.28 fail against the upgraded SDK. When upgrading by hand rather
than with the installer, replace the whole `server-lib` directory so no 2.15.28 jars remain.

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

## Stuck Channels - Stop Grace Period, Thread Diagnostics and Bounded Halt (IRT-2107)

A channel whose connector is waiting on something that never answers, such as
a partner system that accepts a connection and then goes silent, or a database
call that never returns, can no longer take the rest of the server with it.
Before this release Stop never answered for such a channel, Halt could hang on
the same work, and because undeploy stops a channel first, one stuck channel
could hold up Redeploy All and a server shutdown for every other channel.

**Stop**

- **Stop has a grace period.** A stop now waits at most
  `server.channelstopgraceperiod` seconds (Settings > Server > "Channel Stop
  Grace Period", default 120) for its dispatch threads, queue threads and
  connector stop hooks. When the period runs out the stop fails with an error
  that names the stuck thread and its top stack frames, and the channel stays
  Stopping. Nothing escalates to halt on its own. Keep the period at ten
  seconds or more: a source queue thread polls in one-second slices, so a very
  short period trips on healthy stops. Setting it to 0 restores the previous
  wait-forever behaviour for stop; it does not affect halt. It also switches
  off the overdue flag described under Diagnostics, so with 0 the Web Admin
  no longer marks a channel that is stuck Stopping or Starting.
- **A stop that ran out of time finishes on its own once its work does.** When
  the threads the stop gave up on finish, or, for a pooled thread such as a
  web server's, once it leaves the channel, the stop completes and the channel
  reaches Stopped without an operator touching it. A channel whose threads
  never finish still needs a halt, and so does one where repeated attempts to
  finish the stop keep running out of time: after ten it stops trying and logs
  that it has.
- **Only stop is bounded.** Deploy, start, pause, resume and
  remove-all-messages wait without limit, exactly as before. Stop is what the
  setting is named for and the only operation an operator is told to halt out
  of. The one exception is a channel lock held by a thread a halt has already
  abandoned: that holder will never let go, so start, pause, resume and
  remove-all-messages fail at once, name the operation that is stuck, and tell
  you to undeploy the channel and deploy it again to rebuild it.
- **Undeploy and Redeploy All no longer wait forever on one channel.**
  Undeploying a running channel stops it first, so it inherits the grace
  period. If the channel does not stop in time the undeploy stops there, the
  channel is left Stopping, and the rest of an Undeploy All or Redeploy All
  carries on. An undeploy or redeploy of a channel that is already Stopping is
  refused with advice to halt first, because tearing it down under a live
  dispatch thread could deliver a message twice. When a timed-out stop later
  completes on its own the undeploy is not resumed, so issue it again.
  Undeploy's own connector hooks are not time-limited, except after a forced
  halt (see below). Raise the grace period if your channels legitimately take
  longer than it to stop.

**Diagnostics**

- **New endpoint `GET /channels/{channelId}/_threads`.** Returns every live
  thread that belongs to the channel (dispatch, source and destination queues,
  chains, recovery, connector receivers, lifecycle hooks and channel scripts)
  with its state, the lock it is blocked on and its top stack frames, plus
  what the most recent stop timed out waiting on. Plain JSON, stack frames
  only: never message content or connector settings. Requires the dashboard
  view permission. The Web Admin shows it as Thread Diagnostics on the
  dashboard.
- **Dashboard status carries `stateSince` and `lifecycleOverdue`.** The flag
  is set when a channel has been Stopping or Starting longer than the grace
  period, so the Web Admin can offer the diagnostics and the halt.
- **Cancelled channel scripts are visible.** A script whose caller gave up on
  it but whose thread is still blocked inside a Java call now appears in the
  threads endpoint flagged as a cancelled script.

**Halt**

- **Halt still reacts immediately, and now it also finishes.** As before, halt
  shuts down the channel executor, stops the source queue, interrupts every
  busy dispatch thread and tells each connector to shut down, all without
  waiting. What is new is the ending: instead of blocking forever on the
  channel lock, halt gives the threads it interrupted a short fixed interval
  (two seconds) to wind down, then marks the channel Stopped regardless.
  Whatever is still running is logged with its stack frames and recorded as
  abandoned, and the next halt interrupts it again. Abandoned threads stay
  visible in the threads endpoint, flagged `abandoned`, once the channel is
  redeployed; the endpoint answers only for a deployed channel, so between
  undeploy and deploy it returns 404.
- **Halt does not use the stop grace period.** Its wind-down interval is fixed
  and deliberately short, because halt is the emergency action: an operator
  who shortens the grace period to see stop diagnostics is not asking halt to
  take longer, and one who lengthens it is not asking halt to take minutes.
  The two-second wait exists only so a thread that is milliseconds from a
  clean exit is not reported as abandoned. A grace period of 0 cannot make
  halt unbounded.
- **Forced mode.** If a wedged stop or start still holds the channel's
  lifecycle lock when that interval expires, halt proceeds without it,
  interrupts the holder, and reports it. The same applies to undeploy after a
  forced halt, which runs the connector undeploy hooks on halt's short
  interval, so a redeploy always builds a fresh channel instance and is never
  blocked by the old one.
- **Halt interrupts what a stop gave up on, and a later start does not wait
  for it.** A thread that ignored an interrupt is not closer to finishing than
  when it was abandoned, and making a start queue behind it would hand the
  stuck thread control of the next operation too. Instead the write it would
  have made is dropped: a connector state update from an abandoned thread is
  ignored, so a hook that finishes an hour later cannot report a connector
  stopped underneath a channel that has since restarted. A halt that gives up
  on a connector's hook also marks that connector Stopped itself, so the next
  start does not skip it.
- **Abandoned queue threads stay retired.** A destination or source queue
  thread a halt gave up on exits when its blocked call finally returns,
  instead of resuming next to the restarted queue thread as a second sender.
- **Stale permit protection.** A dispatch thread abandoned by a halt that
  completes after a restart cannot release a permit into the restarted
  channel's process lock.
- **A halt during a deploy is not overtaken.** A deploy that goes on to start
  the channel releases the channel's lock in between, and a halt could land in
  that gap, mark the channel Stopped, and then be undone by the start that
  followed. The channel is now left deployed and Stopped with an error in the
  log, and you start it explicitly if that is what you wanted.
- **The REST halt call returns within a bounded time**, about 34 seconds
  (twice the wind-down interval plus a 30-second margin for a task that never
  started), and reports a timeout error if the task is still running; the task
  itself continues.

**What has not changed**

Halt still trades a possible duplicate delivery for never losing a message. A
message that was part-way through delivery may be delivered a second time once
the channel is started again, and because a start no longer waits for
abandoned threads, restarting soon after a halt makes that duplicate reachable
sooner. A message is still never lost. The Web Admin's halt confirmation now
says so.

---
