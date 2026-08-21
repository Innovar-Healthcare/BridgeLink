/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.model;

public class ExtendedLoginStatus extends LoginStatus {

    private String clientPluginClass;
    private String pendingGraceMessage;

    public ExtendedLoginStatus(Status status, String message) {
        this(status, message, null, null);
    }

    public ExtendedLoginStatus(Status status, String message, String updatedUsername, String clientPluginClass) {
        super(status, message, updatedUsername);
        this.clientPluginClass = clientPluginClass;
    }

    /*
     * Used internally to carry a login-time password-policy verdict across a two-leg MFA
     * challenge, since the challenge response otherwise discards it and the second leg never
     * sees the plaintext password to re-derive it. See IRT-1802.
     */
    public ExtendedLoginStatus(ExtendedLoginStatus challenge, String pendingGraceMessage) {
        super(challenge.getStatus(), challenge.getMessage(), challenge.getUpdatedUsername());
        this.clientPluginClass = challenge.getClientPluginClass();
        this.pendingGraceMessage = pendingGraceMessage;
    }

    public String getClientPluginClass() {
        return clientPluginClass;
    }

    public String getPendingGraceMessage() {
        return pendingGraceMessage;
    }
}
