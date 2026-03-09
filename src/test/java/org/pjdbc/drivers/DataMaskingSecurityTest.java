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
                stmt.execute("CREATE TABLE IF NOT EXISTS secure_data (" +
                    "id INT PRIMARY KEY, " +
                    "ssn VARCHAR(11), " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");
                stmt.execute("INSERT INTO secure_data VALUES (1, '123-45-6789', " +
                    "CAST('secret binary' AS BLOB), CAST('secret text' AS CLOB))");
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupSecurityTable("test_alias_bypass");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS alias_name FROM secure_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    String val = rs.getString("alias_name");
                    if (!"[REDACTED]".equals(val)) {
                        fail("Vulnerability: Alias bypass detected! Expected [REDACTED] but got: " + val);
                    }
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
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM secure_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        byte[] bytes = blob.getBytes(1, (int) blob.length());
                        fail("Vulnerability: BLOB bypass detected! Got: " + new String(bytes));
                    } catch (SQLException e) {
                        assertTrue("Expected message to contain 'masked', got: " + e.getMessage(),
                                   e.getMessage().contains("masked"));
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
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM secure_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        String data = clob.getSubString(1, (int) clob.length());
                        fail("Vulnerability: CLOB bypass detected! Got: " + data);
                    } catch (SQLException e) {
                        assertTrue("Expected message to contain 'masked', got: " + e.getMessage(),
                                   e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
