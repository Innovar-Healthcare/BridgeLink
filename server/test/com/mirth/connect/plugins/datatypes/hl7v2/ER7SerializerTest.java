/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.plugins.datatypes.hl7v2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXParseException;

import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.model.datatype.SerializerProperties;

public class ER7SerializerTest {
	private static ER7Serializer serializer;
	
	@BeforeClass
	public static void setupClass() throws Exception {
		SerializerProperties serializerProperties = new SerializerProperties(new HL7v2SerializationProperties(), new HL7v2DeserializationProperties(), null);
		serializer = new ER7Serializer(serializerProperties);
	}
	
	@Test
	public void testFromXMLWithExternalDTD() throws Exception {
		String xml = FileUtils.readFileToString(new File("tests/test-xxe-hl7-example.xml"), "UTF-8");
		
		boolean exceptionThrown = false;
		try {
			serializer.fromXML(xml);
		} catch (MessageSerializerException e) {
			exceptionThrown = true;
			
			// See https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j
			assertTrue(e.getCause() instanceof SAXParseException);
		}
		
		assertTrue(exceptionThrown);
	}

	@Test
	public void testValidFromXMLWithExternalDTD() throws Exception {
		String xml = FileUtils.readFileToString(new File("tests/test-xxe-hl7-example-valid.xml"), "UTF-8");
		
		boolean exceptionThrown = false;
		try {
			serializer.fromXML(xml);
		} catch (MessageSerializerException e) {
			exceptionThrown = true;
			
		}
		
		assertFalse(exceptionThrown);
	}

	/**
	 * IRT-1742: a supplementary-plane character in a field must serialize to a single numeric
	 * character reference, not a pair of surrogate references. Exercises the non-strict
	 * ER7 -> XML path (XMLPrettyPrinter -> MirthXmlUtil.encode) that this fix lives on.
	 */
	@Test
	public void testToXMLEncodesSupplementaryCharacterAsSingleReference() throws Exception {
		String emoji = new String(Character.toChars(0x1F50D));
		String er7 = "MSH|^~\\&|MIRTH|MIRTH|||200612131519||ORM^O01|12345678|P|2.4\rOBX|1|ST|NOTE||" + emoji + "\r";
		String xml = serializer.toXML(er7);
		assertTrue("expected a single code-point reference &#128269;", xml.contains("&#128269;"));
		assertFalse("must not emit a surrogate-half reference", xml.contains("&#55357;"));
	}

	/**
	 * IRT-1742: a raw C0 control character (other than tab/CR/LF) cannot appear in XML 1.0, so
	 * serialization must fail fast with a MessageSerializerException rather than emit malformed XML.
	 */
	@Test
	public void testToXMLWithControlCharacterThrows() {
		String er7 = "MSH|^~\\&|MIRTH|MIRTH|||200612131519||ORM^O01|12345678|P|2.4\rOBX|1|ST|NOTE||AB\u001BCD\r";
		assertThrows(MessageSerializerException.class, () -> serializer.toXML(er7));
	}
}
