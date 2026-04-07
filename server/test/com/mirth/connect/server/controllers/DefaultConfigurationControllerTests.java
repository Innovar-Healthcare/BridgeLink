/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.Invocation;
import org.xml.sax.SAXParseException;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.util.ConnectionTestResponse;
import com.mirth.connect.model.DriverInfo;
import com.mirth.connect.util.ConfigurationProperty;

public class DefaultConfigurationControllerTests {

    @BeforeClass
    public static void setup() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        ScriptController scriptController = mock(ScriptController.class);
        when(controllerFactory.createScriptController()).thenReturn(scriptController);

        ConfigurationController configController = mock(ConfigurationController.class);
        when(configController.getHttpsClientProtocols()).thenReturn(new String[0]);
        when(configController.getHttpsCipherSuites()).thenReturn(new String[0]);
        when(controllerFactory.createConfigurationController()).thenReturn(configController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);
    }

    @Test
    public void getDatabaseDrivers_NotBlank() throws Exception {
        DefaultConfigurationController configurationController = spy(new DefaultConfigurationController());

        String databaseDriversXml = DEFAULT_DRIVERS_XML;
        doReturn(databaseDriversXml).when(configurationController).getProperty(any(), any());

        List<DriverInfo> drivers = configurationController.getDatabaseDrivers();
        assertDefaultDrivers(drivers, false);
    }

    @Test
    public void getDatabaseDrivers_Blank() throws Exception {
        DefaultConfigurationController configurationController = spy(new DefaultConfigurationController());

        String databaseDriversXml = null;
        doReturn(databaseDriversXml).when(configurationController).getProperty(any(), any());

        File testConfDir = new File("./testconf");
        testConfDir.mkdir();
        File dbDriversFile = new File(testConfDir, "dbdrivers.xml");
        FileUtils.writeStringToFile(dbDriversFile, DEFAULT_DBDRIVERS_FILE, "UTF-8", false);
        doReturn(dbDriversFile).when(configurationController).getDbDriversFile();

        List<DriverInfo> drivers = configurationController.getDatabaseDrivers();
        assertDefaultDrivers(drivers, true);
    }

    @Test
    public void getDatabaseDrivers_FileDoesNotExist() throws Exception {
        DefaultConfigurationController configurationController = spy(new DefaultConfigurationController());

        String databaseDriversXml = null;
        doReturn(databaseDriversXml).when(configurationController).getProperty(any(), any());

        File testConfDir = new File("./testconf");
        testConfDir.mkdir();
        File dbDriversFile = new File(testConfDir, "dummy.xml");
        if (dbDriversFile.exists()) {
            dbDriversFile.delete();
        }
        doReturn(dbDriversFile).when(configurationController).getDbDriversFile();

        List<DriverInfo> drivers = configurationController.getDatabaseDrivers();
        assertDefaultDrivers(drivers, false);
    }

    @Test
    public void getDatabaseDrivers_Exception() throws Exception {
        DefaultConfigurationController configurationController = spy(new DefaultConfigurationController());

        String databaseDriversXml = null;
        doReturn(databaseDriversXml).when(configurationController).getProperty(any(), any());

        File testConfDir = new File("./testconf");
        testConfDir.mkdir();
        File dbDriversFile = new File(testConfDir, "dummy.xml");
        FileUtils.writeStringToFile(dbDriversFile, "test", "UTF-8", false);
        doReturn(dbDriversFile).when(configurationController).getDbDriversFile();

        try {
            configurationController.getDatabaseDrivers();
            fail("Exception should have been thrown");
        } catch (ControllerException e) {
            // Expected
        }
    }
    
    @Test
    public void validateServerSettings_Enabled_GoodValue() throws Exception {
        DefaultConfigurationController configurationController = spy(new DefaultConfigurationController());
        
        Properties properties = new Properties();
        String enabledKey = "administratorautologoutinterval.enabled";
        String fieldKey = "administratorautologoutinterval.field";
        properties.setProperty(enabledKey, "1");
        properties.setProperty(fieldKey, "30");

        try {
            configurationController.validateServerSettings(properties);
        } catch (ControllerException e) {
            fail("Exception should not have been thrown");
        }
    }
    
    @Test
    public void validateServerSettings_Exception_Enabled_BadValue() throws Exception {
        DefaultConfigurationController configurationController = spy(new DefaultConfigurationController());
        
        Properties properties = new Properties();
        String enabledKey = "administratorautologoutinterval.enabled";
        String fieldKey = "administratorautologoutinterval.field";
        properties.setProperty(enabledKey, "1");
        properties.setProperty(fieldKey, "0");

        try {
            configurationController.validateServerSettings(properties);
            fail("Exception should have been thrown");
        } catch (ControllerException e) {
            // Expected
        }
    }
    
    @Test
    public void validateServerSettings_Disabled_GoodValue() throws Exception {
        DefaultConfigurationController configurationController = spy(new DefaultConfigurationController());
        
        Properties properties = new Properties();
        String enabledKey = "administratorautologoutinterval.enabled";
        String fieldKey = "administratorautologoutinterval.field";
        properties.setProperty(enabledKey, "0");
        properties.setProperty(fieldKey, "5");

        try {
            configurationController.validateServerSettings(properties);
        } catch (ControllerException e) {
            fail("Exception should not have been thrown");
        }
    }
    
    @Test
    public void validateServerSettings_Exception_Disabled_BadValue() throws Exception {
        DefaultConfigurationController configurationController = spy(new DefaultConfigurationController());
        
        Properties properties = new Properties();
        String enabledKey = "administratorautologoutinterval.enabled";
        String fieldKey = "administratorautologoutinterval.field";
        properties.setProperty(enabledKey, "0");
        properties.setProperty(fieldKey, "a");

        try {
            configurationController.validateServerSettings(properties);
        } catch (ControllerException e) {
            fail("Exception should not have been thrown");
        }
    }

    @Test
    public void setDatabaseDrivers() throws Exception {
        DefaultConfigurationController configurationController = spy(new DefaultConfigurationController());

        doNothing().when(configurationController).saveProperty(any(), any(), any());

        List<DriverInfo> drivers = new ArrayList<DriverInfo>();
        drivers.add(new DriverInfo("MySQL", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://host:port/dbname", "SELECT * FROM ? LIMIT 1", new ArrayList<String>(Arrays.asList(new String[] {
                "com.mysql.jdbc.Driver" }))));
        drivers.add(new DriverInfo("Oracle", "oracle.jdbc.driver.OracleDriver", "jdbc:oracle:thin:@host:port:dbname", "SELECT * FROM ? WHERE ROWNUM < 2"));
        drivers.add(new DriverInfo("PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://host:port/dbname", "SELECT * FROM ? LIMIT 1"));
        drivers.add(new DriverInfo("SQL Server/Sybase (jTDS)", "net.sourceforge.jtds.jdbc.Driver", "jdbc:jtds:sqlserver://host:port/dbname", "SELECT TOP 1 * FROM ?"));
        drivers.add(new DriverInfo("Microsoft SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://host:port;databaseName=dbname", "SELECT TOP 1 * FROM ?"));
        drivers.add(new DriverInfo("SQLite", "org.sqlite.JDBC", "jdbc:sqlite:dbfile.db", "SELECT * FROM ? LIMIT 1"));

        configurationController.setDatabaseDrivers(drivers);

        boolean found = false;
        for (Invocation invocation : mockingDetails(configurationController).getInvocations()) {
            if (invocation.getMethod().getName().equals("saveProperty")) {
                found = true;
                assertEquals("core", invocation.getArgument(0));
                assertEquals("databaseDrivers", invocation.getArgument(1));

                String expected = normalizeXml(DEFAULT_DRIVERS_XML);
                String actual = normalizeXml((String) invocation.getArgument(2));
                assertEquals(expected, actual);
            }
        }

        if (!found) {
            fail("Method saveProperty not called.");
        }
    }

    @Test
    public void testRhinoVersion15() {
        assertEquals(150, (int) new DefaultConfigurationController().getRhinoLanguageVersion("1.5"));
    }

    @Test
    public void testRhinoVersionES6LowerCase() {
        assertEquals(200, (int) new DefaultConfigurationController().getRhinoLanguageVersion("es6"));
    }

    @Test
    public void testRhinoVersionES6UpperCase() {
        assertEquals(200, (int) new DefaultConfigurationController().getRhinoLanguageVersion("ES6"));
    }

    @Test
    public void testRhinoVersionDefault() {
        assertEquals(0, (int) new DefaultConfigurationController().getRhinoLanguageVersion("default"));
    }

    @Test
    public void testRhinoVersionUnknown() {
        assertEquals(0, (int) new DefaultConfigurationController().getRhinoLanguageVersion("asdf"));
    }
    
    @Test
    public void testParseDbdriversXmlWithExternalDtd() {
    	boolean exceptionCaught = false;
    	try {
			new DefaultConfigurationController().parseDbdriversXml(new StringReader(DBDRIVERS_FILE_WITH_EXTERNAL_DTD));
		} catch (Exception e) {
			assertEquals(SAXParseException.class, e.getClass());
			exceptionCaught = true;
		}
    	assertTrue(exceptionCaught);
    }

    // -----------------------------------------------------------------------
    // getRhinoLanguageVersion — remaining versions (1.0–1.4, 1.6–1.8)
    // -----------------------------------------------------------------------

    @Test
    public void testRhinoVersion10() {
        assertEquals(100, (int) new DefaultConfigurationController().getRhinoLanguageVersion("1.0"));
    }

    @Test
    public void testRhinoVersion11() {
        assertEquals(110, (int) new DefaultConfigurationController().getRhinoLanguageVersion("1.1"));
    }

    @Test
    public void testRhinoVersion12() {
        assertEquals(120, (int) new DefaultConfigurationController().getRhinoLanguageVersion("1.2"));
    }

    @Test
    public void testRhinoVersion13() {
        assertEquals(130, (int) new DefaultConfigurationController().getRhinoLanguageVersion("1.3"));
    }

    @Test
    public void testRhinoVersion14() {
        assertEquals(140, (int) new DefaultConfigurationController().getRhinoLanguageVersion("1.4"));
    }

    @Test
    public void testRhinoVersion16() {
        assertEquals(160, (int) new DefaultConfigurationController().getRhinoLanguageVersion("1.6"));
    }

    @Test
    public void testRhinoVersion17() {
        assertEquals(170, (int) new DefaultConfigurationController().getRhinoLanguageVersion("1.7"));
    }

    @Test
    public void testRhinoVersion18() {
        assertEquals(180, (int) new DefaultConfigurationController().getRhinoLanguageVersion("1.8"));
    }

    // -----------------------------------------------------------------------
    // intToBooleanObject — helper used by validateServerSettings
    // -----------------------------------------------------------------------

    @Test
    public void intToBooleanObject_One_ReturnsTrue() {
        DefaultConfigurationController controller = new DefaultConfigurationController();
        assertEquals(Boolean.TRUE, controller.intToBooleanObject("1", false));
    }

    @Test
    public void intToBooleanObject_Zero_ReturnsFalse() {
        DefaultConfigurationController controller = new DefaultConfigurationController();
        assertEquals(Boolean.FALSE, controller.intToBooleanObject("0", true));
    }

    @Test
    public void intToBooleanObject_NonNumeric_ReturnsDefaultValue() {
        DefaultConfigurationController controller = new DefaultConfigurationController();
        assertEquals(Boolean.FALSE, controller.intToBooleanObject("abc", false));
        assertEquals(Boolean.TRUE, controller.intToBooleanObject("abc", true));
    }

    @Test
    public void intToBooleanObject_NonNumeric_NullDefault_ReturnsNull() {
        DefaultConfigurationController controller = new DefaultConfigurationController();
        assertNull(controller.intToBooleanObject("abc", null));
    }

    // -----------------------------------------------------------------------
    // validateServerSettings — boundary cases
    // -----------------------------------------------------------------------

    @Test
    public void validateServerSettings_NoEnabledProperty_DoesNotThrow() throws Exception {
        DefaultConfigurationController controller = spy(new DefaultConfigurationController());
        // Neither key present — autoLogoutEnabled stays false, no validation runs
        try {
            controller.validateServerSettings(new Properties());
        } catch (ControllerException e) {
            fail("Exception should not have been thrown when enabled key is absent");
        }
    }

    @Test
    public void validateServerSettings_Enabled_MinBoundary_DoesNotThrow() throws Exception {
        DefaultConfigurationController controller = spy(new DefaultConfigurationController());
        Properties properties = new Properties();
        properties.setProperty("administratorautologoutinterval.enabled", "1");
        properties.setProperty("administratorautologoutinterval.field", "1");
        try {
            controller.validateServerSettings(properties);
        } catch (ControllerException e) {
            fail("Exception should not have been thrown for minimum valid value (1)");
        }
    }

    @Test
    public void validateServerSettings_Enabled_MaxBoundary_DoesNotThrow() throws Exception {
        DefaultConfigurationController controller = spy(new DefaultConfigurationController());
        Properties properties = new Properties();
        properties.setProperty("administratorautologoutinterval.enabled", "1");
        properties.setProperty("administratorautologoutinterval.field", "60");
        try {
            controller.validateServerSettings(properties);
        } catch (ControllerException e) {
            fail("Exception should not have been thrown for maximum valid value (60)");
        }
    }

    @Test
    public void validateServerSettings_Enabled_OverMax_Throws() throws Exception {
        DefaultConfigurationController controller = spy(new DefaultConfigurationController());
        Properties properties = new Properties();
        properties.setProperty("administratorautologoutinterval.enabled", "1");
        properties.setProperty("administratorautologoutinterval.field", "61");
        try {
            controller.validateServerSettings(properties);
            fail("Exception should have been thrown for value above maximum (61)");
        } catch (ControllerException e) {
            // Expected
        }
    }

    @Test
    public void validateServerSettings_Enabled_NullField_Throws() throws Exception {
        DefaultConfigurationController controller = spy(new DefaultConfigurationController());
        Properties properties = new Properties();
        properties.setProperty("administratorautologoutinterval.enabled", "1");
        // field key intentionally omitted — parseInt(null) causes NumberFormatException
        try {
            controller.validateServerSettings(properties);
            fail("Exception should have been thrown when field is null");
        } catch (ControllerException e) {
            // Expected
        }
    }

    @Test
    public void validateServerSettings_Enabled_NonNumericField_Throws() throws Exception {
        DefaultConfigurationController controller = spy(new DefaultConfigurationController());
        Properties properties = new Properties();
        properties.setProperty("administratorautologoutinterval.enabled", "1");
        properties.setProperty("administratorautologoutinterval.field", "abc");
        try {
            controller.validateServerSettings(properties);
            fail("Exception should have been thrown for non-numeric field value");
        } catch (ControllerException e) {
            // Expected
        }
    }

    // -----------------------------------------------------------------------
    // generateGuid
    // -----------------------------------------------------------------------

    @Test
    public void generateGuid_ReturnsValidUUIDFormat() {
        String guid = new DefaultConfigurationController().generateGuid();
        assertNotNull(guid);
        assertTrue("Expected UUID format 8-4-4-4-12",
                guid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    public void generateGuid_ReturnsDifferentValuesEachCall() {
        DefaultConfigurationController controller = new DefaultConfigurationController();
        assertFalse("Two consecutive GUIDs must be different",
                controller.generateGuid().equals(controller.generateGuid()));
    }

    // -----------------------------------------------------------------------
    // getServerTimezone
    // -----------------------------------------------------------------------

    @Test
    public void getServerTimezone_ReturnsNonNull() {
        assertNotNull(new DefaultConfigurationController().getServerTimezone(Locale.ENGLISH));
    }

    @Test
    public void getServerTimezone_ContainsUTCOffset() {
        String timezone = new DefaultConfigurationController().getServerTimezone(Locale.ENGLISH);
        assertTrue("Expected timezone string to contain 'UTC'", timezone.contains("UTC"));
    }

    // -----------------------------------------------------------------------
    // getAvailableCharsetEncodings
    // -----------------------------------------------------------------------

    @Test
    public void getAvailableCharsetEncodings_ReturnsNonEmpty() throws Exception {
        List<String> encodings = new DefaultConfigurationController().getAvailableCharsetEncodings();
        assertNotNull(encodings);
        assertTrue("Expected at least one charset encoding", encodings.size() > 0);
    }

    @Test
    public void getAvailableCharsetEncodings_ContainsUTF8() throws Exception {
        List<String> encodings = new DefaultConfigurationController().getAvailableCharsetEncodings();
        assertTrue("Expected UTF-8 in the available encodings list", encodings.contains("UTF-8"));
    }

    // -----------------------------------------------------------------------
    // setConfigurationProperties / getConfigurationProperties — in-memory only
    // (persist=false avoids any DB call; mirthConfig is empty so
    //  loadDatabaseConfigPropsIfNecessary() is a no-op)
    // -----------------------------------------------------------------------

    @Test
    public void setConfigurationProperties_WithoutPersist_UpdatesInMemoryMap() throws Exception {
        DefaultConfigurationController controller = new DefaultConfigurationController();

        Map<String, ConfigurationProperty> input = new HashMap<>();
        input.put("key1", new ConfigurationProperty("value1", "comment1"));
        input.put("key2", new ConfigurationProperty("value2", "comment2"));

        controller.setConfigurationProperties(input, false);

        Map<String, ConfigurationProperty> result = controller.getConfigurationProperties();
        assertEquals(2, result.size());
        assertEquals("value1", result.get("key1").getValue());
        assertEquals("comment1", result.get("key1").getComment());
        assertEquals("value2", result.get("key2").getValue());
        assertEquals("comment2", result.get("key2").getComment());
    }

    // -----------------------------------------------------------------------
    // sendTestEmail
    // -----------------------------------------------------------------------

    @Test
    public void testSendTestEmail_InvalidPort_ReturnsFailure() throws Exception {
        DefaultConfigurationController controller = new DefaultConfigurationController();

        Properties properties = new Properties();
        properties.setProperty("host", "smtp.example.com");
        properties.setProperty("port", "not-a-number");
        properties.setProperty("encryption", "TLS");
        properties.setProperty("timeout", "10000");
        properties.setProperty("authentication", "false");
        properties.setProperty("toAddress", "to@example.com");
        properties.setProperty("fromAddress", "from@example.com");

        ConnectionTestResponse response = controller.sendTestEmail(properties);

        assertEquals(ConnectionTestResponse.Type.FAILURE, response.getType());
        assertTrue("Expected port error message", response.getMessage().contains("not-a-number"));
    }

    @Test
    public void testSendTestEmail_OAuthEmptyTokenUrl_ThrowsException() throws Exception {
        DefaultConfigurationController controller = new DefaultConfigurationController();

        Properties properties = new Properties();
        properties.setProperty("host", "smtp.office365.com");
        properties.setProperty("port", "587");
        properties.setProperty("encryption", "TLS");
        properties.setProperty("timeout", "10000");
        properties.setProperty("authentication", "true");
        properties.setProperty("authType", "OAUTH");
        properties.setProperty("oAuthTokenEndpointUrl", ""); // empty — constructor rejects non-https
        properties.setProperty("username", "user@example.com");
        properties.setProperty("toAddress", "to@example.com");
        properties.setProperty("fromAddress", "user@example.com");

        try {
            controller.sendTestEmail(properties);
            fail("Expected IllegalArgumentException for empty OAuth token URL");
        } catch (IllegalArgumentException e) {
            assertTrue("Expected HTTPS error message", e.getMessage().contains("HTTPS"));
        }
    }

    private void assertDefaultDrivers(List<DriverInfo> drivers, boolean includeODBC) {
        assertEquals(includeODBC ? 7 : 6, drivers.size());
        int i = 0;

        if (includeODBC) {
            assertEquals("Sun JDBC-ODBC Bridge", drivers.get(i).getName());
            assertEquals("sun.jdbc.odbc.JdbcOdbcDriver", drivers.get(i).getClassName());
            assertEquals("jdbc:odbc:DSN", drivers.get(i).getTemplate());
            assertEquals("", drivers.get(i).getSelectLimit());
            assertEquals(new ArrayList<String>(), drivers.get(i).getAlternativeClassNames());
            i++;
        }

        assertEquals("MySQL", drivers.get(i).getName());
        assertEquals("com.mysql.cj.jdbc.Driver", drivers.get(i).getClassName());
        assertEquals("jdbc:mysql://host:port/dbname", drivers.get(i).getTemplate());
        assertEquals("SELECT * FROM ? LIMIT 1", drivers.get(i).getSelectLimit());
        assertEquals(Arrays.asList(new String[] {
                "com.mysql.jdbc.Driver" }), drivers.get(i).getAlternativeClassNames());
        i++;

        assertEquals("Oracle", drivers.get(i).getName());
        assertEquals("oracle.jdbc.driver.OracleDriver", drivers.get(i).getClassName());
        assertEquals("jdbc:oracle:thin:@host:port:dbname", drivers.get(i).getTemplate());
        assertEquals("SELECT * FROM ? WHERE ROWNUM < 2", drivers.get(i).getSelectLimit());
        assertEquals(new ArrayList<String>(), drivers.get(i).getAlternativeClassNames());
        i++;

        assertEquals("PostgreSQL", drivers.get(i).getName());
        assertEquals("org.postgresql.Driver", drivers.get(i).getClassName());
        assertEquals("jdbc:postgresql://host:port/dbname", drivers.get(i).getTemplate());
        assertEquals("SELECT * FROM ? LIMIT 1", drivers.get(i).getSelectLimit());
        assertEquals(new ArrayList<String>(), drivers.get(i).getAlternativeClassNames());
        i++;

        assertEquals("SQL Server/Sybase (jTDS)", drivers.get(i).getName());
        assertEquals("net.sourceforge.jtds.jdbc.Driver", drivers.get(i).getClassName());
        assertEquals("jdbc:jtds:sqlserver://host:port/dbname", drivers.get(i).getTemplate());
        assertEquals("SELECT TOP 1 * FROM ?", drivers.get(i).getSelectLimit());
        assertEquals(new ArrayList<String>(), drivers.get(i).getAlternativeClassNames());
        i++;
        
        assertEquals("Microsoft SQL Server", drivers.get(i).getName());
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver", drivers.get(i).getClassName());
        assertEquals("jdbc:sqlserver://host:port;databaseName=dbname", drivers.get(i).getTemplate());
        assertEquals("SELECT TOP 1 * FROM ?", drivers.get(i).getSelectLimit());
        assertEquals(new ArrayList<String>(), drivers.get(i).getAlternativeClassNames());
        i++;

        assertEquals("SQLite", drivers.get(i).getName());
        assertEquals("org.sqlite.JDBC", drivers.get(i).getClassName());
        assertEquals("jdbc:sqlite:dbfile.db", drivers.get(i).getTemplate());
        assertEquals("SELECT * FROM ? LIMIT 1", drivers.get(i).getSelectLimit());
        assertEquals(new ArrayList<String>(), drivers.get(i).getAlternativeClassNames());
    }

    private String normalizeXml(String xml) throws Exception {
        Source source = new StreamSource(new StringReader(xml));
        Writer writer = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        tf.newTransformer().transform(source, new StreamResult(writer));
        return writer.toString();
    }

    // @formatter:off
    private String DEFAULT_DRIVERS_XML = "<list>\n" + 
    		"  <driverInfo>\n" + 
    		"    <className>com.mysql.cj.jdbc.Driver</className>\n" + 
    		"    <name>MySQL</name>\n" + 
    		"    <template>jdbc:mysql://host:port/dbname</template>\n" + 
    		"    <selectLimit>SELECT * FROM ? LIMIT 1</selectLimit>\n" + 
    		"    <alternativeClassNames>\n" + 
    		"      <string>com.mysql.jdbc.Driver</string>\n" + 
    		"    </alternativeClassNames>\n" + 
    		"  </driverInfo>\n" + 
    		"  <driverInfo>\n" + 
    		"    <className>oracle.jdbc.driver.OracleDriver</className>\n" + 
    		"    <name>Oracle</name>\n" + 
    		"    <template>jdbc:oracle:thin:@host:port:dbname</template>\n" + 
    		"    <selectLimit>SELECT * FROM ? WHERE ROWNUM &lt; 2</selectLimit>\n" + 
    		"    <alternativeClassNames/>\n" + 
    		"  </driverInfo>\n" + 
    		"  <driverInfo>\n" + 
    		"    <className>org.postgresql.Driver</className>\n" + 
    		"    <name>PostgreSQL</name>\n" + 
    		"    <template>jdbc:postgresql://host:port/dbname</template>\n" + 
    		"    <selectLimit>SELECT * FROM ? LIMIT 1</selectLimit>\n" + 
    		"    <alternativeClassNames/>\n" + 
    		"  </driverInfo>\n" + 
    		"  <driverInfo>\n" + 
    		"    <className>net.sourceforge.jtds.jdbc.Driver</className>\n" + 
    		"    <name>SQL Server/Sybase (jTDS)</name>\n" + 
    		"    <template>jdbc:jtds:sqlserver://host:port/dbname</template>\n" + 
    		"    <selectLimit>SELECT TOP 1 * FROM ?</selectLimit>\n" + 
    		"    <alternativeClassNames/>\n" + 
    		"  </driverInfo>\n" + 
    		"  <driverInfo>\n" + 
    		"    <className>com.microsoft.sqlserver.jdbc.SQLServerDriver</className>\n" + 
    		"    <name>Microsoft SQL Server</name>\n" + 
    		"    <template>jdbc:sqlserver://host:port;databaseName=dbname</template>\n" + 
    		"    <selectLimit>SELECT TOP 1 * FROM ?</selectLimit>\n" + 
    		"    <alternativeClassNames/>\n" + 
    		"  </driverInfo>\n" + 
    		"  <driverInfo>\n" + 
    		"    <className>org.sqlite.JDBC</className>\n" + 
    		"    <name>SQLite</name>\n" + 
    		"    <template>jdbc:sqlite:dbfile.db</template>\n" + 
    		"    <selectLimit>SELECT * FROM ? LIMIT 1</selectLimit>\n" + 
    		"    <alternativeClassNames/>\n" + 
    		"  </driverInfo>\n" + 
    		"</list>\n";
    
    private String DEFAULT_DBDRIVERS_FILE = "<drivers>\n"+
            "   <driver class=\"sun.jdbc.odbc.JdbcOdbcDriver\" name=\"Sun JDBC-ODBC Bridge\" template=\"jdbc:odbc:DSN\" selectLimit=\"\" />\n"+
    		"   <driver class=\"com.mysql.cj.jdbc.Driver\" name=\"MySQL\" template=\"jdbc:mysql://host:port/dbname\" selectLimit=\"SELECT * FROM ? LIMIT 1\" alternativeClasses=\"com.mysql.jdbc.Driver\" />\n" + 
    		"	<driver class=\"oracle.jdbc.driver.OracleDriver\" name=\"Oracle\" template=\"jdbc:oracle:thin:@host:port:dbname\" selectLimit=\"SELECT * FROM ? WHERE ROWNUM &lt; 2\" />\n" + 
    		"	<driver class=\"org.postgresql.Driver\" name=\"PostgreSQL\" template=\"jdbc:postgresql://host:port/dbname\" selectLimit=\"SELECT * FROM ? LIMIT 1\" />\n" + 
    		"	<driver class=\"net.sourceforge.jtds.jdbc.Driver\" name=\"SQL Server/Sybase (jTDS)\" template=\"jdbc:jtds:sqlserver://host:port/dbname\" selectLimit=\"SELECT TOP 1 * FROM ?\" />\n" + 
    		"	<driver class=\"com.microsoft.sqlserver.jdbc.SQLServerDriver\" name=\"Microsoft SQL Server\" template=\"jdbc:sqlserver://host:port;databaseName=dbname\" selectLimit=\"SELECT TOP 1 * FROM ?\" />\n" + 
    		"	<driver class=\"org.sqlite.JDBC\" name=\"SQLite\" template=\"jdbc:sqlite:dbfile.db\" selectLimit=\"SELECT * FROM ? LIMIT 1\" />\n" + 
    		"</drivers>\n";
    
    private String DBDRIVERS_FILE_WITH_EXTERNAL_DTD = "<!DOCTYPE foo[\n" + 
    		"<!ELEMENT foo ANY >\n" + 
    		"<!ENTITY xxe SYSTEM \"file:///dev/random\" >]\n" + 
    		"<drivers>\n" + 
    		"	<driver class=\"com.mysql.cj.jdbc.Driver\" name=\"&xxe;MySQL\" template=\"jdbc:mysql://host:port/dbname\" selectLimit=\"SELECT * FROM ? LIMIT 1\" alternativeClasses=\"com.mysql.jdbc.Driver\" />\n" + 
    		"	<driver class=\"oracle.jdbc.driver.OracleDriver\" name=\"Oracle\" template=\"jdbc:oracle:thin:@host:port:dbname\" selectLimit=\"SELECT * FROM ? WHERE ROWNUM &lt; 2\" />\n" + 
    		"	<driver class=\"org.postgresql.Driver\" name=\"PostgreSQL\" template=\"jdbc:postgresql://host:port/dbname\" selectLimit=\"SELECT * FROM ? LIMIT 1\" />\n" + 
    		"	<driver class=\"net.sourceforge.jtds.jdbc.Driver\" name=\"SQL Server/Sybase (jTDS)\" template=\"jdbc:jtds:sqlserver://host:port/dbname\" selectLimit=\"SELECT TOP 1 * FROM ?\" />\n" + 
    		"	<driver class=\"com.microsoft.sqlserver.jdbc.SQLServerDriver\" name=\"Microsoft SQL Server\" template=\"jdbc:sqlserver://host:port;databaseName=dbname\" selectLimit=\"SELECT TOP 1 * FROM ?\" />\n" + 
    		"	<driver class=\"org.sqlite.JDBC\" name=\"SQLite\" template=\"jdbc:sqlite:dbfile.db\" selectLimit=\"SELECT * FROM ? LIMIT 1\" />\n" + 
    		"</drivers>";
    // @formatter:on
}
