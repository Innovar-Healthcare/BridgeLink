/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Sample extension for the declarative WebAdmin dataTypes contribution kind (IRT-1442). This class
 * is the extension's registered server class (plugin.xml serverClasses) — the engine keys it into
 * the data type plugin registry by getPluginPointName(), and the webadmin datatype-defaults
 * endpoint serves getDefaultProperties() for it.
 */

package com.innovarhealthcare.connect.plugins.mockdimse;

import com.mirth.connect.model.datatype.DataTypeDelegate;
import com.mirth.connect.plugins.DataTypeServerPlugin;

public class MockDIMSEDataTypeServerPlugin extends DataTypeServerPlugin {

    private DataTypeDelegate dataTypeDelegate = new MockDIMSEDataTypeDelegate();

    @Override
    public String getPluginPointName() {
        return dataTypeDelegate.getName();
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    protected DataTypeDelegate getDataTypeDelegate() {
        return dataTypeDelegate;
    }
}
