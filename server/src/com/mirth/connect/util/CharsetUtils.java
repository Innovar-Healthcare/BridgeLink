/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import java.nio.charset.Charset;

import org.apache.commons.lang3.StringUtils;

public class CharsetUtils {
    public static final String DEFAULT_ENCODING = "DEFAULT_ENCODING";
    public static final String NONE = "NONE";

    /**
     * If the specified charset encoding equals DEFAULT_ENCODING or is NULL/empty, then the
     * specified default encoding will be returned. If the specified default encoding is also
     * NULL/empty then the default charset on the server is returned.
     * 
     * @param charsetEncoding
     * @param defaultEncoding
     * @return
     */
    public static String getEncoding(String charsetEncoding, String defaultEncoding) {
        if (StringUtils.equals(charsetEncoding, DEFAULT_ENCODING) || StringUtils.isBlank(charsetEncoding)) {
            if (StringUtils.isNotBlank(defaultEncoding)) {
                return defaultEncoding;
            } else {
                return Charset.defaultCharset().name();
            }
        } else {
            return charsetEncoding;
        }
    }

    public static String getEncoding(String charsetEncoding) {
        // IRT-1780: fall back to the server-wide ca.uhn.hl7v2.llp.charset override (promoted from
        // mirth.properties in DefaultConfigurationController) before resolving DEFAULT_ENCODING to
        // Charset.defaultCharset(). This gives the single-argument callers (TCP and HTTP connectors)
        // the same operator lever the File/DB/SMTP connectors already pass explicitly, so a channel
        // relying on DEFAULT_ENCODING can be pinned without depending on the JVM platform default
        // (which JEP 400 changed to UTF-8 on Java 18+). With the property unset getProperty returns
        // null and behavior is identical to before.
        return getEncoding(charsetEncoding, System.getProperty("ca.uhn.hl7v2.llp.charset"));
    }
}
