package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
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

public class DataMaskingLeakTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLobTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS lob_data (id INT PRIMARY KEY, secret_blob BLOB, secret_clob CLOB, secret_array INT ARRAY)");
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret blob content' AS BLOB), CAST('secret clob content' AS CLOB), ARRAY[1, 2, 3])");
            }
        }
    }

    @Test
    public void testGetBlobLeak() throws SQLException {
        setupLobTable("test_blob_leak");
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_blob_leak;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getString("secret_blob"));

                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked Blob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobLeak() throws SQLException {
        setupLobTable("test_clob_leak");
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_clob_leak;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getString("secret_clob"));

                    try {
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked Clob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetArrayLeak() throws SQLException {
        setupLobTable("test_array_leak");
        String url = "jdbc:mask[columns=secret_array,strategy=REDACT]:jdbc:h2:mem:test_array_leak;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_array FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getArray("secret_array");
                        fail("Expected SQLException for masked Array column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectGenericLeak() throws SQLException {
        setupLobTable("test_getobject_leak");
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_getobject_leak;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getObject("secret_blob", Blob.class);
                        fail("Expected SQLException for masked column via getObject(..., Class)");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
