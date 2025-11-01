/*
*
* Copyright (c) Innovar Healthcare. All rights reserved.
*
* https://www.innovarhealthcare.com
*
* The software in this package is published under the terms of the MPL license a copy of which has
* been included with this distribution in the LICENSE.txt file.
*/

package com.mirth.connect.client.ui;

import java.util.function.Supplier;

public interface CustomLoginPanelProvider {
    Supplier<AbstractLoginPanel> getFactory();
}
