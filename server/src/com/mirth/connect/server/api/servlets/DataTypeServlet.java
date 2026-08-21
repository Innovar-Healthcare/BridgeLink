/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) NextGen Healthcare. All rights reserved.
 * https://www.nextgen.com/products-and-services/integration-engine
 *
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.api.servlets;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.client.core.api.RawContent;
import com.mirth.connect.client.core.api.servlets.DataTypeServletInterface;
import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.model.converters.IMessageSerializer;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.userutil.SerializerFactory;
import com.mirth.connect.util.MirthJsonUtil;

public class DataTypeServlet extends MirthServlet implements DataTypeServletInterface {

    public DataTypeServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc);
    }

    @Override
    public Response toTree(String dataType, String message) {
        return toXmlResponse(resolveSerializer(dataType), dataType, message);
    }

    @Override
    public Response serialize(String dataType, String direction, String message) {
        if (StringUtils.isBlank(direction)) {
            throw badRequest("A direction query parameter is required (toXML or fromXML).");
        }

        IMessageSerializer serializer = resolveSerializer(dataType);

        if ("toXML".equalsIgnoreCase(direction)) {
            return toXmlResponse(serializer, dataType, message);
        } else if ("fromXML".equalsIgnoreCase(direction)) {
            try {
                // fromXML renders XML back to the raw message format (e.g. pipe-delimited HL7). The
                // plain-text String is returned directly so Jersey's verbatim String writer emits
                // it as-is; RawContent's writer only advertises XML/JSON, not text/plain.
                String raw = serializer.fromXML(StringUtils.defaultString(message));
                return Response.ok(raw, MediaType.TEXT_PLAIN_TYPE).build();
            } catch (MessageSerializerException e) {
                throw badRequest("Unable to serialize XML to " + dataType + ": " + e.getMessage());
            } catch (Exception e) {
                throw new MirthApiException(e);
            }
        } else {
            /*
             * transformWithoutSerializing is intentionally unsupported here: it requires a second
             * (outbound) serializer and property set, which this single-serializer endpoint does
             * not model.
             */
            throw badRequest("Unsupported direction: " + direction + ". Supported directions: toXML, fromXML.");
        }
    }

    @Override
    public Response defaultProperties(String dataType) {
        // Instantiating the serializer validates the data type (400 if blank/unknown) up front.
        resolveSerializer(dataType);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("serialization", SerializerFactory.getDefaultSerializationProperties(dataType));
        result.put("deserialization", SerializerFactory.getDefaultDeserializationProperties(dataType));

        try {
            return Response.ok(new RawContent(MirthJsonUtil.toJson(result)), MediaType.APPLICATION_JSON_TYPE).build();
        } catch (JsonProcessingException e) {
            throw new MirthApiException(e);
        }
    }

    private IMessageSerializer resolveSerializer(String dataType) {
        if (StringUtils.isBlank(dataType)) {
            throw badRequest("A dataType query parameter is required.");
        }

        IMessageSerializer serializer;
        try {
            // Default serialization/deserialization properties for the data type are used.
            serializer = SerializerFactory.getSerializer(dataType);
        } catch (Exception e) {
            throw new MirthApiException(e);
        }
        if (serializer == null) {
            throw badRequest("Unknown data type: " + dataType);
        }
        return serializer;
    }

    private Response toXmlResponse(IMessageSerializer serializer, String dataType, String message) {
        try {
            // Inbound parse: raw message -> structured XML tree (same output the desktop client trees).
            String xml = serializer.toXML(StringUtils.defaultString(message));
            return Response.ok(new RawContent(xml), MediaType.APPLICATION_XML_TYPE).build();
        } catch (MessageSerializerException e) {
            // The supplied message could not be parsed as the requested data type.
            throw badRequest("Unable to parse message as " + dataType + ": " + e.getMessage());
        } catch (Exception e) {
            throw new MirthApiException(e);
        }
    }

    private static MirthApiException badRequest(String message) {
        return new MirthApiException(Response.status(Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity(message).build());
    }
}
