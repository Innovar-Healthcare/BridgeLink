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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ChannelDependency;
import com.mirth.connect.model.ChannelMetadata;
import com.mirth.connect.model.ChannelTag;
import com.mirth.connect.model.Connector;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.api.servlets.ChannelServlet;
import com.mirth.connect.server.api.servlets.ConfigurationServlet;
import com.mirth.connect.server.api.servlets.UserServlet;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

/**
 * Dependency-seam characterization suite (NET-03, D-09/D-10) for the Jersey/Jetty REST contract
 * AS SHIPPED: Jersey 2.22.1 + Jetty 12.0.33 (server/lib/jersey, server/lib/jetty). These tests do
 * NOT boot a real Jetty connector; instead they drive the real
 * {@code MirthResourceInvocationHandlerProvider} invocation-handler proxy that Jersey installs in
 * front of every servlet resource in production (the same object exercised by the existing
 * ServletTestBase-derived servlet tests), so the {@code @MirthOperation}/{@code @DontCheckAuthorized}
 * contract and the operation/authorization wiring are genuinely characterized, not mocked away.
 * <p>
 * Purpose: this suite is the fail-fast unit-level oracle for Phase 26's Jersey 2.48 / HK2 2.6.1
 * bump &mdash; if that bump changes servlet-proxy or annotation-processing behavior, one of these
 * three contract calls should go red here, long before the full smoke harness (Phase 18 D-01..D-08)
 * would catch it.
 * <p>
 * Note on JDK 25: this suite (like every ServletTestBase-derived test) uses Mockito for the
 * static {@code ControllerFactory} injection. Any JDK-25-only failure here traces to the tracked
 * Phase 16 Option A Mockito/ByteBuddy known-red (testlib bump deferred), not to this seam.
 */
public class JerseyJettySeamTest extends ServletTestBase {

    private static final String VERSION_STRING = "26.9.0-seam-test";

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        // ---- server-version contract (ConfigurationServlet.getVersion) ----
        when(configurationController.getServerVersion()).thenReturn(VERSION_STRING);

        // ---- channels-list contract (ChannelServlet.getChannels) ----
        Channel channel1 = createChannel(CHANNEL_ID1, "Channel One");
        Channel channel2 = createChannel(CHANNEL_ID2, "Channel Two");
        when(channelController.getChannels(null)).thenReturn(Arrays.asList(channel1, channel2));

        when(configurationController.getChannelMetadata()).thenReturn(new HashMap<String, ChannelMetadata>());
        when(configurationController.getChannelTags()).thenReturn(new HashSet<ChannelTag>());
        when(configurationController.getChannelDependencies()).thenReturn(new HashSet<ChannelDependency>());

        // ---- login/session contract (UserServlet.login) ----
        // userController.getUser(...) is already stubbed to succeed by ServletTestBase.setup();
        // configurationController.getStatus() defaults to the Mockito int default (0), which is
        // ConfigurationController.STATUS_OK. The real login() call passes a null serverURL (no
        // LOGIN_SERVER_URL_HEADER on the mocked request), which ServletTestBase's
        // anyString()-matched authorizeUser() stub does not cover (anyString() never matches
        // null); re-stub with any() so the null-serverURL contract path succeeds like production.
        when(userController.authorizeUser(anyString(), anyString(), any())).thenReturn(new LoginStatus(LoginStatus.Status.SUCCESS, ""));

        // UserServlet's login path also audits a ServerEvent via EventController -- give it a
        // non-null mock so the real login body completes instead of NPE-ing on the event dispatch.
        EventController eventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(eventController);

        // Make the static ControllerFactory.getFactory() (used directly by ConfigurationServlet,
        // ChannelServlet, and UserServlet's static controller fields) resolve to our mocks.
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);
    }

    private static Channel createChannel(String id, String name) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setName(name);
        channel.setSourceConnector(new Connector());
        return channel;
    }

    // ========== Test 1: server-version contract ==========

    @Test
    public void testServerVersionContract() throws Throwable {
        ConfigurationServlet servlet = new ConfigurationServlet(request, sc);

        String version = (String) ih.invoke(servlet, ConfigurationServlet.class.getMethod("getVersion"), new Object[0]);

        assertNotNull(version);
        assertFalse("version string must not be empty", version.isEmpty());
        assertEquals(VERSION_STRING, version);
    }

    // ========== Test 2: channels-list contract ==========

    @SuppressWarnings("unchecked")
    @Test
    public void testChannelsListContract() throws Throwable {
        ChannelServlet servlet = new ChannelServlet(request, sc);

        List<Channel> channels = (List<Channel>) ih.invoke(servlet, ChannelServlet.class.getMethod("getChannels", Set.class, boolean.class, boolean.class), new Object[] {
                null, false, false });

        assertNotNull(channels);
        assertEquals(2, channels.size());

        Map<String, String> idsToNames = new HashMap<>();
        for (Channel channel : channels) {
            idsToNames.put(channel.getId(), channel.getName());
        }
        assertEquals("Channel One", idsToNames.get(CHANNEL_ID1));
        assertEquals("Channel Two", idsToNames.get(CHANNEL_ID2));
    }

    // ========== Test 3: login/session contract ==========

    @Test
    public void testLoginSessionContract() throws Throwable {
        UserServlet servlet = new UserServlet(request, sc);

        LoginStatus status = (LoginStatus) ih.invoke(servlet, UserServlet.class.getMethod("login", String.class, String.class), new Object[] {
                "admin", "admin" });

        assertNotNull(status);
        assertEquals(LoginStatus.Status.SUCCESS, status.getStatus());

        // The session/auth interaction the mocked controllers observed: the servlet must have
        // marked the mocked HttpSession authorized as part of the real login path.
        verify(session).setAttribute("authorized", true);
    }
}
