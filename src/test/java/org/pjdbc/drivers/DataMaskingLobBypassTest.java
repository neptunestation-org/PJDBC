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
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret blob content' AS BINARY), 'secret clob content')");
            }
        }
    }

    @Test
    public void testGetBlobMaskedBypass() throws SQLException {
        setupLobTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        byte[] bytes = blob.getBytes(1, (int) blob.length());
                        String content = new String(bytes);
                        if (content.contains("secret")) {
                            fail("VULNERABILITY: Masked BLOB column returned original content: " + content);
                        }
                    } catch (SQLException e) {
                        // Expected if fixed
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMaskedBypass() throws SQLException {
        setupLobTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        String content = clob.getSubString(1, (int) clob.length());
                        if (content.contains("secret")) {
                            fail("VULNERABILITY: Masked CLOB column returned original content: " + content);
                        }
                    } catch (SQLException e) {
                        // Expected if fixed
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassBypass() throws SQLException {
        setupLobTable("test_getobject_class_bypass");
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_getobject_class_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    String content = rs.getObject("secret_clob", String.class);
                    if (content != null && content.contains("secret")) {
                        fail("VULNERABILITY: Masked CLOB column via getObject(..., String.class) returned original content: " + content);
                    }
                    org.junit.Assert.assertEquals("[REDACTED]", content);
                }
            }
        }
    }

    @Test
    public void testGetObjectWithOtherClassThrows() throws SQLException {
        setupLobTable("test_getobject_other_class");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_getobject_other_class;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getObject("secret_clob", Integer.class);
                        fail("Expected SQLException for masked column via getObject(..., Integer.class)");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetArrayMaskedThrows() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_array;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS array_data (id INT PRIMARY KEY, secret_arr INT ARRAY)");
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
                    }
                }
            }
        }
    }
}
