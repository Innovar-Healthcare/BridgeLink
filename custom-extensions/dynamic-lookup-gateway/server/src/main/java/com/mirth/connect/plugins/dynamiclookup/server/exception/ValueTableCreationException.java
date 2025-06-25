package com.mirth.connect.plugins.dynamiclookup.server.exception;

public class ValueTableCreationException extends RuntimeException {
    private final Throwable cause;
    public ValueTableCreationException(String message, Throwable cause) {
        super(message);
        this.cause = cause;
    }
    public ValueTableCreationException(String message) {
        this(message, null);
    }
    @Override
    public Throwable getCause() {
        return cause;
    }
}