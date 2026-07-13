/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core.api;

/**
 * Wraps a pre-serialized response body. Entities of this type are written verbatim by
 * RawContentMessageBodyWriter instead of being serialized through the XStream/Staxon message body
 * writers, allowing an endpoint to return raw JSON or XML.
 */
public class RawContent {

    private final String content;

    public RawContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}
