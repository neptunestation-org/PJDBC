package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
                stmt.execute("CREATE TABLE lob_data (id INT PRIMARY KEY, secret_clob CLOB, secret_blob BLOB)");
                stmt.execute("INSERT INTO lob_data VALUES (1, 'very secret data', X'DEADC0DE')");
            }
        }
    }

    @Test
    public void testGetClobMaskedThrows() throws SQLException {
        setupLOBTable("test_clob_throws");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_throws;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // This SHOULD be masked if we use getString()
                    assertEquals("************data", rs.getString("secret_clob"));

                    // getClob() should now throw SQLException
                    try {
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked Clob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver"));
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetBlobMaskedThrows() throws SQLException {
        setupLOBTable("test_blob_throws");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_throws;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getBlob() should now throw SQLException
                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked Blob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver"));
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassMasked() throws SQLException {
        setupLOBTable("test_getobject_class_masked");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_getobject_class_masked;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getObject(int, String.class) should work and return masked string
                    assertEquals("************data", rs.getObject(1, String.class));

                    // getObject(int, Clob.class) should throw
                    try {
                        rs.getObject(1, java.sql.Clob.class);
                        fail("Expected SQLException for masked Clob column via getObject");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver"));
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithMapThrows() throws SQLException {
        setupLOBTable("test_getobject_map_throws");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_getobject_map_throws;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getObject(int, Map) should now throw SQLException
                    try {
                        rs.getObject(1, new java.util.HashMap<String, Class<?>>());
                        fail("Expected SQLException for masked column via getObject(Map)");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("DataMaskingDriver"));
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
