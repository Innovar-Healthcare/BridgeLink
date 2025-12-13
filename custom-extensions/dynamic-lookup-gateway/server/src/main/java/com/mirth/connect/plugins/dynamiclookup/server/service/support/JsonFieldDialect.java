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

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonCondition;

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
     * Builds JSON-based search criteria for the given lookup group and conditions.
     *
     * Implementations typically: - Inspect group metadata (e.g. indexed JSON fields or computed columns) - Prefer indexed
     * or computed columns when available - Fall back to raw JSON extraction expressions when no index exists
     *
     * Each JsonFieldCriterion contains: - expression: SQL fragment representing the JSON field (e.g. JSON_VALUE(...),
     * JSON_EXTRACT(...), or a computed column name) - value: value to compare against
     *
     * The returned criteria are intended to be consumed by MyBatis mappers such as:
     *
     * ${c.expression} = #{c.value}
     *
     * @param group      the lookup group containing metadata and index definitions
     * @param conditions the list of JSON field conditions to apply
     * @return a list of SQL-ready JSON field criteria
     */
    List<JsonFieldCriterion> buildCriteria(LookupGroup group, List<JsonCondition> conditions);

}
