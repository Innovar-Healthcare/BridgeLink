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

public interface JsonFieldDialect {

    /**
     * Builds JSON field index definitions for the given group.
     * 
     * This method provides: - how to name indexes - how to name computed columns (if applicable) - which JSON extraction
     * expression each field should use
     *
     * The returned definitions serve as the single source of truth for: (1) Creating/Dropping physical indexes (DDL) (2)
     * Building search criteria that leverage those indexes
     */
    List<JsonFieldIndexDefinition> buildIndexDefinitions(LookupGroup group);

    /**
     * Builds JSON search criteria for the given group and filter values.
     *
     * Implementations typically: - internally call buildIndexDefinitions(group) - determine whether a field is indexed -
     * use computedColumnName when available - fall back to raw JSON extraction expressions when no index exists
     *
     * Each JsonFieldCriterion contains: - expression: SQL fragment (e.g., JSON_VALUE(...), computed column name) - value:
     * value to compare against
     *
     * Intended for mappers like: ${c.expression} = #{c.value}
     */
    List<JsonFieldCriterion> buildCriteria(LookupGroup group, Map<String, String> filters);
}
