package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

public class DataMaskingSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupSecurityTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS secrets (" +
                    "id INT PRIMARY KEY, " +
                    "secret_val VARCHAR(100), " +
                    "secret_blob BLOB)");
                stmt.execute("INSERT INTO secrets VALUES (1, 'TOP_SECRET_DATA', CAST('BLOB_CONTENT' AS BINARY))");
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupSecurityTable("test_alias_bypass");
        // Mask "secret_val"
        String url = "jdbc:mask[columns=secret_val,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Alias "secret_val" as "public_name"
                try (ResultSet rs = stmt.executeQuery("SELECT secret_val AS public_name FROM secrets WHERE id = 1")) {
                    assertTrue(rs.next());
                    String val = rs.getString("public_name");
                    // VULNERABILITY: If it returns "TOP_SECRET_DATA", it bypassed masking because of the alias
                    if ("TOP_SECRET_DATA".equals(val)) {
                        fail("Masking bypassed via alias! Expected [REDACTED] but got: " + val);
                    }
                    assertEquals("[REDACTED]", val);
                }
            }
        }
    }

    @Test
    public void testBlobBypass() throws SQLException {
        setupSecurityTable("test_blob_bypass");
        // Mask "secret_blob"
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM secrets WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        // VULNERABILITY: If it doesn't throw and returns the real blob, it bypassed masking
                        byte[] bytes = blob.getBytes(1, (int) blob.length());
                        String content = new String(bytes);
                        if ("BLOB_CONTENT".equals(content)) {
                            fail("Masking bypassed for Blob! Got: " + content);
                        }
                    } catch (SQLException e) {
                        // Success: should throw SQLException for masked LOBs
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassBypass() throws SQLException {
        setupSecurityTable("test_getobject_class");
        String url = "jdbc:mask[columns=secret_val,strategy=REDACT]:jdbc:h2:mem:test_getobject_class;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_val FROM secrets WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        String val = rs.getObject("secret_val", String.class);
                        // VULNERABILITY: If it returns "TOP_SECRET_DATA", it bypassed masking
                        if ("TOP_SECRET_DATA".equals(val)) {
                            fail("Masking bypassed for getObject(String, Class)! Got: " + val);
                        }
                        assertEquals("[REDACTED]", val);
                    } catch (SQLException e) {
                        // Also acceptable if it throws, but if it returns it MUST be masked
                    }
                }
            }
        }
    }

    @Test
    public void testOtherComplexTypesBypass() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_complex;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS complex_data (id INT PRIMARY KEY, secret_arr INTEGER ARRAY)");
                stmt.execute("INSERT INTO complex_data VALUES (1, ARRAY[1, 2, 3])");
            }
        }
        String url = "jdbc:mask[columns=secret_arr]:jdbc:h2:mem:test_complex;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_arr FROM complex_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getArray("secret_arr");
                        fail("Expected SQLException for masked Array column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
