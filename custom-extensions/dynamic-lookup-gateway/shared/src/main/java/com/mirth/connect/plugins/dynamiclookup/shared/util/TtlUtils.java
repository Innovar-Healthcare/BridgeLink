/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.util;

import java.util.Date;

public class TtlUtils {

    /**
     * Checks if the given updatedAt timestamp is within the allowed TTL window.
     *
     * @param updatedAt the timestamp to check
     * @param ttlHours  TTL in hours (0 or less means no TTL enforced)
     * @return true if updatedAt is within TTL or TTL is not enforced
     */
    public static boolean isWithinTtl(Date updatedAt, long ttlHours) {
        if (updatedAt == null || ttlHours <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - (ttlHours * 3600 * 1000L);

        return updatedAt.getTime() >= cutoff;
    }
}

