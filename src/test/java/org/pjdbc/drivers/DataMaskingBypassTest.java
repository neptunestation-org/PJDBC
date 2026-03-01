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

public class DataMaskingBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, ssn VARCHAR(11))");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789')");

                stmt.execute("CREATE TABLE lob_data (id INT PRIMARY KEY, secret_blob BLOB, secret_clob CLOB)");
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret blob' AS BLOB), CAST('secret clob' AS CLOB))");
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupTestTable("test_alias_bypass");
        // Only ssn is masked
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Using an alias that does NOT match the 'ssn' pattern
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS my_alias FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    String value = rs.getString("my_alias");
                    if ("123-45-6789".equals(value)) {
                        fail("Bypass detected! Able to retrieve masked data via alias: " + value);
                    }
                    assertEquals("[REDACTED]", value);
                }
            }
        }
    }

    @Test
    public void testGetBlobMaskedBypass() throws SQLException {
        setupTestTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        if (blob != null) {
                            byte[] bytes = blob.getBytes(1, (int) blob.length());
                            String value = new String(bytes);
                            if ("secret blob".equals(value)) {
                                fail("Bypass detected! Able to retrieve masked BLOB data.");
                            }
                        }
                    } catch (SQLException e) {
                        // Expected if fixed
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMaskedBypass() throws SQLException {
        setupTestTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        if (clob != null) {
                            String value = clob.getSubString(1, (int) clob.length());
                            if ("secret clob".equals(value)) {
                                fail("Bypass detected! Able to retrieve masked CLOB data.");
                            }
                        }
                    } catch (SQLException e) {
                        // Expected if fixed
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
