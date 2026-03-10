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

    private void setupSecurityTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS security_data (" +
                    "id INT PRIMARY KEY, " +
                    "secret_text VARCHAR(100), " +
                    "secret_blob BLOB, " +
                    "secret_clob CLOB)");
                stmt.execute("INSERT INTO security_data VALUES (1, 'very-secret-info', X'deadbeef', 'large-secret-clob-content')");
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupSecurityTable("test_alias");
        // Mask "secret_text"
        String url = "jdbc:mask[columns=secret_text,strategy=REDACT]:jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Alias it to something that doesn't match the regex
                try (ResultSet rs = stmt.executeQuery("SELECT secret_text AS safe_column FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // VULNERABILITY: getString(label) with alias might bypass masking!
                    String value = rs.getString("safe_column");
                    if ("very-secret-info".equals(value)) {
                        fail("Security Bypass: Alias 'safe_column' for 'secret_text' bypassed masking!");
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithTypeBypass() throws SQLException {
        setupSecurityTable("test_bypass");
        String url = "jdbc:mask[columns=secret_text,strategy=REDACT]:jdbc:h2:mem:test_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_text FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getString is masked correctly
                    assertEquals("[REDACTED]", rs.getString("secret_text"));

                    // getObject(int) is masked correctly
                    assertEquals("[REDACTED]", rs.getObject(1));

                    // VULNERABILITY: getObject(int, Class) bypasses masking!
                    String unmasked = rs.getObject(1, String.class);
                    if ("very-secret-info".equals(unmasked)) {
                        fail("Security Bypass: getObject(int, Class) returned unmasked data!");
                    }
                }
            }
        }
    }

    @Test
    public void testLOBLeakage() throws SQLException {
        setupSecurityTable("test_lob");
        String url = "jdbc:mask[columns=secret_blob;secret_clob,strategy=REDACT]:jdbc:h2:mem:test_lob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob, secret_clob FROM security_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // Currently these might not be covered and could leak data
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        if (blob != null) {
                            fail("Security Leak: getBlob() returned unmasked data!");
                        }
                    } catch (SQLException e) {
                        // Expected if fixed
                    }

                    try {
                        Clob clob = rs.getClob("secret_clob");
                        if (clob != null) {
                            fail("Security Leak: getClob() returned unmasked data!");
                        }
                    } catch (SQLException e) {
                        // Expected if fixed
                    }
                }
            }
        }
    }
}
