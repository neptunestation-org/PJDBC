package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
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
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;

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
                stmt.execute("CREATE TABLE IF NOT EXISTS sensitive_data (" +
                    "id INT PRIMARY KEY, " +
                    "ssn VARCHAR(11), " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");

                Blob blob = new SerialBlob("sensitive binary data".getBytes());
                Clob clob = new SerialClob("sensitive text data".toCharArray());

                var pstmt = conn.prepareStatement("INSERT INTO sensitive_data VALUES (1, '123-45-6789', ?, ?)");
                pstmt.setBlob(1, blob);
                pstmt.setClob(2, clob);
                pstmt.executeUpdate();
            }
        }
    }

    @Test
    public void testAliasBypassBlocked() throws SQLException {
        setupTestTable("test_alias_bypass");
        // Mask 'ssn' column
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Use alias to try to bypass name-based masking
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS ssn_alias FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    String value = rs.getString("ssn_alias");
                    // Should be masked regardless of alias
                    assertEquals("Masking bypassed via alias!", "[REDACTED]", value);
                }
            }
        }
    }

    @Test
    public void testBlobAccessBlocked() throws SQLException {
        setupTestTable("test_blob_bypass");
        // Mask 'secret_blob' column
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked Blob column");
                    } catch (SQLException e) {
                        assertTrue("Exception message should contain 'masked'", e.getMessage().contains("masked"));
                        assertTrue("Exception message should contain 'getBlob'", e.getMessage().contains("getBlob"));
                    }
                }
            }
        }
    }

    @Test
    public void testClobAccessBlocked() throws SQLException {
        setupTestTable("test_clob_bypass");
        // Mask 'secret_clob' column
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked Clob column");
                    } catch (SQLException e) {
                        assertTrue("Exception message should contain 'masked'", e.getMessage().contains("masked"));
                        assertTrue("Exception message should contain 'getClob'", e.getMessage().contains("getClob"));
                    }
                }
            }
        }
    }
}
