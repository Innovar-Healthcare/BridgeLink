/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) NextGen Healthcare. All rights reserved.
 * https://www.nextgen.com/products-and-services/integration-engine
 *
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.util;

/**
 * Structured result of a server-side Rhino script validation. Returned by the
 * {@code POST /server/_validateScript} endpoint so that clients (e.g. WebAdmin's Monaco editor)
 * can highlight the exact location of a syntax error instead of re-implementing the Rhino engine.
 */
public class ScriptValidationResult {

    private boolean valid;
    private ScriptValidationError error;

    public ScriptValidationResult() {}

    public ScriptValidationResult(boolean valid, ScriptValidationError error) {
        this.valid = valid;
        this.error = error;
    }

    public static ScriptValidationResult valid() {
        return new ScriptValidationResult(true, null);
    }

    public static ScriptValidationResult invalid(ScriptValidationError error) {
        return new ScriptValidationResult(false, error);
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public ScriptValidationError getError() {
        return error;
    }

    public void setError(ScriptValidationError error) {
        this.error = error;
    }

    /**
     * A single syntax error. Line/column are 1-based and are already normalized to the caller's
     * script (the internal Rhino wrapper offset has been removed). A column of 0 means the column
     * could not be determined.
     */
    public static class ScriptValidationError {

        private int line;
        private int column;
        private String message;

        public ScriptValidationError() {}

        public ScriptValidationError(int line, int column, String message) {
            this.line = line;
            this.column = column;
            this.message = message;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        public int getColumn() {
            return column;
        }

        public void setColumn(int column) {
            this.column = column;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
