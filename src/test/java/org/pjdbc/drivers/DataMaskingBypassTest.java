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

    private void setupLOBTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS lob_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB, " +
                    "secret_array INT ARRAY)");
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('48454c4c4f' AS BLOB), 'Secret Message', ARRAY[1, 2, 3])");
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
                        assertTrue("Error message should mention 'masked', but was: " + e.getMessage(),
                            e.getMessage().toLowerCase().contains("masked"));
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
                        assertTrue("Error message should mention 'masked', but was: " + e.getMessage(),
                            e.getMessage().toLowerCase().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetArrayMaskedThrows() throws SQLException {
        setupLOBTable("test_array");
        String url = "jdbc:mask[columns=secret_array]:jdbc:h2:mem:test_array;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_array FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getArray("secret_array");
                        fail("Expected SQLException for masked ARRAY column");
                    } catch (SQLException e) {
                        assertTrue("Error message should mention 'masked', but was: " + e.getMessage(),
                            e.getMessage().toLowerCase().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassMasked() throws SQLException {
        setupLOBTable("test_getobject_class");
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_getobject_class;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    // Should work for String.class
                    assertEquals("[REDACTED]", rs.getObject("secret_clob", String.class));

                    // Should throw for others
                    try {
                        rs.getObject("secret_clob", Clob.class);
                        fail("Expected SQLException for masked column with Clob.class");
                    } catch (SQLException e) {
                        assertTrue("Error message should mention 'masked', but was: " + e.getMessage(),
                            e.getMessage().toLowerCase().contains("masked"));
                    }
                }
            }
        }
    }
}
