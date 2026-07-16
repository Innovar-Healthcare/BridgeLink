# WebAdmin plugin UI contract fixtures (IRT-1422 / IRT-1442)

`manifest-list.response.json`, `defaults.response.xml`, and `datatype-defaults.response.xml`
are copies of the FROZEN contract fixtures from the BridgeLink-Web-UI repo
(`fixtures/webadmin-plugins/_contract/`), the integration-test expectations for the engine
endpoints:

- `GET /extensions/_webadmin`
- `GET /extensions/{extensionName}/webadmin/defaults/{transportName}`
- `GET /extensions/{extensionName}/webadmin/datatype-defaults/{dataTypeName}`

The contract itself is `docs/WEBADMIN-PLUGIN-CONTRACT.md` in that repo (Revision 3,
WebAdmin PRs #647 and #669). Do not edit these copies independently — they must stay in
sync with the source fixtures.

Notes:

- `defaults.response.xml` was generated against a 26.3.1 engine. The tests compare it
  structurally (fixture elements must appear in the current serialization, version
  attributes normalized) because `SmtpDispatcherProperties` may gain fields on newer engine
  lines.
- `datatype-defaults.response.xml` pins the `MockDIMSE*` class FQNs under
  `com.innovarhealthcare.connect.plugins.mockdimse`. The classes on the test classpath
  (`server/test/com/innovarhealthcare/connect/plugins/mockdimse/`) are byte-identical
  mirrors of `samples/mock-dimse-datatype`'s Java — `MockDimseSampleContractTest` guards
  the two against drifting apart, since the sample is deliberately not part of the server
  build.
- `manifest-list.response.json` (Revision 3) carries three entries: `mock-smtp`,
  `mock-syslog-mode` (a WebAdmin-only transmission-mode fixture with no Core sample — the
  manifest endpoint just serves its `webadmin.json` verbatim), and `mock-dimse-datatype`.
