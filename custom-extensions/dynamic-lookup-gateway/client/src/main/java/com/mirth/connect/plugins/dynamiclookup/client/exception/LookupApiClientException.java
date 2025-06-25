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