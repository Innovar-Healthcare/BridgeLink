/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.service;

import com.mirth.connect.plugins.dynamiclookup.shared.constant.LookupConstants;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

public class LookupGroupNormalizer {
    private LookupGroupNormalizer() {
    }

    public static void normalize(LookupGroup group) {
        // Normalize valueType
        String vt = LookupConstants.normalizeValueType(group.getValueType());
        group.setValueType(vt);
    }
}
