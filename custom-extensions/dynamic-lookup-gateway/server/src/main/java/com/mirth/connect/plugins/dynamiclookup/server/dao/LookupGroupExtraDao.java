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

import java.util.List;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;

public interface LookupGroupExtraDao {
    LookupGroupExtra getByGroupId(int groupId);

    List<LookupGroupExtra> getAllGroupExtras();

    int insert(LookupGroupExtra extra);

    void update(LookupGroupExtra extra);

    boolean extraExists(int groupId);
}
