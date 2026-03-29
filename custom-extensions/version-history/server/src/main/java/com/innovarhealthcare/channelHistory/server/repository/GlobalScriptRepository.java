/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.server.repository;

import java.util.Map;

import com.innovarhealthcare.channelHistory.server.file.FileOperations;
import com.innovarhealthcare.channelHistory.server.git.GitOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Repository for Global Scripts version control.
 * Manages all 4 global scripts (Deploy, Undeploy, Preprocessor, Postprocessor)
 * as a single entity using MC-compatible XML map format.
 */
public class GlobalScriptRepository extends BaseRepository<Map<String, String>> {

    private static final Logger logger = LogManager.getLogger(GlobalScriptRepository.class);

    private static final String DIRECTORY = "globalscripts";
    private static final String FILENAME = "scripts";
    private static final String TYPE_NAME = "Global Scripts";

    // Standard global script types as defined by Mirth Connect
    private static final String DEPLOY_SCRIPT = "Deploy";
    private static final String UNDEPLOY_SCRIPT = "Undeploy";
    private static final String PREPROCESSOR_SCRIPT = "Preprocessor";
    private static final String POSTPROCESSOR_SCRIPT = "Postprocessor";

    /**
     * Constructor
     */
    public GlobalScriptRepository(GitOperations gitOps, FileOperations fileOps, String serverId) {
        super(gitOps, fileOps, serverId);
    }

    @Override
    protected Map<String, String> deserializeAndVerify(String content, String filePath) {
        try {
            // Parse MC XML map format
            @SuppressWarnings("unchecked") Map<String, String> scripts = fileOps.deserializeXml(content, Map.class);

            if (scripts == null) {
                logger.warn("Failed to parse global scripts XML: {}", filePath);
                return null;
            }

            // Validate that it contains expected script types
            if (!isValidGlobalScriptsMap(scripts)) {
                logger.warn("Invalid global scripts structure in: {}", filePath);
                return null;
            }

            return scripts;

        } catch (Exception e) {
            logger.error("Error deserializing global scripts from: {}", filePath, e);
            return null;
        }
    }

    @Override
    protected String extractId(Map<String, String> entity) {
        // Global scripts have a fixed ID since there's only one set
        return FILENAME;
    }

    @Override
    protected String extractName(Map<String, String> entity) {
        return TYPE_NAME;
    }

    @Override
    protected Class<Map<String, String>> getEntityClass() {
        // This won't be used directly due to generic type erasure,
        // but required by BaseRepository contract
        return (Class<Map<String, String>>) (Class<?>) Map.class;
    }

    @Override
    public String getDirectory() {
        return DIRECTORY;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    /**
     * Validates if the map contains valid global script structure
     */
    private boolean isValidGlobalScriptsMap(Map<String, String> scripts) {
        if (scripts == null) {
            return false;
        }

        // Should contain only known script types
        for (String key : scripts.keySet()) {
            if (!isValidScriptType(key)) {
                logger.warn("Unknown global script type: {}", key);
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if script type is valid
     */
    private boolean isValidScriptType(String scriptType) {
        return DEPLOY_SCRIPT.equals(scriptType) || UNDEPLOY_SCRIPT.equals(scriptType) || PREPROCESSOR_SCRIPT.equals(scriptType) || POSTPROCESSOR_SCRIPT.equals(scriptType);
    }
}
