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

public class DataMaskingSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupLobTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS lobs (id INT PRIMARY KEY, secret_blob BLOB, secret_clob CLOB)");
                stmt.execute("INSERT INTO lobs VALUES (1, X'CAFEBABE', 'very secret data')");
            }
        }
    }

    @Test
    public void testBlobBypass() throws SQLException {
        setupLobTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lobs WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        assertNotNull(blob);
                        fail("VULNERABILITY CONFIRMED: Got unmasked Blob: " + blob.length() + " bytes");
                    } catch (SQLException e) {
                        System.out.println("Blob access blocked: " + e.getMessage());
                    }
                }
            }
        }
        // If we reach here, getBlob didn't throw, which is the current vulnerable state
    }

    @Test
    public void testGetObjectClassBypass() throws SQLException {
        setupLobTable("test_getobject_class");
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_getobject_class;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lobs WHERE id = 1")) {
                    assertTrue(rs.next());
                    // Should work for String.class
                    assertEquals("[REDACTED]", rs.getObject(1, String.class));
                    assertEquals("[REDACTED]", rs.getObject("secret_blob", String.class));

                    // Should throw for others
                    try {
                        rs.getObject(1, byte[].class);
                        fail("Should have thrown SQLException for getObject(int, byte[].class)");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testClobBypass() throws SQLException {
        setupLobTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lobs WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        assertNotNull(clob);
                        fail("VULNERABILITY CONFIRMED: Got unmasked Clob: " + clob.getSubString(1, 10));
                    } catch (SQLException e) {
                        System.out.println("Clob access blocked: " + e.getMessage());
                    }
                }
            }
        }
    }
}
