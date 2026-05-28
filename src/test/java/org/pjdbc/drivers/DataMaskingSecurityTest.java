package org.pjdbc.drivers;

import static org.junit.Assert.*;
import java.sql.*;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingSecurityTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS users");
                stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, ssn VARCHAR(11), secret_blob BLOB)");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789', CAST('secret data' AS BLOB))");
            }
        }
    }

    @Test
    public void testAliasingBypass() throws SQLException {
        setupTestTable("test_alias_bypass");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS ssn_alias FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    String val = rs.getString("ssn_alias");
                    assertEquals("Bypass detected! Column aliasing bypassed masking.", "[REDACTED]", val);
                }
            }
        }
    }

    @Test
    public void testGetObjectWithTypeBypass() throws SQLException {
        setupTestTable("test_getobject_bypass");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    String val = rs.getObject(1, String.class);
                    assertEquals("Bypass detected! getObject(int, Class) bypassed masking.", "[REDACTED]", val);
                }
            }
        }
    }

    @Test
    public void testBlobBypass() throws SQLException {
        setupTestTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getBlob(1);
                        fail("Bypass detected! getBlob(int) bypassed masking.");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobBypass() throws SQLException {
        setupTestTable("test_clob_bypass");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getClob(1);
                        fail("Bypass detected! getClob(int) bypassed masking.");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver"));
                    }
                }
            }
        }
    }
}
