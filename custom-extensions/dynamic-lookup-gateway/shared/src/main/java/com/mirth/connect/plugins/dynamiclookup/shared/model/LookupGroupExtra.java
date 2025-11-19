package com.mirth.connect.plugins.dynamiclookup.shared.model;

public class LookupGroupExtra {
    private int groupId; // PK = LOOKUP_GROUP.ID
    private String valueType; // TEXT | JSON
    private String jsonIndexMode; // NONE | GIN | FIELD
    private String indexedJsonFields; // JSON string (mapping to JSONB/TEXT)

    public LookupGroupExtra() {
    }

    public LookupGroupExtra(int groupId, String valueType, String jsonIndexMode, String indexedJsonFields) {
        this.groupId = groupId;
        this.valueType = valueType;
        this.jsonIndexMode = jsonIndexMode;
        this.indexedJsonFields = indexedJsonFields;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getJsonIndexMode() {
        return jsonIndexMode;
    }

    public void setJsonIndexMode(String jsonIndexMode) {
        this.jsonIndexMode = jsonIndexMode;
    }

    public String getIndexedJsonFields() {
        return indexedJsonFields;
    }

    public void setIndexedJsonFields(String indexedJsonFields) {
        this.indexedJsonFields = indexedJsonFields;
    }

    //@formatter:off
    @Override
    public String toString() {
        return "LookupGroupExtra{" +
                "groupId=" + groupId +
                ", valueType='" + valueType + '\'' +
                ", jsonIndexMode='" + jsonIndexMode + '\'' +
                ", indexedJsonFields='" + indexedJsonFields + '\'' +
                '}';
    }
    //@formatter:on
}
