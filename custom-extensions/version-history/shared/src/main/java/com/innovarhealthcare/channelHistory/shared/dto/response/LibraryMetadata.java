/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.shared.dto.response;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata for a CodeTemplate library
 * Contains essential information for grouping and displaying code templates
 */
public class LibraryMetadata {

    private String id;
    private String name;
    private List<String> codeTemplateIds;

    /**
     * Default constructor for serialization frameworks
     */
    public LibraryMetadata() {
    }

    /**
     * Full constructor
     */
    @JsonCreator
    public LibraryMetadata(@JsonProperty("id") String id, @JsonProperty("name") String name, @JsonProperty("codeTemplateIds") List<String> codeTemplateIds) {
        this.id = id;
        this.name = name;
        this.codeTemplateIds = codeTemplateIds != null ? codeTemplateIds : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get IDs of code templates in this library
     *
     * @return List of code template IDs
     */
    public List<String> getCodeTemplateIds() {
        return codeTemplateIds;
    }

    public void setCodeTemplateIds(List<String> codeTemplateIds) {
        this.codeTemplateIds = codeTemplateIds;
    }

    //@formatter:off
    @Override
    public String toString() {
        return "LibraryMetadata{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", templateCount=" + (codeTemplateIds != null ? codeTemplateIds.size() : 0) +
                '}';
    }
    //@formatter:on
}
