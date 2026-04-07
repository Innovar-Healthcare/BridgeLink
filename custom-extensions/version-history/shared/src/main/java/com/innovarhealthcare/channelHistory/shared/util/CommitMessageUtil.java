/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.shared.util;

import java.util.Arrays;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;

/**
 * Utility class for creating and parsing commit messages.
 * <p>
 * New format (line 1: subject, line 2: blank, line 3: JSON metadata):
 * <pre>
 * Fixed mapping logic
 *
 * {"type":"Channel","name":"ADT Listener","serverName":"prod-01","serverId":"550e8400-..."}
 * </pre>
 * <p>
 * Auto-commit (empty user message):
 * <pre>
 * Auto-commit: Channel 'ADT Listener'
 *
 * {"type":"Channel","name":"ADT Listener","serverName":"prod-01","serverId":"550e8400-..."}
 * </pre>
 * <p>
 * Old format (single-line, kept for backward-compatible parsing):
 * <pre>
 * Channel name: ADT Listener. Message: Fixed mapping logic. Server Name: prod-01. Server Id: 550e8400-...
 * </pre>
 */
public class CommitMessageUtil {

    public static final String DEFAULT_SERVER_ID = "00000000-0000-0000-0000-000000000000";

    // Legacy format constants — kept for backward-compatible parsing only
    private static final String NAME_SEPARATOR = " name: ";
    private static final String MESSAGE_PREFIX = "Message: ";
    private static final String SERVER_NAME_PREFIX = "Server Name: ";
    private static final String SERVER_ID_PREFIX = "Server Id: ";
    private static final int SERVER_ID_LENGTH = 36;

    // ==================== Inner DTO ====================

    private static class CommitMetadata {
        @JsonProperty("type")
        String type;
        @JsonProperty("name")
        String name;
        @JsonProperty("serverName")
        String serverName;
        @JsonProperty("serverId")
        String serverId;
    }

    // ==================== FORMATTING (Creation) ====================

    /**
     * Creates a formatted commit message.
     *
     * @param entity      The entity being committed (Channel, CodeTemplate, etc.)
     * @param userMessage The commit message from user (null or blank = auto-commit subject)
     * @param serverId    The server identifier
     * @param serverName  The server name (optional)
     * @return Formatted commit message in new two-section format
     */
    public static String create(Object entity, String userMessage, String serverId, String serverName) {
        String type = determineObjectType(entity);
        String name = getObjectName(entity);

        String subject;
        if (userMessage == null || userMessage.trim().isEmpty()) {
            subject = "Auto-commit: " + type + " '" + name + "'";
        } else {
            subject = userMessage.trim();
        }

        CommitMetadata meta = new CommitMetadata();
        meta.type = type;
        meta.name = name;
        meta.serverName = serverName;
        meta.serverId = serverId;

        String metaJson;
        try {
            metaJson = JsonUtils.toJson(meta);
        } catch (Exception e) {
            // Fallback: hand-build minimal JSON if serialization fails
            metaJson = "{\"type\":\"" + type + "\",\"name\":\"" + name + "\",\"serverId\":\"" + serverId + "\"}";
        }

        return subject + "\n\n" + metaJson;
    }

    /**
     * Overloaded create without server name.
     */
    public static String create(Object entity, String userMessage, String serverId) {
        return create(entity, userMessage, serverId, null);
    }

    /**
     * Creates a commit message for generic multi-file commits (e.g. from the Changes tab)
     * where no single entity type/name applies. Only server metadata is appended.
     *
     * @param userMessage The commit message from the user
     * @param serverId    The server identifier
     * @param serverName  The server name (optional)
     * @return Formatted commit message with server metadata only
     */
    public static String createGeneric(String userMessage, String serverId, String serverName) {
        String subject = (userMessage == null || userMessage.trim().isEmpty()) ? "Manual commit" : userMessage.trim();

        CommitMetadata meta = new CommitMetadata();
        meta.serverId = serverId;
        meta.serverName = serverName;

        String metaJson;
        try {
            metaJson = JsonUtils.toJson(meta);
        } catch (Exception e) {
            metaJson = "{\"serverId\":\"" + serverId + "\"}";
        }

        return subject + "\n\n" + metaJson;
    }

    // ==================== PARSING — Public API ====================

    /**
     * Extracts the user-visible subject line from a commit message.
     * New format: returns line 1. Old format: returns the Message field.
     */
    public static String extractContent(String commitMessage) {
        if (commitMessage == null) {
            return "";
        }
        String[] lines = commitMessage.split("\n");
        String lastLine = lines[lines.length - 1].trim();
        if (lastLine.startsWith("{") && tryParseMetadata(commitMessage) != null) {
            int end = lines.length - 1;
            if (end > 0 && lines[end - 1].trim().isEmpty()) {
                end--; // strip blank separator
            }
            return String.join("\n", Arrays.copyOfRange(lines, 0, end)).trim();
        }
        return extractContentLegacy(commitMessage);
    }

    /**
     * Extracts the entity name from a commit message.
     */
    public static String extractName(String commitMessage) {
        CommitMetadata meta = tryParseMetadata(commitMessage);
        if (meta != null) {
            return meta.name;
        }
        return extractNameLegacy(commitMessage);
    }

    /**
     * Extracts the entity type from a commit message (e.g. "Channel", "Code Template").
     */
    public static String extractType(String commitMessage) {
        CommitMetadata meta = tryParseMetadata(commitMessage);
        if (meta != null) {
            return meta.type;
        }
        return extractTypeLegacy(commitMessage);
    }

    /**
     * Extracts the server ID from a commit message.
     * Returns {@link #DEFAULT_SERVER_ID} if not found or invalid UUID.
     */
    public static String extractServerId(String commitMessage) {
        CommitMetadata meta = tryParseMetadata(commitMessage);
        if (meta != null) {
            return meta.serverId != null ? meta.serverId : DEFAULT_SERVER_ID;
        }
        return extractServerIdLegacy(commitMessage);
    }

    /**
     * Extracts the server name from a commit message, or null if absent.
     */
    public static String extractServerName(String commitMessage) {
        CommitMetadata meta = tryParseMetadata(commitMessage);
        if (meta != null) {
            return meta.serverName;
        }
        return extractServerNameLegacy(commitMessage);
    }

    /**
     * Returns true if the message contains a server name field.
     */
    public static boolean hasServerName(String commitMessage) {
        return extractServerName(commitMessage) != null;
    }

    /**
     * Returns true if the message follows either the new or old recognized format.
     */
    public static boolean isValidFormat(String commitMessage) {
        if (commitMessage == null || commitMessage.trim().isEmpty()) {
            return false;
        }
        if (tryParseMetadata(commitMessage) != null) {
            return true;
        }
        return isValidFormatLegacy(commitMessage);
    }

    // ==================== PARSING — New Format (private) ====================

    private static CommitMetadata tryParseMetadata(String commitMessage) {
        if (commitMessage == null) {
            return null;
        }
        String[] lines = commitMessage.split("\n");
        String lastLine = lines[lines.length - 1].trim();
        if (lastLine.startsWith("{")) {
            try {
                CommitMetadata meta = JsonUtils.fromJson(lastLine, CommitMetadata.class);
                if (meta != null) {
                    return meta;
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    // ==================== PARSING — Legacy Format (private) ====================

    private static String extractContentLegacy(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return "";
        }
        try {
            int messageStart = rawMessage.indexOf(MESSAGE_PREFIX);
            int serverStart = rawMessage.indexOf(". " + SERVER_NAME_PREFIX);
            if (serverStart == -1) {
                serverStart = rawMessage.indexOf(". " + SERVER_ID_PREFIX);
            }
            if (messageStart == -1 || serverStart == -1 || messageStart >= serverStart) {
                return rawMessage;
            }
            return rawMessage.substring(messageStart + MESSAGE_PREFIX.length(), serverStart).trim();
        } catch (Exception e) {
            return rawMessage;
        }
    }

    private static String extractNameLegacy(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return null;
        }
        try {
            int nameStart = rawMessage.indexOf(NAME_SEPARATOR);
            int messageStart = rawMessage.indexOf(". " + MESSAGE_PREFIX);
            if (nameStart == -1 || messageStart == -1 || nameStart >= messageStart) {
                return null;
            }
            return rawMessage.substring(nameStart + NAME_SEPARATOR.length(), messageStart).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractTypeLegacy(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return null;
        }
        try {
            int nameIndex = rawMessage.indexOf(NAME_SEPARATOR);
            if (nameIndex == -1) {
                return null;
            }
            return rawMessage.substring(0, nameIndex).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractServerIdLegacy(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return DEFAULT_SERVER_ID;
        }
        try {
            int serverIdStart = rawMessage.indexOf(SERVER_ID_PREFIX);
            if (serverIdStart == -1) {
                return DEFAULT_SERVER_ID;
            }
            int idStart = serverIdStart + SERVER_ID_PREFIX.length();
            if (idStart + SERVER_ID_LENGTH > rawMessage.length()) {
                return DEFAULT_SERVER_ID;
            }
            String serverId = rawMessage.substring(idStart, idStart + SERVER_ID_LENGTH);
            if (serverId.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
                return serverId;
            }
            return DEFAULT_SERVER_ID;
        } catch (Exception e) {
            return DEFAULT_SERVER_ID;
        }
    }

    private static String extractServerNameLegacy(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return null;
        }
        try {
            int serverNameStart = rawMessage.indexOf(". " + SERVER_NAME_PREFIX);
            if (serverNameStart == -1) {
                return null;
            }
            int serverIdStart = rawMessage.indexOf(". " + SERVER_ID_PREFIX, serverNameStart);
            if (serverIdStart == -1) {
                return null;
            }
            String serverName = rawMessage.substring(serverNameStart + 2 + SERVER_NAME_PREFIX.length(), serverIdStart).trim();
            return serverName.isEmpty() ? null : serverName;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isValidFormatLegacy(String rawMessage) {
        return extractTypeLegacy(rawMessage) != null && extractNameLegacy(rawMessage) != null && extractServerIdLegacy(rawMessage) != null;
    }

    // ==================== Private Helper Methods ====================

    private static String determineObjectType(Object object) {
        if (object instanceof Channel) {
            return "Channel";
        }
        if (object instanceof CodeTemplate) {
            return "Code Template";
        }
        if (object instanceof CodeTemplateLibrary) {
            return "Library";
        }
        if (object instanceof BatchLibraries) {
            return "Library";
        }
        if (object instanceof Map) {
            return "Global Scripts";
        }
        return "Object";
    }

    private static String getObjectName(Object object) {
        if (object instanceof Channel) {
            return ((Channel) object).getName();
        }
        if (object instanceof CodeTemplate) {
            return ((CodeTemplate) object).getName();
        }
        if (object instanceof CodeTemplateLibrary) {
            return ((CodeTemplateLibrary) object).getName();
        }
        if (object instanceof BatchLibraries) {
            return ((BatchLibraries) object).getNames();
        }
        if (object instanceof Map) {
            return "Global Scripts";
        }
        return "Unknown";
    }

    // ==================== Batch Libraries Support ====================

    /**
     * Wrapper representing a batch of libraries for commit message formatting.
     */
    public static class BatchLibraries {
        private final String names;

        public BatchLibraries(String names) {
            this.names = names;
        }

        public String getNames() {
            return names;
        }
    }
}
