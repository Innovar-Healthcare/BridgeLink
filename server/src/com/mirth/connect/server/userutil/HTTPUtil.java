/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.userutil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicLineParser;
import org.apache.http.util.CharArrayBuffer;

import com.mirth.connect.connectors.http.HttpMessageConverter;
import com.mirth.connect.donkey.util.DonkeyElement.DonkeyElementException;

/**
 * Provides HTTP utility methods.
 */
public class HTTPUtil {
    private HTTPUtil() {}

    /**
     * Converts a block of HTTP header fields into a Map containing each header key and value.
     * 
     * @param str
     *            The block of HTTP header fields to convert.
     * @return The converted Map containing header key-value pairs.
     * @throws Exception
     *             If the header string could not be parsed.
     */
    public static Map<String, String> parseHeaders(String str) throws Exception {
        Map<String, String> headersMap = new HashMap<String, String>();

        for (String line : unfoldHeaderLines(str)) {
            if (StringUtils.isBlank(line)) {
                // A blank line marks the end of the header block (matches the terminator
                // semantics of the original commons-httpclient HttpParser.parseHeaders), so stop
                // processing rather than skipping past it into any trailing body content.
                break;
            }

            CharArrayBuffer buffer = new CharArrayBuffer(line.length());
            buffer.append(line);
            Header header = BasicLineParser.INSTANCE.parseHeader(buffer);
            headersMap.put(header.getName(), header.getValue());
        }

        return headersMap;
    }

    /**
     * Splits a raw HTTP header block into individual header lines, unfolding RFC-822 continuation
     * lines (lines beginning with a space or tab, which are a continuation of the previous
     * header's value) into the header line they belong to. commons-httpclient's
     * {@code HttpParser.parseHeaders} performed this same unfolding, and customer channel scripts
     * consume the resulting Map, so the replacement parser must reproduce it exactly.
     */
    private static List<String> unfoldHeaderLines(String rawHeaderBlock) {
        List<String> unfolded = new ArrayList<String>();
        String[] rawLines = rawHeaderBlock.split("\r\n|\r|\n");

        for (String rawLine : rawLines) {
            if (!unfolded.isEmpty() && rawLine.length() > 0 && (rawLine.charAt(0) == ' ' || rawLine.charAt(0) == '\t')) {
                // Folded/continuation line -- join to the previous header's value with a single space.
                int lastIndex = unfolded.size() - 1;
                unfolded.set(lastIndex, unfolded.get(lastIndex) + ' ' + rawLine.trim());
            } else {
                unfolded.add(rawLine);
            }
        }

        return unfolded;
    }

    /**
     * Serializes an HTTP request body into XML. Multipart requests will also automatically be
     * parsed into separate XML nodes.
     * 
     * @param httpBody
     *            The request body/payload input stream to parse.
     * @param contentType
     *            The MIME content type of the request.
     * @return The serialized XML string.
     * @throws MessagingException
     *             If the body could not be converted into a multipart object.
     * @throws IOException
     *             If the body could not be read into a string.
     * @throws DonkeyElementException
     *             If an XML parsing error occurs.
     * @throws ParserConfigurationException
     *             If an XML or multipart parsing error occurs.
     */
    public static String httpBodyToXml(InputStream httpBody, String contentType) throws MessagingException, IOException, DonkeyElementException, ParserConfigurationException {
        ContentType type = getContentType(contentType);
        Object content;

        if (type.getMimeType().startsWith(FileUploadBase.MULTIPART)) {
            content = new MimeMultipart(new ByteArrayDataSource(httpBody, type.toString()));
        } else {
            content = IOUtils.toString(httpBody, HttpMessageConverter.getDefaultHttpCharset(type.getCharset().name()));
        }

        return HttpMessageConverter.contentToXml(content, type, true, null);
    }

    /**
     * Serializes an HTTP request body into XML. Multipart requests will also automatically be
     * parsed into separate XML nodes.
     * 
     * @param httpBody
     *            The request body/payload string to parse.
     * @param contentType
     *            The MIME content type of the request.
     * @return The serialized XML string.
     * @throws MessagingException
     *             If the body could not be converted into a multipart object.
     * @throws IOException
     *             If the body could not be read into a string.
     * @throws DonkeyElementException
     *             If an XML parsing error occurs.
     * @throws ParserConfigurationException
     *             If an XML or multipart parsing error occurs.
     */
    public static String httpBodyToXml(String httpBody, String contentType) throws MessagingException, IOException, DonkeyElementException, ParserConfigurationException {
        ContentType type = getContentType(contentType);
        Object content;

        if (type.getMimeType().startsWith(FileUploadBase.MULTIPART)) {
            content = new MimeMultipart(new ByteArrayDataSource(httpBody, type.toString()));
        } else {
            content = httpBody;
        }

        return HttpMessageConverter.contentToXml(content, type, true, null);
    }

    private static ContentType getContentType(String contentType) {
        try {
            return ContentType.parse(contentType);
        } catch (RuntimeException e) {
            return ContentType.TEXT_PLAIN;
        }
    }
}