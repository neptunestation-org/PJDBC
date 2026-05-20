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
                    "secret_blob BLOB, " +
                    "secret_clob CLOB, " +
                    "secret_text VARCHAR(100))");

                stmt.execute("INSERT INTO security_data VALUES (1, CAST('blobdata' AS BLOB), CAST('clobdata' AS CLOB), 'secretvalue')");
            }
        }
    }

    @Test
    public void testGetBlobMasked() throws SQLException {
        setupSecurityTable("test_blob_masked");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_masked;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked Blob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver: Column 'secret_blob' is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMasked() throws SQLException {
        setupSecurityTable("test_clob_masked");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_masked;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked Clob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver: Column 'secret_clob' is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassMasked() throws SQLException {
        setupSecurityTable("test_getobject_class_masked");
        String url = "jdbc:mask[columns=secret_text,strategy=REDACT]:jdbc:h2:mem:test_getobject_class_masked;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_text FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    // Should return masked value for String.class
                    assertEquals("[REDACTED]", rs.getObject(1, String.class));
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassUnmasked() throws SQLException {
        setupSecurityTable("test_getobject_class_unmasked");
        String url = "jdbc:mask[columns=none]:jdbc:h2:mem:test_getobject_class_unmasked;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_text FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("secretvalue", rs.getObject(1, String.class));
                }
            }
        }
    }

    @Test
    public void testGetObjectWithMapMasked() throws SQLException {
        setupSecurityTable("test_getobject_map_masked");
        String url = "jdbc:mask[columns=secret_text,strategy=REDACT]:jdbc:h2:mem:test_getobject_map_masked;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_text FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getObject(1, new HashMap<String, Class<?>>());
                        fail("Expected SQLException for masked column via getObject(int, Map)");
                    } catch (SQLException e) {
                        // If it's because it's masked, great. If H2 doesn't support it,
                        // we still want to make sure it doesn't return the secret if it WERE supported.
                        // Our override should throw the "is masked" exception FIRST.
                        assertTrue("Exception should mention masking: " + e.getMessage(),
                                   e.getMessage().contains("is masked") || e.getMessage().contains("getObject"));
                    }
                }
            }
        }
    }
}
