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

import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingLobBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLobTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS lob_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");

                // Insert some data
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('SECRET_BLOB_CONTENT' AS BLOB), CAST('SECRET_CLOB_CONTENT' AS CLOB))");
            }
        }
    }

    @Test
    public void testGetBlobMaskedBypass() throws SQLException {
        setupLobTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // This SHOULD fail if masking is working for BLOBs
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        if (blob != null) {
                            byte[] bytes = blob.getBytes(1, (int) blob.length());
                            String content = new String(bytes);
                            if (content.equals("SECRET_BLOB_CONTENT")) {
                                fail("Security Bypass: Successfully retrieved original BLOB content from masked column");
                            }
                        }
                    } catch (SQLException e) {
                        // Expected behavior if fixed
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMaskedBypass() throws SQLException {
        setupLobTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // This SHOULD fail if masking is working for CLOBs
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        if (clob != null) {
                            String content = clob.getSubString(1, (int) clob.length());
                            if (content.equals("SECRET_CLOB_CONTENT")) {
                                fail("Security Bypass: Successfully retrieved original CLOB content from masked column");
                            }
                        }
                    } catch (SQLException e) {
                        // Expected behavior if fixed
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_obj_class;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE obj_test (id INT, secret VARCHAR(100))");
                stmt.execute("INSERT INTO obj_test VALUES (1, 'SECRET_VALUE')");
            }
        }
        String url = "jdbc:mask[columns=secret,strategy=REDACT]:jdbc:h2:mem:test_obj_class;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret FROM obj_test WHERE id = 1")) {
                    assertTrue(rs.next());

                    // This SHOULD return masked value or throw, but currently it bypasses
                    String val = rs.getObject(1, String.class);
                    if ("SECRET_VALUE".equals(val)) {
                        fail("Security Bypass: Successfully retrieved original value via getObject(int, Class)");
                    }
                }
            }
        }
    }
}
