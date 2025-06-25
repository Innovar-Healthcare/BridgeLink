package com.mirth.connect.plugins.dynamiclookup.shared.dto.response;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LookupValueResponse {

    private Integer groupId; // Optional: for context in the response
    private String key;
    private String value;
    private Date createdDate;
    private Date updatedDate;

    public static LookupValueResponse from(LookupValue entity, Integer groupId, boolean full) {
        if (full) {
            return new LookupValueResponse(entity, groupId);
        }
        return new LookupValueResponse(entity.getKeyValue(), entity.getValueData(), null, null, groupId);
    }

    public LookupValueResponse() {
    }

    public LookupValueResponse(String key, String value, Date createdDate, Date updatedDate, Integer groupId) {
        this.groupId = groupId;
        this.key = key;
        this.value = value;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public LookupValueResponse(LookupValue entity, Integer groupId) {
        this.groupId = groupId;
        this.key = entity.getKeyValue();
        this.value = entity.getValueData();
        this.createdDate = entity.getCreatedDate();
        this.updatedDate = entity.getUpdatedDate();
    }

    // Getters and setters
    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(Date updatedDate) {
        this.updatedDate = updatedDate;
    }
}
