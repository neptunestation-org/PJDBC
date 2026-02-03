package org.pjdbc.drivers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Array;
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
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret binary data' AS BLOB), CAST('secret character data' AS CLOB))");
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
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked BLOB column");
                    } catch (SQLException e) {
                        assertTrue("Exception message should mention masking", e.getMessage().contains("masked"));
                        assertTrue("Exception message should mention getBlob", e.getMessage().contains("getBlob"));
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
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked CLOB column");
                    } catch (SQLException e) {
                        assertTrue("Exception message should mention masking", e.getMessage().contains("masked"));
                        assertTrue("Exception message should mention getClob", e.getMessage().contains("getClob"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetArrayMaskedThrows() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_array;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE array_data (id INT PRIMARY KEY, secret_arr INTEGER ARRAY)");
                stmt.execute("INSERT INTO array_data VALUES (1, ARRAY[1, 2, 3])");
            }
        }
        String url = "jdbc:mask[columns=secret_arr]:jdbc:h2:mem:test_array;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_arr FROM array_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getArray("secret_arr");
                        fail("Expected SQLException for masked ARRAY column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getArray"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetURLMaskedThrows() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_url;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE url_data (id INT PRIMARY KEY, secret_url VARCHAR(255))");
                stmt.execute("INSERT INTO url_data VALUES (1, 'http://secret.com')");
            }
        }
        String url = "jdbc:mask[columns=secret_url]:jdbc:h2:mem:test_url;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_url FROM url_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getURL("secret_url");
                        fail("Expected SQLException for masked URL column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getURL"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithTypeMaskedThrows() throws SQLException {
        setupLOBTable("test_getobject_type");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_getobject_type;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getObject("secret_clob", Clob.class);
                        fail("Expected SQLException for masked column via getObject(type)");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getObject"));
                    }
                }
            }
        }
    }
}
