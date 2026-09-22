# Channel tag field check (IRT-2431)

Verifies that the Administrator's channel tag field can actually load. The tag field is a
JavaFX `WebView` rendering a bundled jQuery and bootstrap-tokenfield page, and when its
scripts fail to load the field is simply inert: no error dialog, no exception in the UI, and
tags cannot be entered or filtered anywhere they appear (Channels list, channel editor Summary
tab, Dashboard).

That silence is why this check exists. The failure looks identical to "nobody has typed a tag
yet", so it survived unnoticed from the July 2026 OpenJFX update until a community report.

## Why this is not a JUnit test

It needs a real display. CI runners have a JavaFX-bearing JDK (`test-matrix.yml` sets
`java-package: 'jdk+fx'`) but no display and no `xvfb`, so JavaFX toolkit startup fails there.
Anything placed under `client/test/` matching `*Test.class` is collected and run automatically
by `client/ant-build.xml`, so a test there would fail every CI run. This directory is
deliberately outside the ant build graph, and the class is named `...Check`, not `...Test`.

The repo's usual headless guard, `Assume.assumeFalse(GraphicsEnvironment.isHeadless())`, is an
AWT check and would not reliably cover a JavaFX toolkit failure, so it is not a way out here.

## What it actually exercises

It constructs the real `MirthTagWebBrowser`, not a copy of its logic. So it fails both when a
JavaFX update changes what the embedded browser is allowed to load, and when someone
reintroduces the `WebEngine.loadContent` approach that caused IRT-2431.

## Prerequisites

1. A built client, so `client/classes` exists:
   `cd server && ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true`
2. A JDK that bundles JavaFX (a `jdk+fx` build, for example Azul Zulu FX). A plain JDK fails at
   toolkit startup, which is a broken check rather than a failing one.
3. A desktop session. Do not run this over a plain SSH connection.

## Usage

```
client/manual-checks/tagfield/run-tagfield-check.sh /path/to/jdk-fx-home
```

With no argument it uses `$JAVA_HOME`. Both Azul layouts are handled: some bundles are flat,
others nest under `Contents/Home`.

Exit status: 0 pass, 1 the browser could not be constructed, 2 the page loaded but its scripts
did not, 3 timeout.

## Reading the output

`document.styleSheets` is the quickest signal. It is 4 on a working runtime — three external
stylesheets plus the page's inline `<style>` block — and 1 when the external resources were
refused, which is the IRT-2431 failure. `typeof jQuery` is `function` when healthy and
`undefined` when broken.

## Known results

Measured on macOS arm64 with the fix in place. Before the fix, every runtime carrying the July
2026 OpenJFX CPU failed.

| Runtime | Bundled JavaFX | WebKit | Before the fix | After |
|---|---|---|---|---|
| Zulu JDK 17.0.10 FX | 21.0.2 | 616.1 | pass | pass |
| Zulu JDK 17.0.19 FX | 22.0.9 | 623.1 | pass | pass |
| Zulu JDK 21.0.11 FX | 23.0.7 | 623.1 | pass | pass |
| Zulu JDK 17.0.20.1 FX | 22.0.10 | 623.1 | **fail** | pass |
| Zulu JDK 21.0.12.1 FX | 23.0.8 | 623.1 | **fail** | pass |
| Zulu JDK 11.0.32.1 FX | 19.0.16 | 623.1 | **fail** | pass |

The boundary is the July 2026 OpenJFX critical patch update (OpenJFX 26.0.2, 25.0.4, 21.0.12
and 17.0.20, rebuilt by Azul into its own lines), not the Java major version and not the WebKit
version — 17.0.19 and 21.0.11 ship WebKit 623.1 and are fine. Any JDK refreshed after that
update is worth adding to this table.
