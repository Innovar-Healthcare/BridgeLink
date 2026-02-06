/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple replacement for Jetty's MultiException which was removed in Jetty 12.
 * This class collects multiple exceptions and can throw them as a single exception
 * with suppressed exceptions.
 */
public class MultiException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    private final List<Throwable> throwables = new ArrayList<>();
    
    public MultiException() {
        super("Multiple exceptions");
    }
    
    /**
     * Add a throwable to this multi exception.
     * @param t the throwable to add
     */
    public void add(Throwable t) {
        if (t != null) {
            throwables.add(t);
            addSuppressed(t);
        }
    }
    
    /**
     * Get the list of throwables.
     * @return the list of throwables
     */
    public List<Throwable> getThrowables() {
        return throwables;
    }
    
    /**
     * Get the number of throwables.
     * @return the number of throwables
     */
    public int size() {
        return throwables.size();
    }
    
    /**
     * Check if there are any throwables.
     * @return true if there are throwables
     */
    public boolean isEmpty() {
        return throwables.isEmpty();
    }
    
    /**
     * Throw this MultiException if it contains any throwables.
     * This mimics the old Jetty MultiException.ifExceptionThrowMulti() behavior.
     * @throws MultiException if there are any throwables
     */
    public void ifExceptionThrowMulti() throws MultiException {
        if (!throwables.isEmpty()) {
            throw this;
        }
    }
    
    /**
     * Throw if there is exactly one exception, or throw this MultiException if there are multiple.
     * @throws Exception the single exception or this MultiException
     */
    public void ifExceptionThrow() throws Exception {
        if (throwables.size() == 1) {
            Throwable t = throwables.get(0);
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            throw new Exception(t);
        }
        if (!throwables.isEmpty()) {
            throw this;
        }
    }
    
    @Override
    public String getMessage() {
        if (throwables.isEmpty()) {
            return "Multiple exceptions (none recorded)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Multiple exceptions (").append(throwables.size()).append("):");
        for (Throwable t : throwables) {
            sb.append("\n  - ").append(t.getClass().getName()).append(": ").append(t.getMessage());
        }
        return sb.toString();
    }
}
