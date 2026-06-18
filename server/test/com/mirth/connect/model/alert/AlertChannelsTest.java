/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 alert POJO coverage — AlertChannels addChannel / isChannelEnabled / isConnectorEnabled.
 */

package com.mirth.connect.model.alert;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class AlertChannelsTest {

    // ------------------------------------------------------------------
    // Default constructor
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_flagsFalse_setsEmpty() {
        AlertChannels ac = new AlertChannels();
        assertFalse(ac.isNewChannelSource());
        assertFalse(ac.isNewChannelDestination());
        assertNotNull(ac.getEnabledChannels());
        assertTrue(ac.getEnabledChannels().isEmpty());
        assertNotNull(ac.getDisabledChannels());
        assertTrue(ac.getDisabledChannels().isEmpty());
        assertNotNull(ac.getPartialChannels());
        assertTrue(ac.getPartialChannels().isEmpty());
    }

    // ------------------------------------------------------------------
    // setNewChannel: sets source and destination flags
    // ------------------------------------------------------------------

    @Test
    public void setNewChannel_bothTrue_flagsSet() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(true, true);
        assertTrue(ac.isNewChannelSource());
        assertTrue(ac.isNewChannelDestination());
    }

    @Test
    public void setNewChannel_sourceOnly_onlySourceTrue() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(true, false);
        assertTrue(ac.isNewChannelSource());
        assertFalse(ac.isNewChannelDestination());
    }

    @Test
    public void setNewChannel_destinationOnly_onlyDestinationTrue() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(false, true);
        assertFalse(ac.isNewChannelSource());
        assertTrue(ac.isNewChannelDestination());
    }

    // ------------------------------------------------------------------
    // addChannel: allEnabled path → channel in enabledChannels
    // ------------------------------------------------------------------

    @Test
    public void addChannel_allEnabled_addedToEnabledChannels() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(false, false); // does NOT match new channel defaults

        Map<Integer, Boolean> connectors = new HashMap<Integer, Boolean>();
        connectors.put(1, true);
        connectors.put(2, true);
        ac.addChannel("chan-1", connectors);

        assertTrue(ac.getEnabledChannels().contains("chan-1"));
        assertFalse(ac.getDisabledChannels().contains("chan-1"));
        assertFalse(ac.getPartialChannels().containsKey("chan-1"));
    }

    // ------------------------------------------------------------------
    // addChannel: allDisabled path → channel in disabledChannels
    // ------------------------------------------------------------------

    @Test
    public void addChannel_allDisabled_addedToDisabledChannels() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(true, true); // new channel defaults: source=true, dest=true

        Map<Integer, Boolean> connectors = new HashMap<Integer, Boolean>();
        connectors.put(1, false); // doesn't match newChannelDestination=true
        connectors.put(2, false);
        ac.addChannel("chan-2", connectors);

        assertTrue(ac.getDisabledChannels().contains("chan-2"));
        assertFalse(ac.getEnabledChannels().contains("chan-2"));
    }

    // ------------------------------------------------------------------
    // addChannel: partial path → channel in partialChannels
    // ------------------------------------------------------------------

    @Test
    public void addChannel_mixed_addedToPartialChannels() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(false, false);

        // metaDataId=0 is source, metaDataId=1 is destination
        Map<Integer, Boolean> connectors = new HashMap<Integer, Boolean>();
        connectors.put(0, true);  // source=true: matches newChannelSource=false? No → matchesNewChannel=false
        connectors.put(1, false); // dest=false: matches newChannelDest=false? Yes
        // But source doesn't match → partial, not allEnabled (false present), not allDisabled (true present)
        ac.addChannel("chan-3", connectors);

        assertTrue(ac.getPartialChannels().containsKey("chan-3"));
    }

    // ------------------------------------------------------------------
    // addChannel: matchesNewChannel=true → nothing added
    // ------------------------------------------------------------------

    @Test
    public void addChannel_matchesNewChannelDefaults_notAdded() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(true, true); // newChannelSource=true, newChannelDestination=true

        // source (metaDataId=0, enabled=true), dest (metaDataId=1, enabled=true) — matches defaults
        Map<Integer, Boolean> connectors = new HashMap<Integer, Boolean>();
        connectors.put(0, true);
        connectors.put(1, true);
        ac.addChannel("chan-match", connectors);

        assertFalse(ac.getEnabledChannels().contains("chan-match"));
        assertFalse(ac.getDisabledChannels().contains("chan-match"));
        assertFalse(ac.getPartialChannels().containsKey("chan-match"));
    }

    // ------------------------------------------------------------------
    // isChannelEnabled: enabled channel path
    // ------------------------------------------------------------------

    @Test
    public void isChannelEnabled_inEnabledSet_returnsTrue() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(false, false);
        Map<Integer, Boolean> connectors = new HashMap<Integer, Boolean>();
        connectors.put(1, true);
        connectors.put(2, true);
        ac.addChannel("chan-e", connectors);

        assertTrue(ac.isChannelEnabled("chan-e"));
    }

    @Test
    public void isChannelEnabled_inDisabledSet_returnsFalse() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(true, true);
        Map<Integer, Boolean> connectors = new HashMap<Integer, Boolean>();
        connectors.put(1, false);
        connectors.put(2, false);
        ac.addChannel("chan-d", connectors);

        assertFalse(ac.isChannelEnabled("chan-d"));
    }

    @Test
    public void isChannelEnabled_unknown_fallsBackToNewChannelFlags() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(true, true);

        // Channel not in any set — falls back to newChannelSource || newChannelDestination
        assertTrue(ac.isChannelEnabled("unknown-chan"));
    }

    @Test
    public void isChannelEnabled_unknownWithFalseFalse_returnsFalse() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(false, false);
        assertFalse(ac.isChannelEnabled("unknown-chan"));
    }

    @Test
    public void isChannelEnabled_partial_returnsTrue_whenHasEnabled() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(false, false);
        Map<Integer, Boolean> connectors = new HashMap<Integer, Boolean>();
        connectors.put(0, true);  // source enabled → allEnabled false (2nd connector disabled)
        connectors.put(1, false);
        ac.addChannel("chan-p", connectors);

        // partial: enabledConnectors has 0 → size > 0 → true
        assertTrue(ac.isChannelEnabled("chan-p"));
    }

    // ------------------------------------------------------------------
    // isConnectorEnabled: tests all branches
    // ------------------------------------------------------------------

    @Test
    public void isConnectorEnabled_inEnabledChannels_returnsTrue() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(false, false);
        Map<Integer, Boolean> connectors = new HashMap<Integer, Boolean>();
        connectors.put(1, true);
        connectors.put(2, true);
        ac.addChannel("chan-e", connectors);

        assertTrue(ac.isConnectorEnabled("chan-e", 99));
    }

    @Test
    public void isConnectorEnabled_inDisabledChannels_returnsFalse() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(true, true);
        Map<Integer, Boolean> connectors = new HashMap<Integer, Boolean>();
        connectors.put(1, false);
        connectors.put(2, false);
        ac.addChannel("chan-d", connectors);

        assertFalse(ac.isConnectorEnabled("chan-d", 1));
    }

    @Test
    public void isConnectorEnabled_unknownChannel_destination_fallsBackToNewChannelDest() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(false, true);

        // metaDataId > 0 → destination → newChannelDestination=true
        assertTrue(ac.isConnectorEnabled("unknown", 1));
    }

    @Test
    public void isConnectorEnabled_unknownChannel_source_fallsBackToNewChannelSource() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(true, false);

        // metaDataId == 0 → source → newChannelSource=true
        assertTrue(ac.isConnectorEnabled("unknown", 0));
    }

    @Test
    public void isConnectorEnabled_unknownChannel_nullMetaData_fallsBackToNewChannelDest() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(false, true);

        // metaDataId == null → treated as destination → newChannelDestination=true
        assertTrue(ac.isConnectorEnabled("unknown", null));
    }

    // ------------------------------------------------------------------
    // getPurgedProperties: contains expected keys
    // ------------------------------------------------------------------

    @Test
    public void getPurgedProperties_containsExpectedKeys() {
        AlertChannels ac = new AlertChannels();
        ac.setNewChannel(true, false);
        Map<String, Object> purged = ac.getPurgedProperties();
        assertNotNull(purged);
        assertTrue(purged.containsKey("newChannelSource"));
        assertTrue(purged.containsKey("newChannelDestination"));
        assertTrue(purged.containsKey("enabledChannelsCount"));
        assertTrue(purged.containsKey("disabledChannelsCount"));
        assertTrue(purged.containsKey("partialChannelsCount"));
        assertTrue((Boolean) purged.get("newChannelSource"));
        assertFalse((Boolean) purged.get("newChannelDestination"));
    }
}
