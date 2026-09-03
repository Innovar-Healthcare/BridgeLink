# BridgeLink 26.9.0 Release Notes

## 🔐 SFTP / jsch Transport Upgrade (IRT-1541, CVE-12)

The File connector's SFTP transport now runs on a current release of
`com.github.mwiede:jsch`, closing out staleness/CVE-hygiene exposure on the
previously bundled version and maximizing distance from future advisories.

| Library | Before | After |
|---------|--------|-------|
| jsch (com.github.mwiede) | 0.2.18 | 2.28.5 |

- The File connector's SFTP scheme (`SftpConnection`, `SftpSchemeProperties`) is a
  drop-in upgrade — no API changes, no channel reconfiguration required for
  existing SFTP channels connecting to modern servers.
- jsch retains its **secure-by-default hardened algorithm set**, which excludes a
  handful of weak legacy algorithm families: `diffie-hellman-group14-sha1` key
  exchange, the plain `ssh-rsa` host-key/pubkey signature, the `3des-cbc`/`aes128-cbc`
  ciphers, and the non-ETM `hmac-sha1` MAC. **This hardened posture is unchanged by
  the 2.28.5 upgrade** — these families were already excluded from jsch's defaults
  at 0.2.18 (inherited from the original JCraft-to-mwiede fork migration); the
  version bump itself only adds algorithms (post-quantum kex, OpenSSH certificate
  host keys) and reorders cipher preference (AES-GCM now preferred over AES-CTR).
  It does not remove anything a 0.2.18-based channel relied on.
- **Legacy-server impact:** SFTP channels connecting to older or unpatched servers
  that offer *only* algorithms from the excluded families above may fail
  negotiation with `com.jcraft.jsch.JSchAlgoNegoFailException: Algorithm
  negotiation fail`. This is expected — it is the same secure-by-default behavior
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
  support is dropped along with it** — jTDS was BridgeLink's only Sybase
  JDBC option, and mssql-jdbc does not speak the Sybase/TDS dialect. If any
  channel in your environment connects to Sybase, that connectivity does not
  survive this upgrade.
- **`encrypt=true` default, unchanged since mssql-jdbc 10.2.** mssql-jdbc has
  validated the server's TLS certificate by default since driver version
  10.2 — this is not new behavior introduced by the 12.10.2 upgrade. A
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
  **before** starting the server on 26.9 — this is a manual pre-upgrade step,
  not an automated migration. The persisted driver list is migrated
  automatically on first startup (a server-side migrator strips the retired
  jTDS entry), but that driver-list cleanup does not rewrite your
  `mirth.properties` connection settings.
- For the full migration runbook — the exact escape-hatch key/value, the
  jTDS-to-mssql-jdbc URL grammar migration, and the internal database
  migration steps — see:
  **[`docs/mssql-jdbc-encryption-compatibility.md`](./mssql-jdbc-encryption-compatibility.md)**.

---

## Stuck-channel diagnostics and bounded stop (IRT-2107, part 1)

A channel that will not stop no longer sits in Stopping with nothing to look at.

- **Stop has a grace period.** A stop now waits at most `server.channelstopgraceperiod`
  seconds (Settings > Server > "Channel Stop Grace Period", default 120) for its dispatch
  threads, queue threads and connector stop hooks. When the period runs out the stop fails
  with an error that names the stuck thread and its top stack frames, and the channel stays
  Stopping so the operator can decide between waiting and halting. Nothing escalates to halt
  on its own, and an undeploy or redeploy of a channel in that state is refused with the same
  advice, because tearing it down under a live dispatch thread could deliver a message twice.
  Halt interrupts the threads the stop gave up on; a later start waits one more grace period
  for them. Setting the period to 0 restores the previous wait-forever behaviour.
- **New endpoint `GET /channels/{channelId}/_threads`.** Returns every live thread that
  belongs to the channel (dispatch, source and destination queues, chains, recovery, connector
  receivers, lifecycle hooks and channel scripts) with its state, the lock it is blocked on and
  its top stack frames, plus what the most recent stop timed out waiting on. Plain JSON, stack
  frames only: never message content or connector settings. Requires the dashboard view
  permission.
- **Dashboard status carries `stateSince` and `lifecycleOverdue`.** The flag is set when a
  channel has been Stopping or Starting longer than the grace period, so the Web UI can offer
  the diagnostics and the halt.
- **Cancelled channel scripts are visible.** A script whose caller gave up on it but whose
  thread is still blocked inside a Java call now appears in the threads endpoint flagged as a
  cancelled script.

Part 2 (bounded halt and forced undeploy) follows in a separate change.

---
