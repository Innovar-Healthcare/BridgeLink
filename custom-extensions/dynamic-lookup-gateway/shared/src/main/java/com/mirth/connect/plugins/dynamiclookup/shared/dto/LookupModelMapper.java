package com.mirth.connect.plugins.dynamiclookup.shared.dto;

import com.mirth.connect.plugins.dynamiclookup.shared.dto.request.BatchGetValuesRequest;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.request.ImportValuesRequest;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.request.LookupGroupRequest;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.request.LookupValueRequest;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.LookupValueResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;

import java.util.*;

public class LookupModelMapper {
    // --- Group Mapping ---
    public static LookupGroup fromGroupDto(LookupGroupRequest dto) {
        LookupGroup group = new LookupGroup();
//        group.setId(generateId());
        group.setName(dto.getName());
        group.setDescription(dto.getDescription());
        group.setVersion(dto.getVersion());
        group.setCacheSize(dto.getCacheSize());
        group.setCachePolicy(dto.getCachePolicy());
        group.setCreatedDate(new Date());
        group.setUpdatedDate(new Date());
        return group;
    }

    public static LookupValue fromValueDto(LookupValueRequest dto) {
        LookupValue value = new LookupValue();

//        value.setKeyValue(dto.getKeyValue());
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
