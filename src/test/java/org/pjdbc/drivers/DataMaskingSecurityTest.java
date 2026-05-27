package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLOBTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS lob_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_clob CLOB, " +
                    "secret_blob BLOB)");

                PreparedStatement pstmt = conn.prepareStatement("INSERT INTO lob_data VALUES (1, ?, ?)");
                pstmt.setString(1, "very secret large text");
                pstmt.setBytes(2, "secret binary data".getBytes());
                pstmt.executeUpdate();
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

                    // This SHOULD be masked or throw SQLException, but currently it might bypass
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        String content = clob.getSubString(1, (int) clob.length());
                        System.out.println("Bypassed Clob content: " + content);
                        // If we can read the original content, it's a bypass!
                        if ("very secret large text".equals(content)) {
                            fail("Security Bypass: Original CLOB content was accessible from masked column via getClob()");
                        }
                    } catch (SQLException e) {
                        // Expected behavior should be an exception
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
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

                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        byte[] content = blob.getBytes(1, (int) blob.length());
                        String contentStr = new String(content);
                        System.out.println("Bypassed Blob content: " + contentStr);
                        if ("secret binary data".equals(contentStr)) {
                            fail("Security Bypass: Original BLOB content was accessible from masked column via getBlob()");
                        }
                    } catch (SQLException e) {
                        // Expected behavior
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithTypeBypass() throws SQLException {
        setupLOBTable("test_getobject_type_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_getobject_type_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    try {
                        Clob clob = rs.getObject("secret_clob", Clob.class);
                        String content = clob.getSubString(1, (int) clob.length());
                        if ("very secret large text".equals(content)) {
                            fail("Security Bypass: Original content was accessible via getObject(label, Clob.class)");
                        }
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testAliasingBypass() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_aliasing;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users_alias (id INT PRIMARY KEY, ssn VARCHAR(100))");
                stmt.execute("INSERT INTO users_alias VALUES (1, '123-45-6789')");
            }
        }

        // Mask ssn
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_aliasing;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Alias ssn to "public_id"
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS public_id FROM users_alias WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getString("public_id") should be masked!
                    String val = rs.getString("public_id");
                    System.out.println("Aliased value: " + val);
                    if (!"[REDACTED]".equals(val)) {
                        fail("Security Bypass: Aliased column was not masked. Got: " + val);
                    }
                }
            }
        }
    }
}
