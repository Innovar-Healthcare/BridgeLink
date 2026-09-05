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
  Keep the period at ten seconds or more: a source queue thread polls in one-second slices, so a
  very short period trips on healthy stops.
  Halt interrupts the threads the stop gave up on. A later start does **not** wait for them: a
  thread that ignored an interrupt is not closer to finishing than when it was abandoned, and
  making a start queue behind it would hand the stuck thread control of the next operation too.
  Instead the write it would have made is dropped -- a connector state update from an abandoned
  thread is ignored, so a hook that finishes an hour later cannot report the connector stopped
  underneath a channel that has since restarted. A halt that gives up on a connector's hook also
  marks that connector Stopped itself, so the next start does not skip it. One consequence to know:
  because a start no longer waits, restarting a channel while an abandoned send is still in flight
  can deliver that message a second time -- the same duplicate a halt has always risked, now
  reachable sooner. A message is still never lost. Setting the period to 0 restores the previous
  wait-forever behaviour **for stop**; it does not affect halt, which has its own fixed interval.
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

## Bounded halt and forced undeploy (IRT-2107, part 2)

Halt now reaches Stopped in bounded time no matter what a connector or script is doing.

- **Halt still reacts immediately, and now it also finishes.** As before, halt shuts down the
  channel executor, stops the source queue, interrupts every busy dispatch thread and tells each
  connector to shut down, all without waiting. What is new is the ending: instead of blocking
  forever on the channel lock, halt gives the threads it interrupted a short fixed interval
  (two seconds) to wind down, then marks the channel Stopped regardless. Whatever is still
  running is logged with its stack frames and recorded as abandoned. Abandoned threads stay
  visible in `GET /channels/{channelId}/_threads` (flagged `abandoned`) once the channel is
  redeployed -- the endpoint answers only for a deployed channel, so there is a gap between
  undeploy and deploy where it returns 404 -- and
  the next halt interrupts them again.
- **A grace period of 0 no longer disables halt's bound.** Zero still means "wait forever" for
  stop, as before, but halt always finishes within its own fixed interval. An earlier build of
  this work let zero make halt unbounded, which meant the emergency action could be switched off
  by a setting named for something else.
- **Halt does not use the stop grace period.** Its wind-down interval is fixed and deliberately
  short, because halt is the emergency action: an operator who shortens the grace period to see
  stop diagnostics is not asking halt to take longer, and one who lengthens it is not asking
  halt to take minutes. The two-second wait exists only so a thread that is milliseconds from a
  clean exit is not reported as abandoned.
- **Forced mode.** If a wedged stop or start still holds the channel's lifecycle lock when that
  interval expires, halt proceeds without it, interrupts the holder, and reports it. The same
  applies to undeploy after a forced halt, so a redeploy always builds a fresh channel instance.
- **Only stop is bounded; every other operation waits as it always did.** Deploy, start, pause,
  resume and remove-all-messages wait without limit, exactly as they did before this release. An
  earlier build of this work timed all of them on the stop grace period, which meant shortening
  that setting could make a deploy or a pause fail. Only stop fails on a timeout, because stop is
  what the setting is named for and the only operation an operator is told to halt out of.
- **Undeploying a running channel stops it first, so it inherits the stop grace period.** That has
  always been the order; what is new is that the stop can now give up. Undeploy's own connector
  hooks are unbounded and a slow queue flush is never abandoned by an ordinary undeploy -- except after a forced halt,
  where undeploy runs them on halt's own short interval so a redeploy is never blocked by the old
  channel. If the channel does not stop within the grace period the undeploy stops there and the
  channel is left Stopping; when the stop later completes on its own the undeploy is not resumed,
  so issue it again. Raise the
  grace period if your channels legitimately take longer than it to stop.
- **A stop that ran out of time now finishes on its own once its work does.** The channel stays
  Stopping while something is still running, as before, and halt still forces it. But when the
  threads the stop gave up on finish -- or, for a pooled thread such as a web server's, once it
  leaves the channel -- the stop completes and the channel reaches Stopped without an operator
  touching it. A channel whose threads never finish still needs a halt, and so does one where
  repeated attempts to finish the stop keep running out of time -- after ten it stops trying and
  logs that it has, rather than retrying forever. Previously it stayed Stopping for good: nothing moved
  it on, both admin clients offer Stop only on a started or paused channel, and undeploy is
  refused while Stopping -- so the only action left was a halt, which risks a duplicate delivery,
  on a channel where nothing was wrong any more.
- **The one exception: a lock held by a thread a halt already abandoned.** That holder will never
  let go, so start, pause, resume and remove-all-messages fail at once, naming the operation that
  is stuck and telling you to undeploy the channel and deploy it again to rebuild it. Waiting there
  would block for the life of the server and queue every later action for that channel behind it.
  No timer is involved: what makes the holder hopeless is that a halt abandoned it, not the clock.
- **A halt during a deploy is no longer overtaken.** A deploy that goes on to start the channel
  releases the channel's lock in between, and a halt could land in that gap, mark the channel
  Stopped, and then be undone by the start that followed. The channel is now left deployed and
  Stopped with an error in the log, and you start it explicitly if that is what you wanted.
- **Abandoned queue threads stay retired.** A destination or source queue thread a halt gave up
  on exits when its blocked call finally returns, instead of resuming next to the restarted
  queue thread as a second sender.
- **Stale permit protection.** A dispatch thread abandoned by a halt that completes after a
  restart no longer releases a permit into the restarted channel's process lock.
- **The REST halt call returns within a bounded time** (about 34 seconds: twice the wind-down interval plus a 30-second margin for a task that never started)
  and reports a timeout error if the task is still running; the task itself continues.
- **Halt still trades a possible duplicate for never losing a message**, exactly as before; the
  Web UI's halt confirmation should say so and mention that threads may be abandoned.

---
