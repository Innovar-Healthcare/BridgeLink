# Dependency-upgrade behaviour-verification checklist (internal)

**Status:** internal process document (IRT-2118). Customer-facing release-notes
wording is Phase 28 scope; do not lift prose from here into a customer-facing
document without that phase's review. This checklist obeys the one-owner-per-fact
rule: it links to the fact owners below rather than restating their mechanism, so
it cannot drift out from under them. Code is cited by class and method, never
`file:line`, so the checklist survives ordinary code movement.

## How to read this

Run this checklist at every third-party dependency upgrade. Section 2 is the
growing register of behaviours Core reads meaning from that a same-signature
dependency bump can silently change -- each row carries either a covering test
or a written accepted risk. A row with neither is the exact gap this discipline
exists to close.

## 1. The per-upgrade behaviour-verification checklist

A dependency bump is not verified by "the server boots and the suite is green"
alone. Run all four steps before calling a bump landable; the `core-build`
skill owns the broader "is this change done" question, this step is one input
to that decision, not a restatement of it.

1. **Classpath / loadability step.** The new jar loads on the classpath, its
   class-file major version stays within the project's Java-17 build floor
   (`javac --release 17`), and nothing throws `ClassNotFoundException` or
   `NoClassDefFoundError` on startup.
2. **Injection / DI-wiring step.** Anything the old jar registered with HK2 or
   Guice (a binding, a provider, a servlet) still resolves under the new jar's
   injection surface. A silently-unresolved binding does not throw either; it
   falls back to an unscoped code path, which is exactly what the Jersey
   register row below documents.
3. **Third-party-API-behaviour step (this phase's addition).** Enumerate the
   by-behaviour-not-by-signature dependence points this bump touches -- the
   behaviours listed in section 2 below, plus any new ones the bump's own
   release notes surface. Each dependence point this bump touches gets a
   covering test or a written accepted risk before the bump lands.
4. **Boot-and-serve smoke plus the three-JDK CI matrix.** Necessary, but not
   sufficient on its own -- the Jersey incident in section 2 booted, served,
   and passed the whole suite while step 3's behaviour silently regressed.
   This is the entire reason step 3 exists alongside steps 1 and 2.

## 2. Dependence-point register

Column spine: the behaviour Core reads meaning from (not the signature), who
owns that fact, and either a covering test (class and method) or a written
accepted risk.

| Dependency / seam | Behaviour we read meaning from (not the signature) | Owner of the fact (link) | Covering test (class and method) OR written accepted risk |
|---|---|---|---|
| Jersey / HK2 dispatch + `@Param` reflection | Which reflected `Method` the JAX-RS runtime hands the invocation handler is a same-signature behaviour that changed on the Jersey 2.22.1 to 2.48 bump. The handler must read `@Param` and the authorization/audit annotations off the servlet interface; when it silently read the implementation instead, parameter names became `argN`, disabling user scoping, channel redaction, and audit password redaction while boot, serve, and the full suite all stayed green. | connect/CLAUDE.md Gotchas: "Never read a servlet's annotations off the `Method` the JAX-RS runtime hands you." (primary); "`@Param` on every servlet parameter." (supporting) | Primary: `ServletAuthorizationParamContractTest.everyScopedOperationNamesAResolvableParameter` and `ServletParameterMapTest.plaintextPasswordNeverReachesTheAuditedParameterMap`. Supporting: `MirthServletGracePeriodTest.updateUserPasswordStillCarriesTheAnnotationTheScopingDependsOn` and `DataTypeServletTest.testPr12InterfaceMethodsCarryParamOnEveryJaxRsBoundArgument`. |
| XStream deserialization | XStream deserialization does not run constructors, so a field added within a release comes back null, zero, or false on previously-saved XML instead of taking its no-arg constructor's default. Strict XStream also rejects any non-allowlisted class with `ForbiddenClassException`. Both are behaviours a jar bump can silently change. | connect/CLAUDE.md Gotchas: "XStream deserialization does NOT run constructors." and "Strict XStream rejects unknown classes." | `XStreamSeamTest.knownOldFormatChannelXmlDeserializes` (constructor-bypass migration path) and `XStreamSeamTest.disallowedTypeIsRejected` (allowlist rejection). Covering test. |
| Rhino language-version semantics | Core runs Rhino at the shipped `rhino.languageversion = es6` default through `MirthContextFactory`; a jar bump can change E4X, Java-interop, or ES6 semantics that channel scripts read meaning from, and only a runtime characterization catches such a change. | `RhinoSeamTest`'s header, and the Phase 23.1 test family (no connect/CLAUDE.md gotcha bullet exists for Rhino). | `RhinoSeamTest.e4xScriptExecutes` and `RhinoSeamTest.javaInteropWorks`, plus the two-jar differential `RhinoEngineVersionDifferentialTest` (vulnerable rhino-1.7.13 fixture vs. the shipped rhino-1.7.15.1 jar, byte-identical output evidencing compatibility). Covering test. |
| Jetty request handling | Core reads meaning from raw Jetty request-handling behaviours (content-length on static resources and channel responses, GZIP tolerance, strict contextPath routing) that a Jetty bump can change at the same signature. | Tracked compensating controls named in the accepted-risk note below. | Written accepted risk: no seam-level behavioural differential (an isolated old-jar-vs-new-jar characterization, of the kind XStream/Rhino/Jackson each carry) exists for a raw Jetty bump. The tracked compensating controls that do exist: `JettyRegressionTest` and `WebServerRegressionTest` under `smoke-tests/src/com/mirth/connect/smoketest/` (NET-07 boot-and-serve wire contract); the six `regression-scripts/test-irt828..835-*.sh` image-level scripts (`test-irt828-static-resource-content-length.sh`, `test-irt831-context-path-routing.sh`, `test-irt832-http-receiver-content-length.sh`, `test-irt833-installer-content-length.sh`, `test-irt834-swagger-ui-content-length.sh`, `test-irt835-webstart-content-length.sh`); and `JerseyJettySeamTest`, which characterizes the Jersey/Jetty REST contract through the invocation-handler proxy and does not boot a raw Jetty connector. This row is the honest gap the discipline is meant to surface, not a substitute test. |
| BouncyCastle (bcprov/bcpkix/bcutil-jdk18on) provider behaviour | Three same-signature behaviours a bump can silently change: (a) keystore.type resolution (JCEKS or PKCS12, no provider argument) is served by the JDK's providers and never by BouncyCastle, so a BouncyCastle keystore-format change does not reach Core's keystore; (b) PBKDF2WithHmacSHA256 output is provider-independent, so a hash produced by the JDK's SunJCE provider still verifies through a BC-wired Digester; (c) BouncyCastle 1.86 caps raw JCA PBKDF2 at 10,000,000 iterations, bounding the usable digest.iterations. | Bc184UpgradeTest's class header (no connect/CLAUDE.md gotcha exists for BouncyCastle), following the Rhino row's precedent. | `Bc184UpgradeTest.keystoreRoundTripSurvivesForJceks` and `Bc184UpgradeTest.keystoreRoundTripSurvivesForPkcs12` for (a); `Bc184UpgradeTest.crossProviderHashStillVerifies` for (b); `Bc184UpgradeTest.digesterAcceptsDefaultIterationsAndRejectsAboveBcCap` for (c). Covering test. |

## 3. What counts as a behavioural test here

These are the existing behavioural seam tests already run in the suite, named as
worked examples of "exercises API behaviour, not only boot-and-serve" (this
discipline authors no new test; it names the ones that exist):

- The Jackson `MirthJsonUtil` round-trip: `JacksonStreamReadConstraintsTest`,
  red-capable against the shipped jackson 2.18.10 `StreamReadConstraints` cap.
- The Rhino two-jar differential: `RhinoEngineVersionDifferentialTest`.
- The OSHI arm64 script-surface characterization: `OshiScriptSurfaceSeamTest`.
- The XStream seam: `XStreamSeamTest`.

## Cross-references

- connect/CLAUDE.md "## Gotchas" -- owner of the Jersey and XStream coupling
  facts this register links to.
- `RhinoSeamTest`, `RhinoEngineVersionDifferentialTest`, `RhinoEs6BehaviorTest`,
  `XStreamSeamTest`, `OshiScriptSurfaceSeamTest`, `JerseyJettySeamTest`,
  `JacksonStreamReadConstraintsTest` -- the tracked seam tests named above.
- [`docs/derby-10.17-upgrade-note.md`](./derby-10.17-upgrade-note.md) and
  [`docs/irt-1801-oshi-6x-enlighten-migration.md`](./irt-1801-oshi-6x-enlighten-migration.md)
  -- the docs/ family this checklist joins.
- Jira IRT-2118 -- the ticket this checklist closes.
- Phase 28 SC-4 -- the CI lib-change / bump-verification definition-of-done
  this artifact feeds.
