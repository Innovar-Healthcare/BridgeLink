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

public abstract class AbstractLoginPanel extends javax.swing.JFrame {
    public abstract void initialize(String mirthServer, String version, String user, String pass);

    public abstract void setStatus(String status);
}
