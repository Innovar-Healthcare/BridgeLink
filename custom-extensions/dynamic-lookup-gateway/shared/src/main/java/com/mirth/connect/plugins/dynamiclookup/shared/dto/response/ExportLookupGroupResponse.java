package com.mirth.connect.plugins.dynamiclookup.shared.dto.response;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

import java.util.Date;
import java.util.Map;

public class ExportLookupGroupResponse {
    private LookupGroup group;
    private Map<String, String> values;
    private Date exportDate;

    public ExportLookupGroupResponse() {
    }

    public ExportLookupGroupResponse(LookupGroup group, Map<String, String> values, Date exportDate) {
        this.group = group;
        this.values = values;
        this.exportDate = exportDate;
    }

    public LookupGroup getGroup() {
        return group;
    }

    public void setGroup(LookupGroup group) {
        this.group = group;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values = values;
    }

    public Date getExportDate() {
        return exportDate;
    }

    public void setExportDate(Date exportDate) {
        this.exportDate = exportDate;
    }
}



