package com.mirth.connect.plugins.dynamiclookup.server.exception;

public class ValueOperationException extends RuntimeException {
    private final Throwable cause;
    public ValueOperationException(String message, Throwable cause) {
        super(message);
        this.cause = cause;
    }
    public ValueOperationException(String message) {
        this(message, null);
    }
    @Override
    public Throwable getCause() {
        return cause;
    }
}

