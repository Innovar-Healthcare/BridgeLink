# Mock DIMSE Data Type (IRT-1442 sample extension)

A demo extension for the declarative WebAdmin `dataTypes` contribution kind (IRT-1421
Slice 5). It contains:

- `plugin.xml` — the extension identity ("Mock DIMSE Data Type") whose `serverClasses`
  registers `MockDIMSEDataTypeServerPlugin`. The engine keys the data type into its plugin
  registry by the plugin point name (`MockDIMSE`).
- Shared/server Java — a mock DIMSE data type: `MockDIMSEDataTypeProperties` with all five
  property groups (serialization, deserialization, batch, response generation, response
  validation), whose group field names exactly match the field keys in
  `webadmin/webadmin.json`. The serializer is a pass-through; the type performs no real
  DICOM work.
- `webadmin/webadmin.json` — the declarative UI manifest (contract:
  `docs/WEBADMIN-PLUGIN-CONTRACT.md` Revision 3 in the BridgeLink-Web-UI repo). Served by
  `GET /extensions/_webadmin`; data type defaults by
  `GET /extensions/Mock DIMSE Data Type/webadmin/datatype-defaults/MockDIMSE`.

## XStream allow-list (required for channel saves)

The Java classes live under `com.innovarhealthcare.connect.plugins.mockdimse`, which is
**not** in the engine's built-in XStream deserialization allow-list (`com.mirth.*`). The
datatype-defaults endpoint itself works on a stock engine (serialization doesn't consult
the allow-list), but **saving a channel** that uses the MockDIMSE data type requires
admitting the package in `mirth.properties`:

```
xstream.allowtypes = com.innovarhealthcare.**
```

Restart the engine after adding it. This is the standard mechanism for any extension whose
model classes live outside `com.mirth.*`.

## Build

Requires the main server to be compiled first (`server/classes`):

```
cd server && ANT_OPTS=-Xmx512m ant -f mirth-build.xml       # once, from the repo root
cd ../samples/mock-dimse-datatype && ant                     # produces build/mock-dimse-datatype-1.0.0.zip
```

Install the zip through the API (`POST /api/extensions/_install`) or the Administrator,
then restart the engine. `ant install` copies the exploded extension straight into
`server/setup/extensions/mock-dimse-datatype` for local dev instead.

Note: `<mirthVersion>` in `plugin.xml` is pinned to the engine version (26.6.0) — the
engine rejects extensions whose `mirthVersion` list doesn't contain its own version. Bump
it when moving to a newer engine line.

## Contract notes

- The defaults response root is the neutral `<dataTypeProperties class="..." version="...">`
  element; WebAdmin retags it to `inboundProperties`/`outboundProperties` when seeding a
  channel's transformer.
- The frozen golden body lives in the BridgeLink-Web-UI repo at
  `fixtures/webadmin-plugins/_contract/datatype-defaults.response.xml`; Core's copy (used by
  `ExtensionWebAdminDataTypeDefaultsTest`) is
  `server/test/com/mirth/connect/server/api/servlets/webadmin/datatype-defaults.response.xml`.
- `MockDimseSampleContractTest` guards this sample's cross-file invariants (manifest field
  keys vs Java fields, plugin point name, serverClasses) since the sample is deliberately
  not part of the server build.
