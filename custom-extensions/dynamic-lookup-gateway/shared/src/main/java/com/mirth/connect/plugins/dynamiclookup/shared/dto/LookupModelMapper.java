/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.request.BatchGetValuesRequest;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.request.ImportValuesRequest;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.request.LookupGroupRequest;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.request.LookupValueRequest;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.LookupValueResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;
import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

public class LookupModelMapper {
    // --- Group Mapping ---
    public static LookupGroup fromGroupDto(LookupGroupRequest dto) {
        LookupGroup group = new LookupGroup();
        group.setName(dto.getName());
        group.setDescription(dto.getDescription());
        group.setVersion(dto.getVersion());
        group.setCacheSize(dto.getCacheSize());
        group.setCachePolicy(dto.getCachePolicy());
        group.setCreatedDate(new Date());
        group.setUpdatedDate(new Date());
        group.setValueType(dto.getValueType());
        group.setExtra(cloneExtra(dto.getExtra()));

        return group;
    }

    public static LookupGroupExtra cloneExtra(LookupGroupExtra src) {
        if (src == null) {
            return null;
        }

        try {
            ObjectMapper m = JsonUtils.getMapper();
            String json = m.writeValueAsString(src);
            return m.readValue(json, LookupGroupExtra.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone LookupGroupExtra", e);
        }
    }

    public static LookupValue fromValueDto(LookupValueRequest dto) {
        LookupValue value = new LookupValue();

        value.setValueData(dto.getValue());
        value.setCreatedDate(new Date());
        value.setCreatedDate(new Date());

        return value;
    }

    public static Map<String, String> fromImportValuesDto(ImportValuesRequest dto) {
        if (dto == null || dto.getValues() == null) {
            return Collections.emptyMap(); // return empty map instead of null
        }
        return new LinkedHashMap<>(dto.getValues()); // preserve insertion order
    }

    public static List<String> fromBatchGetValues(BatchGetValuesRequest dto) {
        if (dto == null || dto.getKeys() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(dto.getKeys());
    }

    // --- Value Mapping (entity -> response) ---
    public static LookupValueResponse toValueResponse(LookupValue value, Integer groupId) {
        return LookupValueResponse.from(value, groupId, true);
    }

    public static LookupValueResponse toValueResponse(LookupValue value, Integer groupId, boolean full) {
        return LookupValueResponse.from(value, groupId, full);
    }
}
