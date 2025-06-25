package com.mirth.connect.plugins.dynamiclookup.shared.dto.response;

import java.util.List;

public class ImportValuesResponse {
    private Integer groupId;
    private String status;
    private int importedCount;
    private List<String> errors;

    public ImportValuesResponse() {
    }

    public ImportValuesResponse(Integer groupId, String status, int importedCount, List<String> errors) {
        this.groupId = groupId;
        this.status = status;
        this.importedCount = importedCount;
        this.errors = errors;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public void setImportedCount(int importedCount) {
        this.importedCount = importedCount;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}

