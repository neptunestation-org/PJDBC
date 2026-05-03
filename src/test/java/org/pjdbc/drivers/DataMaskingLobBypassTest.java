package org.pjdbc.drivers;

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

public class DataMaskingLobBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLobTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE lob_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");

                // Insert some data
                java.sql.PreparedStatement pstmt = conn.prepareStatement("INSERT INTO lob_data VALUES (1, ?, ?)");
                pstmt.setInt(1, 1);
                pstmt.setBytes(2, "secret binary data".getBytes());
                pstmt.setString(3, "secret character data");
                pstmt.executeUpdate();
            }
        }
    }

    @Test
    public void testGetBlobMaskedThrows() throws SQLException {
        setupLobTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    try {
                        rs.getBlob("secret_blob");
                        fail("getBlob should have thrown SQLException on masked column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("DataMaskingDriver"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMaskedThrows() throws SQLException {
        setupLobTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    try {
                        rs.getClob("secret_clob");
                        fail("getClob should have thrown SQLException on masked column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("DataMaskingDriver"));
                    }
                }
            }
        }
    }
}
