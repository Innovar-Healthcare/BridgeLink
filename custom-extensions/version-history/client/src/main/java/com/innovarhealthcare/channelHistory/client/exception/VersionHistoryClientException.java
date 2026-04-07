/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.exception;

import com.innovarhealthcare.channelHistory.shared.dto.response.ErrorResponse;
import com.mirth.connect.client.core.ClientException;

public class VersionHistoryClientException extends ClientException {
    private final ErrorResponse error;

    public VersionHistoryClientException(ErrorResponse error, Throwable cause) {
        super(error.getMessage(), cause);
        this.error = error;
    }

    public ErrorResponse getError() {
        return error;
    }
}
