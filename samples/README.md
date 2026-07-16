# Samples

Demo-only extensions. Nothing in this directory is wired into the release build
(`server/mirth-build.xml`) or `custom-extensions/` — these must never ship with a
BridgeLink release.

| Sample | Purpose |
|---|---|
| `mock-smtp-connector/` | Reference extension for the declarative WebAdmin plugin UI (IRT-1420/IRT-1422): a mock destination connector whose `webadmin/webadmin.json` manifest drives an engine-served WebAdmin panel. See its README for build instructions. |
| `mock-dimse-datatype/` | Reference extension for the WebAdmin `dataTypes` contribution kind (IRT-1441/IRT-1442): a mock data type whose defaults are served by `GET /extensions/{name}/webadmin/datatype-defaults/{dataTypeName}`. See its README for build instructions and the XStream allow-list note. |
