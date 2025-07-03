/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.dto.response;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;

public class ErrorResponseFactory {

    public static ErrorResponse build(String code, String message) {
        ErrorResponse error = new ErrorResponse();
        error.setStatus("error");
        error.setCode(code);
        error.setMessage(message);
        error.setTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString());
        return error;
    }
}
