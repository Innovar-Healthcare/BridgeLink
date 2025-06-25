package com.mirth.connect.plugins.dynamiclookup.shared.dto.response;

import java.util.ArrayList;
import java.util.List;

public class ImportLookupGroupResponse {
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR = "error";

    private String status;
    private int groupId;
    private int importedCount;
    private List<String> errors;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
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

    public boolean isSuccessful() {
        return STATUS_SUCCESS.equalsIgnoreCase(status);
    }

    public boolean isError() {
        return STATUS_ERROR.equalsIgnoreCase(status);
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public static ImportLookupGroupResponse fromResult(int groupId, int importedCount, List<String> errors) {
        ImportLookupGroupResponse.Builder builder = new ImportLookupGroupResponse.Builder()
                .withGroupId(groupId)
                .withImportedCount(importedCount);

        if (errors != null && !errors.isEmpty()) {
            builder.addErrors(errors);
        }

        return builder.build();
    }

    public static class Builder {
        private final ImportLookupGroupResponse response;

        public Builder() {
            response = new ImportLookupGroupResponse();
            response.setErrors(new ArrayList<>());
        }

        public Builder withGroupId(int groupId) {
            response.setGroupId(groupId);
            return this;
        }

        public Builder withImportedCount(int count) {
            response.setImportedCount(count);
            return this;
        }

        public Builder addError(String errorMessage) {
            response.getErrors().add(errorMessage);
            return this;
        }

        public Builder addErrors(List<String> errors) {
            if (errors != null && !errors.isEmpty()) {
                response.getErrors().addAll(errors);
            }
            return this;
        }

        public ImportLookupGroupResponse build() {
            response.setStatus(ImportLookupGroupResponse.STATUS_SUCCESS);
            return response;
        }
    }

}

