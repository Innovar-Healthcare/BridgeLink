/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.service.support;

import java.util.List;
import java.util.Map;

import com.mirth.connect.plugins.dynamiclookup.server.dao.support.JsonFieldCriterion;
import com.mirth.connect.plugins.dynamiclookup.server.dao.support.JsonFieldIndexDefinition;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

/**
 * JsonFieldDialect implementation for databases that do NOT support JSON.
 *
 * Any attempt to build JSON indexes or criteria will throw an exception, so callers are forced to avoid JSON features
 * on unsupported databases.
 */
public final class NoJsonFieldDialect implements JsonFieldDialect {

    @Override
    public List<JsonFieldIndexDefinition> buildIndexDefinitions(LookupGroup group) {
        throw new UnsupportedOperationException("JSON indexing is not supported for this database.");
    }

    @Override
    public List<JsonFieldCriterion> buildCriteria(LookupGroup group, Map<String, String> filters) {
        throw new UnsupportedOperationException("JSON search is not supported for this database.");
    }
}