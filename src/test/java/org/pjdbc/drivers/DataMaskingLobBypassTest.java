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
import java.util.HashMap;
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;

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
                stmt.execute("CREATE TABLE lob_data (id INT PRIMARY KEY, secret_blob BLOB, secret_clob CLOB, secret_url VARCHAR(100))");

                Blob blob = new SerialBlob("sensitive data".getBytes());
                Clob clob = new SerialClob("sensitive text".toCharArray());

                var pstmt = conn.prepareStatement("INSERT INTO lob_data VALUES (1, ?, ?, 'http://secret.com')");
                pstmt.setBlob(1, blob);
                pstmt.setClob(2, clob);
                pstmt.executeUpdate();
            }
        }
    }

    @Test
    public void testGetBlobMaskedThrows() throws SQLException {
        setupLobTable("test_blob");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked BLOB column");
                    } catch (SQLException e) {
                        assertTrue("Error message should mention masking: " + e.getMessage(),
                            e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMaskedThrows() throws SQLException {
        setupLobTable("test_clob");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked CLOB column");
                    } catch (SQLException e) {
                        assertTrue("Error message should mention masking: " + e.getMessage(),
                            e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassMasked() throws SQLException {
        setupLobTable("test_getobject_class");
        String url = "jdbc:mask[columns=secret_url,strategy=REDACT]:jdbc:h2:mem:test_getobject_class;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_url FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    // getObject with String.class should work and return masked value
                    assertEquals("[REDACTED]", rs.getObject("secret_url", String.class));

                    // getObject with other class should throw
                    try {
                        rs.getObject("secret_url", Object.class);
                        fail("Expected SQLException for masked column with Object.class");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithMapMaskedThrows() throws SQLException {
        setupLobTable("test_getobject_map");
        String url = "jdbc:mask[columns=secret_url]:jdbc:h2:mem:test_getobject_map;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_url FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getObject("secret_url", new HashMap<String, Class<?>>());
                        fail("Expected SQLException for masked column with Map");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetURLMaskedThrows() throws SQLException {
        setupLobTable("test_url");
        String url = "jdbc:mask[columns=secret_url]:jdbc:h2:mem:test_url;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_url FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getURL("secret_url");
                        fail("Expected SQLException for masked URL column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
