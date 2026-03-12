package org.pjdbc.drivers;

import static org.junit.Assert.*;

import java.sql.*;
import java.io.*;
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
                stmt.execute("CREATE TABLE security_data (" +
                    "id INT PRIMARY KEY, " +
                    "ssn VARCHAR(11), " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");

                PreparedStatement pstmt = conn.prepareStatement("INSERT INTO security_data VALUES (?, ?, ?, ?)");
                pstmt.setInt(1, 1);
                pstmt.setString(2, "123-45-6789");
                pstmt.setBytes(3, "secret binary data".getBytes());
                pstmt.setString(4, "secret character data");
                pstmt.execute();
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupSecurityTable("test_alias_bypass");
        // Mask ssn but not "alias"
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS alias FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    // VULNERABILITY: If we use the alias, it might not be masked if the driver only checks the label
                    String value = rs.getString("alias");
                    assertNotEquals("Alias bypass: ssn should be masked even when aliased", "123-45-6789", value);
                    assertEquals("[REDACTED]", value);
                }
            }
        }
    }

    @Test
    public void testBlobBypass() throws SQLException {
        setupSecurityTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    // VULNERABILITY: getBlob might not be overridden and thus bypass masking
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        if (blob != null) {
                            byte[] bytes = blob.getBytes(1, (int) blob.length());
                            assertNotEquals("Blob bypass: secret data leaked", "secret binary data", new String(bytes));
                        }
                        fail("Expected SQLException for masked Blob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testClobBypass() throws SQLException {
        setupSecurityTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    // VULNERABILITY: getClob might not be overridden and thus bypass masking
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        if (clob != null) {
                            String value = clob.getSubString(1, (int) clob.length());
                            assertNotEquals("Clob bypass: secret data leaked", "secret character data", value);
                        }
                        fail("Expected SQLException for masked Clob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithTypeBypass() throws SQLException {
        setupSecurityTable("test_getobject_bypass");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    // VULNERABILITY: getObject(int, Class) might not be overridden
                    try {
                        String value = rs.getObject(1, String.class);
                        assertEquals("[REDACTED]", value);
                    } catch (SQLException e) {
                        // If it throws, it's also acceptable as long as it doesn't leak
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
