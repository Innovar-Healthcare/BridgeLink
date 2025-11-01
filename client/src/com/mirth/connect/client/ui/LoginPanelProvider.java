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

import java.util.Objects;
import java.util.function.Supplier;

public final class LoginPanelProvider {
    private static volatile Supplier<AbstractLoginPanel> customFactory;
    private static AbstractLoginPanel instance;

    private LoginPanelProvider() {
    }

    /**
     * Register a custom login panel provider (must implement CustomLoginPanelProvider). Call once, before getInstance().
     */
    public static synchronized void registerCustomLoginPanelFactory(String className) {
        if (instance != null || customFactory != null) {
            return;
        }

        Objects.requireNonNull(className, "className");

        try {
            Class<?> cls = Class.forName(className);
            CustomLoginPanelProvider provider = (CustomLoginPanelProvider) cls.getDeclaredConstructor().newInstance();

            Supplier<AbstractLoginPanel> factory = provider.getFactory();
            if (factory != null) {
                customFactory = factory;
            }
        } catch (ClassNotFoundException e) {
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** Returns singleton instance (custom if registered, else default). */
    public static synchronized AbstractLoginPanel getInstance() {
        if (instance == null) {
            instance = createPanelFailSafe();
        }

        return instance;
    }

    private static AbstractLoginPanel createPanelFailSafe() {
        try {
            if (customFactory != null) {
                AbstractLoginPanel p = customFactory.get();
                if (p != null) {
                    return p;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return new LoginPanel(); // fallback
    }
}
