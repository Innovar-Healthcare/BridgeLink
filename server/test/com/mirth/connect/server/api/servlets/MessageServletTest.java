/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.api.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.Operation;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.model.message.attachment.Attachment;
import com.mirth.connect.donkey.server.channel.ChannelException;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.message.batch.BatchMessageException;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.model.LoginStatus.Status;
import com.mirth.connect.model.MessageImportResult;
import com.mirth.connect.model.ServerEvent;
import com.mirth.connect.model.User;
import com.mirth.connect.model.filters.MessageFilter;
import com.mirth.connect.server.api.providers.ResponseCodeFilter;
import com.mirth.connect.server.controllers.AuthorizationController;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EngineController;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.controllers.MessageController;
import com.mirth.connect.server.controllers.UserController;
import com.mirth.connect.util.MessageImporter.MessageImportException;
import com.mirth.connect.util.messagewriter.MessageWriterOptions;

public class MessageServletTest {
    private static int ADMIN_USER_ID = 1;
    private static int RESTRICTED_USER_ID = 2;

    static ControllerFactory controllerFactory;
    static EngineController engineController;
    static MessageController messageController;
    static ConfigurationController configurationController;
    static EventController eventController;
    static HttpSession session;
    static HttpServletRequest request;
    static ContainerRequestContext context;
    static SecurityContext sc;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() throws Exception {
        controllerFactory = mock(ControllerFactory.class);

        engineController = mock(EngineController.class);
        DispatchResult result1 = new MessageServletTest().new TestDispatchResult(1L);
        DispatchResult result2 = new MessageServletTest().new TestDispatchResult(100L);
        when(engineController.dispatchRawMessage(eq("channel1"), any(), anyBoolean(), anyBoolean())).thenReturn(result1);
        when(engineController.dispatchRawMessage(eq("channel2"), any(), anyBoolean(), anyBoolean())).thenReturn(result2);
        when(engineController.dispatchRawMessage(eq("channelException"), any(), anyBoolean(), anyBoolean())).thenThrow(new ChannelException(false));
        when(engineController.dispatchRawMessage(eq("batchMessageException"), any(), anyBoolean(), anyBoolean())).thenThrow(new BatchMessageException());
        when(controllerFactory.createEngineController()).thenReturn(engineController);

        messageController = mock(MessageController.class);
        when(controllerFactory.createMessageController()).thenReturn(messageController);

        configurationController = mock(ConfigurationController.class);
        when(configurationController.getServerId()).thenReturn("test-server-id");
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        eventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(eventController);

        UserController userController = mock(UserController.class);
        when(userController.authorizeUser(anyString(), anyString(), anyString())).thenReturn(new LoginStatus(Status.SUCCESS, ""));
        when(userController.getUser(anyInt(), anyString())).thenAnswer((InvocationOnMock invocation) -> {
            User user = new User();
            user.setId(invocation.getArgument(0));
            user.setUsername(invocation.getArgument(1));
            return user;
        });
        when(controllerFactory.createUserController()).thenReturn(userController);

        AuthorizationController authorizationController = mock(AuthorizationController.class);
        when(authorizationController.doesUserHaveChannelRestrictions(anyInt(), any())).thenReturn(false);
        when(authorizationController.isUserAuthorized(anyInt(), any(Operation.class), any(Map.class), any(String.class), anyBoolean())).thenAnswer((InvocationOnMock invocation) -> {
            Object[] args = invocation.getArguments();
            // Do not authorize restricted user to clear statistics
            if ((Integer) args[0] == RESTRICTED_USER_ID && ((Operation) args[1]).getName().equals("clearStatistics")) {
                return false;
            } else {
                return true;
            }
        });
        when(controllerFactory.createAuthorizationController()).thenReturn(authorizationController);

        setupSessionAndRequest(ADMIN_USER_ID);

        context = new MessageServletTest().new TestContainerRequestContext();

        sc = mock(SecurityContext.class);
    }

    @After
    public void teardown() {
        setupSessionAndRequest(ADMIN_USER_ID);
        reset(engineController);
        reset(messageController);
        reset(eventController);
    }

    @Test
    public void testProcessMessageReturnsMessageId() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);

        Long messageId = servlet.processMessage("channel1", "test data", new HashSet<Integer>(), new HashSet<String>(), false, false, null);
        assertEquals(1L, messageId.longValue());
        assertEquals(201, context.getProperty(ResponseCodeFilter.RESPONSE_CODE_PROPERTY));

        messageId = servlet.processMessage("channel2", "test data", new HashSet<Integer>(), new HashSet<String>(), false, false, null);
        assertEquals(100L, messageId.longValue());
        assertEquals(201, context.getProperty(ResponseCodeFilter.RESPONSE_CODE_PROPERTY));
    }

    @Test
    public void testProcessMessageWithException() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);

        Long messageId = servlet.processMessage("channelException", "test data", new HashSet<Integer>(), new HashSet<String>(), false, false, null);
        assertNull(messageId);
        assertEquals(500, context.getProperty(ResponseCodeFilter.RESPONSE_CODE_PROPERTY));

        messageId = servlet.processMessage("batchMessageException", "test data", new HashSet<Integer>(), new HashSet<String>(), false, false, null);
        assertNull(messageId);
        assertEquals(500, context.getProperty(ResponseCodeFilter.RESPONSE_CODE_PROPERTY));
    }

    @Test
    public void testAdminUserCanRemoveMessagesAndClearStats() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.removeAllMessages("channel1", true, true);

        Set<String> channelIds = new HashSet<>();
        channelIds.add("channel1");
        servlet.removeAllMessages(channelIds, true, true);

        verify(engineController, times(2)).removeAllMessages(any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    public void testAdminUserCanRemoveMessagesWithoutClearingStats() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.removeAllMessages("channel1", true, false);

        Set<String> channelIds = new HashSet<>();
        channelIds.add("channel1");
        servlet.removeAllMessages(channelIds, true, false);

        verify(engineController, times(2)).removeAllMessages(any(), anyBoolean(), anyBoolean(), any());
    }

    @Test(expected = MirthApiException.class)
    public void testRestrictedUserCannotRemoveMessagesAndClearStats1() {
        setupSessionAndRequest(RESTRICTED_USER_ID);
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.removeAllMessages("channel1", true, true);
    }

    @Test(expected = MirthApiException.class)
    public void testRestrictedUserCannotRemoveMessagesAndClearStats2() {
        setupSessionAndRequest(RESTRICTED_USER_ID);
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        Set<String> channelIds = new HashSet<>();
        channelIds.add("channel1");
        servlet.removeAllMessages(channelIds, true, true);
    }

    @Test
    public void testRestrictedUserCanRemoveMessagesWithoutClearingStats() {
        setupSessionAndRequest(RESTRICTED_USER_ID);
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.removeAllMessages("channel1", true, false);

        Set<String> channelIds = new HashSet<>();
        channelIds.add("channel1");
        servlet.removeAllMessages(channelIds, true, false);

        verify(engineController, times(2)).removeAllMessages(any(), anyBoolean(), anyBoolean(), any());
    }

    // ========== processMessage with sourceMapEntries ==========

    @Test
    public void testProcessMessageWithSourceMapEntries() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);

        Set<String> sourceMapEntries = new HashSet<>();
        sourceMapEntries.add("key1=value1");
        sourceMapEntries.add("key2=value2");

        Long messageId = servlet.processMessage("channel1", "test data", new HashSet<Integer>(), sourceMapEntries, false, false, null);
        assertEquals(1L, messageId.longValue());
    }

    @Test
    public void testProcessMessageWithEmptySourceMapEntries() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);

        Long messageId = servlet.processMessage("channel1", "test data", new HashSet<Integer>(), new HashSet<String>(), false, false, null);
        assertEquals(1L, messageId.longValue());
    }

    @Test
    public void testProcessMessageWithNullSourceMapEntries() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);

        Long messageId = servlet.processMessage("channel1", "test data", new HashSet<Integer>(), null, false, false, null);
        assertEquals(1L, messageId.longValue());
    }

    @Test
    public void testProcessMessageWithOverwriteFlag() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);

        Long messageId = servlet.processMessage("channel1", "test data", new HashSet<Integer>(), null, true, false, null);
        assertEquals(1L, messageId.longValue());
    }

    @Test
    public void testProcessMessageWithImportedFlag() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);

        Long messageId = servlet.processMessage("channel1", "test data", new HashSet<Integer>(), null, false, true, 42L);
        assertEquals(1L, messageId.longValue());
    }

    @Test
    public void testProcessMessageWithRawMessage() throws Exception {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);

        RawMessage rawMessage = new RawMessage("raw data");
        Long messageId = servlet.processMessage("channel1", rawMessage);
        assertEquals(1L, messageId.longValue());
        assertEquals(201, context.getProperty(ResponseCodeFilter.RESPONSE_CODE_PROPERTY));
    }

    @Test
    public void testProcessMessageWithRawMessageReturnsNull() throws Exception {
        when(engineController.dispatchRawMessage(eq("nullChannel"), any(), anyBoolean(), anyBoolean())).thenReturn(null);
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);

        Long messageId = servlet.processMessage("nullChannel", new RawMessage("data"));
        assertNull(messageId);
        assertEquals(500, context.getProperty(ResponseCodeFilter.RESPONSE_CODE_PROPERTY));
    }

    // ========== getMessageContent ==========

    @Test
    public void testGetMessageContent() {
        Message message = new Message();
        message.setMessageId(1L);
        when(messageController.getMessageContent(eq("channel1"), eq(1L), any())).thenReturn(message);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        Message result = servlet.getMessageContent("channel1", 1L, null);
        assertNotNull(result);
        assertEquals(Long.valueOf(1L), result.getMessageId());
    }

    @Test
    public void testGetMessageContentWithMetaDataIds() {
        Message message = new Message();
        message.setMessageId(5L);
        List<Integer> metaDataIds = new ArrayList<>();
        metaDataIds.add(0);
        metaDataIds.add(1);
        when(messageController.getMessageContent("channel1", 5L, metaDataIds)).thenReturn(message);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        Message result = servlet.getMessageContent("channel1", 5L, metaDataIds);
        assertNotNull(result);
        assertEquals(Long.valueOf(5L), result.getMessageId());
    }

    @Test
    public void testGetMessageContentReturnsNull() {
        when(messageController.getMessageContent(anyString(), any(Long.class), any())).thenReturn(null);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        Message result = servlet.getMessageContent("channel1", 999L, null);
        assertNull(result);
    }

    // ========== getAttachmentsByMessageId ==========

    @Test
    public void testGetAttachmentsByMessageIdWithContent() {
        List<Attachment> attachments = new ArrayList<>();
        Attachment att = new Attachment();
        att.setId("att1");
        attachments.add(att);
        when(messageController.getMessageAttachment("channel1", 1L, true)).thenReturn(attachments);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        List<Attachment> result = servlet.getAttachmentsByMessageId("channel1", 1L, true);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("att1", result.get(0).getId());
    }

    @Test
    public void testGetAttachmentsByMessageIdWithoutContent() {
        List<Attachment> attachmentIds = new ArrayList<>();
        Attachment att = new Attachment();
        att.setId("att2");
        attachmentIds.add(att);
        when(messageController.getMessageAttachmentIds("channel1", 1L, true)).thenReturn(attachmentIds);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        List<Attachment> result = servlet.getAttachmentsByMessageId("channel1", 1L, false);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("att2", result.get(0).getId());
    }

    @Test
    public void testGetAttachmentsByMessageIdEmpty() {
        when(messageController.getMessageAttachment("channel1", 1L, true)).thenReturn(new ArrayList<>());

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        List<Attachment> result = servlet.getAttachmentsByMessageId("channel1", 1L, true);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========== getAttachment ==========

    @Test
    public void testGetAttachment() {
        Attachment attachment = new Attachment();
        attachment.setId("att1");
        when(messageController.getMessageAttachment("channel1", "att1", 1L, true)).thenReturn(attachment);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        Attachment result = servlet.getAttachment("channel1", 1L, "att1");
        assertNotNull(result);
        assertEquals("att1", result.getId());
    }

    // ========== getMaxMessageId ==========

    @Test
    public void testGetMaxMessageId() {
        when(messageController.getMaxMessageId("channel1", true)).thenReturn(500L);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        Long maxId = servlet.getMaxMessageId("channel1");
        assertEquals(Long.valueOf(500L), maxId);
    }

    // ========== getMessages (filter) ==========

    @Test
    public void testGetMessagesWithFilter() {
        List<Message> messages = new ArrayList<>();
        Message msg = new Message();
        msg.setMessageId(1L);
        messages.add(msg);
        when(messageController.getMessages(any(MessageFilter.class), eq("channel1"), eq(true), eq(0), eq(50))).thenReturn(messages);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        MessageFilter filter = new MessageFilter();
        List<Message> result = servlet.getMessages("channel1", filter, true, 0, 50);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    public void testGetMessagesWithFilterReturnsEmpty() {
        when(messageController.getMessages(any(MessageFilter.class), eq("channel1"), any(), anyInt(), anyInt())).thenReturn(new ArrayList<>());

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        List<Message> result = servlet.getMessages("channel1", new MessageFilter(), false, 0, 100);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========== getMessageCount (filter) ==========

    @Test
    public void testGetMessageCountWithFilter() {
        when(messageController.getMessageCount(any(MessageFilter.class), eq("channel1"))).thenReturn(42L);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        Long count = servlet.getMessageCount("channel1", new MessageFilter());
        assertEquals(Long.valueOf(42), count);
    }

    @Test
    public void testGetMessageCountZero() {
        when(messageController.getMessageCount(any(MessageFilter.class), eq("channel1"))).thenReturn(0L);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        Long count = servlet.getMessageCount("channel1", new MessageFilter());
        assertEquals(Long.valueOf(0), count);
    }

    // ========== removeMessages (filter) ==========

    @Test
    public void testRemoveMessagesWithFilter() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        MessageFilter filter = new MessageFilter();
        servlet.removeMessages("channel1", filter);
        verify(messageController).removeMessages(eq("channel1"), any(MessageFilter.class));
    }

    // ========== removeMessage (single) ==========

    @Test
    public void testRemoveMessageSingle() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.removeMessage("channel1", 1L, null, null);
        verify(messageController).removeMessages(eq("channel1"), any(MessageFilter.class));
    }

    @Test
    public void testRemoveMessageWithMetaDataId() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.removeMessage("channel1", 1L, 0, null);

        ArgumentCaptor<MessageFilter> filterCaptor = ArgumentCaptor.forClass(MessageFilter.class);
        verify(messageController).removeMessages(eq("channel1"), filterCaptor.capture());
        MessageFilter capturedFilter = filterCaptor.getValue();
        assertEquals(Long.valueOf(1L), capturedFilter.getMinMessageId());
        assertEquals(Long.valueOf(1L), capturedFilter.getMaxMessageId());
        assertNotNull(capturedFilter.getIncludedMetaDataIds());
        assertEquals(1, capturedFilter.getIncludedMetaDataIds().size());
        assertEquals(Integer.valueOf(0), capturedFilter.getIncludedMetaDataIds().get(0));
    }

    @Test
    public void testRemoveMessageWithoutMetaDataId() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.removeMessage("channel1", 5L, null, null);

        ArgumentCaptor<MessageFilter> filterCaptor = ArgumentCaptor.forClass(MessageFilter.class);
        verify(messageController).removeMessages(eq("channel1"), filterCaptor.capture());
        MessageFilter capturedFilter = filterCaptor.getValue();
        assertEquals(Long.valueOf(5L), capturedFilter.getMinMessageId());
        assertEquals(Long.valueOf(5L), capturedFilter.getMaxMessageId());
        assertNull(capturedFilter.getIncludedMetaDataIds());
    }

    // ========== removeAllMessagesPost ==========

    @Test
    public void testRemoveAllMessagesPost() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        Set<String> channelIds = new HashSet<>();
        channelIds.add("channel1");
        servlet.removeAllMessagesPost(channelIds, true, false);
        verify(engineController).removeAllMessages(any(), eq(true), eq(false), any());
    }

    // ========== reprocessMessage ==========

    @Test
    public void testReprocessMessage() throws Exception {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        Set<Integer> metaDataIds = new HashSet<>();
        metaDataIds.add(1);
        servlet.reprocessMessage("channel1", 1L, false, true, metaDataIds);
        // The reprocessing happens on a separate thread, so just verify no exception is thrown
    }

    @Test
    public void testReprocessMessageNoFilterDestinations() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.reprocessMessage("channel1", 1L, true, false, null);
        // filterDestinations=false means metaDataIds passed as null to controller
    }

    // ========== reprocessMessages (filter) ==========

    @Test
    public void testReprocessMessagesWithFilter() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        MessageFilter filter = new MessageFilter();
        Set<Integer> metaDataIds = new HashSet<>();
        metaDataIds.add(0);
        servlet.reprocessMessages("channel1", filter, true, true, metaDataIds);
        // Runs on separate thread
    }

    // ========== importMessage ==========

    @Test
    public void testImportMessage() throws Exception {
        doNothing().when(messageController).importMessage(anyString(), any(Message.class));

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        Message message = new Message();
        message.setMessageId(1L);
        servlet.importMessage("channel1", message);
        verify(messageController).importMessage(eq("channel1"), eq(message));
    }

    @Test(expected = MirthApiException.class)
    public void testImportMessageException() throws Exception {
        doThrow(new MessageImportException("import error")).when(messageController).importMessage(anyString(), any(Message.class));

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.importMessage("channel1", new Message());
    }

    // ========== importMessagesServer ==========

    @Test
    public void testImportMessagesServer() throws Exception {
        MessageImportResult result = new MessageImportResult(10, 8);
        when(messageController.importMessagesServer("channel1", "/path/to/messages", true)).thenReturn(result);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        MessageImportResult importResult = servlet.importMessagesServer("channel1", "/path/to/messages", true);
        assertNotNull(importResult);
        assertEquals(10, importResult.getTotalCount());
        assertEquals(8, importResult.getSuccessCount());
    }

    @Test(expected = MirthApiException.class)
    public void testImportMessagesServerException() throws Exception {
        when(messageController.importMessagesServer(anyString(), anyString(), anyBoolean())).thenThrow(new MessageImportException("server import error"));

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.importMessagesServer("channel1", "/bad/path", false);
    }

    // ========== exportMessagesServer (filter) ==========

    @Test
    public void testExportMessagesServerWithFilter() throws Exception {
        when(messageController.exportMessages(anyString(), any(MessageFilter.class), anyInt(), any(MessageWriterOptions.class))).thenReturn(25);

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        MessageFilter filter = new MessageFilter();
        MessageWriterOptions options = new MessageWriterOptions();
        int count = servlet.exportMessagesServer("channel1", filter, 100, options);
        assertEquals(25, count);
    }

    @Test(expected = MirthApiException.class)
    public void testExportMessagesServerException() throws Exception {
        when(messageController.exportMessages(anyString(), any(MessageFilter.class), anyInt(), any(MessageWriterOptions.class))).thenThrow(new RuntimeException("export error"));

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.exportMessagesServer("channel1", new MessageFilter(), 100, new MessageWriterOptions());
    }

    // ========== exportAttachmentServer ==========

    @Test
    public void testExportAttachmentServer() throws Exception {
        doNothing().when(messageController).exportAttachment(anyString(), anyString(), any(Long.class), anyString(), anyBoolean());

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.exportAttachmentServer("channel1", 1L, "att1", "/tmp/file.dat", true);
        verify(messageController).exportAttachment("channel1", "att1", 1L, "/tmp/file.dat", true);
    }

    @Test(expected = MirthApiException.class)
    public void testExportAttachmentServerIOException() throws Exception {
        doThrow(new IOException("write error")).when(messageController).exportAttachment(anyString(), anyString(), any(Long.class), anyString(), anyBoolean());

        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.exportAttachmentServer("channel1", 1L, "att1", "/bad/path", false);
    }

    // ========== audit methods ==========

    @Test
    public void testAuditAccessedPHIMessage() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.setOperation(new Operation("auditAccessedPHIMessage", "Audit Accessed PHI Message", Operation.ExecuteType.SYNC, false));

        Map<String, String> attrs = new HashMap<>();
        attrs.put("patientId", "12345");
        attrs.put("channelId", "channel1");
        servlet.auditAccessedPHIMessage(attrs);
        verify(eventController).dispatchEvent(any(ServerEvent.class));
    }

    @Test
    public void testAuditQueriedPHIMessage() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.setOperation(new Operation("auditQueriedPHIMessage", "Audit Queried PHI Message", Operation.ExecuteType.SYNC, false));

        Map<String, String> attrs = new HashMap<>();
        attrs.put("query", "testQuery");
        servlet.auditQueriedPHIMessage(attrs);
        verify(eventController).dispatchEvent(any(ServerEvent.class));
    }

    @Test
    public void testAuditExportMessages() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.setOperation(new Operation("auditExportMessages", "Audit Export Messages", Operation.ExecuteType.SYNC, false));

        Map<String, String> attrs = new HashMap<>();
        attrs.put("exportType", "server");
        servlet.auditExportMessages(attrs);
        verify(eventController).dispatchEvent(any(ServerEvent.class));
    }

    @Test
    public void testAuditExportMessagesSuccess() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.setOperation(new Operation("auditExportMessagesSuccess", "Audit Export Messages Success", Operation.ExecuteType.SYNC, false));

        Map<String, String> attrs = new HashMap<>();
        attrs.put("count", "100");
        servlet.auditExportMessagesSuccess(attrs);
        verify(eventController).dispatchEvent(any(ServerEvent.class));
    }

    @Test
    public void testAuditWithEmptyAttributes() {
        MessageServlet servlet = new MessageServlet(request, context, sc, controllerFactory);
        servlet.setOperation(new Operation("auditAccessedPHIMessage", "Audit Accessed PHI Message", Operation.ExecuteType.SYNC, false));

        servlet.auditAccessedPHIMessage(new HashMap<>());
        verify(eventController).dispatchEvent(any(ServerEvent.class));
    }

    private static void setupSessionAndRequest(int userId) {
        session = mock(HttpSession.class);
        when(session.getAttribute("user")).thenReturn("" + userId);
        when(session.getAttribute("authorized")).thenReturn(Boolean.TRUE);

        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("http://remoteaddress");
        when(request.getSession()).thenReturn(session);
    }

    private class TestDispatchResult extends DispatchResult {

        public TestDispatchResult(long messageId) {
            super(messageId, null, null, true, true);
        }
    }

    private class TestContainerRequestContext implements ContainerRequestContext {

        private Map<String, Object> properties = new HashMap<>();

        @Override
        public Object getProperty(String arg0) {
            return properties.get(arg0);
        }

        @Override
        public void setProperty(String arg0, Object arg1) {
            properties.put(arg0, arg1);
        }

        // Unimplemented methods
        @Override
        public void abortWith(Response arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public List<Locale> getAcceptableLanguages() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public List<MediaType> getAcceptableMediaTypes() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Map<String, Cookie> getCookies() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Date getDate() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public InputStream getEntityStream() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getHeaderString(String arg0) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public MultivaluedMap<String, String> getHeaders() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Locale getLanguage() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public int getLength() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public MediaType getMediaType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getMethod() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Collection<String> getPropertyNames() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Request getRequest() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public SecurityContext getSecurityContext() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public UriInfo getUriInfo() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean hasEntity() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void removeProperty(String arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void setEntityStream(InputStream arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void setMethod(String arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void setRequestUri(URI arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void setRequestUri(URI arg0, URI arg1) {
            // TODO Auto-generated method stub

        }

        @Override
        public void setSecurityContext(SecurityContext arg0) {
            // TODO Auto-generated method stub

        }

    }

}
