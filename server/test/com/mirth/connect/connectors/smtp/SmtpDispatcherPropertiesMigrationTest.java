/*
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 */
package com.mirth.connect.connectors.smtp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

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

    // -----------------------------------------------------------------------
    // cc/bcc survive an XStream round trip and the migration path (A3/F3)
    // -----------------------------------------------------------------------

    @Test
    public void testCcBccSurviveXStreamRoundTrip() throws Exception {
        try {
            ObjectXMLSerializer.getInstance().init(Version.getLatest().toString());
        } catch (Exception e) {
            // Ignore if it has already been initialized
        }

        SmtpDispatcherProperties original = new SmtpDispatcherProperties();
        original.setCc("cc@example.com");
        original.setBcc("bcc@example.com");

        // ObjectXMLSerializer is the serializer Mirth actually uses -- not a hand-rolled
        // XML compare (RESEARCH "Don't Hand-Roll").
        String xml = ObjectXMLSerializer.getInstance().serialize(original);
        SmtpDispatcherProperties deserialized = ObjectXMLSerializer.getInstance()
                .deserialize(xml, SmtpDispatcherProperties.class);

        assertEquals("cc should survive an XStream serialize -> deserialize round trip",
                "cc@example.com", deserialized.getCc());
        assertEquals("bcc should survive an XStream serialize -> deserialize round trip",
                "bcc@example.com", deserialized.getBcc());

        // Migration path: cc/bcc must not be dropped or renamed by migrate26_3_0, mirroring
        // the existing testMigrate_* methods' DonkeyElement + getChildElement style.
        DonkeyElement migratedElement = new DonkeyElement(xml);
        new SmtpDispatcherProperties().migrate26_3_0(migratedElement);

        DonkeyElement ccElement = migratedElement.getChildElement("cc");
        DonkeyElement bccElement = migratedElement.getChildElement("bcc");
        assertNotNull("cc element should survive migration", ccElement);
        assertEquals("cc@example.com", ccElement.getTextContent());
        assertNotNull("bcc element should survive migration", bccElement);
        assertEquals("bcc@example.com", bccElement.getTextContent());
    }
}
