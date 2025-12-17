package com.mirth.connect.plugins.dynamiclookup.shared.validation;

public class FieldPathFormatValidator {
    private FieldPathFormatValidator() {
    }

    public static void validate(String fieldPath) {
        String field = fieldPath != null ? fieldPath.trim() : "";

        // ---- required checks ----
        if (field.isEmpty()) {
            throw new IllegalArgumentException("Field path cannot be empty.");
        }

        // ---- JSON field path validation ----
        // Allowed: letters, digits, underscore (_), dot (.)
        if (!field.matches("[A-Za-z0-9_.]+")) {
            throw new IllegalArgumentException("Invalid field path '" + field + "'. Only letters, digits, underscore (_), and dot (.) are allowed.");
        }

        if (field.startsWith(".") || field.endsWith(".") || field.contains("..")) {
            throw new IllegalArgumentException("Invalid field path '" + field + "'. Field path must be dot-separated (e.g. user.profile.age).");
        }
    }

}
