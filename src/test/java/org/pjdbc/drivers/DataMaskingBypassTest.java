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

public class DataMaskingBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS secrets (" +
                    "id INT PRIMARY KEY, " +
                    "ssn VARCHAR(11), " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");
                stmt.execute("INSERT INTO secrets VALUES (1, '123-45-6789', CAST('secret blob data' AS BLOB), CAST('secret clob data' AS CLOB))");
            }
        }
    }

    @Test
    public void testGetObjectWithTypeBypass() throws SQLException {
        setupTestTable("test_getobject_bypass");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM secrets WHERE id = 1")) {
                    assertTrue(rs.next());
                    // This should be masked
                    String masked = rs.getObject(1, String.class);
                    if ("123-45-6789".equals(masked)) {
                        fail("Bypass detected: getObject(int, Class) returned unmasked data");
                    }
                }
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupTestTable("test_alias_bypass");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS not_ssn FROM secrets WHERE id = 1")) {
                    assertTrue(rs.next());
                    // Label-based getter might be bypassed if it only checks the label
                    String value = rs.getString("not_ssn");
                    if ("123-45-6789".equals(value)) {
                        fail("Bypass detected: getString(String label) returned unmasked data due to aliasing");
                    }
                }
            }
        }
    }

    @Test
    public void testBlobClobBypass() throws SQLException {
        setupTestTable("test_lob_bypass");
        String url = "jdbc:mask[columns=secret_blob;secret_clob,strategy=REDACT]:jdbc:h2:mem:test_lob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob, secret_clob FROM secrets WHERE id = 1")) {
                    assertTrue(rs.next());

                    try {
                        rs.getBlob("secret_blob");
                        fail("Bypass detected: getBlob returned data from masked column");
                    } catch (SQLException e) {
                        // Expected
                    }

                    try {
                        rs.getClob("secret_clob");
                        fail("Bypass detected: getClob returned data from masked column");
                    } catch (SQLException e) {
                        // Expected
                    }
                }
            }
        }
    }
}
