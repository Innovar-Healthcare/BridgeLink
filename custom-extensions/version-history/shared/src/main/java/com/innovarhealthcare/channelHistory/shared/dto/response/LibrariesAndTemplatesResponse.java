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

import java.util.List;

/**
 * Response containing libraries and code template metadata
 * Libraries contain codeTemplateIds, templates contain full metadata
 * Client can use codeTemplateIds to group templates by library
 */
public class LibrariesAndTemplatesResponse {

    private List<LibraryMetadata> libraries;
    private List<RepoItemMetadata> templates;

    /**
     * Default constructor for serialization frameworks
     */
    public LibrariesAndTemplatesResponse() {
    }

    /**
     * Full constructor
     */
    public LibrariesAndTemplatesResponse(List<LibraryMetadata> libraries, List<RepoItemMetadata> templates) {
        this.libraries = libraries;
        this.templates = templates;
    }

    /**
     * Get all libraries with their codeTemplateIds
     *
     * @return List of library metadata
     */
    public List<LibraryMetadata> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<LibraryMetadata> libraries) {
        this.libraries = libraries;
    }

    /**
     * Get metadata for all code templates
     * Use library.codeTemplateIds to group these by library
     *
     * @return List of code template metadata
     */
    public List<RepoItemMetadata> getTemplates() {
        return templates;
    }

    public void setTemplates(List<RepoItemMetadata> templates) {
        this.templates = templates;
    }

    //@formatter:off
    @Override
    public String toString() {
        return "LibrariesAndTemplatesResponse{" +
                "libraries=" + (libraries != null ? libraries.size() : 0) +
                ", templates=" + (templates != null ? templates.size() : 0) +
                '}';
    }
    //@formatter:on
}
