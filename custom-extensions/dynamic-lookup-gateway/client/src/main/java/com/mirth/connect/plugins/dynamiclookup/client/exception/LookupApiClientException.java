/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.client.exception;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.ErrorResponse;

public class LookupApiClientException extends ClientException {
    private final ErrorResponse error;

    public LookupApiClientException(ErrorResponse error, Throwable cause) {
        super(error.getMessage(), cause);
        this.error = error;
    }

    public ErrorResponse getError() {
        return error;
    }
}