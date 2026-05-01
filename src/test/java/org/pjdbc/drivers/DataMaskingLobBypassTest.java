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
                    "secret_clob CLOB, " +
                    "secret_array INT ARRAY, " +
                    "ssn VARCHAR(11))");

                stmt.execute("INSERT INTO lob_data (id, secret_blob, secret_clob, secret_array, ssn) VALUES (1, " +
                    "CAST('SECRET_BLOB_CONTENT' AS BINARY), " +
                    "'SECRET_CLOB_CONTENT', " +
                    "ARRAY[1, 2, 3], " +
                    "'123-45-6789')");
            }
        }
    }

    @Test
    public void testGetBlobMasked() throws SQLException {
        setupLobTable("test_blob");
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_blob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked Blob");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getBlob"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMasked() throws SQLException {
        setupLobTable("test_clob");
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_clob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked Clob");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getClob"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetArrayMasked() throws SQLException {
        setupLobTable("test_array");
        String url = "jdbc:mask[columns=secret_array,strategy=REDACT]:jdbc:h2:mem:test_array;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_array FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getArray("secret_array");
                        fail("Expected SQLException for masked Array");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getArray"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassMasked() throws SQLException {
        setupLobTable("test_getobject_class");
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
                        fail("Expected SQLException for masked Clob via getObject(..., Class)");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testAliasMasking() throws SQLException {
        setupLobTable("test_alias");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS not_a_secret FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getString("not_a_secret"));
                    assertEquals("[REDACTED]", rs.getObject("not_a_secret"));
                }
            }
        }
    }
}
