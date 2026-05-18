package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Blob;
import java.sql.Clob;

import org.junit.BeforeClass;
import org.junit.Test;

public class DataMaskingSecurityTest {

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
                    "secret_text VARCHAR(100))");
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret blob content' AS BLOB), CAST('secret clob content' AS CLOB), 'secret text')");
            }
        }
    }

    @Test
    public void testGetBlobMaskedLeaks() throws SQLException {
        setupLOBTable("test_blob_leak");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob_leak;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        if (blob != null) {
                            byte[] bytes = blob.getBytes(1, (int) blob.length());
                            String content = new String(bytes);
                            System.out.println("Leaked BLOB content: " + content);
                            fail("Expected SQLException for masked BLOB column, but got content: " + content);
                        }
                    } catch (SQLException e) {
                        // Expected behavior if fixed
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMaskedLeaks() throws SQLException {
        setupLOBTable("test_clob_leak");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob_leak;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        if (clob != null) {
                            String content = clob.getSubString(1, (int) clob.length());
                            System.out.println("Leaked CLOB content: " + content);
                            fail("Expected SQLException for masked CLOB column, but got content: " + content);
                        }
                    } catch (SQLException e) {
                        // Expected behavior if fixed
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassLeaks() throws SQLException {
        setupLOBTable("test_getobject_leak");
        String url = "jdbc:mask[columns=secret_text,strategy=REDACT]:jdbc:h2:mem:test_getobject_leak;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_text FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getString works
                    assertEquals("[REDACTED]", rs.getString("secret_text"));

                    // getObject(columnLabel, Class) might leak
                    try {
                        String leaked = rs.getObject("secret_text", String.class);
                        if (!"[REDACTED]".equals(leaked)) {
                            System.out.println("Leaked text via getObject(String, Class): " + leaked);
                            fail("Expected masked value or SQLException, but got: " + leaked);
                        }
                    } catch (SQLException e) {
                        // Also acceptable if we choose to throw
                    }
                }
            }
        }
    }
}
