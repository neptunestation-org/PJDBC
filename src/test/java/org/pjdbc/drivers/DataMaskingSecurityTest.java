package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.Blob;
import java.sql.Clob;

import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
        Class.forName("org.h2.Driver");
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS sensitive_data (" +
                    "id INT PRIMARY KEY, " +
                    "ssn VARCHAR(11), " +
                    "bio CLOB, " +
                    "photo BLOB)");

                // Insert some data
                try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO sensitive_data VALUES (?, ?, ?, ?)")) {
                    pstmt.setInt(1, 1);
                    pstmt.setString(2, "123-45-6789");
                    pstmt.setString(3, "This is a very secret biography.");
                    pstmt.setBytes(4, "fake-photo-data".getBytes());
                    pstmt.executeUpdate();
                }
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupTestTable("test_alias");
        // Mask the 'ssn' column
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Use an alias for the masked column
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS ssn_alias FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // This SHOULD be masked even with an alias
                    assertEquals("[REDACTED]", rs.getString("ssn_alias"));
                }
            }
        }
    }

    @Test
    public void testLOBBypass() throws SQLException {
        setupTestTable("test_lob");
        // Mask 'bio' and 'photo' columns
        String url = "jdbc:mask[columns=bio;photo,strategy=REDACT]:jdbc:h2:mem:test_lob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT bio, photo FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // Test CLOB bypass - should throw SQLException
                    try {
                        rs.getClob("bio");
                        fail("getClob should have thrown SQLException for masked column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }

                    // Test BLOB bypass - should throw SQLException
                    try {
                        rs.getBlob("photo");
                        fail("getBlob should have thrown SQLException for masked column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectClassBypass() throws SQLException {
        setupTestTable("test_getobject_class");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject_class;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM sensitive_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getObject with String.class should return masked string
                    assertEquals("[REDACTED]", rs.getObject("ssn", String.class));

                    // getObject with other classes should throw exception for masked columns
                    try {
                        rs.getObject("ssn", Object.class);
                        fail("getObject(String, Object.class) should have thrown SQLException");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
