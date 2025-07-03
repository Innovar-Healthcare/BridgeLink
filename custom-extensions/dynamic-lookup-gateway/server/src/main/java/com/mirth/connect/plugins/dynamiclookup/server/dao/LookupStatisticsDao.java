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

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupStatistics;

import java.util.List;

public interface LookupStatisticsDao {
    void insertStatistics(int groupId);

    void updateStatistics(int groupId, boolean cacheHit);

    LookupStatistics getStatistics(int groupId);

    void resetStatistics(int groupId);

    List<LookupStatistics> getAllStatistics();
}
