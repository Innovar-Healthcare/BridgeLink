/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.model;

import com.mirth.connect.model.codetemplates.CodeTemplate;

public class CodeTemplateWithRaw {
    private CodeTemplate codeTemplate;
    private String rawContent;

    public CodeTemplateWithRaw(CodeTemplate codeTemplate, String rawContent) {
        this.codeTemplate = codeTemplate;
        this.rawContent = rawContent;
    }

    public CodeTemplate getCodeTemplate() {
        return codeTemplate;
    }

    public void setCodeTemplate(CodeTemplate codeTemplate) {
        this.codeTemplate = codeTemplate;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }
}
