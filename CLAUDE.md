# BridgeLink Core (connect/)

## Ecosystem context
This folder is the **BridgeLink Java core** — Innovar's fork of Mirth Connect: message engine
(`donkey/`), `server/`, the Swing admin `client/`, `manager/`, `command/`, and `webadmin/`.
**Its REST API is the source of truth for the entire ecosystem.**
- Canonical git repo: `Innovar-Healthcare/BridgeLink`. The sibling folder **`BridgeLink/` is a
  duplicate clone of this same repo** (different checkout) — make core changes here, not there.
- Consumed by: **`BridgeLink-Web-UI`** (mirrors this REST API); the Java plugins (`ssl-plugins`,
  `oidc-plugin`, `cognito-auth-plugin`, `openai-plugin`, `advanced-access-control-plugin`,
  `siem-plugin`); `bridgelink-fleet`; `bridgelink-cli`; packaging (`bridgelink-install4j`,
  `mirth-docker`); and `bridgelink-integration-tests`.
- Full map + all sibling repos: `bridgelink_documentation/ECOSYSTEM.md`.
