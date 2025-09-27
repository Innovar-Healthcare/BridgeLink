package com.mirth.connect.plugins.messagetrends.server.exception;

public class MessageTrendsException extends Exception {
	public MessageTrendsException(String message) {
		super(message);
	}

	public MessageTrendsException(String message, Throwable cause) {
		super(message, cause);
	}

	public MessageTrendsException(Throwable cause) {
		super(cause);
	}
}
