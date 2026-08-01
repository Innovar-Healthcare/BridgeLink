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
 * Structured result of a server-side Quartz cron expression validation. Returned by the
 * {@code POST /server/_validateCron} endpoint so that clients (e.g. WebAdmin) don't have to
 * re-implement Quartz's validation strictness.
 */
public class CronValidationResult {

    private boolean valid;
    private String message;

    public CronValidationResult() {}

    public CronValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public static CronValidationResult valid() {
        return new CronValidationResult(true, null);
    }

    public static CronValidationResult invalid(String message) {
        return new CronValidationResult(false, message);
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
