package org.pjdbc.drivers;

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

public class DataMaskingLOBTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLOBTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS lob_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret blob content' AS BINARY), 'secret clob content')");
            }
        }
    }

    @Test
    public void testGetBlobMaskedThrows() throws SQLException {
        setupLOBTable("test_blob");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        // If it doesn't throw, we can check if it's masked, but currently it's likely not.
                        byte[] bytes = blob.getBytes(1, (int) blob.length());
                        String content = new String(bytes);
                        if (content.equals("secret blob content")) {
                            fail("Data leak! Secret blob content exposed: " + content);
                        }
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMaskedThrows() throws SQLException {
        setupLOBTable("test_clob");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        String content = clob.getSubString(1, (int) clob.length());
                        if (content.equals("secret clob content")) {
                            fail("Data leak! Secret clob content exposed: " + content);
                        }
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
