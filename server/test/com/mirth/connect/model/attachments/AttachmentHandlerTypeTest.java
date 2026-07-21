/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 enum coverage — AttachmentHandlerType toString/fromString/getDefaultClassName.
 */

package com.mirth.connect.model.attachments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AttachmentHandlerTypeTest {

    // ------------------------------------------------------------------
    // toString: returns human-readable label
    // ------------------------------------------------------------------

    @Test
    public void toString_none_returnsNone() {
        assertEquals("None", AttachmentHandlerType.NONE.toString());
    }

    @Test
    public void toString_identity_returnsEntireMessage() {
        assertEquals("Entire Message", AttachmentHandlerType.IDENTITY.toString());
    }

    @Test
    public void toString_regex_returnsRegex() {
        assertEquals("Regex", AttachmentHandlerType.REGEX.toString());
    }

    @Test
    public void toString_dicom_returnsDICOM() {
        assertEquals("DICOM", AttachmentHandlerType.DICOM.toString());
    }

    @Test
    public void toString_javascript_returnsJavaScript() {
        assertEquals("JavaScript", AttachmentHandlerType.JAVASCRIPT.toString());
    }

    @Test
    public void toString_custom_returnsCustom() {
        assertEquals("Custom", AttachmentHandlerType.CUSTOM.toString());
    }

    // ------------------------------------------------------------------
    // fromString: parses label back to enum
    // ------------------------------------------------------------------

    @Test
    public void fromString_none_returnsNone() {
        assertEquals(AttachmentHandlerType.NONE, AttachmentHandlerType.fromString("None"));
    }

    @Test
    public void fromString_identity_returnsIdentity() {
        assertEquals(AttachmentHandlerType.IDENTITY, AttachmentHandlerType.fromString("Entire Message"));
    }

    @Test
    public void fromString_regex_returnsRegex() {
        assertEquals(AttachmentHandlerType.REGEX, AttachmentHandlerType.fromString("Regex"));
    }

    @Test
    public void fromString_dicom_returnsDicom() {
        assertEquals(AttachmentHandlerType.DICOM, AttachmentHandlerType.fromString("DICOM"));
    }

    @Test
    public void fromString_javascript_returnsJavaScript() {
        assertEquals(AttachmentHandlerType.JAVASCRIPT, AttachmentHandlerType.fromString("JavaScript"));
    }

    @Test
    public void fromString_custom_returnsCustom() {
        assertEquals(AttachmentHandlerType.CUSTOM, AttachmentHandlerType.fromString("Custom"));
    }

    @Test
    public void fromString_unknown_returnsNull() {
        assertNull(AttachmentHandlerType.fromString("Unknown"));
    }

    // ------------------------------------------------------------------
    // getDefaultClassName: returns class name for handler
    // ------------------------------------------------------------------

    @Test
    public void getDefaultClassName_none_returnsNull() {
        assertNull(AttachmentHandlerType.NONE.getDefaultClassName());
    }

    @Test
    public void getDefaultClassName_identity_returnsClassName() {
        String cn = AttachmentHandlerType.IDENTITY.getDefaultClassName();
        assertNotNull(cn);
        assertTrue(cn.contains("IdentityAttachmentHandlerProvider"));
    }

    @Test
    public void getDefaultClassName_regex_returnsClassName() {
        String cn = AttachmentHandlerType.REGEX.getDefaultClassName();
        assertNotNull(cn);
        assertTrue(cn.contains("RegexAttachmentHandlerProvider"));
    }

    @Test
    public void getDefaultClassName_dicom_returnsClassName() {
        String cn = AttachmentHandlerType.DICOM.getDefaultClassName();
        assertNotNull(cn);
        assertTrue(cn.contains("DICOMAttachmentHandlerProvider"));
    }

    @Test
    public void getDefaultClassName_javascript_returnsClassName() {
        String cn = AttachmentHandlerType.JAVASCRIPT.getDefaultClassName();
        assertNotNull(cn);
        assertTrue(cn.contains("JavaScriptAttachmentHandlerProvider"));
    }

    @Test
    public void getDefaultClassName_custom_returnsEmptyString() {
        assertEquals("", AttachmentHandlerType.CUSTOM.getDefaultClassName());
    }

    // ------------------------------------------------------------------
    // getDefaultProperties: returns non-null properties for most types
    // ------------------------------------------------------------------

    @Test
    public void getDefaultProperties_none_returnsProperties() {
        assertNotNull(AttachmentHandlerType.NONE.getDefaultProperties());
    }

    @Test
    public void getDefaultProperties_regex_containsPattern() {
        assertNotNull(AttachmentHandlerType.REGEX.getDefaultProperties());
        assertTrue(AttachmentHandlerType.REGEX.getDefaultProperties().getProperties().containsKey("regex.pattern0"));
    }

    @Test
    public void getDefaultProperties_javascript_containsScript() {
        assertNotNull(AttachmentHandlerType.JAVASCRIPT.getDefaultProperties());
        assertTrue(AttachmentHandlerType.JAVASCRIPT.getDefaultProperties().getProperties().containsKey("javascript.script"));
    }

    // ------------------------------------------------------------------
    // values: 6 types present
    // ------------------------------------------------------------------

    @Test
    public void values_returns6Types() {
        assertEquals(6, AttachmentHandlerType.values().length);
    }
}
