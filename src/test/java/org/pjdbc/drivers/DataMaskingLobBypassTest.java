package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
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
                stmt.execute("CREATE TABLE lob_data (id INT PRIMARY KEY, secret_blob BLOB, secret_clob CLOB)");
                stmt.execute("INSERT INTO lob_data VALUES (1, CAST('secret blob' AS BLOB), CAST('secret clob' AS CLOB))");
            }
        }
    }

    @Test
    public void testGetBlobBypass() throws SQLException {
        setupLobTable("test_blob_bypass");
        String url = "jdbc:mask[columns=secret_blob,strategy=REDACT]:jdbc:h2:mem:test_blob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getString should be masked
                    assertEquals("[REDACTED]", rs.getString("secret_blob"));

                    // getBlob should now throw SQLException
                    try {
                        rs.getBlob("secret_blob");
                        fail("Expected SQLException for masked blob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobBypass() throws SQLException {
        setupLobTable("test_clob_bypass");
        String url = "jdbc:mask[columns=secret_clob,strategy=REDACT]:jdbc:h2:mem:test_clob_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lob_data WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getString should be masked
                    assertEquals("[REDACTED]", rs.getString("secret_clob"));

                    // getClob should now throw SQLException
                    try {
                        rs.getClob("secret_clob");
                        fail("Expected SQLException for masked clob column");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassBypass() throws SQLException {
        setupLobTable("test_getobject_class_bypass");
        // We reuse the ssn column from setupTestTable logic or just use a simple one here
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_getobject_class_bypass;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, ssn VARCHAR(100))");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789')");
            }
        }

        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_getobject_class_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn FROM users WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getObject(int) is masked
                    assertEquals("[REDACTED]", rs.getObject(1));

                    // getObject(int, Class) should now be masked
                    assertEquals("[REDACTED]", rs.getObject(1, String.class));

                    // getObject(int, Integer.class) should throw SQLException
                    try {
                        rs.getObject(1, Integer.class);
                        fail("Expected SQLException for masked column with Integer.class");
                    } catch (SQLException e) {
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
                stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, ssn VARCHAR(100))");
                stmt.execute("INSERT INTO users VALUES (1, '123-45-6789')");
            }
        }

        String url = "jdbc:mask[columns=ssn,strategy=REDACT]:jdbc:h2:mem:test_alias_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS secret_alias FROM users WHERE id = 1")) {
                    assertTrue(rs.next());

                    // getString(int) should be masked
                    assertEquals("[REDACTED]", rs.getString(1));

                    // getString(String) with alias should now be masked
                    assertEquals("[REDACTED]", rs.getString("secret_alias"));
                }
            }
        }
    }
}
