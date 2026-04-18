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

public class DataMaskingBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.DataMaskingDriver");
    }

    @Test
    public void testGetBlobMaskedBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_blob;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE lobtest (id INT, secret_blob BLOB)");
                stmt.execute("INSERT INTO lobtest (id, secret_blob) VALUES (1, CAST('secret data' AS BLOB))");
            }
        }
        String url = "jdbc:mask[columns=secret_blob]:jdbc:h2:mem:test_blob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_blob FROM lobtest WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Blob blob = rs.getBlob("secret_blob");
                        assertNotNull("Should not be null if bypassed", blob);
                        fail("VULNERABILITY CONFIRMED: getBlob bypassed masking");
                    } catch (SQLException e) {
                        // This is what we want after the fix
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetClobMaskedBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_clob;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE lobtest2 (id INT, secret_clob CLOB)");
                stmt.execute("INSERT INTO lobtest2 (id, secret_clob) VALUES (1, CAST('secret data' AS CLOB))");
            }
        }
        String url = "jdbc:mask[columns=secret_clob]:jdbc:h2:mem:test_clob;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_clob FROM lobtest2 WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Clob clob = rs.getClob("secret_clob");
                        assertNotNull("Should not be null if bypassed", clob);
                        fail("VULNERABILITY CONFIRMED: getClob bypassed masking");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testGetObjectWithClassBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_obj;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE objtest (id INT, secret_int INT)");
                stmt.execute("INSERT INTO objtest VALUES (1, 12345)");
            }
        }
        String url = "jdbc:mask[columns=secret_int]:jdbc:h2:mem:test_obj;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT secret_int FROM objtest WHERE id = 1")) {
                    assertTrue(rs.next());
                    try {
                        Integer val = rs.getObject("secret_int", Integer.class);
                        assertNotNull("Should not be null if bypassed", val);
                        fail("VULNERABILITY CONFIRMED: getObject(String, Class) bypassed masking");
                    } catch (SQLException e) {
                        assertTrue(e.getMessage().contains("masked"));
                    }
                }
            }
        }
    }

    @Test
    public void testAliasBypass() throws SQLException {
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE altest (id INT, ssn VARCHAR(100))");
                stmt.execute("INSERT INTO altest VALUES (1, '123-45-6789')");
            }
        }
        String url = "jdbc:mask[columns=ssn]:jdbc:h2:mem:test_alias;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ssn AS secret_alias FROM altest WHERE id = 1")) {
                    assertTrue(rs.next());
                    String val = rs.getString("secret_alias");
                    if ("123-45-6789".equals(val)) {
                        fail("VULNERABILITY CONFIRMED: Column alias bypassed masking");
                    }
                    assertTrue(val.startsWith("*"));
                }
            }
        }
    }
}
