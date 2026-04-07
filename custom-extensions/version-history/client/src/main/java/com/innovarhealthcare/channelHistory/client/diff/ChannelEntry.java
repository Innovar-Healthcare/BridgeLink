/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.diff;

import com.innovarhealthcare.channelHistory.client.diff.model.ChangeType;

/**
 * Represents one item in the ChannelDiffPanel component list
 * (Channel Info, source connector, or a destination connector).
 */
class ChannelEntry {
    private final String     label;
    private final ChangeType changeType;
    private final String     leftContent;
    private final String     rightContent;

    /**
     * Optional version label overrides shown in the DiffComparisonPanel header.
     * {@code null} means use the version info from the outer dialog unchanged.
     */
    private final String leftVersionOverride;
    private final String rightVersionOverride;

    ChannelEntry(String label, ChangeType changeType,
                 String leftContent, String rightContent,
                 String leftVersionOverride, String rightVersionOverride) {
        this.label                = label        != null ? label        : "";
        this.changeType           = changeType   != null ? changeType   : ChangeType.UNCHANGED;
        this.leftContent          = leftContent  != null ? leftContent  : "";
        this.rightContent         = rightContent != null ? rightContent : "";
        this.leftVersionOverride  = leftVersionOverride;
        this.rightVersionOverride = rightVersionOverride;
    }

    String     getLabel()                { return label; }
    ChangeType getChangeType()           { return changeType; }
    String     getLeftContent()          { return leftContent; }
    String     getRightContent()         { return rightContent; }
    String     getLeftVersionOverride()  { return leftVersionOverride; }
    String     getRightVersionOverride() { return rightVersionOverride; }

    /** Used by DefaultListModel as the default display text. */
    @Override
    public String toString() { return label; }
}
