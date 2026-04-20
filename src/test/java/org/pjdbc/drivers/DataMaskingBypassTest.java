package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

public class DataMaskingBypassTest {

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
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('sensitive blob' AS BLOB), CAST('sensitive clob' AS CLOB))");
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

                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        // If we got here without SQLException, it's a bypass!
                        byte[] bytes = blob.getBytes(1, (int) blob.length());
                        String content = new String(bytes);
                        assertEquals("sensitive blob", content);
                        fail("Expected SQLException for masked Blob column, but retrieved sensitive data instead!");
                    } catch (SQLException e) {
                        // This is what we expect AFTER the fix
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

                    try {
                        Clob clob = rs.getClob("secret_clob");
                        assertNotNull(clob);
                        fail("Expected SQLException for masked Clob column, but retrieved it instead!");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassMaskedBypass() throws SQLException {
        setupLobTable("test_getobject_class_bypass");
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_getobject_class_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    try {
                        Clob clob = rs.getObject("secret_clob", Clob.class);
                        assertNotNull(clob);
                        fail("Expected SQLException for getObject(Clob.class) on masked column!");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testAliasingBypass() throws SQLException {
        setupLobTable("test_alias_bypass");
        // Mask secret_blob, but NOT "alias_blob"
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Aliasing the column - SHOULD BE MASKED BASED ON SOURCE COLUMN
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob AS alias_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    try {
                        rs.getBlob("alias_blob");
                        fail("Bypassed masking via column aliasing!");
                    } catch (SQLException e) {
                        // Desired behavior
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testAliasingBypassString() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_alias_string;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE string_data (id INT PRIMARY KEY, ssn VARCHAR(100))");
                stmt.execute("INSERT INTO string_data VALUES (1, '123-45-6789')");
            }
        }

        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_string;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS public_ssn FROM string_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    // Should be masked even though label is "public_ssn"
                    assertEquals("[REDACTED]", rs.getString("public_ssn"));
                    assertEquals("[REDACTED]", rs.getString(1));
                }
            }
        }
    }

    @Test
    public void testAliasingNullValue() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_alias_null;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE null_data (id INT PRIMARY KEY, secret VARCHAR(100))");
                stmt.execute("INSERT INTO null_data VALUES (1, NULL)");
            }
        }

        String url = "jdbc:mask[columns=secret,strategy=REDACT]:jdbc:h2:mem:test_alias_null;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret AS public_secret FROM null_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertNull(rs.getString("public_secret"));
                    assertTrue(rs.wasNull());
                }
            }
        }
    }
}
