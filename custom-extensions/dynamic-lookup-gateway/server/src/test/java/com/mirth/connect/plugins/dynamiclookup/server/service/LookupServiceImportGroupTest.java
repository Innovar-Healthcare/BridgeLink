/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * REQ-26.4-013: revert-proof regression coverage for PR #13 (LookupService.importGroup JSON dispatch).
 */

package com.mirth.connect.plugins.dynamiclookup.server.service;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.plugins.dynamiclookup.server.cache.LookupCacheManager;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupAuditDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupExtraDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupStatisticsDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupValueDao;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo.DatabaseType;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.LookupJsonCapability;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;

/**
 * Unit tests for {@link LookupService#importGroup(LookupGroup, Map, boolean, String)}.
 *
 * Pins the PR #13 fix ("fix(dynamic-lookup-gateway): dispatch JSON groups to importValuesJson in
 * importGroup"): a JSON-typed group imported via the existing-group + updateIfExists=true path
 * must dispatch to {@link LookupValueDao#importValuesJson(String, Map)} and never to
 * {@link LookupValueDao#importValues(String, Map)}. Reverting the fix flips this dispatch and
 * this test goes RED (revert-proof).
 *
 * A sibling TEXT-group case pins the mirror (non-JSON) branch.
 */
public class LookupServiceImportGroupTest {

    private LookupGroupDao groupDao;
    private LookupValueDao valueDao;
    private LookupAuditDao auditDao;
    private LookupStatisticsDao statisticsDao;
    private LookupGroupExtraDao groupExtraDao;
    private LookupCacheManager cacheManager;
    private LookupService service;

    @Before
    public void setUp() {
        // LookupJsonCapability is a process-wide singleton (server/init.initialize(...) in
        // production). The ant test-run forks a fresh JVM per test class (forkmode="perTest"),
        // so initializing it here is safe and does not leak across test classes; initialize(...)
        // is itself a no-op once set, so calling it once per test method is harmless.
        LookupJsonCapability.initialize(new DatabaseInfo(DatabaseType.POSTGRESQL, 9, 4, "PostgreSQL", "9.4"));

        groupDao = mock(LookupGroupDao.class);
        valueDao = mock(LookupValueDao.class);
        auditDao = mock(LookupAuditDao.class);
        statisticsDao = mock(LookupStatisticsDao.class);
        groupExtraDao = mock(LookupGroupExtraDao.class);
        cacheManager = mock(LookupCacheManager.class);

        service = LookupService.getInstance();
        service.init(groupDao, valueDao, auditDao, statisticsDao, groupExtraDao, cacheManager);
    }

    // ------------------------------------------------------------------
    // JSON group — dispatches to importValuesJson, never importValues
    // ------------------------------------------------------------------

    @Test
    public void importGroup_existingJsonGroup_dispatchesToImportValuesJson_neverImportValues() {
        LookupGroup existingGroup = new LookupGroup();
        existingGroup.setId(42);
        existingGroup.setName("jsonGroup");
        existingGroup.setValueType("JSON");
        existingGroup.setCachePolicy("LRU");
        existingGroup.setCacheSize(1000);
        existingGroup.setExtra(new LookupGroupExtra(42, "NONE", null));

        when(groupDao.getGroupByName("jsonGroup")).thenReturn(existingGroup);
        when(groupDao.getGroupById(42)).thenReturn(existingGroup);
        when(groupExtraDao.getByGroupId(42)).thenReturn(new LookupGroupExtra(42, "NONE", null));

        LookupGroup importGroup = new LookupGroup();
        importGroup.setName("jsonGroup");
        importGroup.setValueType("JSON");
        importGroup.setCachePolicy("LRU");
        importGroup.setCacheSize(1000);
        importGroup.setExtra(new LookupGroupExtra(0, "NONE", null));

        Map<String, String> values = new LinkedHashMap<>();
        values.put("key1", "{\"a\":1}");
        values.put("key2", "{\"b\":2}");

        when(valueDao.importValuesJson(anyString(), anyMap())).thenReturn(2);

        int count = service.importGroup(importGroup, values, true, "testUser");

        verify(valueDao).importValuesJson(anyString(), anyMap());
        verify(valueDao, never()).importValues(anyString(), anyMap());
        org.junit.Assert.assertEquals(2, count);
    }

    // ------------------------------------------------------------------
    // TEXT group — mirror: dispatches to importValues, never importValuesJson
    // ------------------------------------------------------------------

    @Test
    public void importGroup_existingTextGroup_dispatchesToImportValues_neverImportValuesJson() {
        LookupGroup existingGroup = new LookupGroup();
        existingGroup.setId(7);
        existingGroup.setName("textGroup");
        existingGroup.setValueType("TEXT");
        existingGroup.setCachePolicy("LRU");
        existingGroup.setCacheSize(1000);

        when(groupDao.getGroupByName("textGroup")).thenReturn(existingGroup);
        when(groupDao.getGroupById(7)).thenReturn(existingGroup);

        LookupGroup importGroup = new LookupGroup();
        importGroup.setName("textGroup");
        importGroup.setValueType("TEXT");
        importGroup.setCachePolicy("LRU");
        importGroup.setCacheSize(1000);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("key1", "value1");
        values.put("key2", "value2");

        when(valueDao.importValues(anyString(), anyMap())).thenReturn(2);

        int count = service.importGroup(importGroup, values, true, "testUser");

        verify(valueDao).importValues(anyString(), anyMap());
        verify(valueDao, never()).importValuesJson(anyString(), anyMap());
        org.junit.Assert.assertEquals(2, count);
    }
}
