package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingDriverSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS secrets (id INT, secret_val VARCHAR(100))");
                stmt.execute("INSERT INTO secrets VALUES (1, 'SUPER-SECRET-123')");
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupTestTable("test_alias_bypass");
        // Masking "secret_val"
        String url = "jdbc:mask[columns=secret_val,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Using an alias "not_a_secret"
                try (ResultSet rs = stmt.executeQuery("SELECT secret_val AS not_a_secret FROM secrets WHERE id = 1")) {
                    assertTrue(rs.next());
                    String value = rs.getString("not_a_secret");
                    // If it's not [REDACTED], then it's a bypass!
                    if (!"[REDACTED]".equals(value)) {
                        System.err.println("BYPASS DETECTED: Got original value: " + value);
                    }
                    assertEquals("[REDACTED]", value);
                }
            }
        }
    }

    @Test
    public void testBlobLeak() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_blob_leak;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE blob_secrets (id INT, secret_blob BLOB)");
                stmt.execute("INSERT INTO blob_secrets VALUES (1, CAST('SECRET-BLOB-DATA' AS BINARY))");
            }
        }

        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_leak;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM blob_secrets WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        java.sql.Blob blob = rs.getBlob("secret_blob");
                        if (blob != null) {
                            byte[] bytes = blob.getBytes(1, (int) blob.length());
                            System.err.println("LEAK DETECTED: Got blob data: " + new String(bytes));
                        }
                        fail("Expected SQLException for masked Blob column");
                    } catch (SQLException e) {
                        // Success - it should throw
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
