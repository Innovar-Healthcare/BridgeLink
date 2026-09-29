/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.seams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.proxy.WebResourceFactory;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Falsifiable shim gate (D-05) for the vendored
 * {@code server/src/org/glassfish/jersey/client/proxy/WebResourceFactory.java} -- the source-tree
 * copy that shadows {@code jersey-proxy-client-2.48.jar} and adds {@code @FormDataParam} multipart
 * marshalling that upstream Jersey 2.48 still lacks (26-RESEARCH.md D-05 diff).
 * <p>
 * This suite drives the REAL vendored {@code WebResourceFactory.newResource(...)} proxy (not a
 * mock of Jersey) against a minimal servlet-interface analog mirroring the multipart shape already
 * shipped in production ({@code ChannelGroupServletInterface.updateChannelGroups},
 * {@code server/src/.../ChannelGroupServletInterface.java:72-94}: {@code @POST} +
 * {@code @Consumes(MULTIPART_FORM_DATA)} + two {@code @FormDataParam} fields). A
 * {@code ClientRequestFilter} intercepts and captures the outgoing entity, then aborts the request
 * with a canned {@code Response} -- so the assembled {@link FormDataMultiPart} can be inspected
 * directly without booting a live server or making a real network call.
 * <p>
 * <b>D-05 falsifiability (revert&rarr;RED / restore&rarr;GREEN, captured manually per phase 26
 * plan 01's SUMMARY, "D-05 Falsifiability Evidence" section):</b> with the vendored copy on the
 * classpath (via {@code server/classes}, which {@code server/build.xml}'s {@code testclasspath}
 * places <i>before</i> the {@code lib} jar fileset -- the shadow-precedence mechanism), the
 * {@link #formDataParamFieldsAreMarshalledIntoTheMultipartEntity()} assertion is GREEN. Moving
 * {@code server/classes/org/glassfish/jersey/client/proxy/WebResourceFactory.class} out of the way
 * exposes stock {@code jersey-proxy-client-2.48.jar}'s copy of the same class (verified via
 * {@code javap -p} to carry neither {@code encodeQueryParam} nor any {@code FormDataParam}/
 * multipart handling -- upstream 2.48 has {@code BeanParam} instead, per 26-RESEARCH.md). Stock's
 * {@code PARAM_ANNOTATION_CLASSES} list has no {@code FormDataParam.class} entry, so
 * {@code hasAnyParamAnnotation(...)} returns {@code false} for a {@code @FormDataParam}-only
 * parameter, and the param is routed to the {@code entity}/{@code entityType} branch instead --
 * both parameters become "the entity", which is not a legal shape for a two-@FormDataParam method
 * and throws {@code IllegalStateException}/produces the wrong entity type at invocation, turning
 * this test's primary assertion RED. Restoring the class file returns the suite to GREEN. This is
 * a documented manual revert run (D-05 gate discipline copied from {@code RhinoSeamTest}'s
 * PRECONDITION-comment style), not an automated in-process toggle, because the vendored class is
 * shadowed by classpath ORDERING (dirset before jar fileset), which a running JVM's classloader
 * cannot be made to re-evaluate without a fresh process.
 */
public class WebResourceFactoryMultipartSeamTest {

    /**
     * Minimal servlet-interface analog of the multipart shape already shipped in
     * {@code ChannelGroupServletInterface.updateChannelGroups} -- a {@code @POST} resource method
     * consuming multipart form data with two independent {@code @FormDataParam} fields.
     */
    @Path("/multiparttest")
    // Interface-level @Consumes(TEXT_PLAIN) supplies WebResourceFactory's "parentConsumes"
    // (server/src/.../WebResourceFactory.java:203-206, 219-225): the multipart body-part media
    // type ("partType") is resolved from parentConsumes.value()[0] when present, defaulting to
    // APPLICATION_OCTET_STREAM otherwise. TEXT_PLAIN is chosen deliberately so
    // FormDataBodyPart.getValue() (which requires exactly text/plain, per Jersey's own
    // implementation) can read the marshalled value back directly in assertions below.
    @Consumes(MediaType.TEXT_PLAIN)
    public interface MultipartTestResource {
        @POST
        @Path("/upload")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        Response upload(@FormDataParam("data") String data, @FormDataParam("description") String description);
    }

    private static Client client;

    /** Captures the raw outgoing entity from the abort filter; reset before each test. */
    private final Object[] capturedEntity = new Object[1];

    @BeforeClass
    public static void beforeClass() {
        // Mirrors com.mirth.connect.client.core.Client.java's real client construction shape
        // (ClientConfig + MultiPartFeature registration, server/src/.../Client.java:185-196).
        client = ClientBuilder.newClient(new ClientConfig().register(MultiPartFeature.class));
    }

    @AfterClass
    public static void afterClass() {
        if (client != null) {
            client.close();
        }
    }

    private MultipartTestResource newProxy() {
        // Port 0 / never actually connected -- the request filter below aborts before any socket
        // I/O, so no live server is needed.
        WebTarget target = client.target("http://localhost:0/");
        target.register((ClientRequestFilter) (ClientRequestContext requestContext) -> {
            capturedEntity[0] = requestContext.getEntity();
            requestContext.abortWith(Response.ok().build());
        });
        // The proxy under test: the REAL vendored WebResourceFactory, not a mock.
        return WebResourceFactory.newResource(MultipartTestResource.class, target);
    }

    // ========== Test 1: happy path -- FormDataParam fields are marshalled, not dropped ==========

    @Test
    public void formDataParamFieldsAreMarshalledIntoTheMultipartEntity() {
        MultipartTestResource proxy = newProxy();

        proxy.upload("hello world", "a test description");

        assertNotNull("the vendored shim must produce a multipart entity, not drop the body silently "
                + "(the exact silent-defect class this CVE track exists to close)", capturedEntity[0]);
        assertTrue("the captured entity must be the FormDataMultiPart the shim's multipart-assembly "
                + "branch builds (WebResourceFactory.java:313-317)", capturedEntity[0] instanceof FormDataMultiPart);

        FormDataMultiPart entity = (FormDataMultiPart) capturedEntity[0];

        assertNotNull("the 'data' field must be present", entity.getField("data"));
        assertEquals("hello world", entity.getField("data").getValue());
        assertNotNull("the 'description' field must be present", entity.getField("description"));
        assertEquals("a test description", entity.getField("description").getValue());
    }

    // ========== Test 2: empty/null edge -- marshals without NPE, preserves the empty field ==========

    @Test
    public void emptyAndNullFormDataParamValuesMarshalWithoutNpe() {
        MultipartTestResource proxy = newProxy();

        // data="" (empty string, NOT null) still satisfies WebResourceFactory.invoke()'s
        // `if (value != null)` guard (server/src/.../WebResourceFactory.java:256), so the field IS
        // added to the entity with an empty value -- "preserves an empty field".
        // description=null fails that same guard and is silently skipped (never added to the
        // entity at all) -- this is the shim's real, documented behavior (verified by reading the
        // source), not a defect under test here; the assertion below is that it does NOT throw NPE.
        proxy.upload("", null);

        assertNotNull("an empty-string FormDataParam value must still produce a multipart entity "
                + "(no NPE, no dropped body)", capturedEntity[0]);
        assertTrue(capturedEntity[0] instanceof FormDataMultiPart);

        FormDataMultiPart entity = (FormDataMultiPart) capturedEntity[0];

        assertNotNull("the empty-string field must be preserved, not dropped", entity.getField("data"));
        assertEquals("", entity.getField("data").getValue());

        assertNull("a null FormDataParam value is safely omitted from the entity (guarded by "
                + "`if (value != null)`), not NPE'd", entity.getField("description"));
    }
}
