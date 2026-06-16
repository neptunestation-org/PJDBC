package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupBypassTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS sensitive_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB, " +
                    "secret_text VARCHAR(100))");

                byte[] blobData = "SENSITIVE BLOB CONTENT".getBytes();
                java.sql.PreparedStatement pstmt = conn.prepareStatement("INSERT INTO sensitive_data VALUES (1, ?, ?, 'SENSITIVE TEXT')");
                pstmt.setBytes(1, blobData);
                pstmt.setString(2, "SENSITIVE CLOB CONTENT");
                pstmt.executeUpdate();
            }
        }
    }

    @Test
    public void testBlobBypass() throws SQLException {
        setupBypassTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    // This SHOULD throw SQLException if properly masked
                    try {
                        rs.getBlob("secret_blob");
                    } catch (SQLException e) {
                        // Expected behavior: should throw SQLException
                        return;
                    }
                    fail("DataMaskingDriver bypassed via getBlob()");
                }
            }
        }
    }

    @Test
    public void testClobBypass() throws SQLException {
        setupBypassTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getClob("secret_clob");
                    } catch (SQLException e) {
                        return;
                    }
                    fail("DataMaskingDriver bypassed via getClob()");
                }
            }
        }
    }

    @Test
    public void testGetObjectWithTypeBypass() throws SQLException {
        setupBypassTable("test_getobject_bypass");
        String url = "jdbc:mask[columns=secret_text]:jdbc:h2:mem:test_getobject_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_text FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getObject(..., String.class) should return masked value
                    String masked = rs.getObject("secret_text", String.class);
                    assertTrue("Should be masked: " + masked, masked.contains("*"));

                    // getObject(..., Object.class) should throw SQLException for masked columns
                    try {
                        rs.getObject("secret_text", Object.class);
                        fail("DataMaskingDriver bypassed via getObject(String, Object.class)");
                    } catch (SQLException e) {
                        // Expected
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithMapBypass() throws SQLException {
        setupBypassTable("test_getobject_map_bypass");
        String url = "jdbc:mask[columns=secret_text]:jdbc:h2:mem:test_getobject_map_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_text FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getObject("secret_text", new java.util.HashMap<>());
                        fail("DataMaskingDriver bypassed via getObject(String, Map)");
                    } catch (SQLException e) {
                        // Expected
                    }
                }
            }
        }
    }

    @Test
    public void testAliasingBypass() throws SQLException {
        setupBypassTable("test_aliasing_bypass");
        // Mask 'secret_text' column
        String url = "jdbc:mask[columns=secret_text]:jdbc:h2:mem:test_aliasing_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Bypass attempt: alias the masked column to something else
                try (ResultSet rs = stmt.executeQuery("SELECT secret_text AS public_text FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    String value = rs.getString("public_text");
                    // If my initMaskedColumns check both name and label, this should still be masked.
                    assertTrue("Aliased column should still be masked: " + value, value.contains("*"));
                }
            }
        }
    }
}
