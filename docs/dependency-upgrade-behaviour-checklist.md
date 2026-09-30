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
| BouncyCastle (bcprov/bcpkix/bcutil-jdk18on) provider behaviour | Three same-signature behaviours a bump can silently change: (a) keystore.type resolution (JCEKS or PKCS12, no provider argument) is served by the JDK's providers and never by BouncyCastle, so a BouncyCastle keystore-format change does not reach Core's keystore; (b) PBKDF2WithHmacSHA256 output is provider-independent, so a hash produced by the JDK's SunJCE provider still verifies through a BC-wired Digester; (c) BouncyCastle 1.86 caps raw JCA PBKDF2 at 10,000,000 iterations, bounding the usable digest.iterations; above the cap, `digest()` throws, but the fallback-wired `matches()` path admin login actually calls returns false silently instead of throwing. | Bc184UpgradeTest's class header (no connect/CLAUDE.md gotcha exists for BouncyCastle), following the Rhino row's precedent. | `Bc184UpgradeTest.keystoreRoundTripSurvivesForJceks` and `Bc184UpgradeTest.keystoreRoundTripSurvivesForPkcs12` for (a); `Bc184UpgradeTest.crossProviderHashStillVerifies` for (b); `Bc184UpgradeTest.digesterAcceptsDefaultIterationsAndRejectsAboveBcCap` for (c). Covering test. |
| jsch's vendored BouncyCastle bridges (`server/lib/jsch-2.28.5.jar`, `com.jcraft.jsch.bc.*`: ChaCha20-Poly1305, ML-KEM 768/1024, sntrup761, Ed25519/Ed448, Twofish, SCrypt, Argon2) | jsch is a live Core BouncyCastle consumer through the SFTP connector (`SftpConnection.java`), separate from the keystore/Digester surface above. Every `org.bouncycastle` class reference in these bridges was confirmed present and resolvable against 1.86 (ML-KEM ships from `org.bouncycastle.crypto.kems`), but no behavioural test exercises the bridged algorithms themselves. | Phase 26.17 core-reviewer pass (IRT-2441). No connect/CLAUDE.md gotcha exists for jsch. | No behavioural test covers the bridged algorithms directly; accepted risk. The existing `smoke-tests` SFTP leg (`SftpParamsTest`, `channels/file-sftp-*-test.xml`) boots and exercises SFTP connectivity but does not target these specific BC-bridged ciphers/KEMs. |
| AWS SDK for Java (software.amazon.awssdk 2.55.8): credential chain and HTTP client selection | Four same-signature behaviours a bump can silently change: (a) `EC2MetadataUtils` and the default credential chain initialize against Core's vendored Jackson. The shipped 2.15.28 read `PropertyNamingStrategy.PASCAL_CASE_TO_CAMEL_CASE`, which Jackson 2.18 removed, so the IAM-role path failed with `NoSuchFieldError` (an `Error` that escapes `catch (Exception)`) while boot, serve and the suite stayed green; 2.55.8 shades its own Jackson (`third-party-jackson-core`) and references none of Core's. (b) An SDK client built without an explicit HTTP client picks one by classpath priority, and `apache5-client` outranks `apache-client`, so adding that jar would silently move the ssl plugin's `S3Client` (which runs on Core's SDK) and the STS clients the default chain builds onto Apache 5. `S3Connection` passes `ApacheHttpClient` explicitly and is immune. (c) `DefaultCredentialsProvider.create()` is a JVM-wide singleton, and closing an S3Client closes the credentials provider it was built with. `S3Connection.destroy` (every Test Read, undeploy and redeploy) closes the S3 client, and in STS mode also Core's own `StsClient`, both built with that provider, so on 2.55.8 one connection's close shut the STS client that a role-assuming profile or EKS web identity uses, and every later default-chain connection failed with "Connection pool shut down" once its cached credentials expired, until restart. 2.15.28 closes the singleton on client close too, but the close stops at `software.amazon.awssdk.utils.Lazy`, which is not closeable there; on 2.55.8 `Lazy.close()` forwards to the held chain and reaches the STS client. The EC2 instance-profile path is unaffected because it reads the metadata service without a pooled client. `S3Connection.createCredentialsProvider` now hands out a view of the singleton that cannot be closed. (d) `Region.of` throws only for blank or null input, so the summary text built by `S3SchemeProperties` is unchanged. | `S3DefaultCredentialChainTest`'s class header, and `S3SharedCredentialsCloseTest`'s for (c) (no connect/CLAUDE.md gotcha exists for the AWS SDK), following the Rhino and BouncyCastle rows' precedent; IRT-2573. | `S3DefaultCredentialChainTest.ec2MetadataUtilsInitializesAgainstVendoredJackson`, `S3DefaultCredentialChainTest.instanceProfileProviderFailsWithSdkClientExceptionWhenImdsUnreachable` and `S3DefaultCredentialChainTest.defaultChainFailsWithSdkClientExceptionNotError` for (a), red on 2.15.28 and green on 2.55.8; `S3DefaultCredentialChainTest.apache5SyncHttpServiceIsAbsentFromClasspath` for (b), proven falsifiable by placing `apache5-client` on the test classpath; `S3SharedCredentialsCloseTest.destroyingOneConnectionLeavesSharedChainRefreshingForTheNext` for (c), which drives a role-assuming profile against a local mock STS and is red without the non-closeable view. Written accepted risk for (d): probe-verified during Phase 26.18 research with no committed test. |

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
