package org.pjdbc.drivers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;

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

    private void setupComplexTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS complex_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB, " +
                    "secret_array INT ARRAY, " +
                    "secret_url VARCHAR(100))");
                stmt.execute("INSERT INTO complex_data VALUES (1, X'48454C4C4F', 'SENSITIVE CLOB DATA', ARRAY[1, 2, 3], 'http://secret.com')");
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
                        fail("Expected SQLException for masked blob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver: Column 'secret_blob' is masked"));
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
                        fail("Expected SQLException for masked clob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver: Column 'secret_clob' is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetArrayMaskedThrows() throws SQLException {
        setupComplexTable("test_array");
        String url = "jdbc:mask[columns=secret_array]:jdbc:h2:mem:test_array;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_array FROM complex_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getArray("secret_array");
                        fail("Expected SQLException for masked array column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver: Column 'secret_array' is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetURLMaskedThrows() throws SQLException {
        setupComplexTable("test_url");
        String url = "jdbc:mask[columns=secret_url]:jdbc:h2:mem:test_url;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_url FROM complex_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getURL("secret_url");
                        fail("Expected SQLException for masked url column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver: Column 'secret_url' is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassMaskedThrows() throws SQLException {
        setupComplexTable("test_getobject_class");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_getobject_class;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM complex_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getObject with String.class should return masked string
                    assertEquals("****", rs.getObject("secret_clob", String.class).substring(0, 4));

                    try {
                        rs.getObject("secret_clob", Clob.class);
                        fail("Expected SQLException for masked clob column via getObject(Class)");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver: Column 'secret_clob' is masked"));
                    }
                }
            }
        }
    }
}
