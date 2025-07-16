/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.dao;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

import java.util.List;

public interface LookupGroupDao {
    // Group CRUD operations
    LookupGroup getGroupById(int id);

    LookupGroup getGroupByName(String name);

    List<LookupGroup> getAllGroups();

    int insertGroup(LookupGroup group);

    void updateGroup(LookupGroup group);

    void deleteGroup(int id);

    // Dynamic table management
    void createValueTable(String tableName);

    void dropValueTable(String tableName);

    boolean tableExists(String tableName);

}
