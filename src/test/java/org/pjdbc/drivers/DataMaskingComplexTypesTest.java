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
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;

import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingComplexTypesTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupComplexTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS complex_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");

                Blob blob = new SerialBlob("secret blob content".getBytes());
                Clob clob = new SerialClob("secret clob content".toCharArray());

                var pstmt = conn.prepareStatement("INSERT INTO complex_data VALUES (1, ?, ?)");
                pstmt.setBlob(1, blob);
                pstmt.setClob(2, clob);
                pstmt.executeUpdate();
            }
        }
    }

    @Test
    public void testGetBlobMaskedThrows() throws SQLException {
        setupComplexTable("test_blob");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM complex_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked BLOB column, but it leaked data!");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getBlob"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMaskedThrows() throws SQLException {
        setupComplexTable("test_clob");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM complex_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked CLOB column, but it leaked data!");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getClob"));
                    }
                }
            }
        }
    }
}
