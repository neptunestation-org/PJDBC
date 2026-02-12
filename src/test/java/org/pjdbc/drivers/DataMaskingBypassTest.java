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
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupBypassTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS bypass_test (" +
                    "id INT PRIMARY KEY, " +
                    "secret_clob CLOB, " +
                    "secret_blob BLOB, " +
                    "secret_text VARCHAR(100))");

                // Insert a Clob
                stmt.execute("INSERT INTO bypass_test VALUES (1, 'very secret clob content', X'0102030405', 'plain secret')");
            }
        }
    }

    @Test
    public void testClobBypassBlocked() throws SQLException {
        setupBypassTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM bypass_test WHERE id = 1")) {
                    assertTrue(rs.next());

                    try {
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked clob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testBlobBypassBlocked() throws SQLException {
        setupBypassTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM bypass_test WHERE id = 1")) {
                    assertTrue(rs.next());

                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked blob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassBypassBlocked() throws SQLException {
        setupBypassTable("test_getobject_class_bypass");
        String url = "jdbc:mask[columns=secret_text,strategy=REDACT]:jdbc:h2:mem:test_getobject_class_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_text FROM bypass_test WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getString() is masked correctly
                    assertEquals("[REDACTED]", rs.getString("secret_text"));

                    // getObject(label, Class) should now return masked string if type is String.class
                    String masked = rs.getObject("secret_text", String.class);
                    assertEquals("[REDACTED]", masked);

                    // Other classes should throw
                    try {
                        rs.getObject("secret_text", Object.class);
                        fail("Expected SQLException for getObject(Object.class) on masked column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
