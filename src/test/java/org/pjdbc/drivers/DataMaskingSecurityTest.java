package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
                stmt.execute("CREATE TABLE IF NOT EXISTS lob_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret blob' AS BLOB), CAST('secret clob' AS CLOB))");
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
                        rs.getBlob("secret_blob");
                        // If this succeeds, it's a bypass!
                    } catch (SQLException e) {
                        // Expected if secured
                        return;
                    }
                    fail("Bypass detected: getBlob succeeded on masked column");
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
                        rs.getClob("secret_clob");
                    } catch (SQLException e) {
                        return;
                    }
                    fail("Bypass detected: getClob succeeded on masked column");
                }
            }
        }
    }

    @Test
    public void testGetObjectWithTypeBypass() throws SQLException {
        setupLobTable("test_getobject_bypass");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_getobject_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getObject("secret_clob", java.sql.Clob.class);
                    } catch (SQLException e) {
                        return;
                    }
                    fail("Bypass detected: getObject(label, Class) succeeded on masked column");
                }
            }
        }
    }

    @Test
    public void testAliasingBypass() throws SQLException {
        setupLobTable("test_alias_bypass");
        // Only ssn is masked
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT, ssn VARCHAR(11))");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789')");
                // Select ssn with an alias that DOES NOT match masking pattern
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS alias FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    String val = rs.getString("alias");
                    if (!"[REDACTED]".equals(val)) {
                        fail("Bypass detected: Aliased column 'alias' (original 'ssn') returned unmasked value: " + val);
                    }
                }
            }
        }
    }

    @Test
    public void testGetURLBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_url;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE urltest (id INT, secret_url VARCHAR(100))");
                stmt.execute("INSERT INTO urltest VALUES (1, 'https://secret.com')");
            }
        }
        String url = "jdbc:mask[columns=secret_url]:jdbc:h2:mem:test_url;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_url FROM urltest WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getURL("secret_url");
                    } catch (SQLException e) {
                        return;
                    }
                    fail("Bypass detected: getURL succeeded on masked column");
                }
            }
        }
    }

    @Test
    public void testGetArrayBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_array;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE arraytest (id INT, secret_array INT ARRAY)");
                stmt.execute("INSERT INTO arraytest VALUES (1, ARRAY[1, 2, 3])");
            }
        }
        String url = "jdbc:mask[columns=secret_array]:jdbc:h2:mem:test_array;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_array FROM arraytest WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        rs.getArray("secret_array");
                    } catch (SQLException e) {
                        return;
                    }
                    fail("Bypass detected: getArray succeeded on masked column");
                }
            }
        }
    }
}
