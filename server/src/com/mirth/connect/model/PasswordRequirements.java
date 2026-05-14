/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.model;

import java.io.Serializable;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("passwordRequirements")
public class PasswordRequirements implements Serializable {

    private static final long serialVersionUID = 1L;

    private int minLength;
    private int minUpper;
    private int minLower;
    private int minNumeric;
    private int minSpecial;
    private int retryLimit;
    private int lockoutPeriod;
    private int expiration;
    private int gracePeriod;
    private int reusePeriod;
    private int reuseLimit;
    private boolean allowUsernameEnumeration;

    /**
     * No-arg constructor — hardened defaults bundled with issue #125 (SEC-04).
     *
     * <p>Previously every integer field defaulted to {@code 0}, which left
     * operators who never explicitly configured a password policy with
     * <em>no</em> enforcement at all (zero-length passwords, no retry limit,
     * no lockout). The hardened defaults below match the OWASP Authentication
     * Cheat Sheet baseline:
     * <ul>
     *   <li>{@code minLength = 8}</li>
     *   <li>{@code minUpper = 1}, {@code minLower = 1}, {@code minNumeric = 1}</li>
     *   <li>{@code minSpecial = 0} (kept zero — special characters are often
     *       blocked or normalized by upstream LDAP/AD integrations)</li>
     *   <li>{@code retryLimit = 5}, {@code lockoutPeriod = 5} (minutes)</li>
     *   <li>{@code expiration = 0}, {@code gracePeriod = 0},
     *       {@code reusePeriod = 0}, {@code reuseLimit = 0} (opt-in — unchanged)</li>
     *   <li>{@code allowUsernameEnumeration = false} (unchanged — already safe)</li>
     * </ul>
     * Operators who relied on the old all-zero defaults to bypass policy can
     * still call setters explicitly to restore that posture.
     */
    public PasswordRequirements() {
        this.minLength = 8;
        this.minUpper = 1;
        this.minLower = 1;
        this.minNumeric = 1;
        this.minSpecial = 0;
        this.retryLimit = 5;
        this.lockoutPeriod = 5;
        this.expiration = 0;
        this.gracePeriod = 0;
        this.reusePeriod = 0;
        this.reuseLimit = 0;
        this.allowUsernameEnumeration = false;
    }
    /**
     * @deprecated Use {@link #PasswordRequirements(int, int, int, int, int, int, int, int, int, int, int, boolean)} instead.
     */
    @Deprecated
    public PasswordRequirements(int minLength, int minUpper, int minLower, int minNumeric, int minSpecial, int retryLimit, int lockoutPeriod, int expiration, int gracePeriod, int reusePeriod, int reuseLimit) {
        this(minLength, minUpper, minLower, minNumeric, minSpecial, retryLimit, lockoutPeriod, expiration, gracePeriod, reusePeriod, reuseLimit, false);
    }

    public PasswordRequirements(int minLength, int minUpper, int minLower, int minNumeric, int minSpecial, int retryLimit, int lockoutPeriod, int expiration, int gracePeriod, int reusePeriod, int reuseLimit, boolean allowUsernameEnumeration) {
        this.minLength = minLength;
        this.minUpper = minUpper;
        this.minLower = minLower;
        this.minNumeric = minNumeric;
        this.minSpecial = minSpecial;
        this.retryLimit = retryLimit;
        this.lockoutPeriod = lockoutPeriod;
        this.expiration = expiration;
        this.gracePeriod = gracePeriod;
        this.reusePeriod = reusePeriod;
        this.reuseLimit = reuseLimit;
        this.allowUsernameEnumeration = allowUsernameEnumeration;
    }

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public int getMinUpper() {
        return minUpper;
    }

    public void setMinUpper(int minUpper) {
        this.minUpper = minUpper;
    }

    public int getMinLower() {
        return minLower;
    }

    public void setMinLower(int minLower) {
        this.minLower = minLower;
    }

    public int getMinNumeric() {
        return minNumeric;
    }

    public void setMinNumeric(int minNumeric) {
        this.minNumeric = minNumeric;
    }

    public int getMinSpecial() {
        return minSpecial;
    }

    public void setMinSpecial(int minSpecial) {
        this.minSpecial = minSpecial;
    }

    public int getRetryLimit() {
        return retryLimit;
    }

    public void setRetryLimit(int retryLimit) {
        this.retryLimit = retryLimit;
    }

    public int getLockoutPeriod() {
        return lockoutPeriod;
    }

    public void setLockoutPeriod(int lockoutPeriod) {
        this.lockoutPeriod = lockoutPeriod;
    }

    public int getExpiration() {
        return expiration;
    }

    public void setExpiration(int expiration) {
        this.expiration = expiration;
    }

    public int getGracePeriod() {
        return gracePeriod;
    }

    public void setGracePeriod(int gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    public int getReusePeriod() {
        return reusePeriod;
    }

    public void setReusePeriod(int reusePeriod) {
        this.reusePeriod = reusePeriod;
    }

    public int getReuseLimit() {
        return reuseLimit;
    }

    public void setReuseLimit(int reuseLimit) {
        this.reuseLimit = reuseLimit;
    }


    public boolean getAllowUsernameEnumeration() {
        return allowUsernameEnumeration;
    }

    public void setAllowUsernameEnumeration(boolean allowUsernameEnumeration) {
        this.allowUsernameEnumeration = allowUsernameEnumeration;
    }
}
