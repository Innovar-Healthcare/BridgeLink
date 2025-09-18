// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Richard Ogin

package com.mirth.connect.userutil;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mirth.connect.server.userutil.DestinationSet;
import org.junit.Test;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.server.Constants;


public class DestinationSetTest {
    private Set<Integer> createMetadataIds() {
        Set<Integer> metaDataIds = new HashSet<>();
        metaDataIds.add(1);
        metaDataIds.add(3);
        metaDataIds.add(5);
        metaDataIds.add(7);
        metaDataIds.add(9);

        return metaDataIds;
    }

    private Map<String, Integer> createDestinationIdMap() {
        Map<String, Integer> destinationIdMap = new HashMap<>();
        destinationIdMap.put("One", 1);
        destinationIdMap.put("Three", 3);
        destinationIdMap.put("Five", 5);
        destinationIdMap.put("Seven", 7);
        destinationIdMap.put("Nine", 9);

        return destinationIdMap;
    }

    private ImmutableConnectorMessage createMessage(Map<String, Integer> destinationIdMap, Set<Integer> metadataIds) {
        ConnectorMessage cm = new ConnectorMessage();

        if(metadataIds != null) {
            cm.getSourceMap().put(Constants.DESTINATION_SET_KEY, metadataIds);
        }

        if(destinationIdMap != null) {
            return new ImmutableConnectorMessage(cm,true, destinationIdMap);
        } else {
            return new ImmutableConnectorMessage(cm, true);
        }
    }

    @Test
    public void test_removeAllExceptObject_withSourceMap_removeAllForMetadataIdWhichDoesNotExist() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAllExcept("I_DONT_EXIST"));
        assertTrue(metaDataIds.isEmpty());
    }

    @Test
    public void test_removeAllExceptObject_withSourceMap_removeForMatchingMetadataId() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAllExcept(3));
        assertTrue(metaDataIds.size() == 1);
    }

    @Test
    public void test_removeAllExceptObject_withSourceMap_removeForMatchingConnectorName() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAllExcept("Seven"));
        assertTrue(metaDataIds.size() == 1);
    }

    @Test
    public void test_removeAllExceptObject_noSourceMap_noRemovalForMetadataIdWhichDoesNotExist() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        assertFalse(destinationSet.removeAllExcept("I_DONT_EXIST"));
    }

    @Test
    public void test_removeAllExceptObject_noSourceMap_noRemovalForMatchingMetadataId() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        assertFalse(destinationSet.removeAllExcept(3));
    }

    @Test
    public void test_removeAllExceptObject_noSourceMap_noRemovalForMatchingConnectorName() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        assertFalse(destinationSet.removeAllExcept("Seven"));
    }
}