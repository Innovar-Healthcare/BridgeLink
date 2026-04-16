/*
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved.
 */

package com.mirth.connect.util;

public class KeystoreRegenerationResponse {

    public enum Type {
        REGENERATED,
        ALREADY_SECURE
    }

    private Type type;
    private String message;

    public KeystoreRegenerationResponse(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
