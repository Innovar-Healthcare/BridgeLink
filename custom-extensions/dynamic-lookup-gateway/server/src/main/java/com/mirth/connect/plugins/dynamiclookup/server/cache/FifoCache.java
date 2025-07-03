/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.cache;

import java.util.LinkedHashMap;
import java.util.Map;

public class FifoCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    public FifoCache(int maxSize) {
        super(maxSize, 0.75f, false); // false = insertion order
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}

