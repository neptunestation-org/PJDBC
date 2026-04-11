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

public class DataMaskingBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    private void setupTestTable(String dbName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, ssn VARCHAR(11))");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789')");

                stmt.execute("CREATE TABLE IF NOT EXISTS lob_data (id INT PRIMARY KEY, secret_blob BLOB, secret_clob CLOB)");
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret blob content' AS BLOB), CAST('secret clob content' AS CLOB))");
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        setupTestTable("test_alias");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS my_ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("[REDACTED]", rs.getString(1));
                    assertEquals("[REDACTED]", rs.getString("my_ssn"));
                }
            }
        }
    }

    @Test
    public void testBlobBypass() throws SQLException {
        setupTestTable("test_blob");
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Blob b = rs.getBlob("secret_blob");
                        assertNotNull(b);
                        fail("Bypassed masking using getBlob()");
                    } catch (SQLException e) {
                        assertTrue("Error message should contain 'DataMaskingDriver', but was: " + e.getMessage(),
                                   e.getMessage().contains("DataMaskingDriver"));
                    }
                }
            }
        }
    }

    @Test
    public void testClobBypass() throws SQLException {
        setupTestTable("test_clob");
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Clob c = rs.getClob("secret_clob");
                        assertNotNull(c);
                        fail("Bypassed masking using getClob()");
                    } catch (SQLException e) {
                        assertTrue("Error message should contain 'DataMaskingDriver', but was: " + e.getMessage(),
                                   e.getMessage().contains("DataMaskingDriver"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectClassBypass() throws SQLException {
        setupTestTable("test_getobject_class");
        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject_class;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        String s = rs.getObject(1, String.class);
                        assertEquals("[REDACTED]", s);
                    } catch (SQLException e) {
                        fail("getObject(int, String.class) should work and return masked value, but got: " + e.getMessage());
                    }

                    try {
                        rs.getObject(1, Long.class);
                        fail("getObject(int, Long.class) on masked column should throw");
                    } catch (SQLException e) {
                        assertTrue("Error message should contain 'DataMaskingDriver', but was: " + e.getMessage(),
                                   e.getMessage().contains("DataMaskingDriver"));
                    }
                }
            }
        }
    }
}
