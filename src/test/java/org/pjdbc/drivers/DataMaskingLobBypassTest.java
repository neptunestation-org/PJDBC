package org.pjdbc.drivers;

import static org.junit.Assert.*;
import java.sql.*;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingLobBypassTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @Test
    public void testBlobBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_blob;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE lobtest (id INT, secret_blob BLOB)");
                PreparedStatement pstmt = setupConn.prepareStatement("INSERT INTO lobtest VALUES (1, ?)");
                pstmt.setBytes(1, "SENSITIVE DATA".getBytes());
                pstmt.executeUpdate();
            }
        }

        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lobtest WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        if (blob != null) {
                            byte[] bytes = blob.getBytes(1, (int) blob.length());
                            assertNotEquals("SENSITIVE DATA", new String(bytes));
                        }
                        fail("Should have thrown SQLException for masked Blob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testClobBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_clob;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE clobtest (id INT, secret_clob CLOB)");
                stmt.execute("INSERT INTO clobtest VALUES (1, 'SENSITIVE CLOB DATA')");
            }
        }

        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM clobtest WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        if (clob != null) {
                            String data = clob.getSubString(1, (int) clob.length());
                            assertNotEquals("SENSITIVE CLOB DATA", data);
                        }
                        fail("Should have thrown SQLException for masked Clob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("is masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectClassBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_getobject;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE objtest (id INT, secret_ssn VARCHAR(100))");
                stmt.execute("INSERT INTO objtest VALUES (1, '123-45-6789')");
            }
        }

        String url = "jdbc:mask[columns=secret_ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_ssn FROM objtest WHERE id = 1")) {
                    assertTrue(rs.next());
                    String ssn = rs.getObject("secret_ssn", String.class);
                    assertEquals("[REDACTED]", ssn);
                }
            }
        }
    }

    @Test
    public void testColumnAliasingBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE aliestest (id INT, secret_ssn VARCHAR(100))");
                stmt.execute("INSERT INTO aliestest VALUES (1, '123-45-6789')");
            }
        }

        // Masking is configured for 'secret_ssn'
        String url = "jdbc:mask[columns=secret_ssn,strategy=REDACT]:jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Use an alias for the masked column
                try (ResultSet rs = stmt.executeQuery("SELECT secret_ssn AS public_info FROM aliestest WHERE id = 1")) {
                    assertTrue(rs.next());
                    // Should be masked even when accessed via alias
                    assertEquals("[REDACTED]", rs.getString("public_info"));
                }
            }
        }
    }
}
