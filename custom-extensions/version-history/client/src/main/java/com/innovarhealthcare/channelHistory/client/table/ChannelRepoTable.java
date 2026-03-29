/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.table;

import com.innovarhealthcare.channelHistory.client.model.ChannelRepoTableModel;

import javax.swing.table.TableModel;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */
public class ChannelRepoTable extends AbstractRepoTable {
    private static final int[] COLUMN_WIDTHS = {150, 200}; // Channel Id, Channel Name

    public ChannelRepoTable() {
        super(2); // Hide Last Commit Id (index 2)
    }

    @Override
    protected int[] getColumnWidths() {
        return COLUMN_WIDTHS;
    }

    @Override
    protected boolean validateModel(TableModel dataModel) {
        return dataModel instanceof ChannelRepoTableModel && dataModel.getColumnCount() >= 3;
    }
}