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

public class DataMaskingLobBypassTest {

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
                PreparedStatement pstmt = conn.prepareStatement("INSERT INTO lob_data VALUES (1, ?, ?)");
                pstmt.setBytes(1, "SECRET_BLOB_CONTENT".getBytes());
                pstmt.setString(2, "SECRET_CLOB_CONTENT");
                pstmt.execute();
            }
        }
    }

    @Test
    public void testBlobBypass() throws SQLException {
        String dbName = "test_blob_bypass";
        setupLobTable(dbName);
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // This SHOULD be masked/blocked
                    try {
                        rs.getBlob("secret_blob");
                        fail("Security Bypass: Secret BLOB content leaked even though column is masked!");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                        assertTrue(e.getMessage().contains("getBlob"));
                    }

                    // But getString should work and return masked value
                    assertEquals("[REDACTED]", rs.getString("secret_blob"));
                }
            }
        }
    }

    @Test
    public void testClobBypass() throws SQLException {
        String dbName = "test_clob_bypass";
        setupLobTable(dbName);
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // This SHOULD be masked/blocked
                    try {
                        rs.getClob("secret_clob");
                        fail("Security Bypass: Secret CLOB content leaked even though column is masked!");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                        assertTrue(e.getMessage().contains("getClob"));
                    }

                    // But getString should work and return masked value
                    assertEquals("[REDACTED]", rs.getString("secret_clob"));
                }
            }
        }
    }
}
