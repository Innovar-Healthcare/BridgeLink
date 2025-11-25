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

public class MysqlJsonFieldDialect implements JsonFieldDialect {
    @Override
    public List<JsonFieldIndexDefinition> buildIndexDefinitions(LookupGroup group) {
        throw new UnsupportedOperationException("JSON indexing is not implement yet.");
    }

    @Override
    public List<JsonFieldCriterion> buildCriteria(LookupGroup group, Map<String, String> filters) {
        throw new UnsupportedOperationException("JSON search is not implement yet.");
    }
}
