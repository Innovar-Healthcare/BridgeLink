/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.server.util;

import com.mirth.connect.model.User;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Helper class for converting User to Git PersonIdent
 */
public class GitCommitterHelper {

    private static final String DEFAULT_EMAIL_DOMAIN = "local";

    /**
     * Converts Mirth User to JGit PersonIdent
     *
     * @param user Mirth user
     * @return PersonIdent for git commits
     * @throws IllegalArgumentException if user is null or invalid
     */
    public static PersonIdent fromUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        String username = user.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        String email = user.getEmail();
        if (email == null || email.trim().isEmpty()) {
            email = username + "@" + DEFAULT_EMAIL_DOMAIN;
        }

        // Create PersonIdent with current timestamp, UTC timezone
        return new PersonIdent(username, email);
    }

    /**
     * Converts with custom email domain for users without email
     *
     * @param user          Mirth user
     * @param defaultDomain Domain to use if user has no email
     * @return PersonIdent for git commits
     */
    public static PersonIdent fromUser(User user, String defaultDomain) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        String username = user.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        String email = user.getEmail();
        if (email == null || email.trim().isEmpty()) {
            String domain = (defaultDomain != null && !defaultDomain.trim().isEmpty()) ? defaultDomain : DEFAULT_EMAIL_DOMAIN;
            email = username + "@" + domain;
        }

        return new PersonIdent(username, email);
    }
}
