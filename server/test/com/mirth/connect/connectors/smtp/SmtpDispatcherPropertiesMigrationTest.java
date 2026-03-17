/*
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 */
package com.mirth.connect.connectors.smtp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.mirth.connect.donkey.util.DonkeyElement;

public class SmtpDispatcherPropertiesMigrationTest {

    // -----------------------------------------------------------------------
    // authType derivation
    // -----------------------------------------------------------------------

    @Test
    public void testMigrate_AuthTrueBecomesBasic() throws Exception {
        DonkeyElement element = new DonkeyElement(
                "<SmtpDispatcherProperties><authentication>true</authentication></SmtpDispatcherProperties>");

        new SmtpDispatcherProperties().migrate26_3_0(element);

        DonkeyElement authType = element.getChildElement("authType");
        assertNotNull("authType element should be added", authType);
        assertEquals("BASIC", authType.getTextContent());
    }

    @Test
    public void testMigrate_AuthFalseBecomesNone() throws Exception {
        DonkeyElement element = new DonkeyElement(
                "<SmtpDispatcherProperties><authentication>false</authentication></SmtpDispatcherProperties>");

        new SmtpDispatcherProperties().migrate26_3_0(element);

        DonkeyElement authType = element.getChildElement("authType");
        assertNotNull("authType element should be added", authType);
        assertEquals("NONE", authType.getTextContent());
    }

    @Test
    public void testMigrate_AuthAbsentBecomesNone() throws Exception {
        DonkeyElement element = new DonkeyElement("<SmtpDispatcherProperties/>");

        new SmtpDispatcherProperties().migrate26_3_0(element);

        DonkeyElement authType = element.getChildElement("authType");
        assertNotNull("authType element should be added when authentication is absent", authType);
        assertEquals("NONE", authType.getTextContent());
    }

    // -----------------------------------------------------------------------
    // OAuth fields are added
    // -----------------------------------------------------------------------

    @Test
    public void testMigrate_OAuthFieldsAdded() throws Exception {
        DonkeyElement element = new DonkeyElement("<SmtpDispatcherProperties/>");

        new SmtpDispatcherProperties().migrate26_3_0(element);

        assertNotNull("oAuthClientId should be added", element.getChildElement("oAuthClientId"));
        assertNotNull("oAuthClientSecret should be added", element.getChildElement("oAuthClientSecret"));
        assertNotNull("oAuthTokenEndpointUrl should be added", element.getChildElement("oAuthTokenEndpointUrl"));
        assertNotNull("oAuthScope should be added", element.getChildElement("oAuthScope"));

        assertEquals("", element.getChildElement("oAuthClientId").getTextContent());
        assertEquals("", element.getChildElement("oAuthClientSecret").getTextContent());
        assertEquals("", element.getChildElement("oAuthTokenEndpointUrl").getTextContent());
        assertEquals("https://outlook.office365.com/.default",
                element.getChildElement("oAuthScope").getTextContent());
    }

    // -----------------------------------------------------------------------
    // Existing values are preserved (addChildElementIfNotExists)
    // -----------------------------------------------------------------------

    @Test
    public void testMigrate_ExistingAuthTypeNotOverwritten() throws Exception {
        DonkeyElement element = new DonkeyElement(
                "<SmtpDispatcherProperties>"
                        + "<authentication>true</authentication>"
                        + "<authType>OAUTH</authType>"
                        + "</SmtpDispatcherProperties>");

        new SmtpDispatcherProperties().migrate26_3_0(element);

        // authType was already OAUTH — must not be reset to BASIC
        assertEquals("OAUTH", element.getChildElement("authType").getTextContent());
    }

    @Test
    public void testMigrate_ExistingOAuthScopeNotOverwritten() throws Exception {
        DonkeyElement element = new DonkeyElement(
                "<SmtpDispatcherProperties>"
                        + "<oAuthScope>https://custom.scope/.default</oAuthScope>"
                        + "</SmtpDispatcherProperties>");

        new SmtpDispatcherProperties().migrate26_3_0(element);

        assertEquals("https://custom.scope/.default",
                element.getChildElement("oAuthScope").getTextContent());
    }
}
