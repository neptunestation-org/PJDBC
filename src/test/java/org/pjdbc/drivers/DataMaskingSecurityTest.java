package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupSecurityTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS security_data (" +
                    "id INT PRIMARY KEY, " +
                    "ssn VARCHAR(11), " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");

                // Insert some data
                stmt.execute("INSERT INTO security_data VALUES (1, '123-45-6789', " +
                    "CAST('sensitive binary' AS BLOB), CAST('sensitive text' AS CLOB))");
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupSecurityTable("test_alias_bypass");
        // Mask 'ssn'
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Alias ssn as 'public_info' which is NOT in the mask list
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS public_info FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    String val = rs.getString("public_info");

                    // IF THE VULNERABILITY EXISTS: val will be "123-45-6789"
                    // IF FIXED: val should be "[REDACTED]"
                    assertEquals("Alias bypass: masked column 'ssn' accessed via alias 'public_info' should be masked",
                        "[REDACTED]", val);
                }
            }
        }
    }

    @Test
    public void testLobBypass() throws SQLException {
        setupSecurityTable("test_lob_bypass");
        // Mask secret_blob and secret_clob
        String url = "jdbc:mask[columns=secret_.*,strategy=REDACT]:jdbc:h2:mem:test_lob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob, secret_clob FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // Test Blob - should throw SQLException
                    try {
                        rs.getBlob("secret_blob");
                        fail("Blob bypass: masked column 'secret_blob' should throw SQLException");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }

                    // Test Clob - should throw SQLException
                    try {
                        rs.getClob("secret_clob");
                        fail("Clob bypass: masked column 'secret_clob' should throw SQLException");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testComplexGetObjectBypass() throws SQLException {
        setupSecurityTable("test_getobject_bypass");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // Test getObject(int, Class)
                    // For String.class, it should return masked value
                    assertEquals("getObject(int, String.class) should be masked",
                        "[REDACTED]", rs.getObject(1, String.class));

                    // For other classes, it should throw SQLException
                    try {
                        rs.getObject(1, Integer.class);
                        fail("getObject(int, Class) bypass: masked column should throw SQLException for non-String types");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }

                    // Test getObject(int, Map) - Not overridden in MaskingResultSet
                    try {
                        rs.getObject(1, new HashMap<String, Class<?>>());
                        fail("getObject(int, Map) bypass: masked column should throw SQLException");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
