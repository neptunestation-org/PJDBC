package org.pjdbc.drivers;

import static org.junit.Assert.*;
import java.sql.*;
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
                PreparedStatement pstmt = conn.prepareStatement("INSERT INTO sensitive_data VALUES (?, ?, ?, ?)");
                pstmt.setInt(1, 1);
                pstmt.setString(2, "123-45-6789");
                pstmt.setBytes(3, "secret blob content".getBytes());
                pstmt.setString(4, "secret clob content");
                pstmt.execute();
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupTestTable("test_alias_bypass");
        // Mask 'ssn' column
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Use alias for ssn
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS my_alias FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    String value = rs.getString("my_alias");
                    assertEquals("[REDACTED]", value);
                }
            }
        }
    }

    @Test
    public void testBlobBypassThrows() throws SQLException {
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
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getBlob"));
                    }
                }
            }
        }
    }

    @Test
    public void testClobBypassThrows() throws SQLException {
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
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getClob"));
                    }
                }
            }
        }
    }

    @Test
    public void testTypeSafeGetObjectBypassThrows() throws SQLException {
        setupTestTable("test_getobject_bypass");
        // Mask 'ssn' column
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    // getObject(..., String.class) should return masked value
                    assertEquals("[REDACTED]", rs.getObject("ssn", String.class));
                    // getObject(..., Object.class) or other types should throw
                    try {
                        rs.getObject("ssn", Object.class);
                        fail("Expected SQLException for masked column with Object.class");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
