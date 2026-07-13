# WebAdmin plugin UI contract fixtures (IRT-1422)

`manifest-list.response.json` and `defaults.response.xml` are copies of the FROZEN contract
fixtures from the BridgeLink-Web-UI repo (`fixtures/webadmin-plugins/_contract/`), the
integration-test expectations for the two engine endpoints:

- `GET /extensions/_webadmin`
- `GET /extensions/{extensionName}/webadmin/defaults/{transportName}`

The contract itself is `docs/WEBADMIN-PLUGIN-CONTRACT.md` in that repo (WebAdmin PR #647).
Do not edit these copies independently — they must stay in sync with the source fixtures.

Note: `defaults.response.xml` was generated against a 26.3.1 engine. The tests compare it
structurally (fixture elements must appear in the current serialization, version attributes
normalized) because `SmtpDispatcherProperties` may gain fields on newer engine lines.
