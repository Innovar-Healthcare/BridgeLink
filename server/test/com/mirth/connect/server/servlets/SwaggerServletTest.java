/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.client.core.Version;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

public class SwaggerServletTest {

    private static final String BASE_PATH = "https://localhost:8443/api";
    private Version version;
    private Version apiVersion;
    private Set<String> resourcePackages;
    private Set<Class<?>> resourceClasses;
    private ServletConfig mockServletConfig;
    private ServletContext mockServletContext;

    @Before
    public void setUp() {
        version = Version.getLatest();
        apiVersion = Version.getApiEarliest();
        resourcePackages = new HashSet<>();
        resourcePackages.add("com.mirth.connect.server.api.servlets");
        resourceClasses = new HashSet<>();
        resourceClasses.add(com.mirth.connect.server.api.servlets.ChannelServlet.class);

        mockServletConfig = mock(ServletConfig.class);
        mockServletContext = mock(ServletContext.class);
        when(mockServletConfig.getServletContext()).thenReturn(mockServletContext);
        when(mockServletConfig.getServletName()).thenReturn("SwaggerServlet");
        when(mockServletContext.getInitParameterNames()).thenReturn(Collections.emptyEnumeration());
        when(mockServletConfig.getInitParameterNames()).thenReturn(Collections.emptyEnumeration());
    }

    // ========== Constructor ==========

    @Test
    public void testConstructorStoresBasePath() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        String basePath = getPrivateField(servlet, "basePath", String.class);
        assertEquals(BASE_PATH, basePath);
    }

    @Test
    public void testConstructorStoresVersion() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        Version storedVersion = getPrivateField(servlet, "version", Version.class);
        assertEquals(version, storedVersion);
    }

    @Test
    public void testConstructorStoresApiVersion() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        Version storedApiVersion = getPrivateField(servlet, "apiVersion", Version.class);
        assertEquals(apiVersion, storedApiVersion);
    }

    @Test
    public void testConstructorStoresResourcePackages() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        @SuppressWarnings("unchecked")
        Set<String> storedPackages = getPrivateField(servlet, "resourcePackages", Set.class);
        assertEquals(resourcePackages, storedPackages);
    }

    @Test
    public void testConstructorStoresResourceClasses() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        @SuppressWarnings("unchecked")
        Set<Class<?>> storedClasses = getPrivateField(servlet, "resourceClasses", Set.class);
        assertEquals(resourceClasses, storedClasses);
    }

    @Test
    public void testConstructorStoresAllowHTTP() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, true);
        boolean storedAllowHTTP = getPrivateField(servlet, "allowHTTP", Boolean.class);
        assertTrue(storedAllowHTTP);
    }

    // ========== init ==========

    @Test
    public void testInitCreatesOpenAPI() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        servlet.init(mockServletConfig);

        OpenAPI openAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        assertNotNull("OpenAPI should be initialized after init()", openAPI);
    }

    @Test
    public void testInitSetsServerUrl() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        servlet.init(mockServletConfig);

        OpenAPI openAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        assertNotNull(openAPI.getServers());
        assertEquals(1, openAPI.getServers().size());

        Server server = openAPI.getServers().get(0);
        assertEquals(BASE_PATH, server.getUrl());
    }

    @Test
    public void testInitSetsInfoTitle() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        servlet.init(mockServletConfig);

        OpenAPI openAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        Info info = openAPI.getInfo();
        assertNotNull(info);
        assertEquals("BridgeLink Client API", info.getTitle());
    }

    @Test
    public void testInitSetsInfoDescription() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        servlet.init(mockServletConfig);

        OpenAPI openAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        Info info = openAPI.getInfo();
        assertNotNull(info);
        assertEquals("Swagger documentation for the BridgeLink Client API.", info.getDescription());
    }

    @Test
    public void testInitSetsInfoVersionFromApiVersion() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        servlet.init(mockServletConfig);

        OpenAPI openAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        Info info = openAPI.getInfo();
        assertNotNull(info);
        assertEquals(apiVersion.toString(), info.getVersion());
    }

    @Test
    public void testInitWithDifferentBasePath() throws Exception {
        String customPath = "http://example.com:9090/custom-api";
        SwaggerServlet servlet = new SwaggerServlet(customPath, version, apiVersion, resourcePackages, resourceClasses, true);
        servlet.init(mockServletConfig);

        OpenAPI openAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        assertEquals(customPath, openAPI.getServers().get(0).getUrl());
    }

    @Test
    public void testInitWithLatestApiVersion() throws Exception {
        Version latestApi = Version.getLatest();
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, latestApi, resourcePackages, resourceClasses, false);
        servlet.init(mockServletConfig);

        OpenAPI openAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        assertEquals(latestApi.toString(), openAPI.getInfo().getVersion());
    }

    @Test
    public void testInitWithMultipleResourceClasses() throws Exception {
        Set<Class<?>> multipleClasses = new HashSet<>();
        multipleClasses.add(com.mirth.connect.server.api.servlets.ChannelServlet.class);
        multipleClasses.add(com.mirth.connect.server.api.servlets.EventServlet.class);

        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, multipleClasses, false);
        servlet.init(mockServletConfig);

        OpenAPI openAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        assertNotNull("OpenAPI should be initialized with multiple resource classes", openAPI);
        assertNotNull(openAPI.getInfo());
    }

    @Test
    public void testInitWithEmptyResourceClasses() throws Exception {
        Set<Class<?>> emptyClasses = new HashSet<>();

        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, emptyClasses, false);
        servlet.init(mockServletConfig);

        OpenAPI openAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        assertNotNull("OpenAPI should still be initialized with empty resource classes", openAPI);
    }

    // ========== init - servers list ==========

    @Test
    public void testInitServerListHasExactlyOneEntry() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        servlet.init(mockServletConfig);

        OpenAPI openAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        assertNotNull(openAPI.getServers());
        assertEquals("Should have exactly one server entry", 1, openAPI.getServers().size());
    }

    // ========== init - called multiple times ==========

    @Test
    public void testInitCalledTwiceReplacesOpenAPI() throws Exception {
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, apiVersion, resourcePackages, resourceClasses, false);
        servlet.init(mockServletConfig);

        OpenAPI firstOpenAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        assertNotNull(firstOpenAPI);

        // Reset servlet context to allow re-init
        ServletConfig secondConfig = mock(ServletConfig.class);
        ServletContext secondContext = mock(ServletContext.class);
        when(secondConfig.getServletContext()).thenReturn(secondContext);
        when(secondConfig.getServletName()).thenReturn("SwaggerServlet2");
        when(secondContext.getInitParameterNames()).thenReturn(Collections.emptyEnumeration());
        when(secondConfig.getInitParameterNames()).thenReturn(Collections.emptyEnumeration());

        String newBasePath = "https://other-host:8443/api";
        SwaggerServlet servlet2 = new SwaggerServlet(newBasePath, version, apiVersion, resourcePackages, resourceClasses, false);
        servlet2.init(secondConfig);

        OpenAPI secondOpenAPI = getPrivateField(servlet2, "openAPI", OpenAPI.class);
        assertNotNull(secondOpenAPI);
        assertEquals(newBasePath, secondOpenAPI.getServers().get(0).getUrl());
    }

    // ========== init - version toString in info ==========

    @Test
    public void testInfoVersionMatchesApiVersionToString() throws Exception {
        Version specificVersion = Version.v4_5_2;
        SwaggerServlet servlet = new SwaggerServlet(BASE_PATH, version, specificVersion, resourcePackages, resourceClasses, false);
        servlet.init(mockServletConfig);

        OpenAPI openAPI = getPrivateField(servlet, "openAPI", OpenAPI.class);
        assertEquals("4.5.2", openAPI.getInfo().getVersion());
    }

    // ========== Utility ==========

    @SuppressWarnings("unchecked")
    private <T> T getPrivateField(Object target, String fieldName, Class<T> fieldType) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in class hierarchy");
    }
}
