/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core.api.providers;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

import org.apache.commons.io.IOUtils;

import com.mirth.connect.client.core.api.RawContent;

/**
 * Reads RawContent entities verbatim, so the Java Client proxy can consume endpoints that return
 * raw (non-envelope) JSON or XML bodies.
 */
@Provider
@Singleton
@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
public class RawContentMessageBodyReader implements MessageBodyReader<RawContent> {

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return RawContent.class.isAssignableFrom(type);
    }

    @Override
    public RawContent readFrom(Class<RawContent> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
        return new RawContent(IOUtils.toString(entityStream, StandardCharsets.UTF_8));
    }
}
