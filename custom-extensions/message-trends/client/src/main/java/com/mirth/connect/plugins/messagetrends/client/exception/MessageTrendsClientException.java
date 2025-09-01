package com.mirth.connect.plugins.messagetrends.client.exception;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.plugins.messagetrends.shared.dto.response.ErrorResponse;

public class MessageTrendsClientException extends ClientException {
	private final ErrorResponse error;

	public MessageTrendsClientException(ErrorResponse error, Throwable cause) {
		super(error.getMessage(), cause);
		this.error = error;
	}

	public ErrorResponse getError() {
		return error;
	}
}
