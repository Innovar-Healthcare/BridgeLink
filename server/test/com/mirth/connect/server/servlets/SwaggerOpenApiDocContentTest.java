/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.servlets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.junit.Test;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.server.util.PackagePredicate;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Phase 26.7 Wave 2 (D-02, D-04): closes the doc-content coverage gap behind
 * {@link SwaggerServletTest}'s assertions, which are shallow object-level metadata (basePath,
 * server URL, {@code Info} fields) and would still pass 12/12 even if a swagger-core 2.2.28
 * {@code ModelResolver} regression silently dropped every generated path and model schema from
 * the document. This test asserts the GENERATED OpenAPI document's actual CONTENT: the real
 * endpoint paths and model schemas produced by scanning BridgeLink's own production servlet
 * interfaces, exactly as {@code MirthWebServer} does at boot.
 * <p>
 * <b>Real production inputs (mirrors {@code MirthWebServer.getClassesInPackage}, verified this
 * session):</b> an org.reflections scan (SubTypesScanner + a package-prefix
 * {@link PackagePredicate} input filter, matching {@code MirthWebServer.getClassesInPackage}
 * exactly) over {@code com.mirth.connect.client.core.api.servlets} for subtypes of
 * {@link BaseServletInterface} reliably finds all 17 interfaces in this forked test JVM
 * (verified via a standalone harness this session: {@code Scanned interfaces: 17}), so no
 * fallback to explicit enumeration is needed -- the {@code >= 17} size assert below guards
 * against a future scan regression shrinking that input silently.
 * <p>
 * <b>Field-read vs. Reader fallback (verified this session):</b> calling
 * {@code new SwaggerServlet(...).init(config)} and reading the private {@code openAPI} field via
 * reflection (the same field {@link SwaggerServletTest} reads for its object-level assertions)
 * returns an {@link OpenAPI} instance whose {@code getPaths()}/{@code getComponents()} are BOTH
 * null in this forked-JVM harness -- {@code GenericOpenApiContext.read()} does not write the
 * scanned paths/schemas back onto the exact object reference passed into
 * {@code SwaggerConfiguration.openAPI(...)}. Per the plan's documented fallback, this test
 * therefore also drives {@code io.swagger.v3.jaxrs2.Reader} directly with the SAME
 * {@link SwaggerConfiguration} (same {@code openAPI} skeleton, same {@code resourceClasses}) to
 * obtain the actual scanned document, which DOES carry the real paths/schemas (verified this
 * session: 167 paths, 88 schemas, all three exact path keys present, {@code Channel} schema
 * present). Every assertion below is against that Reader-produced document. The servlet's own
 * {@code init(config)} call is still exercised first (see {@link #buildDocument()}) so this test
 * also independently re-proves the Wave 2 blocker resolution ({@code SwaggerServlet.init()} must
 * not throw under jackson 2.18.10 + swagger 2.2.28) before ever falling back to the Reader.
 * <p>
 * <b>Falsifiability (D-02):</b> every assertion is a hard equality/containment check against the
 * scanned document; there is no {@code assumeTrue} or silent-pass path. A ModelResolver
 * regression that dropped paths/schemas, a scanning crash, or a serialization failure each fails
 * a hard assert here. This test is authored and lands INSIDE the Wave 2 atomic swap commit
 * (D-04) and therefore only ever runs on the jackson 2.18.10 + swagger 2.2.28 stack -- it is not,
 * and must not be, baselined against the pre-swap 2.0.10/2.14.3 stack, where
 * {@code SwaggerServlet.init()} crashes with the recorded blocker
 * {@code NoSuchMethodError} (see {@code 26.7-SWAGGER-CORE-BLOCKER.md}'s 12/12 error transcript).
 */
public class SwaggerOpenApiDocContentTest {

    private static final String BASE_PATH = "https://localhost:8443/api";

    /**
     * A static @Path scan across the 17 servlet interfaces counted 167 distinct path templates
     * on 2026-08-25 (verified this session: {@code paths size: 167}). The 100 floor leaves
     * roughly 40 percent slack so small legitimate path additions/removals never flap this test.
     */
    private static final int MIN_PATH_COUNT = 100;

    @Test
    public void generatedDocumentContainsRealPathsAndSchemasFromTheScannedServletInterfaces() throws Exception {
        OpenAPI document = buildDocument();

        assertNotNull("Generated OpenAPI document must not be null", document);

        assertNotNull("Generated OpenAPI document must have a non-null paths map", document.getPaths());
        assertTrue("Generated document's paths map must not be empty", !document.getPaths().isEmpty());
        assertTrue("Generated document must contain the /channels/{channelId} path",
                document.getPaths().containsKey("/channels/{channelId}"));
        assertTrue("Generated document must contain the /server/version path",
                document.getPaths().containsKey("/server/version"));
        assertTrue("Generated document must contain the /users/_login path",
                document.getPaths().containsKey("/users/_login"));
        assertTrue("Generated document must contain at least " + MIN_PATH_COUNT
                + " path templates (observed 167 on 2026-08-25); actual: " + document.getPaths().size(),
                document.getPaths().size() >= MIN_PATH_COUNT);

        assertNotNull("Generated document must have non-null components", document.getComponents());
        assertNotNull("Generated document's components must have a non-null schemas map",
                document.getComponents().getSchemas());
        assertTrue("Generated document's components.schemas must contain the Channel schema",
                document.getComponents().getSchemas().containsKey("Channel"));

        String serialized = Json.mapper().writeValueAsString(document);
        assertTrue("The jackson-backed Json.mapper() serialization of the full document must "
                + "still contain the /server/version path (proving the document survives "
                + "jackson 2.18.10 serialization end to end)", serialized.contains("/server/version"));
    }

    /**
     * Independently re-verifies the Wave 2 blocker resolution: constructing a
     * {@link SwaggerServlet} exactly as {@code MirthWebServer.addSwaggerServlets} does (real
     * scanned resourceClasses, not the single-servlet stub {@link SwaggerServletTest} uses) and
     * calling {@code init()} must not throw under jackson 2.18.10 + swagger 2.2.28.
     */
    private OpenAPI buildDocument() throws Exception {
        Set<Class<?>> scannedInterfaces = scanServletInterfaces();
        assertTrue("Scanned servlet-interface set must be at least the 17 known interfaces "
                + "(a shrunken scan would silently narrow this test's input); actual: "
                + scannedInterfaces.size(), scannedInterfaces.size() >= 17);

        // Exercise the real production init path first (mirrors MirthWebServer:575) with a
        // UNIQUE servlet name -- OpenApiContextLocator caches contexts by ctxId, the same reason
        // SwaggerServletTest#testInitCalledTwiceReplacesOpenAPI uses "SwaggerServlet2".
        ServletConfig mockServletConfig = mock(ServletConfig.class);
        ServletContext mockServletContext = mock(ServletContext.class);
        when(mockServletConfig.getServletContext()).thenReturn(mockServletContext);
        when(mockServletConfig.getServletName()).thenReturn("SwaggerOpenApiDocContentTest");
        when(mockServletContext.getInitParameterNames()).thenReturn(Collections.emptyEnumeration());
        when(mockServletConfig.getInitParameterNames()).thenReturn(Collections.emptyEnumeration());

        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, Version.getLatest(), Version.getApiEarliest(),
                Collections.emptySet(), scannedInterfaces, false);
        servlet.init(mockServletConfig);

        // GenericOpenApiContext.read() does not write the scanned paths/schemas back onto the
        // exact OpenAPI reference passed into SwaggerConfiguration.openAPI(...) (verified this
        // session), so obtain the actual scanned document via the same Reader/configuration
        // shape SwaggerServlet.init() uses internally, per the plan's documented fallback.
        OpenAPI skeleton = new OpenAPI();
        List<Server> servers = new ArrayList<>();
        servers.add(new Server().url(BASE_PATH));
        skeleton.servers(servers);
        Info info = new Info().title("BridgeLink Client API")
                .description("Swagger documentation for the BridgeLink Client API.")
                .version(Version.getApiEarliest().toString());
        skeleton.info(info);

        SwaggerConfiguration oasConfig = new SwaggerConfiguration().openAPI(skeleton).resourceClasses(
                scannedInterfaces.stream().map(Class::getName).collect(Collectors.toSet()));

        return new Reader(oasConfig).read(scannedInterfaces);
    }

    /**
     * Mirrors {@code MirthWebServer.getClassesInPackage}: org.reflections with
     * {@code SubTypesScanner} over {@code ClasspathHelper.forPackage(...)} plus a
     * {@link PackagePredicate} input filter, scanning
     * {@code com.mirth.connect.client.core.api.servlets} for subtypes of
     * {@link BaseServletInterface}.
     */
    private Set<Class<?>> scanServletInterfaces() {
        String packageName = "com.mirth.connect.client.core.api.servlets";
        ConfigurationBuilder config = new ConfigurationBuilder();
        config.setScanners(new ResourcesScanner(), new TypeAnnotationsScanner(), new SubTypesScanner(false));
        config.addUrls(ClasspathHelper.forPackage(packageName));
        config.setInputsFilter(new PackagePredicate(packageName));
        Reflections reflections = new Reflections(config);
        return new HashSet<>(reflections.getSubTypesOf(BaseServletInterface.class));
    }
}
