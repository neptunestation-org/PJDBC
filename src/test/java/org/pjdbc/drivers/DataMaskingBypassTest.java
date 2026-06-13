package org.pjdbc.drivers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLOBTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE lob_data (id INT PRIMARY KEY, secret_blob BLOB, secret_clob CLOB)");

                // Insert a blob
                byte[] blobData = "SENSITIVE BLOB DATA".getBytes();
                java.sql.PreparedStatement pstmt = conn.prepareStatement("INSERT INTO lob_data VALUES (1, ?, ?)");
                pstmt.setBytes(1, blobData);
                pstmt.setString(2, "SENSITIVE CLOB DATA");
                pstmt.executeUpdate();
            }
        }
    }

    @Test
    public void testGetBlobBypass() throws SQLException {
        setupLOBTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // This SHOULD throw SQLException if masked, but currently it might bypass
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        assertNotNull(blob);
                        byte[] data = blob.getBytes(1, (int) blob.length());
                        String secret = new String(data);
                        if ("SENSITIVE BLOB DATA".equals(secret)) {
                            fail("Security Bypass: Successfully retrieved sensitive BLOB data from masked column!");
                        }
                    } catch (SQLException e) {
                        // Success: masking driver blocked access
                        assertTrue(e.getMessage().contains("is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobBypass() throws SQLException {
        setupLOBTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    try {
                        Clob clob = rs.getClob("secret_clob");
                        assertNotNull(clob);
                        String secret = clob.getSubString(1, (int) clob.length());
                        if ("SENSITIVE CLOB DATA".equals(secret)) {
                            fail("Security Bypass: Successfully retrieved sensitive CLOB data from masked column!");
                        }
                    } catch (SQLException e) {
                        // Success: masking driver blocked access
                        assertTrue(e.getMessage().contains("is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testAliasingBypass() throws SQLException {
        setupLOBTable("test_alias_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Try to bypass by aliasing the column
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob AS public_info FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getString with alias should be masked
                    String masked = rs.getString("public_info");
                    assertTrue("Expected masked value, got: " + masked, masked.contains("*") || masked.equals("[REDACTED]"));

                    // getClob with alias should throw
                    try {
                        rs.getClob("public_info");
                        fail("Security Bypass: Successfully retrieved CLOB data via alias!");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGenericGetObjectBypass() throws SQLException {
        setupLOBTable("test_generic_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_generic_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getObject(index, Class) for String should return masked
                    String masked = rs.getObject(1, String.class);
                    assertTrue("Expected masked value, got: " + masked, masked.contains("*") || masked.equals("[REDACTED]"));

                    // getObject(label, Class) for String should return masked
                    masked = rs.getObject("secret_clob", String.class);
                    assertTrue("Expected masked value, got: " + masked, masked.contains("*") || masked.equals("[REDACTED]"));

                    // getObject(index, Class) for other types should throw
                    try {
                        rs.getObject(1, Clob.class);
                        fail("Security Bypass: Successfully retrieved Clob object via getObject(int, Class)!");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                    }

                    // getObject(label, Class) for other types should throw
                    try {
                        rs.getObject("secret_clob", Clob.class);
                        fail("Security Bypass: Successfully retrieved Clob object via getObject(String, Class)!");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                    }
                }
            }
        }
    }
}
