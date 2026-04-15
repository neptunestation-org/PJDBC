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

import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingLOBBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLOBTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE lob_data (id INT PRIMARY KEY, secret_blob BLOB, secret_clob CLOB)");
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret blob content' AS BLOB), CAST('secret clob content' AS CLOB))");
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
                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked BLOB column");
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
        setupLOBTable("test_clob_throws");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_throws;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked CLOB column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                        assertTrue(e.getMessage().contains("getClob"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetTypedObjectMasked() throws SQLException {
        setupLOBTable("test_typed_object");
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_typed_object;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getObject(..., String.class) should work and return masked value
                    String masked = rs.getObject("secret_clob", String.class);
                    assertNotNull(masked);
                    assertTrue("[REDACTED]".equals(masked));

                    // getObject(..., Clob.class) should throw
                    try {
                        rs.getObject("secret_clob", Clob.class);
                        fail("Expected SQLException for masked column with Clob.class");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }
}
