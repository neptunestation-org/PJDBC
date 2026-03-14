package org.pjdbc.drivers;

import static org.junit.Assert.*;
import java.sql.*;
import java.util.Properties;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLobTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE lob_data (id INT PRIMARY KEY, secret_blob BLOB, secret_clob CLOB, secret_text VARCHAR(100))");
                PreparedStatement pstmt = conn.prepareStatement("INSERT INTO lob_data VALUES (1, ?, ?, ?)");
                pstmt.setBytes(1, "secret blob content".getBytes());
                pstmt.setString(2, "secret clob content");
                pstmt.setString(3, "secret text content");
                pstmt.execute();
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupLobTable("test_alias_bypass");
        String url = "jdbc:mask[columns=secret_text]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Current implementation checks both Name and Label in initMaskedColumns,
                // but getString(String label) might be bypassing it if it only checks the label.
                // Actually DataMaskingDriver.initMaskedColumns does:
                // maskedColumns[i] = config.shouldMask(columnName) || config.shouldMask(columnLabel);
                // But getString(String columnLabel) does:
                // if (config.shouldMask(columnLabel) && value != null)
                // If the alias is NOT in the mask list, it bypasses even if the underlying column IS.

                try (ResultSet rs = stmt.executeQuery("SELECT secret_text AS public_alias FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    String value = rs.getString("public_alias");
                    // This SHOULD be masked because the underlying column is secret_text,
                    // and initMaskedColumns should have caught it.
                    // However, getString(String columnLabel) only checks if the LABEL should be masked.
                    assertNotEquals("secret text content", value);
                }
            }
        }
    }

    @Test(expected = SQLException.class)
    public void testBlobSecure() throws SQLException {
        setupLobTable("test_blob_secure");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_secure;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    rs.getBlob("secret_blob"); // Should throw SQLException
                }
            }
        }
    }

    @Test(expected = SQLException.class)
    public void testClobSecure() throws SQLException {
        setupLobTable("test_clob_secure");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_secure;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    rs.getClob("secret_clob"); // Should throw SQLException
                }
            }
        }
    }

    @Test(expected = SQLException.class)
    public void testGetObjectWithTypeSecure() throws SQLException {
        setupLobTable("test_getobject_secure");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_getobject_secure;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    rs.getObject("secret_clob", Clob.class); // Should throw SQLException
                }
            }
        }
    }

    @Test
    public void testGetObjectWithStringClassSecure() throws SQLException {
        setupLobTable("test_getobject_string_secure");
        String url = "jdbc:mask[columns=secret_text,strategy=REDACT]:jdbc:h2:mem:test_getobject_string_secure;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_text FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    Object value = rs.getObject(1, String.class);
                    assertEquals("[REDACTED]", value);
                }
            }
        }
    }
}
