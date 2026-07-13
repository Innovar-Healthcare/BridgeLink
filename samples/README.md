# Samples

Demo-only extensions. Nothing in this directory is wired into the release build
(`server/mirth-build.xml`) or `custom-extensions/` — these must never ship with a
BridgeLink release.

| Sample | Purpose |
|---|---|
| `mock-smtp-connector/` | Reference extension for the declarative WebAdmin plugin UI (IRT-1420/IRT-1422): a mock destination connector whose `webadmin/webadmin.json` manifest drives an engine-served WebAdmin panel. See its README for build instructions. |
