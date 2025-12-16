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
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;

public class LookupGroupNormalizer {
    private LookupGroupNormalizer() {
    }

    public static void normalize(LookupGroup group) {
        if (group == null) {
            return;
        }

        // Normalize valueType
        String vt = LookupConstants.normalizeValueType(group.getValueType());
        group.setValueType(vt);

        // --- TEXT: no JSON extra allowed
        if (LookupConstants.isTextValueType(vt)) {
            group.setExtra(null);
            return;
        }

        // --- JSON mode below: extra is required
        LookupGroupExtra extra = group.getExtra();
        if (extra == null) {
            return; // Validator throw a proper error later
        }

        // --- Normalize jsonIndexMode (NONE default)
        if (!LookupConstants.isFieldMode(extra.getJsonIndexMode())) {
            // NONE -> indexedJsonFields must be ignored
            extra.setIndexedJsonFields(null);
        }
    }
}
