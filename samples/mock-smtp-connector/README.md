# Mock SMTP Connector (IRT-1422 sample extension)

A demo extension for the declarative engine-served WebAdmin plugin UI (IRT-1420 Phase 2).
It contains:

- `destination.xml` — a destination connector ("Mock SMTP Sender", protocol `mocksmtp`)
  with its own `MockSmtpDispatcherProperties` class whose field names exactly match the
  field keys in `webadmin/webadmin.json`. The dispatcher performs no real I/O; `send()`
  always succeeds.
- `plugin.xml` — the extension identity ("Mock SMTP Connector") that WebAdmin lists,
  gates on enable/disable, and uses as the key for settings-panel properties
  (`GET/PUT /extensions/{name}/properties`).
- `webadmin/webadmin.json` — the declarative UI manifest (contract:
  `docs/WEBADMIN-PLUGIN-CONTRACT.md` in the BridgeLink-Web-UI repo). Served by
  `GET /extensions/_webadmin`; connector defaults by
  `GET /extensions/Mock SMTP Connector/webadmin/defaults/Mock SMTP Sender`.
- An action servlet answering `POST /extensions/mock-smtp/webadmin/actions/test-connection`
  (the manifest's "Test Connection" button) with a canned success.

The Java classes live under `com.mirth.connect.connectors.mocksmtp` so channel save/deploy
pass the engine's XStream allow-list (`com.mirth.connect.connectors.**`) without any
`mirth.properties` changes.

## Build

Requires the main server and client to be compiled first (`server/classes`, `client/classes`):

```
cd server && ANT_OPTS=-Xmx512m ant -f mirth-build.xml     # once, from the repo root
cd ../samples/mock-smtp-connector && ant                   # produces build/mock-smtp-1.0.0.zip
```

Install the zip through the API (`POST /api/extensions/_install`) or the Administrator,
then restart the engine. `ant install` copies the exploded extension straight into
`server/setup/extensions/mock-smtp` for local dev instead.

Note: `<mirthVersion>` in `plugin.xml`/`destination.xml` is pinned to the engine version
(26.6.0) — the engine rejects extensions whose `mirthVersion` list doesn't contain its own
version. Bump it when moving to a newer engine line.
