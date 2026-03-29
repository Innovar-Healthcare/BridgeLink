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

import com.innovarhealthcare.channelHistory.server.file.FileOperations;
import com.innovarhealthcare.channelHistory.server.git.GitOperations;
import com.mirth.connect.model.codetemplates.CodeTemplate;

public class CodeTemplateRepository extends BaseRepository<CodeTemplate> {
    private static final String DIRECTORY = "codetemplates";
    private static final String TYPE_NAME = "Code Template";

    public CodeTemplateRepository(GitOperations gitOps, FileOperations fileOps, String serverId) {
        super(gitOps, fileOps, serverId);
    }

    @Override
    protected CodeTemplate deserializeAndVerify(String content, String filePath) {
        try {
            CodeTemplate template = fileOps.deserializeXml(content, CodeTemplate.class);

            if (template == null) {
                logger.warn("Deserialized code template is null: {}", filePath);
                return null;
            }

            // ✅ CodeTemplate specific: no special validation needed
            return template;

        } catch (Exception e) {
            logger.warn("Failed to deserialize code template from: {}", filePath, e);
            return null;
        }
    }
    
    @Override
    protected String extractId(CodeTemplate template) {
        return template.getId();
    }

    @Override
    protected String extractName(CodeTemplate template) {
        return template.getName();
    }

    @Override
    protected Class<CodeTemplate> getEntityClass() {
        return CodeTemplate.class;
    }

    @Override
    public String getDirectory() {
        return DIRECTORY;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }
}
