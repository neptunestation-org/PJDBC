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
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret blob content' AS BLOB), CAST('secret clob content' AS CLOB))");
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
                        if (blob != null) {
                            byte[] bytes = blob.getBytes(1, (int) blob.length());
                            String content = new String(bytes);
                            if ("secret blob content".equals(content)) {
                                fail("Bypass detected! Masked BLOB column returned real data.");
                            }
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
                        if (clob != null) {
                            String content = clob.getSubString(1, (int) clob.length());
                            if ("secret clob content".equals(content)) {
                                fail("Bypass detected! Masked CLOB column returned real data.");
                            }
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
    public void testAliasingBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS alias_test (id INT, ssn VARCHAR(11))");
                stmt.execute("INSERT INTO alias_test VALUES (1, '123-45-6789')");
            }
        }
        String url = "jdbc:mask[columns=ssn,strategy=FULL]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Alias 'ssn' to 'public_info' which is not in masked columns
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS public_info FROM alias_test WHERE id = 1")) {
                    assertTrue(rs.next());
                    String value = rs.getString("public_info");
                    if ("123-45-6789".equals(value)) {
                        fail("Bypass detected! Masked column accessed via alias returned real data.");
                    }
                }
            }
        }
    }
}
